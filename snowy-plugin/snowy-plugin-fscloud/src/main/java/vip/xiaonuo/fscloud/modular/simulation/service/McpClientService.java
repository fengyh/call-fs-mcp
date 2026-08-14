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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.xiaonuo.fscloud.config.McpProperties;
import vip.xiaonuo.common.exception.CommonException;

import jakarta.annotation.Resource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MCP 远程仿真控制客户端服务
 * <p>
 * 通过 HTTP JSON-RPC 2.0 调用 FactorySimulation 的 MCP 远程仿真控制服务。
 * 供 Controller 统一调用，避免重复实现 HTTP 通信逻辑。
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Slf4j
@Service
public class McpClientService {

    @Resource
    private McpProperties mcpProperties;

    private String endpointUrl;
    private String apiKey;
    private int requestId = 0;

    @PostConstruct
    public void init() {
        this.endpointUrl = mcpProperties.getEndpointUrl();
        this.apiKey = mcpProperties.getApiKey();
        log.info("McpClientService initialized, endpointUrl={}", endpointUrl);
    }

    // ==================== 高层 API ====================

    /** 初始化 MCP 会话 */
    public JSONObject initialize() {
        return call("initialize", null);
    }

    /** 获取 MCP 工具列表 */
    public JSONObject listTools() {
        return call("tools/list", null);
    }

    /** 加载仿真模型 */
    public JSONObject loadModel(String userId, String recordId, String modelFileName, Boolean loadInUI) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("recordId", recordId);
        args.put("modelFileName", modelFileName);
        args.put("loadInUI", loadInUI);
        log.info("MCP loadModel: userId={}, recordId={}, modelFileName={}, loadInUI={}", userId, recordId, modelFileName, loadInUI);
        return callTool("loadRemoteSimulationModel", args);
    }

    /** 卸载仿真模型 */
    public JSONObject unloadModel(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("unloadRemoteSimulationModel", args);
    }

    /** 列出用户已加载的模型 */
    public JSONObject listModels(String userId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        return callTool("listRemoteSimulationModels", args);
    }

    /** 查询仿真状态 */
    public JSONObject getStatus(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("getRemoteSimulationStatus", args);
    }

    /** 启动仿真 */
    public JSONObject startSimulation(String userId, String modelId, boolean showAnimation) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("showAnimation", showAnimation);
        return callTool("startRemoteSimulation", args);
    }

    /** 暂停仿真 */
    public JSONObject pauseSimulation(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("pauseRemoteSimulation", args);
    }

    /** 恢复仿真 */
    public JSONObject resumeSimulation(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("resumeRemoteSimulation", args);
    }

    /** 重置仿真 */
    public JSONObject resetSimulation(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("resetRemoteSimulation", args);
    }

    /** 停止仿真 */
    public JSONObject stopSimulation(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("stopRemoteSimulation", args);
    }

    /** 获取仿真结果快照 */
    public JSONObject getResultSnapshot(String userId, String modelId, String runId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        return callTool("getRemoteSimulationResultSnapshot", args);
    }

    /** 列出参数表 */
    public JSONObject listParameterTables(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("listRemoteSimulationParameterTables", args);
    }

    /** 获取参数快照 */
    public JSONObject getParameterSnapshot(String userId, String modelId, String runId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        return callTool("getRemoteSimulationParameterSnapshot", args);
    }

    /** 更新参数表（全量替换） */
    public JSONObject updateParameterTable(String userId, String modelId, String tableName, String rowsJson) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("tableName", tableName);
        args.put("rowsJson", rowsJson);
        return callTool("updateRemoteSimulationParameterTable", args);
    }

    /** 更新参数表（单元格级） */
    public JSONObject updateParameterCells(String userId, String modelId, String tableName, String patchesJson) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("tableName", tableName);
        args.put("patchesJson", patchesJson);
        return callTool("updateRemoteSimulationParameterCells", args);
    }

    /** 批量更新参数表（原子写入多张表） */
    public JSONObject batchUpdateParameterTables(String userId, String modelId, String tablesJson, boolean bSaveModel) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("tablesJson", tablesJson);
        args.put("bSaveModel", bSaveModel);
        return callTool("batchUpdateRemoteSimulationParameterTable", args);
    }

    /** 获取结果数据集分页 */
    public JSONObject getResultDatasetPage(String userId, String modelId, String runId,
                                            String dataset, int limit, int offset) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        args.put("dataset", dataset);
        args.put("limit", limit);
        args.put("offset", offset);
        return callTool("getRemoteSimulationResultDatasetPage", args);
    }

    /** 获取动画层列表 */
    public JSONObject listAnimationLayers(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("listRemoteSimulationAnimationLayers", args);
    }

    /** 选择动画层 */
    public JSONObject selectAnimationLayer(String userId, String modelId, String layerId, int frameRate) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("layerId", layerId);
        args.put("frameRate", frameRate);
        return callTool("selectRemoteSimulationAnimationLayer", args);
    }

    /** 开关动画 */
    public JSONObject setAnimationEnabled(String userId, String modelId, boolean enabled) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("enabled", enabled);
        return callTool("setRemoteSimulationAnimationEnabled", args);
    }

    /** 开启/停止 MU 事件 MQTT 发布（events 主题） */
    public JSONObject setEventPublishing(String userId, String modelId, boolean enabled, Integer publishIntervalMs) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("enabled", enabled);
        if (publishIntervalMs != null) {
            args.put("publishIntervalMs", publishIntervalMs);
        }
        return callTool("setRemoteSimulationEventPublishing", args);
    }

    /** 设置仿真加速因子 */
    public JSONObject setAccelerateFactor(String userId, String modelId, int factor) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("accelerateFactor", factor);
        return callTool("setRemoteSimulationAccelerateFactor", args);
    }

    /** 获取 SVG 高清静态图 */
    public JSONObject getSvgPicture(String userId, String modelId, String layerId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("layerId", layerId);
        return callTool("getSvgPicture", args);
    }

    /** 查询时间轴对象位置快照 */
    public JSONObject getObjectPositionSnapshot(String userId, String modelId, String runId, String layerId, long simTime) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        args.put("layerId", layerId != null ? layerId : "root");
        args.put("simTime", simTime);
        return callTool("getRemoteSimulationObjectPositionSnapshot", args);
    }

    /** 查询时间窗口内的仿真事件 */
    public JSONObject getSimulationEvents(String userId, String modelId, String runId,
                                          long startSimTime, long duration, int offset, int limit) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        args.put("startSimTime", startSimTime);
        args.put("duration", duration);
        args.put("offset", offset);
        args.put("limit", limit);
        return callTool("getRemoteSimulationEvents", args);
    }

    // ==================== 底层 JSON-RPC ====================

    /**
     * 调用非 tool 类 RPC 方法（initialize, tools/list 等）
     */
    public JSONObject call(String method, JSONObject params) {
        JSONObject request = buildRequest(method, params);
        return post(request);
    }

    /**
     * 调用 tools/call 包装的工具
     */
    public JSONObject callTool(String toolName, JSONObject arguments) {
        JSONObject params = new JSONObject();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : new JSONObject());

        JSONObject request = buildRequest("tools/call", params);
        JSONObject rawResponse = post(request);

        // 解包 MCP content[0].text → 业务 JSON
        return unwrapResult(rawResponse);
    }

    // ==================== 内部实现 ====================

    private JSONObject buildRequest(String method, JSONObject params) {
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", String.valueOf(++requestId));
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }
        return request;
    }

    /**
     * 发送 HTTP POST 请求，返回解析后的 JSON
     */
    private JSONObject post(JSONObject requestBody) {
        try {
            URL url = URI.create(endpointUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-API-Key", apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            // 发送请求体
            String jsonStr = requestBody.toString();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            String responseBody;
            if (statusCode >= 200 && statusCode < 300) {
                responseBody = readStream(conn.getInputStream());
            } else {
                responseBody = readStream(conn.getErrorStream());
            }

            JSONObject result = JSONUtil.parseObj(responseBody);

            // 如果有 MCP error 字段, 抛出异常
            if (result.containsKey("error")) {
                JSONObject error = result.getJSONObject("error");
                throw new McpServiceException(
                        error.getInt("code"),
                        error.getStr("message"),
                        requestBody
                );
            }

            return result;
        } catch (McpServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new McpServiceException(-1, "MCP connection failed: " + e.getMessage(), requestBody);
        }
    }

    /**
     * 从 MCP tools/call 响应中解包: content[0].text → JSONObject
     */
    private JSONObject unwrapResult(JSONObject rawResponse) {
        JSONObject result = rawResponse.getJSONObject("result");
        if (result == null) {
            throw new McpServiceException(-1, "No 'result' field in MCP response", null);
        }
        JSONArray content = result.getJSONArray("content");
        if (content == null || content.isEmpty()) {
            throw new McpServiceException(-1, "No 'content' in MCP result", null);
        }
        String text = content.getJSONObject(0).getStr("text");
        if (text == null || text.isEmpty()) {
            throw new McpServiceException(-1, "Empty 'text' in MCP content", null);
        }
        return JSONUtil.parseObj(text);
    }

    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // ==================== Exception ====================

    /**
     * MCP 服务调用异常
     */
    public static class McpServiceException extends RuntimeException {
        private final int code;
        private final JSONObject request;

        public McpServiceException(int code, String message, JSONObject request) {
            super(message);
            this.code = code;
            this.request = request;
        }

        public int getErrorCode() {
            return code;
        }

        public JSONObject getRequest() {
            return request;
        }
    }
}
