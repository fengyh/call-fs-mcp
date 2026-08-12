# 远程仿真 MCP 接口说明与调用示例

日期：2026-06-16

## 1. 调用角色建议

推荐链路：

```text
Vue 前端
  -> 云端后端 REST/WebSocket
  -> MCP HTTP Server
  -> FactorySimulation 仿真引擎
```

不推荐 Vue 直接调用 MCP，原因：

- MCP Server 当前是内部控制接口，不适合直接暴露给浏览器。
- MCP 鉴权、跨域、端口暴露、用户权限需要后端统一处理。
- MCP 返回格式是工具协议格式，前端直接解析会更绕。

## 2. MCP HTTP 基础信息

当前 MCP Server 使用 HTTP JSON-RPC。

默认端点：

```text
POST http://127.0.0.1:8081/mcp
```

请求头：

```text
Content-Type: application/json
X-API-Key: <MCP_API_KEY>
```

注意：文档中不写真实 API key。实际部署时请从配置或安全渠道提供给云端后端。

## 3. MCP 初始化

请求：

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": {
      "name": "cloud-backend",
      "version": "0.1.0"
    }
  }
}
```

成功后再发送：

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "notifications/initialized"
}
```

## 4. 查询工具列表

请求：

```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/list",
  "params": {}
}
```

结果中应能看到以下远程仿真工具：

- `loadRemoteSimulationModel`
- `unloadRemoteSimulationModel`
- `listRemoteSimulationModels`
- `getRemoteSimulationStatus`
- `listRemoteSimulationAnimationLayers`
- `getSvgPicture`
- `getRemoteSimulationObjectPositionSnapshot`
- `getRemoteSimulationObjectPositionSnapshotPage`
- `getRemoteSimulationEvents`
- `setRemoteSimulationEventPublishing`
- `selectRemoteSimulationAnimationLayer`
- `setRemoteSimulationAnimationEnabled`
- `setRemoteSimulationAccelerateFactor`
- `startRemoteSimulation`
- `pauseRemoteSimulation`
- `resumeRemoteSimulation`
- `resetRemoteSimulation`
- `stopRemoteSimulation`
- `getRemoteSimulationResultSnapshot`
- `getRemoteSimulationResultDatasetPage`
- `listRemoteSimulationParameterTables`
- `getRemoteSimulationParameterSnapshot`
- `updateRemoteSimulationParameterTable`
- `batchUpdateRemoteSimulationParameterTable`
- `updateRemoteSimulationParameterCells`

## 5. MCP tools/call 通用格式

请求格式：

```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "method": "tools/call",
  "params": {
    "name": "工具名",
    "arguments": {
      "参数名": "参数值"
    }
  }
}
```

响应格式：

```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"success\":true,\"code\":\"OK\",\"message\":\"success\",\"data\":{}}"
      }
    ]
  }
}
```

重点：远程仿真工具的业务 JSON 在 `result.content[0].text` 中，是一个 JSON 字符串。云端后端需要做两层解析：

1. 先解析 MCP JSON-RPC 响应。
2. 再解析 `content[0].text` 里的业务 JSON。

## 6. 统一业务响应格式

成功：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败：

```json
{
  "success": false,
  "code": "MODEL_NOT_FOUND",
  "message": "Model not found or not loaded by this user.",
  "data": {}
}
```

错误码：

- `OK`
- `INVALID_ARGUMENT`
- `MODEL_FILE_NOT_FOUND`
- `USER_MODEL_LIMIT_EXCEEDED`
- `GLOBAL_MODEL_LIMIT_EXCEEDED`
- `GLOBAL_RUNNING_LIMIT_EXCEEDED`
- `MODEL_NOT_FOUND`
- `INVALID_STATE`
- `SIMULATION_START_FAILED`
- `SIMULATION_CONTROL_FAILED`
- `UI_LOAD_FAILED`
- `PARAMETER_TABLE_NOT_FOUND`
- `RESULT_NOT_READY`
- `DATASET_NOT_FOUND`
- `PAGE_SIZE_LIMIT_EXCEEDED`
- `INTERNAL_ERROR`

## 7. 工具接口详情

### 7.1 loadRemoteSimulationModel

作用：加载模型，并通过 `loadInUI` 决定是否同时在 FactorySimulation 桌面程序中创建模型 tab。

参数：

```json
{
  "userId": "u001",
  "recordId": "backend-model-record-id",
  "modelFileName": "AGV.fs",
  "loadInUI": true
}
```

说明：

- `modelFileName` 可以传模型文件绝对路径，例如后端上传后保存到云端引擎服务器的实际路径。
- `recordId` 是后端模型库中的模型记录 ID，必填；`modelId` 仍然是 MCP Server 内存中的仿真实例 ID。
- 也可以传相对路径；相对路径只按 MCP Server 当前工作目录解析。
- 不再扫描 `help/examples`、`models`、`model` 等兜底目录。
- 如果不带 `.fs` 后缀，会自动尝试补 `.fs`。
- 同一 `userId` 同时只能加载 1 个模型。
- `loadInUI=true`：使用同一个 `SimulationModel` 创建桌面 UI tab，MCP 状态、MQTT、结果和桌面动画仍指向同一次仿真。
- `loadInUI=false`：只加载引擎对象，不创建桌面 UI，适合云端无人值守运行。
- `loadInUI` 控制是否创建 UI；`startRemoteSimulation.showAnimation` 控制本次运行是否执行动画，两者含义不同。

请求示例：

```json
{
  "jsonrpc": "2.0",
  "id": "load-1",
  "method": "tools/call",
  "params": {
    "name": "loadRemoteSimulationModel",
    "arguments": {
      "userId": "u001",
      "recordId": "backend-model-record-id",
      "modelFileName": "AGV.fs",
      "loadInUI": true
    }
  }
}
```

业务成功响应示例：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "userId": "u001",
    "recordId": "backend-model-record-id",
    "modelId": "模型ID",
    "modelFileName": "AGV.fs",
    "modelFilePath": "/data/uploaded_models/AGV.fs",
    "uiLoaded": true,
    "status": "LOADED",
    "runId": null,
    "simTime": 0,
    "message": "Model loaded.",
    "loadedAt": 1781620000000,
    "startedAt": 0,
    "finishedAt": 0
  }
}
```

### 7.2 unloadRemoteSimulationModel

作用：停止并卸载模型；如果模型加载了 UI，同时移除对应桌面 tab。

运行线程在 5 秒内无法退出时，接口返回 `SIMULATION_CONTROL_FAILED`，模型保持加载状态，可稍后重试卸载，不会无限等待。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

说明：

- 如果模型正在运行或暂停，会先 stop，再卸载。
- 会从远程管理器移除 session。
- 会从 `SimulationModel.modelMap` 中移除模型。

### 7.3 listRemoteSimulationModels

作用：列出当前 MCP Server 进程内所有已加载的远程模型。

参数：

```json
{}
```

返回：

```json
{
  "models": [
    {
      "userId": "u001",
      "recordId": "backend-model-record-id",
      "modelId": "模型ID",
      "modelFileName": "AGV.fs",
      "status": "LOADED"
    }
  ]
}
```

说明：

- 该接口不需要 `userId`，后端可以直接用它恢复当前内存中的模型列表。
- 每条模型数据都会返回 `userId`、`recordId` 和 `modelId`；后续状态查询、启动、暂停、恢复、停止、卸载等控制接口仍然使用 `userId + modelId` 定位模型。
- 当前限制仍然是单用户最多 1 个模型、全局最多加载 10 个模型，因此数组最多 10 条。

### 7.4 getRemoteSimulationStatus

作用：查询模型状态。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

常见状态：

- `LOADED`
- `RUNNING`
- `PAUSED`
- `FINISHED`
- `STOPPED`
- `FAILED`

### 7.4.1 listRemoteSimulationAnimationLayers

作用：返回根模型和全部嵌套子模型的动画层级。根层固定使用 `root`，子层使用引擎对象 ID 作为 `layerId`。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

成功响应的 `data` 包含 `activeLayerId`、`frameRate`、`frameTopic`、`frameMetaTopic` 和 `layers`。每个层包含：

```json
{
  "layerId": "root",
  "parentLayerId": null,
  "name": "demo.fs",
  "displayName": "demo.fs",
  "simpleNo": "",
  "objectName": "",
  "wholeNo": "",
  "depth": 0,
  "breadcrumb": [
    {"layerId": "root", "name": "demo.fs"}
  ],
  "width": 1280,
  "height": 720,
  "frameTopic": "factory/sim/u001/model-id/animation/root/frame"
}
```

### 7.4.2 getSvgPicture

作用：在工具调用时刻，将指定模型层的当前状态绘制为 SVG 并直接返回。该工具不会修改当前 MQTT 动画层，也不会发布 MQTT 消息。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "layerId": "root"
}
```

- `layerId` 来自 `listRemoteSimulationAnimationLayers`；传 `root` 或空字符串表示主模型。
- SVG 画面最大为 1280×720，保持模型比例，仍可由浏览器按矢量方式缩放。
- `svg` 是原始 XML 文本，位于统一响应的 `data.svg` 字段中，不是 Base64。

成功响应的 `data` 示例：

```json
{
  "userId": "u001",
  "recordId": "record-001",
  "modelId": "model-001",
  "runId": "run-001",
  "layerId": "root",
  "layerName": "demo.fs",
  "simTime": 3600000,
  "capturedAt": 1781620000200,
  "width": 1280,
  "height": 720,
  "contentType": "image/svg+xml;charset=UTF-8",
  "byteLength": 42816,
  "svg": "<svg ...>...</svg>"
}
```

指定层不存在时返回 `ANIMATION_LAYER_NOT_FOUND`，绘制失败时返回 `SVG_CAPTURE_FAILED`。

### 7.4.3 selectRemoteSimulationAnimationLayer

作用：选择服务端当前 JPEG 和结构化对象帧推送的模型层，同时设置帧率。切换层不需要在 FactorySimulation 桌面 UI 中打开子模型。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "layerId": "root",
  "frameRate": 5
}
```

- `frameRate` 范围为 1～10；传 `0` 使用默认 5 FPS。
- 每个模型同一时间只周期推送一个活动层，避免层级数量成倍放大截图和对象采样开销。
- 切换成功后会立即异步生成一帧；仿真运行期间继续按指定帧率推送。

### 7.4.4 setRemoteSimulationAnimationEnabled

作用：独立开启或关闭当前模型的引擎动画、MQTT JPEG 推帧和结构化对象帧，可在仿真运行过程中调用。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "enabled": false
}
```

- `false` 会立即停止周期截图推送，并关闭引擎组件动画计算。
- `true` 会开启引擎组件动画；模型正在运行时恢复周期推帧，未运行时生成一张当前静态帧。
- `loadInUI=true` 时会同步桌面工具栏的动画复选框。
- 成功后发布 `simulationAnimationChanged` 到 `status` topic，并更新 `animation/state` topic。

### 7.4.5 setRemoteSimulationAccelerateFactor

作用：独立调整当前模型的仿真加速因子，可在仿真运行过程中调用。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "accelerateFactor": 100
}
```

- 允许范围为 `1–1000`，桌面软件默认值为 `50`。
- 该设置属于当前内存模型实例，不影响其他并行模型，也不会自动保存到 `.fs` 文件。
- 加速因子主要影响开启动画时的播放/节奏；关闭动画时引擎本身已经采用高速计算，实际耗时差异可能不明显。
- `loadInUI=true` 时会同步桌面工具栏的速度滑块。
- 成功后发布 `simulationAccelerateFactorChanged` 到 `status` topic。

### 7.5 startRemoteSimulation

作用：启动仿真。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "showAnimation": true
}
```

说明：

- 需要浏览器动画时传 `true`；只做高速无动画计算时传 `false`。
- 如果 MCP 调用中不传 `showAnimation`，当前桥接逻辑会给 boolean 默认值 `false`。
- `showAnimation=true` 会启用组件动态绘制并启动 MQTT JPEG 推帧，不要求 `loadInUI=true`。
- `showAnimation` 继续作为本次启动的初始动画设置；启动后可用 `setRemoteSimulationAnimationEnabled` 独立修改。
- 启动成功后会生成新的 `runId`。
- 全局最多允许 5 个模型同时处于 `RUNNING`。

### 7.6 pauseRemoteSimulation

作用：暂停运行中的仿真。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

成功后状态变为：

```text
PAUSED
```

### 7.7 resumeRemoteSimulation

作用：恢复暂停中的仿真。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

成功后状态变为：

```text
RUNNING
```

### 7.8 resetRemoteSimulation

作用：重置仿真模型。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

说明：

- 当前实现要求模型不能处于 `RUNNING` 或 `PAUSED`。
- 如需重置运行中的模型，请先调用 `stopRemoteSimulation`。
- 成功后状态回到 `LOADED`。
- 成功后 `runId` 清空，`startedAt/finishedAt/simTime` 重置。

### 7.9 stopRemoteSimulation

作用：停止运行中或暂停中的仿真。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID"
}
```

成功后状态变为：

```text
STOPPED
```

### 7.10 getRemoteSimulationResultSnapshot

作用：读取第一层有界结果摘要和可分页数据集目录，不再返回可能无限增长的完整 `chartResults`。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "runId": "运行ID"
}
```

说明：

- `runId` 可以传空字符串，表示当前运行。
- `resultStatus=NOT_READY` 表示仿真尚未结束。
- `resultStatus=BUILDING` 表示仿真已经停止，但运行线程还未完全退出。
- `resultStatus=READY` 表示摘要和第二层分页数据可以读取。
- `summaryResults` 只包含体量有界的节拍、UPH、利用率、Lean 汇总和 Energy 汇总。
- 五类 `summaryResults` 均使用 `rowCount` 和 `rows` 表示记录集合；Lean/Energy 的一条 `row` 对应一个分析组件，组件内部继续保留 `summary` 等嵌套汇总。
- `componentCount` 暂时作为 Lean/Energy 的兼容计数字段保留，其值与 `rowCount` 相同；不再返回 `components` 数组。
- `datasets` 是第二层全量数据的数据集目录。
- MQTT `event=resultReady` 在仿真线程完成运行循环、结束回调生成最终快照后发布，收到该事件即可读取摘要和分页数据。

成功响应节选：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "userId": "u001",
    "runId": "运行ID",
    "recordId": "backend-model-record-id",
    "modelId": "模型ID",
    "status": "FINISHED",
    "simTime": 3600000,
    "totalOutput": 100,
    "throughputPerHour": 100.0,
    "currentWip": 0,
    "resultStatus": "READY",
    "summaryResults": {
      "taktTime": {},
      "uph": {},
      "utilRate": {},
      "leanMetrics": {
        "source": "LeanMetricsComponent",
        "componentCount": 1,
        "rowCount": 1,
        "rows": [
          {
            "componentId": "lean-component-id",
            "componentNo": "精益指标1",
            "objectType": "LeanMetrics",
            "productLines": [],
            "summary": {}
          }
        ],
        "success": true,
        "message": "success"
      },
      "energyAnalysis": {
        "source": "EnergyAnalysisComponent",
        "componentCount": 1,
        "rowCount": 1,
        "rows": [
          {
            "componentId": "energy-component-id",
            "componentNo": "能耗分析1",
            "objectType": "EnergyAnalysis",
            "summary": {}
          }
        ],
        "success": true,
        "message": "success"
      }
    },
    "datasets": [
      {"name": "partCycleTime", "type": "table"},
      {"name": "wip.timeline", "type": "timeSeries"},
      {"name": "lean.lt", "type": "table"},
      {"name": "energy.powerCurve", "type": "timeSeries"}
    ]
  }
}
```

### 7.11 getRemoteSimulationResultDatasetPage

作用：按数据集名称分页读取第二层全量结果。只允许在仿真结束并且 `resultStatus=READY` 后调用。

参数：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "runId": "运行ID",
  "dataset": "lean.lt",
  "offset": 0,
  "limit": 500
}
```

约束：

- `offset >= 0`。
- `1 <= limit <= 1000`。
- 下一页使用响应中的 `nextOffset`；`hasMore=false` 时结束。
- 分页查询不发布 MQTT 消息。

成功响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "userId": "u001",
    "recordId": "backend-model-record-id",
    "modelId": "模型ID",
    "runId": "运行ID",
    "dataset": "lean.lt",
    "offset": 0,
    "limit": 500,
    "returned": 500,
    "total": 12000,
    "hasMore": true,
    "nextOffset": 500,
    "rows": []
  }
}
```

当前数据集：

- 基础结果：`partCycleTime`、`rbData`、`taktTime`、`uph`、`utilRate`、`wip.timeline`、`wip.distribution`
- 精益结果：`lean.productLines`、`lean.lt`、`lean.fpy`、`lean.wip`、`lean.wipTimeline`、`lean.capacity`、`lean.cycleTime`、`lean.processEfficiency`、`lean.changeover`、`lean.changeoverRecords`、`lean.oee`、`lean.pplh`
- 能耗结果：`energy.mediums`、`energy.params`、`energy.rows`、`energy.mediumSummary`、`energy.powerCurve`

### 7.12 MQTT 实时消息

模型控制台输出会通过监听器发布到 MQTT：

当前 MQTT 发布状态、进度、控制台和动画 topic：

```text
factory/sim/{username}/{modelId}/status
factory/sim/{username}/{modelId}/progress
factory/sim/{username}/{modelId}/console
factory/sim/{username}/{modelId}/events
factory/sim/{username}/{modelId}/animation/layers
factory/sim/{username}/{modelId}/animation/state
factory/sim/{username}/{modelId}/animation/frameMeta
factory/sim/{username}/{modelId}/animation/{layerId}/frame
factory/sim/{username}/{modelId}/animation/{layerId}/objects
```

公共字段：

```json
{
  "username": "u001",
  "recordId": "backend-model-record-id",
  "modelId": "模型ID",
  "runId": "运行ID或null",
  "event": "simulationStart",
  "timestamp": 1781620000000,
  "msgType": "status"
}
```

说明：

- `status` 消息额外包含 `status`、`message`、`simTime`。
- `progress` 消息额外包含 `status`、`simTime`。
- `console` 消息额外包含 `level`、`content`、`sequence`。
- `events` 是可开关的 MU 事件批次，按 200～500ms 的现实时间窗口聚合发布；批次内事件不采样、不丢弃，并按 `sequence` 排序。超过 500 条时通过 `chunkIndex/chunkCount` 拆包。
- `animation/layers` 是 Retain 的 JSON 层级目录。
- `animation/state` 是 Retain 的 JSON 推帧状态。
- `animation/frameMeta` 是 QoS 0 JSON，包含 `layerId`、`sequence`、`simTime`、尺寸、字节数和对应帧 topic。
- `animation/{layerId}/frame` 是 QoS 0、非 Retain 的原始 JPEG 二进制，不能按 UTF-8 或 JSON 解析。
- `animation/{layerId}/objects` 是 QoS 0、非 Retain 的结构化 JSON 完整对象帧，与 JPEG 使用相同的 `sequence` 和 `simTime`。
- 对象帧每个 MQTT 包最多包含 500 个对象；`chunkCount>1` 时按 `sequence` 收齐全部分块后再渲染。
- 对象位置以 `containerObjectId` 为参考，`positionReference=CONTAINER_RELATIVE`。数字孪生端负责结合 SVG 或自身场景拓扑转换坐标。
- 动画默认 5 FPS、最高 10 FPS，输出尺寸保持比例且不超过 1280×720，JPEG 质量当前为 0.72。
- 仿真结果可获取时不再发布单独的 `result-ready` topic，而是在 `status` topic 中发布 `event=resultReady`。
- MQTT 实时消息携带 `recordId` 和 `modelId`；不携带 `modelFileName`、`modelFilePath`、`loopNum`、`simulationStatus`。这些属于引擎内部或排障字段，不建议前端依赖。

### 7.13 分页查询完整对象位置快照

推荐工具名：`getRemoteSimulationObjectPositionSnapshotPage`

兼容工具 `getRemoteSimulationObjectPositionSnapshot` 仍可用，但会一次返回完整数组，大模型应使用分页工具。

首次请求：

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "runId": "运行ID",
  "layerId": "root",
  "simTime": -1,
  "snapshotId": "",
  "entityKind": "ALL",
  "offset": 0,
  "limit": 500
}
```

- `simTime=-1` 立即捕获当前时刻；历史时间返回该时刻或之前最近的保留快照。
- 首次请求传空 `snapshotId`。响应会返回稳定的 `snapshotId`，后续页面必须原样回传，防止仿真继续运行时分页内容漂移。
- `entityKind` 支持 `ALL`、`STATIC_OBJECT`、`LAYER`、`MU`、`WAREHOUSE`、`STORAGE_LOCATION`。
- `layerId=root` 返回所有层；指定子层只返回该层对象。
- `offset >= 0`，`1 <= limit <= 1000`。
- 快照包含模型静态对象和设备坐标、Buffer/工位等物料流对象的 WIP、MU 所在容器与位置、仓库及货位 MU 清单。
- 顶层坐标系标记为 `PER_OBJECT_POSITION_REFERENCE`。每条对象通过 `positionReference` 说明是层内坐标、仓库内坐标、沿轨道位置或容器位置；数字孪生端需要依据 `layerId` 和 SVG/自身场景坐标完成变换。

成功响应：

```json
{
  "success": true,
  "data": {
    "recordId": "backend-model-record-id",
    "modelId": "模型ID",
    "runId": "运行ID",
    "snapshotId": "运行ID:18",
    "snapshotSimTime": 3600000,
    "layerId": "root",
    "entityKind": "ALL",
    "coordinateSystem": "PER_OBJECT_POSITION_REFERENCE",
    "offset": 0,
    "limit": 500,
    "returned": 500,
    "total": 2306,
    "hasMore": true,
    "nextOffset": 500,
    "objects": [
      {
        "entityKind": "STATIC_OBJECT",
        "objectId": "Buffer01",
        "objectType": "Buffer",
        "position": {"x": 120.0, "y": 80.0, "z": 0.0},
        "wip": 12,
        "capacity": 50,
        "muIds": ["Part001", "Part002"]
      },
      {
        "entityKind": "MU",
        "muId": "Part001",
        "containerObjectId": "Buffer01",
        "positionReference": "AT_CONTAINER",
        "position": {"x": 120.0, "y": 80.0, "z": 0.0}
      }
    ]
  }
}
```

### 7.14 查询时间窗口内的 MU 事件

工具名：`getRemoteSimulationEvents`

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "runId": "运行ID",
  "startSimTime": 3600000,
  "duration": 10000,
  "offset": 0,
  "limit": 500
}
```

- 查询区间为 `[startSimTime, startSimTime + duration)`，单位是仿真毫秒；常用窗口为 5000 或 10000。
- 返回仅与 MU 有关的事件，按 `sequence` 和 `simTime` 有序。
- 数据来自当前 run 的独立内存事件历史，不依赖模型的 `dataRecordMode`，也不要求开启 MQTT 发布。
- MCP 查询与 MQTT `/events` 使用完全相同的事件字段。
- 内存事件历史有上限；发生淘汰时返回 `historyTruncated=true`、`historyDiscardedEventCount` 和 `earliestRetainedSimTime`。

事件示例：

```json
{
  "eventId": "运行ID-10001",
  "sequence": 10001,
  "simTime": 3612000,
  "timestamp": 1781620000000,
  "eventType": "Enter",
  "eventTypeLabel": "进入",
  "objectId": "Station01",
  "objectName": "装配工位",
  "objectType": "WorkStation",
  "layerId": "root",
  "unitNo": 1,
  "currentWip": 1,
  "muId": "Part001",
  "muName": "零件001",
  "muObjectType": "Part",
  "muType": "产品A",
  "locationObjectId": "Station01",
  "locationObjectType": "WorkStation",
  "containerObjectId": "",
  "containerObjectType": "",
  "predecessorObjectId": "Buffer01",
  "predecessorObjectType": "Buffer",
  "positionReference": "AT_CONTAINER",
  "relativePosition": 0.0,
  "quantity": 0
}
```

### 7.15 开关 MQTT MU 事件发布

工具名：`setRemoteSimulationEventPublishing`

```json
{
  "userId": "u001",
  "modelId": "模型ID",
  "enabled": true,
  "publishIntervalMs": 500
}
```

- `publishIntervalMs` 允许 200～500；传 0 使用默认 500ms。
- 这是现实时间的批次刷新间隔，不是仿真时间采样间隔。窗口内发生的 MU 事件全部进入数组，不会只保留最后一条。
- 关闭后仅停止后续 MQTT 入队和发布；MCP 时间窗事件仍继续记录。
- Topic：`factory/sim/{username}/{modelId}/events`，QoS 0，非 Retain。
- Payload 公共字段包含 `recordId`、`modelId`、`runId`、`msgType=events`，并包含 `batchSequence`、`chunkIndex`、`chunkCount`、`firstSequence`、`lastSequence`、`startSimTime`、`endSimTime`、`eventCount`、`totalEventCount`、`events`。

## 8. 推荐调用顺序

```text
initialize
notifications/initialized
tools/list
loadRemoteSimulationModel
listRemoteSimulationAnimationLayers
selectRemoteSimulationAnimationLayer（需要时切换层或帧率）
setRemoteSimulationEventPublishing（需要实时 MU 事件时开启，建议 500ms）
startRemoteSimulation
getRemoteSimulationStatus
getRemoteSimulationObjectPositionSnapshotPage  (完整对象清单，固定 snapshotId 后分页)
getRemoteSimulationEvents  (按仿真时间窗口分页查询同构 MU 事件)
pauseRemoteSimulation
resumeRemoteSimulation
getRemoteSimulationStatus
stopRemoteSimulation 或等待 FINISHED
getRemoteSimulationResultSnapshot
getRemoteSimulationResultDatasetPage  (按 datasets 目录分页，可重复调用)
unloadRemoteSimulationModel
```

## 9. 后端解析伪代码

```java
JsonObject mcpResponse = postMcp(request);
String text = mcpResponse
    .getAsJsonObject("result")
    .getAsJsonArray("content")
    .get(0)
    .getAsJsonObject()
    .get("text")
    .getAsString();

JsonObject businessResult = gson.fromJson(text, JsonObject.class);
```

后端建议将 `businessResult` 原样转给前端，或转换成公司内部统一响应格式。
