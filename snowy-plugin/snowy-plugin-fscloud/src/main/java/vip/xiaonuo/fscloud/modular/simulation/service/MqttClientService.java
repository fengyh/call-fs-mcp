/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 *
 * Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *
 * 1.请不要删除和修改根目录下的LICENSE文件。
 * 2.请不要删除和修改Snowy源码头部的版权声明。
 * 3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 * 4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 * 5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 * 6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
package vip.xiaonuo.fscloud.modular.simulation.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import vip.xiaonuo.fscloud.config.MqttProperties;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.modelfile.service.FsModelFileService;

import jakarta.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MQTT 客户端服务
 * <p>
 * 订阅 FS 云仿真服务的实时消息（状态/进度/控制台日志），
 * 更新到 {@link SimulationModelTrackingMap} 中。
 * <p>
 * 连接容错机制：<br>
 * - 应用启动后若 Broker 未就绪，每 10 秒重试直到连接成功<br>
 * - 连接稳定后若 Broker 重启，Paho 自动重连（{@code setAutomaticReconnect(true)}），
 *   重连成功后自动重新订阅 topic
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Slf4j
@Service
public class MqttClientService {

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private SimulationModelTrackingMap trackingMap;

    @Resource
    private FsModelFileService fsModelFileService;

    private MqttClient mqttClient;
    private volatile boolean connected = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mqtt-callback");
        t.setDaemon(true);
        return t;
    });

    /**
     * 应用完全就绪后启动 MQTT 连接。
     * 使用 {@code ApplicationReadyEvent} 而非 {@code @PostConstruct}，
     * 确保不阻塞 Spring 的启动流程。
     * <p>
     * 如果 Broker 未就绪，每隔 10 秒重试。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        executor.submit(() -> {
            while (true) {
                try {
                    connectAndSubscribe();
                    connected = true;
                    break; // 连接成功，退出重试循环
                } catch (Exception e) {
                    log.warn("MQTT connect failed, retry in 10s: {}", e.getMessage());
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    /** 连接并订阅 */
    private void connectAndSubscribe() throws Exception {
        String clientId = mqttProperties.getClientId() + "_" + System.currentTimeMillis();
        mqttClient = new MqttClient(mqttProperties.getBrokerUrl(), clientId, new MemoryPersistence());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setConnectionTimeout(mqttProperties.getConnectionTimeout());
        opts.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());
        opts.setAutomaticReconnect(true);

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    log.info("MQTT reconnected to {}, re-subscribing...", serverURI);
                    subscribeTopic();
                }
                connected = true;
            }

            @Override
            public void connectionLost(Throwable cause) {
                connected = false;
                log.warn("MQTT connection lost: {}", cause.getMessage());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // not used
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload());
                handleMessage(topic, payload);
            }
        });

        // 使用 connectWithResult 捕获连接成功事件，在 connectComplete 回调中订阅
        mqttClient.connect(opts);
        // 连接成功后立即订阅
        subscribeTopic();
        connected = true;
        log.info("MQTT connected and subscribed to {}", mqttProperties.getSubTopic());
    }

    /**
     * 订阅 topic，在初始连接和断线重连后调用
     */
    private void subscribeTopic() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.subscribe(mqttProperties.getSubTopic(), 0);
                log.debug("MQTT subscribed to {}", mqttProperties.getSubTopic());
            }
        } catch (Exception e) {
            log.warn("MQTT subscribe failed: {}", e.getMessage());
        }
    }

    /** 断开连接 */
    private void disconnect() {
        try {
            if (mqttClient != null) {
                mqttClient.disconnect(3000);
                mqttClient.close();
                log.info("MQTT disconnected");
            }
        } catch (Exception e) {
            log.warn("MQTT disconnect error: {}", e.getMessage());
        }
    }

    /**
     * 解析 MQTT 消息，更新 tracking map
     * <p>
     * topic 格式: factory/sim/{username}/{modelId}/{type}
     * type = status / progress / console
     */
    private void handleMessage(String topic, String payload) {
        try {
            // 解析 topic：factory/sim/{username}/{modelId}/{type}
            String[] parts = topic.split("/");
            if (parts.length < 5) return;
            String username = parts[2];
            String modelId = parts[3];
            String type = parts[4];

            JSONObject json = JSONUtil.parseObj(payload);

            // 从 tracking map 获取记录
            SimulationModelRecord record = trackingMap.get(username, modelId);
            if (record == null) {
                log.debug("MQTT message for unknown model: {}", topic);
                return;
            }

            switch (type) {
                case "status" -> handleStatusMessage(record, json, username);
                case "progress" -> handleProgressMessage(record, json);
                case "console" -> handleConsoleMessage(record, json);
                default -> log.debug("Unknown MQTT message type: {}", type);
            }
        } catch (Exception e) {
            log.warn("MQTT handleMessage error: {}", e.getMessage());
        }
    }

    /** 处理 status 消息 */
    private void handleStatusMessage(SimulationModelRecord record, JSONObject json, String username) {
        String state = json.getStr("state"); // running / paused / finished
        if (state == null) return;

        String mcpStatus = switch (state) {
            case "running" -> "RUNNING";
            case "paused" -> "PAUSED";
            case "finished" -> "FINISHED";
            default -> null;
        };
        if (mcpStatus == null) return;

        record.setStatus(mcpStatus);
        Long timestamp = json.getLong("timestamp");
        if (timestamp != null) {
            if ("RUNNING".equals(mcpStatus)) {
                record.setStartedAt(timestamp);
            } else if ("FINISHED".equals(mcpStatus)) {
                record.setFinishedAt(timestamp);
            }
        }

        // 同步 DB 的 simStatus
        String simStatus = SimulationModelTrackingMap.mapMcpStatusToSimStatus(mcpStatus);
        if (simStatus != null && record.getRecordId() != null) {
            FsModelFile dbRecord = fsModelFileService.getById(record.getRecordId());
            if (dbRecord != null) {
                dbRecord.setSimStatus(simStatus);
                fsModelFileService.updateById(dbRecord);
            }
        }
        log.info("MQTT status update: modelId={}, state={}", record.getModelId(), state);
    }

    /** 处理 progress 消息 */
    private void handleProgressMessage(SimulationModelRecord record, JSONObject json) {
        Double progress = json.getDouble("progress");
        Integer simTimeStep = json.getInt("simTimeStep");
        if (progress != null) {
            record.setSimProgress(progress);
        }
        if (simTimeStep != null) {
            record.setSimTime(simTimeStep.longValue());
        }
    }

    /** 处理 console 消息 */
    private void handleConsoleMessage(SimulationModelRecord record, JSONObject json) {
        String level = json.getStr("level");
        String content = json.getStr("content");
        Long timestamp = json.getLong("timestamp");
        if (content != null) {
            record.addConsoleLog(level != null ? level : "INFO", content,
                    timestamp != null ? timestamp : System.currentTimeMillis());
        }
    }
}
