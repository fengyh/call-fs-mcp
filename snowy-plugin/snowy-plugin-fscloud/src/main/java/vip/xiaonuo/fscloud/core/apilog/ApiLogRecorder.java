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
package vip.xiaonuo.fscloud.core.apilog;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vip.xiaonuo.fscloud.config.ApiLogProperties;
import vip.xiaonuo.fscloud.modular.apilog.entity.ApiLog;
import vip.xiaonuo.fscloud.modular.apilog.service.ApiLogService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 访问日志记录器
 * <p>
 * 业务线程只做入队，由单个后台线程批量写入 dm8，写库异常不影响业务请求。
 *
 * @author fengyuhe
 * @date  2026/10/29
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "fscloud.api-log", name = "enabled", havingValue = "true")
public class ApiLogRecorder {

    private final ApiLogProperties properties;

    private final ApiLogService apiLogService;

    private final BlockingQueue<ApiLog> queue;

    private final AtomicLong droppedCount = new AtomicLong();

    private volatile boolean running = true;

    private Thread worker;

    public ApiLogRecorder(ApiLogProperties properties, ApiLogService apiLogService) {
        this.properties = properties;
        this.apiLogService = apiLogService;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, properties.getQueueCapacity()));
    }

    /**
     * 记录一次接口访问（不会抛出任何异常）
     *
     * @param endpoint 接口地址
     * @author fengyuhe
     * @date 2026/10/29
     */
    public void record(String endpoint) {
        try {
            ApiLog apiLog = new ApiLog();
            apiLog.setAccessTime(new Date());
            apiLog.setEndpoint(truncate(endpoint));
            if (!queue.offer(apiLog)) {
                long dropped = droppedCount.incrementAndGet();
                if (dropped % 1000 == 1) {
                    log.warn("API_LOG queue is full, dropped {} records", dropped);
                }
            }
        } catch (Exception e) {
            log.warn("record API_LOG failed: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void start() {
        worker = new Thread(this::consume, "api-log-writer");
        worker.setDaemon(true);
        worker.start();
        log.info("API_LOG writer started, queueCapacity={}, batchSize={}", properties.getQueueCapacity(),
                properties.getBatchSize());
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            List<ApiLog> rest = new ArrayList<>();
            queue.drainTo(rest);
            saveQuietly(rest);
        } catch (Exception e) {
            log.warn("flush API_LOG on shutdown failed: {}", e.getMessage());
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    /**
     * 后台消费：空闲时等待，有数据时按 batchSize 攒批写入
     *
     * @author fengyuhe
     * @date 2026/10/29
     */
    private void consume() {
        while (running) {
            try {
                ApiLog first = queue.poll(Math.max(1, properties.getFlushIntervalMs()), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<ApiLog> batch = new ArrayList<>(Math.max(1, properties.getBatchSize()));
                batch.add(first);
                queue.drainTo(batch, Math.max(0, properties.getBatchSize() - 1));
                saveQuietly(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("API_LOG writer loop error: {}", e.getMessage());
            }
        }
    }

    private void saveQuietly(List<ApiLog> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            apiLogService.saveLogs(batch);
        } catch (Exception e) {
            log.warn("save API_LOG batch failed, size={}, error={}", batch.size(), e.getMessage());
        }
    }

    /**
     * 按 UTF-8 字节安全截断，避免切断多字节字符
     *
     * @author fengyuhe
     * @date 2026/10/29
     */
    private String truncate(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        int maxBytes = Math.max(1, properties.getMaxEndpointBytes());
        byte[] bytes = endpoint.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return endpoint;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }
}
