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
package vip.xiaonuo.fscloud.modular.simulation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vip.xiaonuo.common.annotation.CommonLog;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.common.pojo.CommonResult;
import vip.xiaonuo.fscloud.modular.modelfile.entity.FsModelFile;
import vip.xiaonuo.fscloud.modular.modelfile.service.FsModelFileService;
import vip.xiaonuo.fscloud.modular.simulation.SimStatusEnum;
import vip.xiaonuo.fscloud.modular.simulation.dto.*;
import vip.xiaonuo.fscloud.modular.simulation.param.*;
import vip.xiaonuo.fscloud.modular.simulation.service.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仿真控制控制器
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Tag(name = "仿真控制控制器")
@RestController
@Validated
@Slf4j
public class SimController {

    /** fscloud 上传文件根路径，从 application 配置读取 */
    @org.springframework.beans.factory.annotation.Value("${fscloud.sim.static-path:${user.dir}${file.separator}static}")
    private String fscloudSimStaticPath;

    @Resource
    private McpClientService mcpClientService;

    @Resource
    private FsModelFileService fsModelFileService;

    @Resource
    private SimulationModelTrackingMap trackingMap;

    // ==================== 加载模型 ====================

    /**
     * 加载模型到 FS 云仿真服务
     */
    @Operation(summary = "加载模型")
    @CommonLog("加载模型")
    @SaCheckPermission("/fscloud/sim/loadModel")
    @PostMapping("/fscloud/sim/loadModel")
    public CommonResult<LoadModelDto> loadModel(@RequestBody @Valid LoadModelParam loadModelParam) {
        log.info("loadModelParam: {}", loadModelParam);

        Long fileId = loadModelParam.getId();
        FsModelFile record = fsModelFileService.getById(fileId);
        if (record == null) {
            throw new CommonException("仿真模型记录不存在，id={}", fileId);
        }
        Boolean loadInUI = loadModelParam.getLoadInUI();

        String userId = StpUtil.getLoginIdAsString();

        String modelRelativePath = record.getModel();
        if (StrUtil.isBlank(modelRelativePath)) {
            throw new CommonException("模型文件路径为空，无法加载，id={}", fileId);
        }
        Long recordUserId = record.getUserId();
        if (recordUserId == null) {
            throw new CommonException("模型记录缺少 userId，无法定位文件，id={}", fileId);
        }
        Path fullPath = Paths.get(fscloudSimStaticPath, "uploaded",
                String.valueOf(recordUserId), modelRelativePath);
        String modelFileName = fullPath.toString();

        log.info("Loading model: userId={}, fileId={}, modelFileName={}, loadInUI={}", userId, fileId, modelFileName, loadInUI);

        LoadModelDto dto = new LoadModelDto();
        dto.setModelName(record.getName());
        dto.setRecordId(String.valueOf(fileId));

        JSONObject mcpResult;
        try {
            mcpResult = mcpClientService.loadModel(userId, String.valueOf(fileId), modelFileName, loadInUI);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when loading model: {}", e.getMessage());
            dto.setMessage(e.getMessage());
            return CommonResult.data(dto);
        }

        boolean success = mcpResult.getBool("success", false);
        if (!success) {
            String errorMessage = mcpResult.getStr("message", "加载模型失败");
            log.warn("loadModel failed: {}", errorMessage);
            dto.setMessage(errorMessage);
            return CommonResult.data(dto);
        }

        JSONObject data = mcpResult.getJSONObject("data");

        // 更新 DB
        dto.setStatus(SimStatusEnum.SIM_STATUS_WAITING.getCode());
        record.setSimStatus(SimStatusEnum.SIM_STATUS_WAITING.getCode());
        String mcpModelId = data != null ? data.getStr("modelId") : null;
        if (StrUtil.isNotBlank(mcpModelId)) {
            record.setModelId(mcpModelId);
        }
        fsModelFileService.updateById(record);

        // 写入 tracking map
        SimulationModelRecord trackingRecord = new SimulationModelRecord();
        trackingRecord.setRecordId(fileId);
        trackingRecord.setUserId(userId);
        trackingRecord.setModelId(mcpModelId);
        if (data != null) {
            trackingRecord.setModelFileName(data.getStr("modelFileName"));
            trackingRecord.setModelFilePath(data.getStr("modelFilePath"));
            trackingRecord.setStatus(data.getStr("status"));
            trackingRecord.setSimTime(data.getLong("simTime"));
            trackingRecord.setMessage(data.getStr("message"));
            trackingRecord.setLoadedAt(data.getLong("loadedAt"));
            trackingRecord.setStartedAt(data.getLong("startedAt"));
            trackingRecord.setFinishedAt(data.getLong("finishedAt"));
        }
        trackingMap.put(trackingRecord);

        // DTO 填充
        if (data != null) {
            dto.setUserId(data.getStr("userId"));
            dto.setModelId(data.getStr("modelId"));
            dto.setModelFileName(data.getStr("modelFileName"));
            dto.setModelFilePath(data.getStr("modelFilePath"));
            dto.setSimTime(data.getLong("simTime"));
            dto.setMessage(data.getStr("message"));
            dto.setLoadedAt(data.getLong("loadedAt"));
            dto.setStartedAt(data.getLong("startedAt"));
            dto.setFinishedAt(data.getLong("finishedAt"));
        }
        log.info("Model loaded successfully, modelId={}", dto.getModelId());
        return CommonResult.data(dto);
    }

    // ==================== 启动仿真 ====================

    @Operation(summary = "启动仿真")
    @CommonLog("启动仿真")
    @SaCheckPermission("/fscloud/sim/runModel")
    @PostMapping("/fscloud/sim/runModel")
    public CommonResult<RunModelDto> runModel(@RequestBody @Valid RunModelParam runModelParam) {
        log.info("runModelParam: {}", runModelParam);

        String userId = StpUtil.getLoginIdAsString();
        String modelId = runModelParam.getModelId();
        Long fileId = runModelParam.getId();

        RunModelDto dto = new RunModelDto();
        JSONObject mcpResult;
        try {
            boolean showAnimation = Boolean.TRUE.equals(runModelParam.getShowAnimation());
            mcpResult = mcpClientService.startSimulation(userId, modelId, showAnimation);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when starting simulation: {}", e.getMessage());
            dto.setMessage(e.getMessage());
            return CommonResult.data(dto);
        }

        boolean success = mcpResult.getBool("success", false);
        if (!success) {
            String errorMessage = mcpResult.getStr("message", "启动仿真失败");
            log.warn("runModel failed: {}", errorMessage);
            dto.setMessage(errorMessage);
            return CommonResult.data(dto);
        }

        // 更新 DB
        FsModelFile record = fsModelFileService.getById(fileId);
        if (record != null) {
            record.setSimStatus(SimStatusEnum.SIM_STATUS_GOING.getCode());
            record.setSimTimes(record.getSimTimes() == null ? 1 : record.getSimTimes() + 1);
            fsModelFileService.updateById(record);
            dto.setSimStatus(record.getSimStatus());
            dto.setSimTimes(record.getSimTimes());
        }

        // 更新 tracking map
        JSONObject data = mcpResult.getJSONObject("data");
        if (data != null) {
            SimulationModelRecord trackingRecord = trackingMap.get(userId, modelId);
            if (trackingRecord != null) {
                trackingRecord.setStatus(data.getStr("status"));
                trackingRecord.setRunId(data.getStr("runId"));
                trackingRecord.setSimTime(data.getLong("simTime"));
                trackingRecord.setMessage(data.getStr("message"));
                trackingRecord.setStartedAt(data.getLong("startedAt"));
            }
            dto.setUserId(data.getStr("userId"));
            dto.setModelId(data.getStr("modelId"));
            dto.setModelFileName(data.getStr("modelFileName"));
            dto.setRunId(data.getStr("runId"));
            dto.setStatus(data.getStr("status"));
            dto.setSimTime(data.getLong("simTime"));
            dto.setMessage(data.getStr("message"));
        }
        log.info("Simulation started successfully, status={}, runId={}", dto.getStatus(), dto.getRunId());
        return CommonResult.data(dto);
    }

    // ==================== 停止仿真 ====================

    @Operation(summary = "停止仿真")
    @CommonLog("停止仿真")
    @SaCheckPermission("/fscloud/sim/stopModel")
    @PostMapping("/fscloud/sim/stopModel")
    public CommonResult<RunModelDto> stopModel(@RequestBody @Valid RunModelParam param) {
        return executeControlAction("stop", param, mcpClientService::stopSimulation);
    }

    // ==================== 暂停仿真 ====================

    @Operation(summary = "暂停仿真")
    @CommonLog("暂停仿真")
    @SaCheckPermission("/fscloud/sim/pauseModel")
    @PostMapping("/fscloud/sim/pauseModel")
    public CommonResult<RunModelDto> pauseModel(@RequestBody @Valid RunModelParam param) {
        return executeControlAction("pause", param, mcpClientService::pauseSimulation);
    }

    // ==================== 继续仿真 ====================

    @Operation(summary = "继续仿真")
    @CommonLog("继续仿真")
    @SaCheckPermission("/fscloud/sim/resumeModel")
    @PostMapping("/fscloud/sim/resumeModel")
    public CommonResult<RunModelDto> resumeModel(@RequestBody @Valid RunModelParam param) {
        return executeControlAction("resume", param, mcpClientService::resumeSimulation);
    }

    // ==================== 重置仿真 ====================

    @Operation(summary = "重置仿真")
    @CommonLog("重置仿真")
    @SaCheckPermission("/fscloud/sim/resetModel")
    @PostMapping("/fscloud/sim/resetModel")
    public CommonResult<RunModelDto> resetModel(@RequestBody @Valid RunModelParam param) {
        return executeControlAction("reset", param, mcpClientService::resetSimulation);
    }

    // ==================== 卸载模型 ====================

    @Operation(summary = "卸载模型")
    @CommonLog("卸载模型")
    @SaCheckPermission("/fscloud/sim/unloadModel")
    @PostMapping("/fscloud/sim/unloadModel")
    public CommonResult<RunModelDto> unloadModel(@RequestBody @Valid RunModelParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        Long fileId = param.getId();

        RunModelDto dto = new RunModelDto();
        JSONObject mcpResult;
        try {
            mcpResult = mcpClientService.unloadModel(userId, modelId);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when unloading model: {}", e.getMessage());
            dto.setMessage(e.getMessage());
            return CommonResult.data(dto);
        }

        boolean success = mcpResult.getBool("success", false);
        if (!success) {
            dto.setMessage(mcpResult.getStr("message", "卸载模型失败"));
            return CommonResult.data(dto);
        }

        // 从 tracking map 删除
        trackingMap.remove(userId, modelId);

        // 更新 DB
        FsModelFile record = fsModelFileService.getById(fileId);
        if (record != null) {
            // 仿真次数>0时置为 ALREADY，否则置为 NOTYET
            if (record.getSimTimes() != null && record.getSimTimes() > 0) {
                record.setSimStatus(SimStatusEnum.SIM_STATUS_ALREADY.getCode());
            } else {
                record.setSimStatus(SimStatusEnum.SIM_STATUS_NOTYET.getCode());
            }
            record.setModelId(null);
            fsModelFileService.updateById(record);
        }

        dto.setMessage("模型已卸载");
        log.info("Model unloaded: userId={}, modelId={}", userId, modelId);
        return CommonResult.data(dto);
    }

    // ==================== 查询已加载模型 ====================

    @Operation(summary = "查询当前用户已加载的模型")
    @SaCheckPermission("/fscloud/sim/getLoadedModel")
    @PostMapping("/fscloud/sim/getLoadedModel")
    public CommonResult<GetLoadedModelDto> getLoadedModel() {
        String userId = StpUtil.getLoginIdAsString();
        List<SimulationModelRecord> list = trackingMap.getByUserId(userId);
        if (list.isEmpty()) {
            return CommonResult.data(null);
        }
        SimulationModelRecord tracking = list.get(0);

        // 合并 DB 记录
        GetLoadedModelDto dto = new GetLoadedModelDto();
        if (tracking.getRecordId() != null) {
            FsModelFile dbRecord = fsModelFileService.getById(tracking.getRecordId());
            if (dbRecord != null) {
                dto.setRecordId(dbRecord.getId());
                dto.setName(dbRecord.getName());
                dto.setModel(dbRecord.getModel());
                dto.setSimTimes(dbRecord.getSimTimes());
            }
        }
        // tracking map 字段
        dto.setRecordId(tracking.getRecordId());
        dto.setModelId(tracking.getModelId());
        dto.setStatus(tracking.getStatus());
        dto.setSimStatus(SimulationModelTrackingMap.mapMcpStatusToSimStatus(tracking.getStatus()));
        dto.setRunId(tracking.getRunId());
        dto.setSimTime(tracking.getSimTime());
        dto.setSimProgress(tracking.getSimProgress());

        log.debug("getLoadedModel: userId={}, modelId={}", userId, tracking.getModelId());
        return CommonResult.data(dto);
    }

    // ==================== 获取参数表列表 ====================

    @Operation(summary = "获取参数表列表")
    @SaCheckPermission("/fscloud/sim/listParameterTables")
    @PostMapping("/fscloud/sim/listParameterTables")
    public CommonResult<GetModelParamsDto> listParameterTables(@RequestBody @Valid GetModelParamsParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();

        log.info("listParameterTables: userId={}, modelId={}", userId, modelId);

        GetModelParamsDto dto = new GetModelParamsDto();
        JSONObject mcpResult;
        try {
            mcpResult = mcpClientService.listParameterTables(userId, modelId);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when listing parameter tables: {}", e.getMessage());
            dto.setStatus("FAILED");
            return CommonResult.data(dto);
        }

        JSONObject data = mcpResult.getJSONObject("data");
        if (data != null) {
            dto.setModelId(data.getStr("modelId"));
            dto.setModelFileName(data.getStr("modelFileName"));
            dto.setRecordId(data.getStr("recordId"));
            dto.setStatus(data.getStr("status"));
            dto.setMcpTableName(data.getStr("mcpTableName"));
            dto.setMcpTableFound(data.getBool("mcpTableFound"));

            JSONArray tables = data.getJSONArray("parameterTables");
            if (tables != null) {
                dto.setParameterTables(tables.toList(String.class));
            }
        }

        log.info("listParameterTables success: modelId={}, tableCount={}",
                modelId, dto.getParameterTables() != null ? dto.getParameterTables().size() : 0);
        return CommonResult.data(dto);
    }

    // ==================== 获取参数快照 ====================

    @Operation(summary = "获取参数快照")
    @SaCheckPermission("/fscloud/sim/getParameterSnapshot")
    @PostMapping("/fscloud/sim/getParameterSnapshot")
    public CommonResult<GetParameterSnapshotDto> getParameterSnapshot(@RequestBody @Valid GetParameterSnapshotParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        String runId = param.getRunId();
        if (StrUtil.isBlank(runId)) {
            runId = "";
        }

        log.info("getParameterSnapshot: userId={}, modelId={}, runId={}", userId, modelId, runId);

        GetParameterSnapshotDto dto = new GetParameterSnapshotDto();
        JSONObject mcpResult;
        try {
            mcpResult = mcpClientService.getParameterSnapshot(userId, modelId, runId);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when getting parameter snapshot: {}", e.getMessage());
            return CommonResult.data(dto);
        }

        JSONObject data = mcpResult.getJSONObject("data");
        if (data != null) {
            dto.setRecordId(data.getStr("recordId"));
            dto.setModelId(data.getStr("modelId"));
            dto.setModelFileName(data.getStr("modelFileName"));
            dto.setStatus(data.getStr("status"));
            dto.setSnapshotAt(data.getLong("snapshotAt"));
            dto.setMcpTableName(data.getStr("mcpTableName"));
            dto.setMcpTableFound(data.getBool("mcpTableFound"));

            JSONArray mcpRows = data.getJSONArray("mcpRows");
            if (mcpRows != null) {
                dto.setMcpRows(mcpRows.toList(Object.class));
            }

            JSONArray tables = data.getJSONArray("tables");
            if (tables != null) {
                dto.setTables(tables.toList(Object.class));
            }

            JSONArray missingTables = data.getJSONArray("missingTables");
            if (missingTables != null) {
                dto.setMissingTables(missingTables.toList(String.class));
            }
        }

        log.info("getParameterSnapshot success: modelId={}", modelId);
        return CommonResult.data(dto);
    }

    // ==================== 参数快照写模型 ====================

    @Operation(summary = "参数快照写模型")
    @SaCheckPermission("/fscloud/sim/saveParamSnapshotToModel")
    @PostMapping("/fscloud/sim/saveParamSnapshotToModel")
    public CommonResult<SaveParamSnapshotToModelDto> saveParamSnapshotToModel(@RequestBody @Valid SaveParamSnapshotToModelParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        String tablesJson = param.getTablesJson();
        boolean bSaveModel = param.getBSaveModel() != null ? param.getBSaveModel() : true;

        log.info("saveParamSnapshotToModel: userId={}, modelId={}, bSaveModel={}", userId, modelId, bSaveModel);

        SaveParamSnapshotToModelDto dto = new SaveParamSnapshotToModelDto();
        JSONObject mcpResult;
        try {
            mcpResult = mcpClientService.batchUpdateParameterTables(userId, modelId, tablesJson, bSaveModel);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when saving param snapshot to model: {}", e.getMessage());
            return CommonResult.data(dto);
        }

        JSONObject data = mcpResult.getJSONObject("data");
        if (data != null) {
            dto.setRecordId(data.getStr("recordId"));
            dto.setModelId(data.getStr("modelId"));
            dto.setUpdatedTableCount(data.getInt("updatedTableCount"));
            dto.setUpdatedRowCount(data.getInt("updatedRowCount"));

            JSONArray tableNames = data.getJSONArray("tableNames");
            if (tableNames != null) {
                dto.setTableNames(tableNames.toList(String.class));
            }

            dto.setBSaveModel(data.getBool("bSaveModel"));
            dto.setModelSaved(data.getBool("modelSaved"));
            dto.setModelFilePath(data.getStr("modelFilePath"));
        }

        log.info("saveParamSnapshotToModel success: modelId={}, updatedTableCount={}",
                modelId, dto.getUpdatedTableCount());
        return CommonResult.data(dto);
    }

    // ==================== 获取仿真结果快照 ====================

    @Operation(summary = "获取仿真结果快照")
    @SaCheckPermission("/fscloud/sim/getResultSnapshot")
    @PostMapping("/fscloud/sim/getResultSnapshot")
    public CommonResult<GetResultSnapshotDto> getResultSnapshot(@RequestBody @Valid GetResultSnapshotParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        String runId = param.getRunId();
        if (StrUtil.isBlank(runId)) {
            runId = "";
        }

        log.info("getResultSnapshot: userId={}, modelId={}, runId={}", userId, modelId, runId);

        GetResultSnapshotDto dto = new GetResultSnapshotDto();
        try {
            JSONObject mcpResult = mcpClientService.getResultSnapshot(userId, modelId, runId);
            JSONObject data = mcpResult.getJSONObject("data");
            if (data != null) {
                dto.setUserId(data.getStr("userId"));
                dto.setRunId(data.getStr("runId"));
                dto.setRecordId(data.getStr("recordId"));
                dto.setModelId(data.getStr("modelId"));
                dto.setModelFileName(data.getStr("modelFileName"));
                dto.setStatus(data.getStr("status"));
                dto.setSimTime(data.getLong("simTime"));
                dto.setStartedAt(data.getLong("startedAt"));
                dto.setFinishedAt(data.getLong("finishedAt"));
                dto.setTotalOutput(data.getInt("totalOutput"));
                dto.setThroughputPerHour(data.getDouble("throughputPerHour"));
                dto.setCurrentWip(data.getInt("currentWip"));
                dto.setParameterSnapshotAt(data.getLong("parameterSnapshotAt"));
                dto.setResultStatus(data.getStr("resultStatus"));
                dto.setResultSnapshotAt(data.getLong("resultSnapshotAt"));

                JSONObject summary = data.getJSONObject("summaryResults");
                if (summary != null) {
                    dto.setSummaryResults((Map<String, Object>) summary);
                }

                JSONArray datasets = data.getJSONArray("datasets");
                if (datasets != null) {
                    dto.setDatasets((List) datasets.toList(Map.class));
                }
            }
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when getting result snapshot: {}", e.getMessage());
            return CommonResult.data(dto);
        }

        log.info("getResultSnapshot success: modelId={}, resultStatus={}", modelId, dto.getResultStatus());
        return CommonResult.data(dto);
    }

    // ==================== 获取结果数据集分页 ====================

    @Operation(summary = "获取结果数据集分页")
    @SaCheckPermission("/fscloud/sim/getResultDatasetPage")
    @PostMapping("/fscloud/sim/getResultDatasetPage")
    public CommonResult<Map<String, Object>> getResultDatasetPage(@RequestBody @Valid GetResultDatasetPageParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        String runId = param.getRunId();
        if (StrUtil.isBlank(runId)) runId = "";
        String dataset = param.getDataset();
        int limit = param.getLimit() != null ? param.getLimit() : 25;
        int offset = param.getOffset() != null ? param.getOffset() : 0;

        log.info("getResultDatasetPage: modelId={}, dataset={}, limit={}, offset={}",
                modelId, dataset, limit, offset);

        Map<String, Object> result = new HashMap<>();
        result.put("rows", new ArrayList<>());
        result.put("total", 0);
        result.put("hasMore", false);
        result.put("offset", offset);
        result.put("limit", limit);

        try {
            JSONObject mcpResult = mcpClientService.getResultDatasetPage(userId, modelId, runId, dataset, limit, offset);
            JSONObject mcpData = mcpResult.getJSONObject("data");
            if (mcpData != null) {
                result.put("total", mcpData.getInt("total", 0));
                result.put("hasMore", mcpData.getBool("hasMore", false));
                result.put("offset", mcpData.getInt("offset", offset));
                result.put("limit", mcpData.getInt("limit", limit));

                JSONArray rows = mcpData.getJSONArray("rows");
                List<Map<String, Object>> rowList = new ArrayList<>();
                if (rows != null) {
                    for (int i = 0; i < rows.size(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        if (row != null) {
                            rowList.add((Map<String, Object>) row);
                        }
                    }
                }
                result.put("rows", rowList);
            }
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP error when getting result dataset page: {}", e.getMessage());
        }

        log.info("getResultDatasetPage success: dataset={}, returned={}", dataset, ((List<?>)result.get("rows")).size());
        return CommonResult.data(result);
    }

    @Operation(summary = "获取结果图表数据（全量+降采样）")
    @SaCheckPermission("/fscloud/sim/getResultChartData")
    @PostMapping("/fscloud/sim/getResultChartData")
    public CommonResult<ChartDataDto> getResultChartData(@RequestBody @Valid GetResultChartDataParam param) {
        String userId = StpUtil.getLoginIdAsString();
        int maxPoints = param.getMaxPoints() != null ? param.getMaxPoints() : 2000;
        log.info("getResultChartData: id={}, modelId={}, dataset={}, maxPoints={}, xColumn={}",
                param.getId(), param.getModelId(), param.getDataset(), maxPoints, param.getXColumn());
        ChartDataDto dto = fsSimResultSnapshotService.getChartData(userId, param.getId(),
                param.getModelId(), param.getRunId(), param.getDataset(), maxPoints, param.getXColumn());
        log.info("getResultChartData success: dataset={}, total={}, returned={}, sampled={}",
                dto.getDataset(), dto.getTotal(), dto.getReturned(), dto.getSampled());
        return CommonResult.data(dto);
    }

    // ==================== 参数快照（落库） ====================

    @Resource
    private FsSimParamSnapshotService fsSimParamSnapshotService;

    @Resource
    private FsSimResultSnapshotService fsSimResultSnapshotService;

    @Operation(summary = "保存参数快照")
    @SaCheckPermission("/fscloud/sim/saveSnapshot")
    @CommonLog("保存参数快照")
    @PostMapping("/fscloud/sim/saveSnapshot")
    public CommonResult<Long> saveSnapshot(@RequestBody @Valid SaveSnapshotParam param) {
        Long id = fsSimParamSnapshotService.saveSnapshot(param);
        log.info("saveSnapshot success: id={}, modelFileId={}", id, param.getModelFileId());
        return CommonResult.data(id);
    }

    @Operation(summary = "查询某模型的所有快照列表")
    @SaCheckPermission("/fscloud/sim/listSnapshots")
    @PostMapping("/fscloud/sim/listSnapshots")
    public CommonResult<List<SnapshotDto>> listSnapshots(@RequestBody @Valid SnapshotListParam param) {
        List<SnapshotDto> list = fsSimParamSnapshotService.listByModelFileId(param.getModelFileId());
        return CommonResult.data(list);
    }

    @Operation(summary = "查询单个快照详情")
    @SaCheckPermission("/fscloud/sim/getSnapshot")
    @PostMapping("/fscloud/sim/getSnapshot")
    public CommonResult<SnapshotDto> getSnapshot(@RequestBody @Valid SnapshotIdParam param) {
        SnapshotDto dto = fsSimParamSnapshotService.getById(param.getId());
        return CommonResult.data(dto);
    }

    @Operation(summary = "删除参数快照")
    @SaCheckPermission("/fscloud/sim/deleteSnapshot")
    @CommonLog("删除参数快照")
    @PostMapping("/fscloud/sim/deleteSnapshot")
    public CommonResult<Void> deleteSnapshot(@RequestBody @Valid SnapshotIdParam param) {
        fsSimParamSnapshotService.deleteById(param.getId());
        log.info("deleteSnapshot success: id={}", param.getId());
        return CommonResult.ok();
    }

    // ==================== 结果快照（成对落库） ====================

    @Operation(summary = "保存结果快照（成对落库：参数+结果）")
    @SaCheckPermission("/fscloud/sim/saveResultSnapshot")
    @CommonLog("保存结果快照")
    @PostMapping("/fscloud/sim/saveResultSnapshot")
    public CommonResult<Long> saveResultSnapshot(@RequestBody @Valid SaveResultSnapshotParam param) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("saveResultSnapshot: userId={}, modelId={}, runId={}",
                userId, param.getModelId(), param.getRunId());
        Long id = fsSimResultSnapshotService.saveResultSnapshot(userId, param.getModelId(), param.getRunId());
        log.info("saveResultSnapshot success: id={}, runId={}", id, param.getRunId());
        return CommonResult.data(id);
    }

    @Operation(summary = "查询某模型的所有结果快照列表")
    @SaCheckPermission("/fscloud/sim/listResultSnapshots")
    @PostMapping("/fscloud/sim/listResultSnapshots")
    public CommonResult<List<ResultSnapshotDto>> listResultSnapshots(@RequestBody @Valid ResultSnapshotListParam param) {
        List<ResultSnapshotDto> list = fsSimResultSnapshotService.listByModelFileId(param.getModelFileId());
        return CommonResult.data(list);
    }

    @Operation(summary = "查询单个结果快照详情")
    @SaCheckPermission("/fscloud/sim/getResultSnapshotById")
    @PostMapping("/fscloud/sim/getResultSnapshotById")
    public CommonResult<ResultSnapshotDto> getResultSnapshotById(@RequestBody @Valid ResultSnapshotIdParam param) {
        ResultSnapshotDto dto = fsSimResultSnapshotService.getById(param.getId());
        return CommonResult.data(dto);
    }

    @Operation(summary = "按页读取结果快照某数据集数据")
    @SaCheckPermission("/fscloud/sim/getResultDatasetPageInDb")
    @PostMapping("/fscloud/sim/getResultDatasetPageInDb")
    public CommonResult<ResultDatasetPageDto> getResultDatasetPageInDb(@RequestBody @Valid ResultDatasetPageInDbParam param) {
        int limit = param.getLimit() != null ? param.getLimit() : 25;
        int offset = param.getOffset() != null ? param.getOffset() : 0;
        ResultDatasetPageDto dto = fsSimResultSnapshotService.getDatasetPage(
                param.getId(), param.getDataset(), limit, offset);
        return CommonResult.data(dto);
    }

    @Operation(summary = "删除结果快照（连带成对参数快照与 partCycleTime 文件）")
    @SaCheckPermission("/fscloud/sim/deleteResultSnapshot")
    @CommonLog("删除结果快照")
    @PostMapping("/fscloud/sim/deleteResultSnapshot")
    public CommonResult<Void> deleteResultSnapshot(@RequestBody @Valid ResultSnapshotIdParam param) {
        fsSimResultSnapshotService.deleteById(param.getId());
        log.info("deleteResultSnapshot success: id={}", param.getId());
        return CommonResult.ok();
    }

    // ==================== 动画播放 ====================

    @Operation(summary = "获取动画层列表")
    @SaCheckPermission("/fscloud/sim/listAnimationLayers")
    @PostMapping("/fscloud/sim/listAnimationLayers")
    public CommonResult<Object> listAnimationLayers(@RequestBody @Valid AnimationLayerParam param) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        JSONObject mcpResult = mcpClientService.listAnimationLayers(userId, modelId);
        log.info("listAnimationLayers: userId={}, modelId={}, \n\tresult={}", userId, modelId, mcpResult);
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "选择动画层")
    @SaCheckPermission("/fscloud/sim/selectAnimationLayer")
    @PostMapping("/fscloud/sim/selectAnimationLayer")
    public CommonResult<Object> selectAnimationLayer(@RequestBody @Valid SelectAnimationLayerParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.selectAnimationLayer(
                userId, param.getModelId(), param.getLayerId(), param.getFrameRate());
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "开关动画")
    @SaCheckPermission("/fscloud/sim/setAnimationEnabled")
    @PostMapping("/fscloud/sim/setAnimationEnabled")
    public CommonResult<Object> setAnimationEnabled(@RequestBody @Valid SetAnimationEnabledParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.setAnimationEnabled(userId, param.getModelId(), param.getEnabled());
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "开启/停止事件 MQTT 发布")
    @SaCheckPermission("/fscloud/sim/setEventPublishing")
    @PostMapping("/fscloud/sim/setEventPublishing")
    public CommonResult<Object> setEventPublishing(@RequestBody @Valid SetEventPublishingParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.setEventPublishing(
                userId, param.getModelId(), param.getEnabled(), param.getPublishIntervalMs());
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "设置仿真加速因子")
    @SaCheckPermission("/fscloud/sim/setAccelerateFactor")
    @PostMapping("/fscloud/sim/setAccelerateFactor")
    public CommonResult<Object> setAccelerateFactor(@RequestBody @Valid SetAccelerateFactorParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.setAccelerateFactor(userId, param.getModelId(), param.getAccelerateFactor());
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "获取 SVG 高清静态图")
    @SaCheckPermission("/fscloud/sim/getSvgPicture")
    @PostMapping("/fscloud/sim/getSvgPicture")
    public CommonResult<Object> getSvgPicture(@RequestBody @Valid GetSvgPictureParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.getSvgPicture(userId, param.getModelId(), param.getLayerId());
        return CommonResult.data(convertToPlain(mcpResult));
    }

    // ==================== 时间轴数据分析 ====================

    @Operation(summary = "查询时间轴对象位置快照")
    @SaCheckPermission("/fscloud/sim/getObjectPositionSnapshot")
    @PostMapping("/fscloud/sim/getObjectPositionSnapshot")
    public CommonResult<Object> getObjectPositionSnapshot(@RequestBody @Valid GetObjectPositionSnapshotParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.getObjectPositionSnapshot(
                userId, param.getModelId(), param.getRunId(), param.getLayerId(), param.getSimTime());
        log.info("getObjectPositionSnapshot: userId={}, modelId={}, simTime={}, \n\tresult={}",
                userId, param.getModelId(), param.getSimTime(), mcpResult);
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "分页查询完整对象位置快照")
    @SaCheckPermission("/fscloud/sim/getObjectPositionSnapshot")
    @PostMapping("/fscloud/sim/getObjectPositionSnapshotPage")
    public CommonResult<Object> getObjectPositionSnapshotPage(@RequestBody @Valid GetObjectPositionSnapshotPageParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.getObjectPositionSnapshotPage(
                userId, param.getModelId(), param.getRunId(), param.getLayerId(),
                param.getSimTime(), param.getSnapshotId(), param.getEntityKind(),
                param.getOffset() != null ? param.getOffset() : 0,
                param.getLimit() != null ? param.getLimit() : 500);
        log.info("getObjectPositionSnapshotPage: userId={}, modelId={}, simTime={}, snapshotId={}, offset={}, limit={}, \n\tresult={}",
                userId, param.getModelId(), param.getSimTime(), param.getSnapshotId(),
                param.getOffset(), param.getLimit(), mcpResult);
        return CommonResult.data(convertToPlain(mcpResult));
    }

    @Operation(summary = "查询时间窗口内的仿真事件")
    @SaCheckPermission("/fscloud/sim/getSimulationEvents")
    @PostMapping("/fscloud/sim/getSimulationEvents")
    public CommonResult<Object> getSimulationEvents(@RequestBody @Valid GetSimulationEventsParam param) {
        String userId = StpUtil.getLoginIdAsString();
        JSONObject mcpResult = mcpClientService.getSimulationEvents(
                userId, param.getModelId(), param.getRunId(),
                param.getStartSimTime(), param.getDuration(),
                param.getOffset() != null ? param.getOffset() : 0,
                param.getLimit() != null ? param.getLimit() : 500);
        log.info("getSimulationEvents: userId={}, modelId={}, startSimTime={}, duration={}, \n\tresult={}",
                userId, param.getModelId(), param.getStartSimTime(), param.getDuration(), mcpResult);
        return CommonResult.data(convertToPlain(mcpResult));
    }

    /** 递归将 Hutool JSONObject 转为 Jackson 友好的 Map/List（消除 JSONNull） */
    @SuppressWarnings("unchecked")
    private Object convertToPlain(Object obj) {
        if (obj instanceof JSONObject json) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            for (var entry : json.entrySet()) {
                map.put(entry.getKey(), convertToPlain(entry.getValue()));
            }
            return map;
        } else if (obj instanceof JSONArray arr) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                list.add(convertToPlain(arr.get(i)));
            }
            return list;
        } else if (obj instanceof cn.hutool.json.JSONNull) {
            return null;
        }
        return obj;
    }

    // ==================== 公共控制方法 ====================

    /** 控制动作函数接口 */
    @FunctionalInterface
    private interface ControlFunction {
        JSONObject execute(String userId, String modelId);
    }

    /**
     * 执行 stop/pause/resume/reset 等控制动作
     */
    private CommonResult<RunModelDto> executeControlAction(String action, RunModelParam param,
                                                            ControlFunction controlFunc) {
        String userId = StpUtil.getLoginIdAsString();
        String modelId = param.getModelId();
        Long fileId = param.getId();

        RunModelDto dto = new RunModelDto();
        JSONObject mcpResult;
        try {
            mcpResult = controlFunc.execute(userId, modelId);
        } catch (McpClientService.McpServiceException e) {
            log.warn("MCP service error when {}: {}", action, e.getMessage());
            dto.setMessage(e.getMessage());
            return CommonResult.data(dto);
        }

        boolean success = mcpResult.getBool("success", false);
        if (!success) {
            dto.setMessage(mcpResult.getStr("message", action + "失败"));
            return CommonResult.data(dto);
        }

        // 更新 tracking map 和 DB
        JSONObject data = mcpResult.getJSONObject("data");
        if (data != null) {
            SimulationModelRecord trackingRecord = trackingMap.get(userId, modelId);
            if (trackingRecord != null) {
                String newStatus = data.getStr("status");
                trackingRecord.setStatus(newStatus);
                trackingRecord.setSimTime(data.getLong("simTime"));
                trackingRecord.setMessage(data.getStr("message"));
            }

            FsModelFile record = fsModelFileService.getById(fileId);
            if (record != null) {
                String mappedStatus = SimulationModelTrackingMap.mapMcpStatusToSimStatus(
                        data.getStr("status"));
                if (mappedStatus != null) {
                    record.setSimStatus(mappedStatus);
                    fsModelFileService.updateById(record);
                    dto.setSimStatus(mappedStatus);
                }
                dto.setSimTimes(record.getSimTimes());
            }

            dto.setUserId(data.getStr("userId"));
            dto.setModelId(data.getStr("modelId"));
            dto.setRunId(data.getStr("runId"));
            dto.setStatus(data.getStr("status"));
            dto.setSimTime(data.getLong("simTime"));
            dto.setMessage(data.getStr("message"));
        }

        log.info("{} success: userId={}, modelId={}", action, userId, modelId);
        return CommonResult.data(dto);
    }
}
