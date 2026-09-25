# 1. Python与FS集成最小验证系统构建

## 1.1 加载和关闭需求

- [x] 在pyqt_app/ui/mcp_module.py中，先验证通过MCP加载和关闭FS模型
  - 已在该程序中实现了磁盘文件的打开和关闭（只是文件名的获取）
  - 还需要调用后文的封装的MCP接口实现在FS中打开和关闭模型
## 1.2 仿真控制需求 
在pyqt_app/ui/mcp_module.py中添加以下ui和相应处理:
- [ ] 开始、暂停、继续共用button1，根据text()，点击时调用相应的MCP接口
- [ ] 停止button2, 点击时调用相应的MCP接口, 停止仿真
- [ ] 重置button3, 点击时调用相应的MCP接口, 重置仿真
- [ ] 加速因子slide-bar，1-1000取值, 滑动结束（鼠标放开）时，调用相应的MCP接口设置加速因子
- [ ] button1、button2、button3的enable和disable状态，参考 snowy-admin-web/src/views/fscloud/simcontrol/index.vue 的 btn1Text、btn2Text、btn3Text的处理

# 2. 移植接口封装类java->python

## 2.1 需求

- 原封装：java springboot的service: snowy-plugin/snowy-plugin-fscloud/src/main/java/vip/xiaonuo/fscloud/modular/simulation/service/McpClientService.java
- 新封装：pyqt_app/mcp_client/mcpClient.py
- 目的：将原来的java封装转换为python封装，以便在PyQt应用中使用。

## 2.2 约束条件

- 之前的封装是基于Java的Spring Boot框架，架构为“MCP Server - Spring Boot 后端 - Vue前端”，新的封装需要适应Python的PyQt框架，架构简化为“MCP Server - Python 应用程序”。
- 新封装需要保持与原封装相同的功能和接口。

## 2.3 实现细节

### 2.3.1 模块划分

| 文件 | 变更 | 说明 |
|---|---|---|
| `pyqt_app/mcp_client/mcpClient.py` | 新增 | `McpClient` + `McpServiceException`，Java `McpClientService` 的 Python 等价实现 |
| `pyqt_app/mcp_client/__init__.py` | 新增 | 导出 `McpClient` / `McpServiceException` / 默认 endpoint、api key |
| `pyqt_app/mcp_client/mcp_runner.py` | 新增 | `McpCallRunner(QThread)`：后台线程执行单次 MCP 调用 |
| `pyqt_app/ui/mcp_module.py` | 改造 | 页签5：MCP 会话状态、操作按钮、`loadInUI` 勾选、调用日志与状态机 |
| `pyqt_app/app_config.py` | 修改 | 新增 `MCP_DEFAULTS` 配置段与 `mcp_settings()` |
| `pyqt_app/app_config.json` | 修改 | 新增 `mcp` 段：`endpoint_url` / `api_key` / `user_id` / `load_in_ui` |

Java 侧代码不改动，`McpClientService.java` 保留为对照实现。

### 2.3.2 配置注入

Java 的 `@Resource McpProperties` + `@PostConstruct init()` 改为构造参数注入：

- `McpProperties.endpointUrl` → `McpClient(endpoint_url=...)`
- `McpProperties.apiKey` → `McpClient(api_key=...)`（请求头 `X-API-Key`）
- `@Slf4j log.info` → 可选 `log_hook` 回调，PyQt 侧经 `pyqtSignal` 转到界面日志区（跨线程安全）

默认值与 `McpProperties.java` 保持一致（`http://127.0.0.1:8081/mcp`、`7sK9p2G@2026`），
可用 `app_config.json` 的 `mcp.endpoint_url` 切到部署地址（对应 `application-prod.properties` 的 `fscloud.mcp.endpoint-url`）。

### 2.3.3 接口对应关系

方法名、参数顺序、默认值语义与 Java 版完全一致（保持 camelCase），共 **28 个高层方法 + `call` / `callTool`**：

- 会话：`initialize` / `notifyInitialized` / `initializeSession` / `listTools`
- 模型：`loadModel` / `unloadModel` / `listModels` / `getStatus`
- 仿真控制：`startSimulation` / `pauseSimulation` / `resumeSimulation` / `resetSimulation` / `stopSimulation`
- 结果与参数：`getResultSnapshot` / `listParameterTables` / `getParameterSnapshot` /
  `updateParameterTable` / `updateParameterCells` / `batchUpdateParameterTables` /
  `getResultDatasetPage` / `getEnergyPowerCurvePage`
- 动画与事件：`listAnimationLayers` / `selectAnimationLayer` / `setAnimationEnabled` /
  `setEventPublishing` / `setAccelerateFactor` / `getSvgPicture` /
  `getObjectPositionSnapshot` / `getObjectPositionSnapshotPage` / `getSimulationEvents`

底层实现与 Java 一一对应：

- `buildRequest` → JSON-RPC 2.0 请求体，`id` 自增（加锁，比 Java 的裸 `++` 更稳）
- `post` → `urllib.request` POST，`Content-Type: application/json; charset=UTF-8` + `X-API-Key`；
  非 2xx 仍读取响应体（错误流里可能是带 `error` 字段的 JSON-RPC 响应）；有 `error` 字段即抛 `McpServiceException`
- `unwrapResult` → `result.content[0].text` 二次解析为业务 JSON，缺字段时抛 `McpServiceException`
- 业务失败（`success=false` + `code`）不抛异常，与原封装一致，由调用方检查
- 空值语义保留：`runId`/`layerId` 等 `null` → `""`，`scopeType` 空 → `ALL_DEVICES`，
  `entityKind` 空 → `ALL`，`publishIntervalMs` 为 `None` 时不下发

与 Java 版的差异（3 处）：

1. `initialize()` 按 MCP 协议带上 `protocolVersion` / `capabilities` / `clientInfo`（Java 版未带，实测该 Server 两者都接受）；
   另提供 `initializeSession()` 补发 `notifications/initialized`。
2. Java 的 `connectTimeout=10s` / `readTimeout=60s` 分开设置，`urllib` 只有单一超时，统一取 60s。
3. 响应体不是合法 JSON 时抛 `McpServiceException`（Java 版由 hutool 抛解析异常）。

### 2.3.4 页签5 的最小验证流程

1. `📂加载模型`：选择本地 `.fs` 文件（仅取文件名，沿用原有逻辑）；
2. `🔌测试连接`：`initialize` + `notifications/initialized` + `tools/list`，日志打印 Server 名称与工具数；
3. `▶加载到 FS`：生成 `recordId`（本地无模型库，取 `pyqt-<uuid4 前 8 位>`）后调用
   `loadRemoteSimulationModel(userId, recordId, modelFileName, loadInUI)`，
   `modelFileName` 传所选文件**绝对路径**（MCP Server 与 PyQt 应用同机），保存返回的 `modelId`；
4. `■关闭 FS 模型`：用 `modelId` 调 `unloadRemoteSimulationModel(userId, modelId)`，成功后复位本地状态；
5. `↻刷新已加载`：`listRemoteSimulationModels` 校正本地状态（含“FS 侧已被卸载”的复位场景）。

状态机与约束：未选文件时禁用「加载到 FS」；已加载时禁用「加载到 FS」（MCP 侧限制单用户 1 个模型）；
调用进行中禁用全部按钮；本地文件关闭时若 FS 仍在加载则提示先关闭 FS 模型。
所有 MCP 调用均通过 `McpCallRunner` 在后台线程执行，避免 60s 读超时卡住界面。

### 2.3.5 验证记录（2026-08-23）

- `python -m py_compile` 全部新增/修改文件通过；`from mcp_client import McpClient` 等导入检查通过。
- 连通性（真实调用本机 `http://127.0.0.1:8081/mcp`，无副作用）：
  - `initializeSession()` → `serverInfo = {langchain4j-factory-simulation, 1.0.0}`；
  - `listTools()` → 26 个工具，含 `loadRemoteSimulationModel` / `unloadRemoteSimulationModel` /
    `getRemoteSimulationEnergyPowerCurvePage`；
  - `listModels("pyqt-local")` → `success=true, models=[]`；
  - 业务错误路径：`unloadModel("pyqt-local", "no-such-model")` → `success=false, code=MODEL_NOT_FOUND`（不抛异常）；
  - 连接失败路径：不可达端点 → `McpServiceException(code=-1, "MCP connection failed: ...")`。
- 界面状态机：offscreen 模式下实例化 `MCPModule`，逐个注入伪响应（加载成功/失败、卸载成功/失败、
  刷新同步、测试连接、调用异常），按钮使能、状态文案与日志输出均符合预期；`MainWindow` 整窗构造冒烟通过。
- 待人工验证：在带界面的实际环境中用 `道路回填【5V5】.fs` 走「加载 → 刷新/状态 → 卸载」，
  确认 FS 侧实际加载效果与 `loadInUI` 行为。

### 2.3.6 已知风险

- `道路回填【5V5】.fs` 文件名为中文：JSON 层按 UTF-8 传输无问题，若 FS 引擎拒绝中文路径，可用同内容英文名副本验证。
- `loadInUI=true` 时 FS 需要具备桌面 UI，否则返回 `UI_LOAD_FAILED`；`app_config.json` 的 `mcp.load_in_ui` 可改默认值。
- MCP 侧限制：单用户同时 1 个模型、全局最多 10 个；重复加载需先卸载。
- `app_config.json` 中 `api_key` 为明文（与 `McpProperties.java` 中的默认值同源，非新增泄露面）。
