# -*- coding: utf-8 -*-
"""MCP 远程仿真控制客户端（Python版）

本模块直接通过 HTTP JSON-RPC 2.0 调用 FactorySimulation 的 MCP 远程仿真控制服务，

底层 HTTP 通信使用标准库 urllib（零第三方依赖），业务 JSON 仍在 `result.content[0].text` 中二次解析。

@author fengyuhe
@date   2026/08/23
"""
import json
import threading
import urllib.error
import urllib.request

DEFAULT_ENDPOINT_URL = "http://127.0.0.1:8081/mcp"
DEFAULT_API_KEY = "7sK9p2G@2026"


class McpServiceException(RuntimeError):
    """MCP 服务调用异常"""

    def __init__(self, code, message, request=None):
        super().__init__(message)
        #: 业务错误码；连接层失败为 -1
        self.code = code
        #: 错误描述
        self.message = message
        #: 触发失败的 JSON-RPC 请求体（可能为 None）
        self.request = request

    @property
    def error_code(self):
        return self.code

    def __str__(self):
        return "[%s] %s" % (self.code, self.message)


class McpClient(object):
    """通过 HTTP JSON-RPC 2.0 调用 FactorySimulation 的 MCP 远程仿真控制服务

    :param endpoint_url: MCP 服务地址，如 http://127.0.0.1:8081/mcp
    :param api_key: X-API-Key 值
    :param timeout: HTTP 超时（秒），默认 60
    :param log_hook: 可选日志回调 callable(str)
    """

    def __init__(self, endpoint_url=DEFAULT_ENDPOINT_URL, api_key=DEFAULT_API_KEY,
                 timeout=60.0, log_hook=None):
        self.endpoint_url = endpoint_url
        self.api_key = api_key
        self.timeout = timeout
        self.log_hook = log_hook
        self.requestId = 0
        self._id_lock = threading.Lock()
        self.log("McpClient initialized, endpointUrl=%s", endpoint_url)

    # ==================== 日志 ====================

    def log(self, fmt, *args):
        """写一条客户端日志（无 log_hook 时静默）"""
        if self.log_hook is None:
            return
        try:
            self.log_hook(fmt % args if args else fmt)
        except Exception:
            pass

    # ==================== 高层 API ====================

    def initialize(self):
        """初始化 MCP 会话"""
        params = {
            "protocolVersion": "2025-06-18",
            "capabilities": {},
            "clientInfo": {"name": "pyqt-app", "version": "0.1.0"},
        }
        return self.call("initialize", params)

    def notifyInitialized(self):
        """发送 notifications/initialized 通知（无响应体，失败不抛异常）"""
        request = self.buildRequest("notifications/initialized", None)
        try:
            self._post(request, expect_json=False)
        except McpServiceException as exc:
            self.log("notifications/initialized failed: %s", exc)
            return False
        return True

    def initializeSession(self):
        """initialize + notifications/initialized，返回 initialize 的原始响应"""
        response = self.initialize()
        self.notifyInitialized()
        return response

    def listTools(self):
        """获取 MCP 工具列表"""
        return self.call("tools/list", {})

    def loadModel(self, userId, recordId, modelFileName, loadInUI):
        """加载仿真模型"""
        args = {
            "userId": userId,
            "recordId": recordId,
            "modelFileName": modelFileName,
            "loadInUI": loadInUI,
        }
        self.log("MCP loadModel: userId=%s, recordId=%s, modelFileName=%s, loadInUI=%s",
                 userId, recordId, modelFileName, loadInUI)
        return self.callTool("loadRemoteSimulationModel", args)

    def unloadModel(self, userId, modelId):
        """卸载仿真模型"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("unloadRemoteSimulationModel", args)

    def listModels(self, userId):
        """列出用户已加载的模型"""
        args = {"userId": userId}
        return self.callTool("listRemoteSimulationModels", args)

    def getStatus(self, userId, modelId):
        """查询仿真状态"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("getRemoteSimulationStatus", args)

    def startSimulation(self, userId, modelId, showAnimation):
        """启动仿真"""
        args = {"userId": userId, "modelId": modelId, "showAnimation": showAnimation}
        return self.callTool("startRemoteSimulation", args)

    def pauseSimulation(self, userId, modelId):
        """暂停仿真"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("pauseRemoteSimulation", args)

    def resumeSimulation(self, userId, modelId):
        """恢复仿真"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("resumeRemoteSimulation", args)

    def resetSimulation(self, userId, modelId):
        """重置仿真"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("resetRemoteSimulation", args)

    def stopSimulation(self, userId, modelId):
        """停止仿真"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("stopRemoteSimulation", args)

    def getResultSnapshot(self, userId, modelId, runId):
        """获取仿真结果快照"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
        }
        return self.callTool("getRemoteSimulationResultSnapshot", args)

    def listParameterTables(self, userId, modelId):
        """列出参数表"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("listRemoteSimulationParameterTables", args)

    def getParameterSnapshot(self, userId, modelId, runId):
        """获取参数快照"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
        }
        return self.callTool("getRemoteSimulationParameterSnapshot", args)

    def updateParameterTable(self, userId, modelId, tableName, rowsJson):
        """更新参数表（全量替换）"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "tableName": tableName,
            "rowsJson": rowsJson,
        }
        return self.callTool("updateRemoteSimulationParameterTable", args)

    def updateParameterCells(self, userId, modelId, tableName, patchesJson):
        """更新参数表（单元格级）"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "tableName": tableName,
            "patchesJson": patchesJson,
        }
        return self.callTool("updateRemoteSimulationParameterCells", args)

    def batchUpdateParameterTables(self, userId, modelId, tablesJson, bSaveModel):
        """批量更新参数表（原子写入多张表）"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "tablesJson": tablesJson,
            "bSaveModel": bSaveModel,
        }
        return self.callTool("batchUpdateRemoteSimulationParameterTable", args)

    def getResultDatasetPage(self, userId, modelId, runId, dataset, limit, offset):
        """获取结果数据集分页"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
            "dataset": dataset,
            "limit": limit,
            "offset": offset,
        }
        return self.callTool("getRemoteSimulationResultDatasetPage", args)

    def getEnergyPowerCurvePage(self, userId, modelId, runId, componentId, mediumId,
                                scopeType, deviceName, limit, offset):
        """获取能耗功率曲线分页（MCP getRemoteSimulationEnergyPowerCurvePage）

        仅可在仿真结束且 resultStatus=READY 后调用。
        """
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
            "componentId": componentId if componentId is not None else "",
            "mediumId": mediumId if mediumId is not None else "",
            "scopeType": scopeType if (scopeType and scopeType.strip()) else "ALL_DEVICES",
            "deviceName": deviceName if deviceName is not None else "",
            "limit": limit,
            "offset": offset,
        }
        return self.callTool("getRemoteSimulationEnergyPowerCurvePage", args)

    def listAnimationLayers(self, userId, modelId):
        """获取动画层列表"""
        args = {"userId": userId, "modelId": modelId}
        return self.callTool("listRemoteSimulationAnimationLayers", args)

    def selectAnimationLayer(self, userId, modelId, layerId, frameRate):
        """选择动画层"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "layerId": layerId,
            "frameRate": frameRate,
        }
        return self.callTool("selectRemoteSimulationAnimationLayer", args)

    def setAnimationEnabled(self, userId, modelId, enabled):
        """开关动画"""
        args = {"userId": userId, "modelId": modelId, "enabled": enabled}
        return self.callTool("setRemoteSimulationAnimationEnabled", args)

    def setEventPublishing(self, userId, modelId, enabled, publishIntervalMs):
        """开启/停止 MU 事件 MQTT 发布（events 主题）"""
        args = {"userId": userId, "modelId": modelId, "enabled": enabled}
        if publishIntervalMs is not None:
            args["publishIntervalMs"] = publishIntervalMs
        return self.callTool("setRemoteSimulationEventPublishing", args)

    def setAccelerateFactor(self, userId, modelId, factor):
        """设置仿真加速因子"""
        args = {"userId": userId, "modelId": modelId, "accelerateFactor": factor}
        return self.callTool("setRemoteSimulationAccelerateFactor", args)

    def getSvgPicture(self, userId, modelId, layerId):
        """获取 SVG 高清静态图"""
        args = {"userId": userId, "modelId": modelId, "layerId": layerId}
        return self.callTool("getSvgPicture", args)

    def getObjectPositionSnapshot(self, userId, modelId, runId, layerId, simTime):
        """查询时间轴对象位置快照"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
            "layerId": layerId if layerId is not None else "root",
            "simTime": simTime,
        }
        return self.callTool("getRemoteSimulationObjectPositionSnapshot", args)

    def getObjectPositionSnapshotPage(self, userId, modelId, runId, layerId, simTime,
                                      snapshotId, entityKind, offset, limit):
        """分页查询完整对象位置快照

        全量快照需多次分页拉取，snapshotId 需原样回传防止漂移。
        """
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
            "layerId": layerId if layerId is not None else "root",
            "simTime": simTime,
            "snapshotId": snapshotId if snapshotId is not None else "",
            "entityKind": entityKind if entityKind is not None else "ALL",
            "offset": offset,
            "limit": limit,
        }
        return self.callTool("getRemoteSimulationObjectPositionSnapshotPage", args)

    def getSimulationEvents(self, userId, modelId, runId, startSimTime, duration,
                            offset, limit):
        """查询时间窗口内的仿真事件"""
        args = {
            "userId": userId,
            "modelId": modelId,
            "runId": runId if runId is not None else "",
            "startSimTime": startSimTime,
            "duration": duration,
            "offset": offset,
            "limit": limit,
        }
        return self.callTool("getRemoteSimulationEvents", args)

    # ==================== 底层 JSON-RPC ====================

    def call(self, method, params):
        """调用非 tool 类 RPC 方法（initialize, tools/list 等）"""
        request = self.buildRequest(method, params)
        return self.post(request)

    def callTool(self, toolName, arguments):
        """调用 tools/call 包装的工具（解包 content[0].text → 业务 JSON）"""
        params = {
            "name": toolName,
            "arguments": arguments if arguments is not None else {},
        }
        request = self.buildRequest("tools/call", params)
        rawResponse = self.post(request)
        return self.unwrapResult(rawResponse)

    # ==================== 内部实现 ====================

    def buildRequest(self, method, params):
        """构造 JSON-RPC 2.0 请求体（id 自增，线程安全）"""
        with self._id_lock:
            self.requestId += 1
            request_id = str(self.requestId)
        request = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
        }
        if params is not None:
            request["params"] = params
        return request

    def post(self, requestBody):
        """发送 HTTP POST 请求，返回解析后的 JSON"""
        return self._post(requestBody, expect_json=True)

    def _post(self, requestBody, expect_json=True):
        data = json.dumps(requestBody, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.endpoint_url, data=data, method="POST")
        request.add_header("Content-Type", "application/json; charset=UTF-8")
        request.add_header("X-API-Key", self.api_key)

        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                status_code = response.status
                response_body = response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as exc:
            # 非 2xx：错误流里可能仍是带 error 字段的 JSON-RPC 响应
            status_code = exc.code
            response_body = exc.read().decode("utf-8", "replace")
        except Exception as exc:
            raise McpServiceException(
                -1, "MCP connection failed: %s" % exc, requestBody)

        if not response_body.strip():
            if expect_json:
                raise McpServiceException(
                    -1, "Empty MCP response body (HTTP %s)" % status_code, requestBody)
            return None

        try:
            result = json.loads(response_body)
        except ValueError as exc:
            raise McpServiceException(
                -1, "MCP response is not valid JSON (HTTP %s): %s" % (status_code, exc),
                requestBody)

        # 如果有 MCP error 字段, 抛出异常
        if isinstance(result, dict) and result.get("error") is not None:
            error = result.get("error") or {}
            if not isinstance(error, dict):
                raise McpServiceException(-1, str(error), requestBody)
            raise McpServiceException(
                error.get("code", -1), error.get("message", ""), requestBody)

        return result

    def unwrapResult(self, rawResponse):
        """从 MCP tools/call 响应中解包: content[0].text → 业务 JSON dict"""
        result = rawResponse.get("result") if isinstance(rawResponse, dict) else None
        if result is None:
            raise McpServiceException(-1, "No 'result' field in MCP response", None)

        content = result.get("content")
        if not content:
            raise McpServiceException(-1, "No 'content' in MCP result", None)

        first = content[0] or {}
        text = first.get("text")
        if not text:
            raise McpServiceException(-1, "Empty 'text' in MCP content", None)

        try:
            return json.loads(text)
        except ValueError as exc:
            raise McpServiceException(
                -1, "Invalid JSON in MCP content text: %s" % exc, None)
