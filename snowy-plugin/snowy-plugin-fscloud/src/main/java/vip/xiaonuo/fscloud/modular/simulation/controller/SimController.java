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
import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import vip.xiaonuo.fscloud.modular.simulation.dto.GetLoadedModelDto;
import vip.xiaonuo.fscloud.modular.simulation.dto.LoadModelDto;
import vip.xiaonuo.fscloud.modular.simulation.dto.RunModelDto;
import vip.xiaonuo.fscloud.modular.simulation.param.LoadModelParam;
import vip.xiaonuo.fscloud.modular.simulation.param.RunModelParam;
import vip.xiaonuo.fscloud.modular.simulation.service.McpClientService;
import vip.xiaonuo.fscloud.modular.simulation.service.SimulationModelRecord;
import vip.xiaonuo.fscloud.modular.simulation.service.SimulationModelTrackingMap;

import jakarta.annotation.Resource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

        log.info("Loading model: userId={}, fileId={}, modelFileName={}", userId, fileId, modelFileName);

        LoadModelDto dto = new LoadModelDto();
        dto.setModelName(record.getName());
        dto.setRecordId(String.valueOf(fileId));

        JSONObject mcpResult;
        try {
            mcpResult = mcpClientService.loadModel(userId, String.valueOf(fileId), modelFileName);
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
