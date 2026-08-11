package fs;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MCP JSON-RPC 2.0 客户端
 * <p>
 * 用于通过 HTTP 调用 FactorySimulation 的 MCP 远程仿真控制服务。
 * 协议详情参考: codex_doc_output/remote_simulation_mcp_docs/
 */
public class McpClientTest {

    private final String endpointUrl;
    private final String apiKey;
    private int requestId = 0;

    /**
     * @param endpointUrl MCP 服务地址, 例如 "http://127.0.0.1:8081/mcp"
     * @param apiKey      X-API-Key 值
     */
    public McpClientTest(String endpointUrl, String apiKey) {
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
    }

    public McpClientTest() {
        this("http://127.0.0.1:8081/mcp", "7sK9p2G@2026");
    }

    // ==================== 高层 API ====================

    /** Initialize MCP session. */
    public JSONObject initialize() {
        return call("initialize", null);
    }

    /** List MCP tools. */
    public JSONObject listTools() {
        return call("tools/list", null);
    }

    /** Load a remote simulation model. */
    public JSONObject loadModel(String userId, String recordId, String modelFileName, boolean loadInUI) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("recordId", recordId);
        args.put("modelFileName", modelFileName);
        args.put("loadInUI", loadInUI);
        return callTool("loadRemoteSimulationModel", args);
    }

    /** Unload a remote simulation model. */
    public JSONObject unloadModel(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("unloadRemoteSimulationModel", args);
    }

    /** List all models loaded in the MCP server process. */
    public JSONObject listModels() {
        return callTool("listRemoteSimulationModels", new JSONObject());
    }

    /** 查询仿真状态 */
    public JSONObject getStatus(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("getRemoteSimulationStatus", args);
    }

    public JSONObject listAnimationLayers(String userId, String modelId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        return callTool("listRemoteSimulationAnimationLayers", args);
    }

    public JSONObject getSvgPicture(String userId, String modelId, String layerId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("layerId", layerId);
        return callTool("getSvgPicture", args);
    }

    public JSONObject getObjectPositionSnapshot(String userId, String modelId, String runId, String layerId,
            long simTime) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        args.put("layerId", layerId != null ? layerId : "root");
        args.put("simTime", simTime);
        return callTool("getRemoteSimulationObjectPositionSnapshot", args);
    }

    public JSONObject getSimulationEvents(String userId, String modelId, String runId, long startSimTime,
            long duration, int offset, int limit) {
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

    public JSONObject selectAnimationLayer(String userId, String modelId, String layerId, int frameRate) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("layerId", layerId);
        args.put("frameRate", frameRate);
        return callTool("selectRemoteSimulationAnimationLayer", args);
    }

    public JSONObject setAnimationEnabled(String userId, String modelId, boolean enabled) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("enabled", enabled);
        return callTool("setRemoteSimulationAnimationEnabled", args);
    }

    public JSONObject setAccelerateFactor(String userId, String modelId, int accelerateFactor) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("accelerateFactor", accelerateFactor);
        return callTool("setRemoteSimulationAccelerateFactor", args);
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

    /** 获取结果快照 */
    public JSONObject getResultSnapshot(String userId, String modelId, String runId) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        return callTool("getRemoteSimulationResultSnapshot", args);
    }

    /** Get one page from a completed simulation result dataset. */
    public JSONObject getResultDatasetPage(String userId, String modelId, String runId, String dataset,
            int offset, int limit) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("runId", runId != null ? runId : "");
        args.put("dataset", dataset);
        args.put("offset", offset);
        args.put("limit", limit);
        return callTool("getRemoteSimulationResultDatasetPage", args);
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

    /** 更新参数表 (全量替换) */
    public JSONObject updateParameterTable(String userId, String modelId, String tableName, String rowsJson) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("tableName", tableName);
        args.put("rowsJson", rowsJson);
        return callTool("updateRemoteSimulationParameterTable", args);
    }

    /** Replace multiple parameter tables and optionally save the model file. */
    public JSONObject batchUpdateParameterTables(String userId, String modelId, String tablesJson,
            boolean bSaveModel) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("tablesJson", tablesJson);
        args.put("bSaveModel", bSaveModel);
        return callTool("batchUpdateRemoteSimulationParameterTable", args);
    }

    /** 更新参数表 (单元格级) */
    public JSONObject updateParameterCells(String userId, String modelId, String tableName, String patchesJson) {
        JSONObject args = new JSONObject();
        args.put("userId", userId);
        args.put("modelId", modelId);
        args.put("tableName", tableName);
        args.put("patchesJson", patchesJson);
        return callTool("updateRemoteSimulationParameterCells", args);
    }

    // ==================== 底层 JSON-RPC ====================

    /**
     * 调用非 tool 类方法 (initialize, tools/list 等)
     */
    public JSONObject call(String method, JSONObject params) {
        JSONObject request = buildRequest(method, params);
        JSONObject rawResponse = post(request);
        return rawResponse;
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

        System.out.println("..............................tool invoke begin...................................");
        System.out.println("Tool: "+toolName);
        System.out.println("Input:");
        printJson(arguments);
        System.out.println("Output:");
        printJson(rawResponse);
        System.out.println("..............................tool invoke end.....................................");
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
            String jsonStr = requestBody.toJSONString();
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

            JSONObject result = JSON.parseObject(responseBody);

            // 如果有 MCP error 字段, 抛出异常
            if (result.containsKey("error")) {
                JSONObject error = result.getJSONObject("error");
                throw new McpException(
                        error.getInteger("code"),
                        error.getString("message"),
                        requestBody
                );
            }
            // HTTP 层错误
            if (result.containsKey("error") && result.getString("error") != null) {
                throw new McpException(statusCode,
                        "HTTP " + statusCode + ": " + result.getString("error"),
                        requestBody);
            }

            return result;
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException(-1, "Connection failed: " + e.getMessage(), requestBody);
        }
    }

    /**
     * 从 MCP tools/call 响应中解包: content[0].text → JSONObject
     */
    private JSONObject unwrapResult(JSONObject rawResponse) {
        JSONObject result = rawResponse.getJSONObject("result");
        if (result == null) {
            throw new McpException(-1, "No 'result' field in MCP response", null);
        }
        JSONArray content = result.getJSONArray("content");
        if (content == null || content.isEmpty()) {
            throw new McpException(-1, "No 'content' in MCP result", null);
        }
        String text = content.getJSONObject(0).getString("text");
        if (text == null || text.isEmpty()) {
            throw new McpException(-1, "Empty 'text' in MCP content", null);
        }
        return JSON.parseObject(text);
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

    // ==================== demo runner ====================

    // private static final String DEFAULT_MODEL_FILE = "/Users/daaaa/IdeaProjects/factorysimulation/help/examples/step-by-step.fs";
    private static final String DEFAULT_MODEL_FILE;
    static {
        try {
            DEFAULT_MODEL_FILE = java.nio.file.Paths.get(McpClientTest.class.getResource("/step-by-step.fs").toURI()).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void printStep(String title) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(title);
        System.out.println("------------------------------------------------------------");
    }

    private static void printJson(JSONObject object) {
        System.out.println(JSON.toJSONString(object, JSONWriter.Feature.PrettyFormat));
    }

    private static JSONObject requireOk(JSONObject response, String action) {
        if (response == null || !Boolean.TRUE.equals(response.getBoolean("success"))) {
            throw new McpException(-1, action + " failed: " + JSON.toJSONString(response, JSONWriter.Feature.PrettyFormat), null);
        }
        JSONObject data = response.getJSONObject("data");
        return data != null ? data : new JSONObject();
    }

    private static String responseStatus(JSONObject response) {
        JSONObject data = response == null ? null : response.getJSONObject("data");
        return data == null ? null : data.getString("status");
    }

    private static boolean isTerminalStatus(String status) {
        return "FINISHED".equals(status) || "STOPPED".equals(status) || "FAILED".equals(status);
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * End-to-end MCP + MQTT smoke test.
     *
     * Args:
     *   0: model file path, default DEFAULT_MODEL_FILE
     *   1: recordId, default record-{timestamp}
     *   2: showAnimation, default false. false usually runs fast; true is better for pause/resume observation.
     *   3: max poll seconds, default 10 when showAnimation=false, 30 when showAnimation=true
     *   4: userId, default demo-{timestamp}
     *   5: unloadAtEnd, default true.
     *   6: loadInUI, default same as showAnimation.
     */
    public static void main(String[] args) {
        String modelFileName = args.length > 0 ? args[0] : DEFAULT_MODEL_FILE;
        String recordId = args.length > 1 ? args[1] : "record-" + System.currentTimeMillis();
        boolean showAnimation = args.length > 2 && Boolean.parseBoolean(args[2]);
        int maxPollSeconds = args.length > 3 ? Integer.parseInt(args[3]) : (showAnimation ? 30 : 10);
        String userId = args.length > 4 ? args[4] : "demo-" + System.currentTimeMillis();
        boolean unloadAtEnd = args.length > 5 ? Boolean.parseBoolean(args[5]) : true;
        boolean loadInUI = args.length > 6 ? Boolean.parseBoolean(args[6]) : showAnimation;

        McpClientTest client = new McpClientTest();
        String modelId = null;
        String runId = "";
        boolean loaded = false;
        boolean unloaded = false;

        try {
            printStep("0. test config");
            System.out.println("userId        = " + userId);
            System.out.println("recordId      = " + recordId);
            System.out.println("modelFileName = " + modelFileName);
            System.out.println("showAnimation = " + showAnimation);
            System.out.println("loadInUI      = " + loadInUI);
            System.out.println("maxPollSeconds= " + maxPollSeconds);
            System.out.println("unloadAtEnd  = " + unloadAtEnd);
            System.out.println("MQTTX subscribe topic: factory/sim/#");
            if (!showAnimation) {
                System.out.println("Note: showAnimation=false usually finishes quickly, so pause/resume may be skipped.");
            }

            printStep("1. initialize");
            printJson(client.initialize());

            printStep("2. tools/list");
            JSONObject toolsResp = client.listTools();
            printJson(toolsResp);

            printStep("3. loadRemoteSimulationModel");
            JSONObject loadResp = client.loadModel(userId, recordId, modelFileName, loadInUI);
            printJson(loadResp);
            JSONObject loadData = requireOk(loadResp, "loadRemoteSimulationModel");
            modelId = loadData.getString("modelId");
            loaded = modelId != null && !modelId.isEmpty();
            System.out.println("Loaded modelId = " + modelId);

            printStep("4. listRemoteSimulationModels");
            printJson(client.listModels());

            printStep("4.1. animation layer catalog");
            JSONObject animationLayersResp = client.listAnimationLayers(userId, modelId);
            printJson(animationLayersResp);
            requireOk(animationLayersResp, "listRemoteSimulationAnimationLayers");
            if (showAnimation) {
                printStep("4.2. select root animation layer at 5 FPS");
                JSONObject animationLayerResp = client.selectAnimationLayer(userId, modelId, "root", 5);
                printJson(animationLayerResp);
                requireOk(animationLayerResp, "selectRemoteSimulationAnimationLayer");
            }

            printStep("5. parameter table catalog and snapshot");
            JSONObject parameterTablesResp = client.listParameterTables(userId, modelId);
            printJson(parameterTablesResp);
            requireOk(parameterTablesResp, "listRemoteSimulationParameterTables");
            JSONObject parameterSnapshotResp = client.getParameterSnapshot(userId, modelId, "");
            printJson(parameterSnapshotResp);
            requireOk(parameterSnapshotResp, "getRemoteSimulationParameterSnapshot");

            printStep("6. startRemoteSimulation");
            JSONObject startResp = client.startSimulation(userId, modelId, showAnimation);
            printJson(startResp);
            JSONObject startData = requireOk(startResp, "startRemoteSimulation");
            runId = startData.getString("runId");
            System.out.println("runId = " + runId);

            printStep("7. poll status and optionally pause/resume");
            boolean pauseResumeAttempted = false;
            String latestStatus = responseStatus(startResp);
            for (int i = 1; i <= maxPollSeconds; i++) {
                sleepSilently(1000);
                JSONObject statusResp = client.getStatus(userId, modelId);
                printJson(statusResp);
                requireOk(statusResp, "getRemoteSimulationStatus");
                latestStatus = responseStatus(statusResp);

                if (showAnimation && !pauseResumeAttempted && "RUNNING".equals(latestStatus) && i >= 2) {
                    printStep("7a. pauseRemoteSimulation");
                    JSONObject pauseResp = client.pauseSimulation(userId, modelId);
                    printJson(pauseResp);
                    requireOk(pauseResp, "pauseRemoteSimulation");
                    sleepSilently(1000);

                    printStep("7b. resumeRemoteSimulation");
                    JSONObject resumeResp = client.resumeSimulation(userId, modelId);
                    printJson(resumeResp);
                    requireOk(resumeResp, "resumeRemoteSimulation");
                    pauseResumeAttempted = true;
                }

                if (isTerminalStatus(latestStatus)) {
                    System.out.println("Reached terminal status: " + latestStatus);
                    break;
                }
            }

            JSONObject finalStatusResp = client.getStatus(userId, modelId);
            printStep("8. final status");
            printJson(finalStatusResp);
            requireOk(finalStatusResp, "getRemoteSimulationStatus");
            latestStatus = responseStatus(finalStatusResp);

            if ("RUNNING".equals(latestStatus) || "PAUSED".equals(latestStatus)) {
                printStep("9. stopRemoteSimulation because test window ended");
                JSONObject stopResp = client.stopSimulation(userId, modelId);
                printJson(stopResp);
                requireOk(stopResp, "stopRemoteSimulation");
                latestStatus = responseStatus(stopResp);
            } else {
                printStep("9. stop skipped");
                System.out.println("Current status is " + latestStatus + ", no stop needed.");
            }

            printStep("10. getRemoteSimulationResultSnapshot");
            JSONObject resultResp = client.getResultSnapshot(userId, modelId, runId);
            printJson(resultResp);
            JSONObject resultData = requireOk(resultResp, "getRemoteSimulationResultSnapshot");

            if ("READY".equals(resultData.getString("resultStatus"))) {
                printStep("10a. getRemoteSimulationResultDatasetPage (partCycleTime, first 100 rows)");
                JSONObject pageResp = client.getResultDatasetPage(userId, modelId, runId, "partCycleTime", 0, 100);
                printJson(pageResp);
                requireOk(pageResp, "getRemoteSimulationResultDatasetPage");
            } else {
                System.out.println("Detailed result datasets are not ready yet: " + resultData.getString("resultStatus"));
            }

            printStep("11. resetRemoteSimulation");
            JSONObject resetResp = client.resetSimulation(userId, modelId);
            printJson(resetResp);
            requireOk(resetResp, "resetRemoteSimulation");

            if (unloadAtEnd) {
                printStep("12. unloadRemoteSimulationModel");
                JSONObject unloadResp = client.unloadModel(userId, modelId);
                printJson(unloadResp);
                requireOk(unloadResp, "unloadRemoteSimulationModel");
                unloaded = true;
            } else {
                printStep("12. unload skipped");
                System.out.println("Model remains loaded. Call unloadRemoteSimulationModel manually when done.");
            }

            printStep("DONE");
            System.out.println("MCP control test finished. Check MQTTX for status/progress/console topics.");
        } catch (McpException e) {
            System.err.println("MCP error [code=" + e.getErrorCode() + "]: " + e.getMessage());
            if (e.getRequest() != null) {
                System.err.println("Request: " + JSON.toJSONString(e.getRequest(), JSONWriter.Feature.PrettyFormat));
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (unloadAtEnd && loaded && !unloaded && modelId != null) {
                try {
                    printStep("cleanup unloadRemoteSimulationModel");
                    printJson(client.unloadModel(userId, modelId));
                } catch (Exception cleanupError) {
                    System.err.println("Cleanup unload failed: " + cleanupError.getMessage());
                }
            }
        }
    }

    // ==================== Exception ====================

    public static class McpException extends RuntimeException {
        private final int code;
        private final JSONObject request;

        public McpException(int code, String message, JSONObject request) {
            super(message);
            this.code = code;
            this.request = request;
        }

        public int getErrorCode() { return code; }
        public JSONObject getRequest() { return request; }
    }
}
