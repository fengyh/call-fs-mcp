# 1. Python与FS集成最小验证系统构建

## 1.1 加载和关闭需求

- [x] 在pyqt_app/ui/mcp_module.py中，先验证通过MCP加载和关闭FS模型
  - 已在该程序中实现了磁盘文件的打开和关闭（只是文件名的获取）
  - 还需要调用后文的封装的MCP接口实现在FS中打开和关闭模型
## 1.2 仿真控制需求 
在pyqt_app/ui/mcp_module.py中添加以下ui和相应处理:
- [x] 开始、暂停、继续共用button1，根据text()，点击时调用相应的MCP接口
- [x] 停止button2, 点击时调用相应的MCP接口, 停止仿真
- [x] 重置button3, 点击时调用相应的MCP接口, 重置仿真
- [x] 加速因子slide-bar，1-1000取值, 滑动结束（鼠标放开）时，调用相应的MCP接口设置加速因子
- [x] button1、button2、button3的enable和disable状态，参考 snowy-admin-web/src/views/fscloud/simcontrol/index.vue 的 btn1Text、btn2Text、btn3Text的处理
- [x] 需订阅MQTT（对应于vue版前端的websocket）的仿真状态、仿真进度、仿真控制台，并在相应的位置显示
### 备注
- 暂时不实现参数快照、动画订阅、结果快照
- 如果方便，需要遵循模块化设计，在其它class中处理仿真相关的数据管理
### 实施记录

#### 2026-08-23 需求 1.2 仿真控制（已实现并验证）

**变更文件**

| 文件 | 变更 | 说明 |
|---|---|---|
| `pyqt_app/mcp_client/mqtt_client.py` | 新增 | `MqttSubscriber(QObject)`：paho-mqtt 订阅 `factory/sim/{userId}/{modelId}/+`，信号 `connected`/`disconnected`/`failed`/`status_received`/`progress_received`/`console_received` |
| `pyqt_app/mcp_client/sim_state.py` | 新增 | `SimulationState(QObject)`：MCP↔SIM_STATUS 映射、状态机、进度、控制台环形缓冲、按钮文案/使能派生量（不依赖 Widget） |
| `pyqt_app/ui/mcp_module.py` | 改造 | 右侧新增仿真控制行（button1/2/3 + 开动画 + 加速因子 slider）、实时状态行、FS 控制台日志区；MCP 调用日志移入左下卡片（小高度只读区） |
| `pyqt_app/ui/main_window.py` | 修改 | 新增 `closeEvent` → `module_mcp.shutdown()`（断开 MQTT、等待后台线程） |
| `pyqt_app/app_config.py` / `app_config.json` | 修改 | `mcp` 段新增 `show_animation`、`accelerate_factor`；新增 `mqtt` 段（`broker_host`/`broker_port`/`client_id_prefix`/`topic_prefix`/`keepalive`）与 `mqtt_settings()` |

依赖：新增第三方库 `paho-mqtt 2.1.0`（`pip install --user paho-mqtt`）。缺失时 `mqtt_client.py` 仍可导入，仅 MQTT 功能不可用并给出提示。

**需求逐条对应**

| 需求 | 实现 |
|---|---|
| 开始/暂停/继续 共用 button1 | `run_btn`，点击 → `_on_run_clicked()` 按 `SimulationState.btn1_action`（与按钮文本同源）分派：`start`→`startSimulation(userId, modelId, showAnimation)`、`pause`→`pauseSimulation`、`resume`→`resumeSimulation` |
| 停止 button2 | `stop_btn` → `stopSimulation(userId, modelId)` |
| 重置 button3 | `reset_btn` → `resetSimulation(userId, modelId)` |
| 加速因子 slider（1-1000，松开下发） | `QSlider(1..1000)` + 数值标签；`valueChanged` 只更新标签，`sliderReleased` 才调用 `setAccelerateFactor`，并写入 `app_config.json` 的 `mcp.accelerate_factor` |
| 按钮 enable/disable 参照 `simcontrol/index.vue` | `SimulationState.btn1_enabled/btn2_enabled/btn3_enabled`，规则见下表 |
| 订阅 MQTT 并显示状态/进度/控制台 | 加载成功自动订阅，卸载/关闭停止订阅；状态、进度（`x% (步/总步)`）、最近消息时间、MQTT 连接状态显示在状态卡片；`console` 消息进入「FS 控制台日志」区（带条数统计与清除按钮） |

**按钮规则（照搬 `simcontrol/index.vue` 第 280-311 行，输入为 MQTT/接口返回的 MCP 状态）**

| MCP 状态 | 本地状态 | button1 | button2(停止) | button3(重置) |
|---|---|---|---|---|
| 无 modelId | —（≈NOTYET） | 禁用 | 禁用 | 禁用 |
| `LOADED` / `RESET` | WAITING | 「▶ 开始」可用 | 禁用 | 可用 |
| `RUNNING` | GOING | 「⏸ 暂停」可用 | 可用 | 禁用 |
| `PAUSED` | PAUSED | 「⏵ 继续」可用 | 可用 | 禁用 |
| `FINISHED`/`STOPPED`/`FAILED` | FINISHED | 「▶ 开始」可用 | 禁用 | 可用 |

另有统一约束：MCP 调用进行中（`_busy`）时上述按钮全部禁用。

**MQTT 设计**

- Broker 与协议：`tcp://127.0.0.1:1883`（Python 用 TCP；浏览器走 `ws://...:8083/mqtt`，即前端 `mqttClient.js` 的 MQTT over WebSocket），MQTT 3.1.1、QoS 0、clean session、keepalive 30、`reconnect_delay_set(1, 10)` 自动重连，clientId = `pyqt-fs-{userId}-{毫秒}`。
- 订阅主题：`factory/sim/{userId}/{modelId}/+`（与前端订阅一致；Java 后端为 `factory/sim/+/+/+`，这里按用户/模型收窄）。
- payload（以 `docs/MqttClientTest.log.md` 与 Java `MqttClientService.handleMessage` 为准）：
  - `status` → `{"username","modelId","state":"running|paused|finished|loaded|stopped|reset|failed","timestamp"}`
  - `progress` → `{"username","modelId","progress":12.5,"simTimeStep":250,"totalStep":2000,"timestamp"}`
  - `console` → `{"username","modelId","level","content","timestamp"}`
- 字段兼容：前端两处字段假设不一致（`simcontrol` 读 `data.status`/`data.simTime`，`modelfile` 与 Java 后端读 `state`/`progress`/`simTimeStep`），Python 侧以 Java 后端为准并同时兼容另一套命名。
- 数据流：`FS → MQTT → MqttSubscriber（paho 网络线程）→ pyqtSignal → SimulationState（UI 线程）→ 界面刷新`；MCP 接口返回的 `status` 走同一状态机，MQTT 消息随后校正。

**验证记录（本机实测）**

- `python -m py_compile` 全部新增/修改文件通过；`from ui.mcp_module import MCPModule` 等导入检查通过。
- MQTT 连通性与全链路（真实连接 `127.0.0.1:1883`，用 `verify-publisher` 向**本用户自己的 topic** 发布模拟消息）：订阅 `factory/sim/pyqt-local/MID-1/+` 成功；`status: running` → 按钮变「⏸ 暂停」（停止可用、重置禁用）；`progress` → `25.0% (500/2000 步)`；两条 `console` 正确入库（界面计数 2 条、时间戳格式化）；`paused` → 「⏵ 继续」；`finished` → 「▶ 开始」且重置可用；兼容 `{timestamp, simTime}` 命名 → `37.5% (750/2000 步)`；单条 14 行日志被截断为 10 行 + `{...以下省略...}`；卸载后订阅自动停止（`MQTT: 未连接`）。
- 按钮分派与状态机（offscreen，替换 `_run` 与 `_save_pref` 避免真实调用）：禁用状态下点击无任何调用；LOADED→`start`/`reset`、RUNNING→`pause`/`stop`、PAUSED→`resume`/`stop`、FINISHED→`start`/`reset` 分派正确；拖动 slider 仅更新标签、松开才产生 `setAccelerateFactor` 调用与偏好写入；`开动画`/`loadInUI` 复选框切换即持久化。
- 接口返回注入：`start` 成功 → `RUNNING` + `runId` 记录 + 按钮切换；`pause` 失败（`INVALID_STATE`）、`load` 失败（`MODEL_FILE_NOT_FOUND`）、调用异常（`MCP 错误 -1`）均正确写入 MCP 调用日志与状态栏；`MainWindow` 构造冒烟通过，`closeEvent` 后 MQTT 已断开。
- 待人工验证：真实模型「加载 → 开始/暂停/继续 → 停止 → 重置 → 关闭」全流程与 FS 引擎实际表现、MQTT 真实推送。

**实施中的默认决定（与需求文本的差异说明）**

1. button1 分派使用 `SimulationState.btn1_action`（`start`/`pause`/`resume`）而非比较中文按钮文本；该属性与 `btn1_text` 同源，等价于「根据 text() 分派」且避免文本含图标时比较出错。
2. `开动画` 复选框：需求表未列，但 `startRemoteSimulation` 必须传 `showAnimation`，故补一个复选框（默认 `false`，即不启动动画推流），值持久化到 `app_config.json`。
3. `RESET` 状态映射：前端 `myModel.updateStatus` 的映射表缺 `RESET`（会导致重置后按钮全灰），这里按「重置后回到待仿真（WAITING）」处理。
4. MCP 调用进行中若再次触发调用（如快速连续拖动加速因子），本次调用被忽略并在日志中提示，避免并发覆盖后台线程。

**遗留（按需求备注暂不实现）**：参数快照、动画订阅（`animation/*` JPEG/对象帧）、结果快照、MU 事件（`events`）；后续按需接入。

## 1.3 仿真控制debug
### bug to fix
- [x] 仿真进度不是计算百分数，而是直接显示 simTime 的值 (毫秒)，显示时转换为 x天 hh:MM:ss.sss 的格式
### 实施记录

#### 2026-08-23 需求 1.3 仿真进度显示修复

**问题定位**

真实 `progress` 消息（证据：`docs/McpClientTest.mqttx.export.json`，MQTTX 对 `factory/sim/+/+/+` 的真实抓包）为：

```json
{"username":"demo-1786607903143","recordId":"record-1786607903133","modelId":"a10eddf6cc80",
 "runId":"11070f5c-94fc-4986-950d-3e57d3f7ceb7","uiLoaded":false,"event":"simulationProgress",
 "timestamp":1786607904822,"status":"RUNNING","simTime":99159500,"msgType":"progress"}
```

- `simTime` 为**仿真毫秒**（依据：`latex-doc/public-sections/interface.tex:23`）。
- 真实消息**不含** `progress`/`simTimeStep`/`totalStep`；这三个字段只存在于 `docs/MqttClientTest.log.md` 的测试客户端伪造消息里。
- 1.2 的实现按伪造字段做百分数换算（`totalStep` 缺失 → `progress=0`），导致真实运行时「仿真进度」恒显示 `-`。

**修复内容**

| 文件 | 改动 |
|---|---|
| `pyqt_app/mcp_client/sim_state.py` | 新增 `format_sim_time(ms)`；状态字段 `progress`/`sim_time_step`/`total_step` → `sim_time`（仿真毫秒）；`update_from_mqtt_progress` 只取 `simTime`（不使用 `status` 等其它字段）；派生量 `progress_text` → `sim_time_text`；`reset_runtime` 复位 `sim_time` |
| `pyqt_app/ui/mcp_module.py` | 状态行「仿真进度」改用 `state.sim_time_text`（标签文案不变） |

**时间格式规则**：从最高非零单位开始显示，前面为 0 的单位不显示；毫秒固定 3 位、时/分/秒各 2 位；天数非零时带「天」字。

| `simTime`(ms) | 显示 |
|---|---|
| `0` / `250` | `00.000` / `00.250` |
| `20000` | `20.000` |
| `72750` | `01:12.750` |
| `60000` / `600000` | `01:00.000` / `10:00.000` |
| `3600000` | `01:00:00.000` |
| `86400000` | `1天 00:00:00.000` |
| `99159500` | `1天 03:32:39.500` |
| `None` / 负数 / 非数字 | `00.000` |

未加载模型（无 `modelId`）时显示 `-`。

**验证记录（本机实测）**

- `format_sim_time` 逐例校验 14 例（上表全部取值 + `None`、负数、浮点 `72750.7`、字符串 `"x"`）全部通过。
- 离线回放真实抓包的 `status`（`LOADED`/`RUNNING`）与 `progress`（`simTime` 为 `0`、`99159500`）消息：状态机与按钮使能正确，进度显示 `00.000` → `1天 03:32:39.500`；用户提供的实例（`simTime=72750`、`status=STOPPED`）→ `01:12.750`，且 `status` 未被用于状态更新（符合「不要使用其它字段」）。
- 真实 MQTT 端到端（连 `127.0.0.1:1883`，用隔离的 userId/modelId 自发布）：界面「仿真进度」连续刷新 `00.250 → 20.000 → 01:12.750 → 1天 03:32:39.500`；`RUNNING`/`PAUSED`/`STOPPED` 的状态文案与按钮使能正确；停止后进度保留。
- 交叉验证：验证期间订阅到本机真实运行的 `factory/sim/pyqt-local/223fb9269181` 的 `status: STOPPED` 消息，界面随之显示「仿真结束」，说明真实 FS 消息格式与我们的解析完全兼容。
- `python -m py_compile` 与导入检查通过；offscreen `MainWindow` 冒烟：进度 `-` → `00.000` → `1天 03:32:39.500`。

**与 1.2 记录的差异更正**：1.2 记录中「进度（`x% (步/总步)`）」「兼容 `{timestamp, simTime}` 命名」等描述基于测试客户端的伪造字段，按本节结论作废。

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
