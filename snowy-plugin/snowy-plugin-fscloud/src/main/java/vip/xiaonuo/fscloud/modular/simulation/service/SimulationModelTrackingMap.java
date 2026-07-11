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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.simulation.SimStatusEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仿真模型跟踪 Map
 * <p>
 * 内存中跟踪已加载到 FS 云仿真服务的模型。
 * key 格式：{@code userId + "_" + modelId}（例如 "devAdmin_800d121e6507"）<br>
 * value：{@link SimulationModelRecord}
 * <p>
 * 当 loadModel 成功时写入，MCP 轮询和 MQTT 消息更新，
 * 模型被卸载时删除。
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Slf4j
@Component
public class SimulationModelTrackingMap {

    private final ConcurrentHashMap<String, SimulationModelRecord> cache = new ConcurrentHashMap<>();

    /** 构建 key */
    public String buildKey(String userId, String modelId) {
        return userId + "_" + modelId;
    }

    /** 写入或更新 */
    public void put(SimulationModelRecord record) {
        String key = buildKey(record.getUserId(), record.getModelId());
        cache.put(key, record);
        log.debug("TrackingMap put: key={}, status={}", key, record.getStatus());
    }

    /** 获取 */
    public SimulationModelRecord get(String userId, String modelId) {
        return cache.get(buildKey(userId, modelId));
    }

    /** 删除 */
    public SimulationModelRecord remove(String userId, String modelId) {
        SimulationModelRecord removed = cache.remove(buildKey(userId, modelId));
        if (removed != null) {
            log.info("TrackingMap remove: userId={}, modelId={}", userId, modelId);
        }
        return removed;
    }

    /** 是否存在 */
    public boolean containsKey(String userId, String modelId) {
        return cache.containsKey(buildKey(userId, modelId));
    }

    /** 获取当前用户的所有跟踪记录 */
    public List<SimulationModelRecord> getByUserId(String userId) {
        List<SimulationModelRecord> result = new ArrayList<>();
        String prefix = userId + "_";
        for (java.util.Map.Entry<String, SimulationModelRecord> entry : cache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    /** 通过 recordId（fs_model_file id）查找 */
    public SimulationModelRecord getByRecordId(Long recordId) {
        for (SimulationModelRecord r : cache.values()) {
            if (r.getRecordId() != null && r.getRecordId().equals(recordId)) {
                return r;
            }
        }
        return null;
    }

    /** 获取所有 key */
    public Set<String> getAllKeys() {
        return cache.keySet();
    }

    /** 获取全部记录 */
    public List<SimulationModelRecord> getAll() {
        return new ArrayList<>(cache.values());
    }

    /** 清空 */
    public void clear() {
        cache.clear();
    }

    /** 通过 recordId 查找对应的 key */
    public String findKeyByRecordId(Long recordId) {
        for (java.util.Map.Entry<String, SimulationModelRecord> entry : cache.entrySet()) {
            if (entry.getValue().getRecordId() != null
                    && entry.getValue().getRecordId().equals(recordId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 将 tracking map 中的实时状态合并到 DB 实体上
     * <p>
     * 用于 {@code /fscloud/modelfile/listByIds} 接口，
     * 使前端一次调用同时拿到 DB 最新数据 + tracking 实时状态。
     *
     * @param dbRecord 从 DB 查询的 FsModelFile 实体
     * @return 已合并的 FsModelFile 实体（tracking 中的 simStatus/modelId 覆盖 DB 值）
     */
    public FsModelFile mergeDbWithTracking(FsModelFile dbRecord) {
        if (dbRecord == null) return null;
        SimulationModelRecord tracking = getByRecordId(dbRecord.getId());
        if (tracking != null) {
            // 将 MCP status 映射为 SimStatusEnum 后覆盖 DB 的 simStatus
            String mappedStatus = mapMcpStatusToSimStatus(tracking.getStatus());
            if (mappedStatus != null) {
                dbRecord.setSimStatus(mappedStatus);
            }
            // modelId 以 tracking map 为准
            if (tracking.getModelId() != null) {
                dbRecord.setModelId(tracking.getModelId());
            }
        } else {
            // 未找到对应 tracking 记录，如果dbRecord的simStatus修改为NOTYET(simTimes为0)或ALREADY（simTimes大于0）。
            dbRecord.setModelId(null);
            dbRecord.setSimStatus(SimStatusEnum.SIM_STATUS_NOTYET.getCode());
            if (dbRecord.getSimTimes() > 0) {
                dbRecord.setSimStatus(SimStatusEnum.SIM_STATUS_ALREADY.getCode());
            }
        }
        return dbRecord;
    }

    /**
     * MCP status → SimStatusEnum 映射
     */
    public static String mapMcpStatusToSimStatus(String mcpStatus) {
        if (mcpStatus == null) return null;
        return switch (mcpStatus.toUpperCase()) {
            case "LOADED" -> SimStatusEnum.SIM_STATUS_WAITING.getCode();
            case "RUNNING" -> SimStatusEnum.SIM_STATUS_GOING.getCode();
            case "PAUSED" -> SimStatusEnum.SIM_STATUS_PAUSED.getCode();
            case "FINISHED" -> SimStatusEnum.SIM_STATUS_FINISHED.getCode();
            case "STOPPED" -> SimStatusEnum.SIM_STATUS_STOPPED.getCode();
            case "FAILED" -> SimStatusEnum.SIM_STATUS_FAILED.getCode();
            default -> null;
        };
    }
}
