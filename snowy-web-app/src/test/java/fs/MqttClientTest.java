package fs;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT 客户端测试工具
 * <p>
 * 模拟 Publisher（user001 / model01）定时发布三类仿真消息，
 * 同时作为 Subscriber 订阅当前用户所有仿真消息并打印到控制台。
 * <p>
 * 用法:
 * <pre>
 *   java fs.test.mqttclient.MqttClientTest [brokerUrl]
 *   默认 brokerUrl = tcp://localhost:1883
 * </pre>
 * 按 Ctrl+C 优雅退出。
 */
public class MqttClientTest {

    // ==================== 测试常量 ====================
    private static final String USERNAME   = "user001";
    private static final String MODEL_ID   = "model01";
    private static final String TOPIC_BASE = "factory/sim/" + USERNAME + "/" + MODEL_ID;

    // 订阅 topic: 匹配当前用户下所有模型、所有消息类型
    private static final String SUB_TOPIC = "factory/sim/" + USERNAME + "/+/+";

    // ==================== 消息类型枚举 ====================
    enum MsgType {
        STATUS("status"),
        PROGRESS("progress"),
        CONSOLE("console");

        final String value;
        MsgType(String value) { this.value = value; }
    }

    // ==================== 状态模拟 ====================
    private static final AtomicInteger stepCounter = new AtomicInteger(0);
    private static final String[] STATES = {"running", "running", "running", "running", "paused",
                                            "running", "running", "finished"};
    private static final String[] LOG_LEVELS = {"INFO", "INFO", "WARN", "INFO", "ERROR",
                                                 "INFO", "INFO", "INFO"};
    private static final String[] LOG_CONTENTS = {
            "Simulation initialized",
            "AGV001 start from warehouse",
            "AGV002 battery level below 20%, heading to charging station",
            "AGV003 arrived at processing station W01",
            "AGV001 path planning conflict, recalculating",
            "AGV002 charging complete, resuming task",
            "AGV003 cargo loading complete, heading to warehouse",
            "Simulation running stably, all AGVs operating normally"
    };

    public static void main(String[] args) throws InterruptedException {
        String brokerUrl = (args.length > 0) ? args[0] : "tcp://localhost:1883";
        String clientId = "mqttClient_" + USERNAME + "_" + System.currentTimeMillis();

        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  MQTT 仿真测试客户端");
        System.out.println("  Broker : " + brokerUrl);
        System.out.println("  用户   : " + USERNAME);
        System.out.println("  模型   : " + MODEL_ID);
        System.out.println("  订阅   : " + SUB_TOPIC);
        System.out.println("  ClientId: " + clientId);
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  结束调试，处理退出信号");
        System.out.println();

        // 用 CountDownLatch 阻塞主线程，替代 while(true) 轮询
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[STOP] 收到退出信号，正在停止...");
            shutdownLatch.countDown();
        }));

        MqttClient mqttClient = null;
        ScheduledExecutorService scheduler = null;

        try {
            // 创建 MQTT 客户端（非持久化）
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            // 连接选项
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setConnectionTimeout(10);
            connOpts.setKeepAliveInterval(30);
            connOpts.setAutomaticReconnect(true);

            // 设置订阅回调：打印收到的消息
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("  [!] 连接断开: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    System.out.println("  [← 接收] " + topic);
                    // 格式化打印 JSON
                    try {
                        Object obj = JSON.parse(payload);
                        String pretty = JSON.toJSONString(obj);
                        for (String line : pretty.split("\n")) {
                            System.out.println("    " + line);
                        }
                    } catch (Exception e) {
                        System.out.println("    " + payload);
                    }
                    System.out.println();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 发布完成回调（不需要处理）
                }
            });

            // 连接
            System.out.print("  [*] 正在连接... ");
            mqttClient.connect(connOpts);
            System.out.println("已连接");

            // 订阅
            mqttClient.subscribe(SUB_TOPIC, 0);
            System.out.println("  [*] 已订阅: " + SUB_TOPIC);
            System.out.println();

            // 定时发布三类消息（每 2 秒一轮），使用 daemon 线程防止阻止 JVM 退出
            MqttClient finalClient = mqttClient;
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-publisher");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    int cycle = stepCounter.incrementAndGet();

                    publishStatus(finalClient, cycle);
                    Thread.sleep(50);
                    publishProgress(finalClient, cycle);
                    Thread.sleep(50);
                    publishConsole(finalClient, cycle);

                } catch (Exception e) {
                    System.err.println("  [!] 发布失败: " + e.getMessage());
                }
            }, 1, 2, TimeUnit.SECONDS);

            // 阻塞等待退出信号
            shutdownLatch.await();

        } catch (MqttException e) {
            System.err.println("  [!] MQTT 异常: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("  [!] 异常: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // 优雅关闭（不阻塞退出）
            System.out.println("  [*] 正在断开连接...");
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (mqttClient != null) {
                try {
                    mqttClient.disconnect(3000);
                } catch (Exception ignored) {
                    // ignore disconnect timeout
                }
                try {
                    mqttClient.close();
                } catch (Exception ignored) {
                    // ignore close error
                }
            }
            System.out.println("  [OK] 已断开，程序退出");
        }
    }

    // ==================== 发布方法 ====================

    /** 发布仿真状态消息 */
    private static void publishStatus(MqttClient client, int cycle) throws MqttException {
        String topic = TOPIC_BASE + "/" + MsgType.STATUS.value;
        String state = STATES[(cycle - 1) % STATES.length];

        JSONObject payload = new JSONObject();
        payload.put("username", USERNAME);
        payload.put("modelId", MODEL_ID);
        payload.put("state", state);
        payload.put("timestamp", Instant.now().toEpochMilli());

        publish(client, topic, payload);
    }

    /** 发布仿真进度消息 */
    private static void publishProgress(MqttClient client, int cycle) throws MqttException {
        String topic = TOPIC_BASE + "/" + MsgType.PROGRESS.value;
        int totalStep = 2000;
        int simTimeStep = Math.min(cycle * 250, totalStep);
        double progress = (double) simTimeStep / totalStep * 100;

        JSONObject payload = new JSONObject();
        payload.put("username", USERNAME);
        payload.put("modelId", MODEL_ID);
        payload.put("progress", Math.round(progress * 10.0) / 10.0);
        payload.put("simTimeStep", simTimeStep);
        payload.put("totalStep", totalStep);
        payload.put("timestamp", Instant.now().toEpochMilli());

        publish(client, topic, payload);
    }

    /** 发布控制台日志消息 */
    private static void publishConsole(MqttClient client, int cycle) throws MqttException {
        String topic = TOPIC_BASE + "/" + MsgType.CONSOLE.value;
        int idx = (cycle - 1) % LOG_CONTENTS.length;

        JSONObject payload = new JSONObject();
        payload.put("username", USERNAME);
        payload.put("modelId", MODEL_ID);
        payload.put("level", LOG_LEVELS[idx]);
        payload.put("content", LOG_CONTENTS[idx]);
        payload.put("timestamp", Instant.now().toEpochMilli());

        publish(client, topic, payload);
    }

    /** 通用发布方法 */
    private static void publish(MqttClient client, String topic, JSONObject payload) throws MqttException {
        String jsonStr = JSON.toJSONString(payload);
        MqttMessage message = new MqttMessage(jsonStr.getBytes());
        message.setQos(0);
        message.setRetained(false);
        client.publish(topic, message);

        System.out.println("  [→ 发送] " + topic);
        System.out.println("    " + jsonStr);
    }
}
