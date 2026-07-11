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
package vip.xiaonuo.fscloud.modular.simulation.task;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.modelfile.service.FsModelFileService;
import vip.xiaonuo.fscloud.modular.simulation.SimStatusEnum;
import vip.xiaonuo.fscloud.modular.simulation.service.McpClientService;
import vip.xiaonuo.fscloud.modular.simulation.service.SimulationModelRecord;
import vip.xiaonuo.fscloud.modular.simulation.service.SimulationModelTrackingMap;

import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 仿真模型同步定时任务
 * <p>
 * 每 10 秒调用 MCP listRemoteSimulationModels 获取当前已加载模型列表，
 * 与 {@link SimulationModelTrackingMap} 同步：<br>
 * - MCP 中存在的模型 → 更新 tracking map 的 status/simTime<br>
 * - MCP 中不存在的模型 → 从 tracking map 删除，DB simStatus 置为 SIM_STATUS_NOTYET
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Slf4j
@Component
public class SimulationSyncTask {

    @Resource
    private McpClientService mcpClientService;

    @Resource
    private SimulationModelTrackingMap trackingMap;

    @Resource
    private FsModelFileService fsModelFileService;

    /**
     * 每 10 秒执行一次
     */
    @Scheduled(fixedRate = 10000)
    public void syncLoadedModels() {
        try {
            // 1. 调用 MCP 获取当前已加载的模型列表
            JSONObject mcpResult = mcpClientService.listModels(null);
            if (mcpResult == null || !mcpResult.getBool("success", false)) {
                log.warn("MCP listModels failed: {}", mcpResult);
                return;
            }

            // 2. 解析 MCP 返回的 modelId 集合
            Set<String> activeMcpKeys = new HashSet<>();
            JSONObject data = mcpResult.getJSONObject("data");
            if (data != null) {
                JSONArray models = data.getJSONArray("models");
                if (models != null) {
                    for (int i = 0; i < models.size(); i++) {
                        JSONObject m = models.getJSONObject(i);
                        String userId = m.getStr("userId");
                        String modelId = m.getStr("modelId");
                        String status = m.getStr("status");
                        String recordIdStr = m.getStr("recordId");
                        Long simTime = m.getLong("simTime");

                        if (userId != null && modelId != null) {
                            String key = userId + "_" + modelId;
                            activeMcpKeys.add(key);

                            // 更新 tracking map
                            SimulationModelRecord record = trackingMap.get(userId, modelId);
                            if (record != null) {
                                record.setStatus(status);
                                if (simTime != null) record.setSimTime(simTime);
                            } else {
                                // tracking map 中没有该记录（例如后端重启后），从 DB 重建
                                rebuildTrackingRecord(userId, modelId, status, simTime, recordIdStr);
                            }
                        }
                    }
                }
            }

            // 3. 遍历 tracking map，移除不在 MCP 列表中的记录
            for (String key : trackingMap.getAllKeys()) {
                if (!activeMcpKeys.contains(key)) {
                    // 从 key 中解析 userId 和 modelId
                    int underscoreIdx = key.indexOf('_');
                    if (underscoreIdx > 0) {
                        String userId = key.substring(0, underscoreIdx);
                        String modelId = key.substring(underscoreIdx + 1);
                        SimulationModelRecord removed = trackingMap.remove(userId, modelId);
                        if (removed != null && removed.getRecordId() != null) {
                            // 更新 DB simStatus：仿真次数>0 置为 ALREADY，否则置为 NOTYET
                            FsModelFile dbRecord = fsModelFileService.getById(removed.getRecordId());
                            if (dbRecord != null) {
                                if (dbRecord.getSimTimes() != null && dbRecord.getSimTimes() > 0) {
                                    dbRecord.setSimStatus(SimStatusEnum.SIM_STATUS_ALREADY.getCode());
                                } else {
                                    dbRecord.setSimStatus(SimStatusEnum.SIM_STATUS_NOTYET.getCode());
                                }
                                fsModelFileService.updateById(dbRecord);
                                log.info("Model unloaded, DB updated: recordId={}", removed.getRecordId());
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("SimulationSyncTask error: {}", e.getMessage());
        }
    }

    /**
     * 根据 MCP 返回的 recordId（即 fs_model_file 表主键）查询 DB 记录，重建 tracking map 条目
     */
    private void rebuildTrackingRecord(String userId, String modelId, String status, Long simTime, String recordIdStr) {
        try {
            // 使用 recordId（fs_model_file 表主键）直接查询
            Long fileId;
            try {
                fileId = Long.parseLong(recordIdStr);
            } catch (NumberFormatException e) {
                log.warn("Cannot rebuild tracking record: invalid recordIdStr={}", recordIdStr);
                return;
            }
            FsModelFile dbRecord = fsModelFileService.getById(fileId);
            if (dbRecord == null) {
                log.warn("Cannot rebuild tracking record: no DB record found for recordId={}", recordIdStr);
                return;
            }

            // 同步 DB 的 modelId 和 simStatus 与 MCP 实际状态一致
            String mappedStatus = SimulationModelTrackingMap.mapMcpStatusToSimStatus(status);
            if (mappedStatus != null) {
                dbRecord.setModelId(modelId);
                dbRecord.setSimStatus(mappedStatus);
                fsModelFileService.updateById(dbRecord);
            }

            // 重建 tracking record
            SimulationModelRecord trackingRecord = new SimulationModelRecord();
            trackingRecord.setRecordId(dbRecord.getId());
            trackingRecord.setUserId(userId);
            trackingRecord.setModelId(modelId);
            trackingRecord.setModelFileName(dbRecord.getModel());
            trackingRecord.setStatus(status);
            trackingRecord.setSimTime(simTime);
            trackingMap.put(trackingRecord);
            log.info("Rebuilt tracking record: recordId={}, userId={}, modelId={}, status={}", recordIdStr, userId, modelId, status);
        } catch (Exception e) {
            log.warn("rebuildTrackingRecord error: {}", e.getMessage());
        }
    }
}
