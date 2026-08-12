# 远程仿真控制 MVP 实现总结

日期：2026-06-16

## 1. 本轮目标

本轮实现的是基于 MCP 的远程仿真控制 MVP。目标不是 AI 助手，也不是让 LLM 调工具，而是让云端后端通过 MCP 直接控制 FactorySimulation 仿真引擎。

第一期 MVP 覆盖：

- 加载模型
- 卸载模型
- 查询已加载模型
- 启动仿真
- 暂停仿真
- 恢复仿真
- 重置仿真
- 停止仿真
- 查询状态
- 分层获取结果：第一层有界摘要，第二层全量数据分页读取
- 通过 MQTT 以 5 FPS 默认帧率推送浏览器动画 JPEG 帧
- 按根模型/子模型层级选择动画画面
- 分页获取完整对象、设备、MU、仓库和货位位置快照
- 通过 MCP 时间窗和可开关 MQTT 批次提供同构 MU 事件

暂不包含：

- AI 助手
- LLM 调用
- 知识库
- PostgreSQL / TimescaleDB
- 通用全量过程数据落库
- 浏览器原生矢量动画与交互式建模
- 参数快照完整体系
- 自动优化 / 自动改参数
- 复杂权限系统

## 2. 本轮新增/修改文件

新增远程仿真模型与管理代码：

- `src/main/java/fs/util/ai/mcp/remote/RemoteSimulationStatus.java`
- `src/main/java/fs/util/ai/mcp/remote/RemoteSimulationSession.java`
- `src/main/java/fs/util/ai/mcp/remote/RemoteSimulationResponse.java`
- `src/main/java/fs/util/ai/mcp/remote/RemoteSimulationMuEvent.java`
- `src/main/java/fs/util/ai/mcp/remote/RemoteSimulationManager.java`

新增 MCP 工具类：

- `src/main/java/fs/util/ai/mcp/tools/RemoteSimulationTools.java`

修改 MCP 工具注册：

- `src/main/java/fs/util/ai/mcp/McpToolBridge.java`

修改 Maven 编译参数：

- `pom.xml`

新增 `-parameters` 的原因：当前 `McpToolBridge` 通过 Java 反射参数名生成 MCP `inputSchema`，如果不保留参数名，工具参数可能变成 `arg0`、`arg1`，前后端联调会很难用。

## 3. 核心类职责

### 3.1 RemoteSimulationStatus

远程仿真状态枚举：

- `NOT_LOADED`
- `LOADED`
- `RUNNING`
- `PAUSED`
- `FINISHED`
- `STOPPED`
- `FAILED`

### 3.2 RemoteSimulationSession

内存态会话对象，保存一个远程模型会话的关键字段：

- `userId`
- `recordId`
- `modelId`
- `modelFileName`
- `modelFilePath`
- `SimulationModel model`
- `RemoteSimulationStatus status`
- `runId`
- `simTime`
- `message`
- `loadedAt`
- `startedAt`
- `finishedAt`
- `resultSnapshot`

### 3.3 RemoteSimulationManager

远程仿真管理器，当前为单 JVM 内存管理，不落库。

主要职责：

- 管理 `userId -> RemoteSimulationSession`
- 管理 `modelId -> RemoteSimulationSession`
- 控制单用户最多 1 个模型
- 控制全局最多 10 个已加载模型
- 控制全局最多 5 个运行中模型
- 支持按 MCP Server 所在机器上的绝对路径加载模型，也支持相对当前工作目录解析
- 调用当前工程实际仿真 API 完成加载、运行、暂停、恢复、重置、停止
- 监听仿真事件并更新状态
- 输出统一 JSON 响应

模型路径由云端后端上传/存储模块控制，MCP 工具层不再做默认目录兜底搜索。

### 3.4 RemoteSimulationTools

面向 MCP 暴露的工具类，核心工具方法包括：

- `loadRemoteSimulationModel`
- `unloadRemoteSimulationModel`
- `listRemoteSimulationModels`
- `getRemoteSimulationStatus`
- `listRemoteSimulationAnimationLayers`
- `selectRemoteSimulationAnimationLayer`
- `startRemoteSimulation`
- `pauseRemoteSimulation`
- `resumeRemoteSimulation`
- `resetRemoteSimulation`
- `stopRemoteSimulation`
- `getRemoteSimulationResultSnapshot`
- `getRemoteSimulationResultDatasetPage`

所有方法返回 JSON 字符串，云端后端需要解析 MCP `content[0].text` 中的 JSON。

说明：

- `recordId` 是后端模型库记录 ID，由 `loadRemoteSimulationModel` 必填传入，并在后续响应与 MQTT payload 中透传。
- `modelId` 是 FactorySimulation 加载后的内存仿真实例 ID，仍用于 MCP 控制接口定位 session。
- `loadInUI` 决定加载时是否创建 FactorySimulation 桌面模型 tab；响应中的 `uiLoaded` 表示实际 UI 加载状态。
- `showAnimation=true` 会启用引擎动画状态，并启动 MQTT JPEG 动画帧推送；它不负责创建桌面 UI。
- 浏览器动画默认推送根层 `root`，默认 5 FPS，最高 10 FPS；子模型通过动画层选择工具切换。

## 4. 状态流转

常见状态流转如下：

```text
NOT_LOADED
  -> LOADED
  -> RUNNING
  -> PAUSED
  -> RUNNING
  -> FINISHED

RUNNING / PAUSED
  -> STOPPED
  -> LOADED   (reset 后)

任何控制失败
  -> FAILED   (主要用于 start 异常)
```

说明：

- `loadRemoteSimulationModel` 成功后状态为 `LOADED`。
- `loadInUI=true` 时 UI 和 MCP 复用同一个模型实例，并通过组合监听器同时更新桌面动画和远程状态。
- `startRemoteSimulation` 成功发起运行后状态为 `RUNNING`。
- 仿真自然结束后监听器会将状态更新为 `FINISHED`。
- `stopRemoteSimulation` 会将状态更新为 `STOPPED`。
- `resetRemoteSimulation` 只允许在非运行/非暂停状态调用，成功后状态回到 `LOADED`。

## 5. 分层结果

`getRemoteSimulationResultSnapshot` 返回第一层有界摘要：

- `runId`
- `recordId`
- `modelId`
- `modelFileName`
- `status`
- `simTime`
- `startedAt`
- `finishedAt`
- `totalOutput`
- `throughputPerHour`
- `currentWip`
- `resultStatus`
- `summaryResults`
- `datasets`
- `message`

计算逻辑：

- `simTime` 来自 `model.getDefaultInstance().getSimTime()`。
- `totalOutput` 优先遍历 `ExitInstance`，累加 `exit.size()`；失败时返回 0。
- `throughputPerHour = totalOutput * 3600000.0 / simTime`，当 `simTime <= 0` 时返回 0。
- `currentWip` 优先使用 `instance.getWIPNumber()`；失败时返回 0。
- `summaryResults` 包含体量有界的节拍、UPH、利用率、Lean 汇总和 Energy 汇总。
- `datasets` 返回第二层可分页数据集目录。
- `getRemoteSimulationResultDatasetPage` 使用 `dataset + offset + limit` 分页读取全量数据，单页上限 1000 条。
- WIP 时间线、Lean LT、Lean 换型明细、Energy 功率曲线均已拆为独立数据集，避免单次生成超大 JSON。
- 分页计算使用会话级结果锁，不占用远程仿真管理器的全局锁。

## 6. 验证情况

已通过：

- `git diff --check`
- 新增远程包 + `RemoteSimulationTools` 窄编译：

```bash
javac -encoding GBK -parameters -implicit:none \
  -cp "$(cat /private/tmp/factorysimulation-cp.txt):target/classes" \
  -sourcepath src/main/java \
  -d /private/tmp/factorysimulation-remote-classes \
  src/main/java/fs/util/ai/mcp/remote/*.java \
  src/main/java/fs/util/ai/mcp/tools/RemoteSimulationTools.java
```

全量 Maven 编译：

```bash
mvn -q -DskipTests compile
```

当前未通过，但失败集中在当前工程既有源码问题，例如 UI/3D/Lombok 相关类缺失方法：

- `ModelTabPanel.getLayer()`
- `TDAttribute.getExt()`
- `TDAttribute.getAnimationMap()`
- `WorkUnit.getStatus()`

这些不是本轮远程 MCP 实现新增代码直接导致。

## 7. 一句话结论

本轮已经在现有 MCP Server 框架中新增了远程仿真控制工具集。云端后端可以通过 MCP `tools/call` 调用这些工具，实现模型加载、运行控制、状态查询和结果快照读取。前端建议通过云端后端封装后的 REST/Web API 调用，不建议 Vue 直接连 MCP。
