# 参数管理页面开发日志
## step 1 - 获取参数表列表，临时用<pre>显示

- [x] snowy-admin-web/src/views/fscloud/simparam/index.vue 页面创建时，如果有已加载的模型，则获取参数列表，用pre标签临时显示

- 页面onMounted时，调用js api （snowy-admin-web/src/api/fscloud/fsSimApi.js）的fsSimGetModelParams函数，入参、出参为：


  > ### 入参
  >
  > | 参数名 | 类型 | 必填 | 说明 |
  > |---|---|---|---|
  > | userId | String | 是 | 用户标识 |
  > | modelId | String | 是 | 模型 ID |
  >
  > ### 出参（data 字段）
  >
  > | 字段 | 类型 | 说明 |
  > |---|---|---|
  > | modelId | String | 模型 ID |
  > | modelFileName | String | 模型文件名 |
  > | recordId | String | 模型库记录 ID |
  > | status | String | 模型状态 |
  > | mcpTableName | String | MCP 配置表名（默认 "mcp"） |
  > | mcpTableFound | Boolean | 是否找到 MCP 配置表 |
  > | parameterTables | Array | 参数表名称列表 |
  >
  > 其中的入参的modelId可在模型加载成功的结果中（snowy-admin-web/src/store/myModel.js）得到，userId这样获得：
  >
  > ```
  > const userInfo = tool.data.get('USER_INFO') || {}
  > const userId = userInfo.id || 'admin'
  > ```


  - 在snowy-plugin/snowy-plugin-fscloud/src/main/java/vip/xiaonuo/fscloud/modular/simulation/controller/SimController.java中添加对应的endpoint “getModelParams”

  - 调用snowy-plugin/snowy-plugin-fscloud/src/main/java/vip/xiaonuo/fscloud/modular/simulation/service/McpClientService.java的listParameterTables函数

  - 最终是调用MCP工具listRemoteSimulationParameterTables

  - 返回的内容，在snowy-admin-web/src/views/fscloud/simparam/index.vue 中用 pre 标签显示

---

### 实施备注（2026-07-13）

**决策变更（经用户确认）：**

- API 命名改为 `listParameterTables`（前端 `fsSimListParameterTables` / 后端 `POST /fscloud/sim/listParameterTables`）
- userId 不从前端传，后端从 `StpUtil.getLoginIdAsString()` 获取
- 添加 `@SaCheckPermission("/fscloud/sim/listParameterTables")` 权限注解
- 无模型时显示空状态提示，不静默空白

**新增文件：**

- `GetModelParamsParam.java` — 入参（仅 modelId）
- `GetModelParamsDto.java` — 出参（modelId, modelFileName, recordId, status, mcpTableName, mcpTableFound, parameterTables）

**修改文件：**
- `SimController.java` — 新增 `listParameterTables` endpoint
- `fsSimApi.js` — 新增 `fsSimListParameterTables` 函数
- `simparam/index.vue` — 实现完整页面逻辑（加载、显示、空状态、错误处理）

**未涉及：**
- 数据库/菜单/种子数据（"参数管理"菜单已存在）
- 路由配置（已有菜单驱动路由）
- McpClientService（`listParameterTables` 方法已存在） 

### 前端出入参举例

- 入参

  "modelId": "b65f4ff31559"

- 出参

```
{
    "modelId": "b65f4ff31559", 
    "modelFileName": "ProductionPlan-mcp.fs", 
    "recordId": "2076513799918522369", 
    "status": "FINISHED", 
    "mcpTableName": "MCP", 
    "mcpTableFound": true, 
    "parameterTables": [
        "{\"rowIndex\":0,\"tableName\":\".订单表\",\"remark\":\"\",\"exists\":true,\"tableId\":\"72ed87c4e9d6\",\"wholeNo\":\".订单表\",\"rowCount\":30,\"columnCount\":7}", 
        "{\"rowIndex\":1,\"tableName\":\".ProcessTable\",\"remark\":\"\",\"exists\":true,\"tableId\":\"f8962b14-5611-4be7-8d2e-639649982376\",\"wholeNo\":\".ProcessTable\",\"rowCount\":30,\"columnCount\":31}"
    ]
}
```

### 改进
- [x] 当myModel.modelId为空时，显示空状态提示。
- [x] 当myModel.modelId与页面显示的modelId不一致时，自动刷新页面，重新获取参数列表。
  - 实现方式：用 `watch(() => myModel.modelId, ..., { immediate: true })` 替代 `onMounted`
  - 记录 `lastLoadedModelId` 避免重复请求
  - modelId 置空时自动清空数据显示

## step 2 - 获取参数快照，临时用<pre>显示
- [x] 和 step 1 类似，这次是调用MCP 的 getRemoteSimulationParameterSnapshot 工具
- [x] 这个工具的入参是 userId, modelId, runId。 此时的runId赋值为""
- [x] 也使用 pre 标签显示返回的内容

  > ### 实施备注（2026-07-13）
  >
  > **新增文件：**
  > - `GetParameterSnapshotParam.java` — 入参（modelId + 可选 runId）
  > - `GetParameterSnapshotDto.java` — 出参（recordId, modelId, modelFileName, status, snapshotAt, mcpTableName, mcpTableFound, mcpRows, tables, missingTables）
  >
  > **修改文件：**
  > - `SimController.java` — 新增 `getParameterSnapshot` endpoint，带 `@SaCheckPermission`
  > - `fsSimApi.js` — 新增 `fsSimGetParameterSnapshot` 函数
  > - `simparam/index.vue` — 新增第二张卡片"参数快照"，与"参数表列表"垂直排列
  >
  > **决策说明：**
  > - runId 设计为入参中可选字段（经确认），为空时后端传 ""
  > - 两张卡片共用 `watch(myModel.modelId)` 自动刷新
  > - 各自独立 loading/error 状态

## step 3 - 参数快照的显示和修改
- [x] 在myModel中保存参数快照的内容, 以便后序显示、修改
- [x] 保存的方案，要方便选择表名分别显示和修改，所以要把参数快照的内容解析为一个对象，key是表名，value是表的内容（二维数组或对象数组）
- [x] 保存一个为原始内容，另一个为可修改的内容，方便以后用户修改、恢复、落库等
- [x] 以这些数据为基础，做一个表格显示和修改的组件，支持增删行、增删列、修改单元格内容
- [x] step1 和 2 的pre内容 放到 表格组件 之后，等表格组件完成后再移除

  > ### 实施备注（2026-07-13）
  >
  > **新增文件：**
  > - `ParamTableEditor.vue` — 通用表格编辑组件，支持编辑模式切换、单元格编辑、增删行/列、保存/取消
  >
  > **修改文件：**
  > - `myModel.js` store — 新增 `parameterTables`, `snapshotRaw`, `snapshotData`, `snapshotBackup`, `selectedTableName` 状态字段，以及 `clearSnapshot`, `parseAndSetSnapshot` action
  > - `simparam/index.vue` — 集成 store + ParamTableEditor 组件；下拉选择表名；快照加载后自动解析存入 store；保存写回 store；pre 原始数据折叠保留
  >
  > **核心设计：**
  > - 二维数组格式，首行=列头
  > - 全局编辑开关 🔒/🔓，默认锁定
  > - 解锁后单击单元格进入编辑（类 Excel）
  > - dirty 追踪：数据变化后 💾保存/↩取消 按钮 enable
  > - 保存 → 更新 `snapshotData` + `snapshotBackup`
  > - 取消 → 从 `snapshotBackup` 恢复

## step 4 - 参数快照数据落库，可有多版本的参数快照，可删除某版本
### 数据库表设计
- [x] 新增表 fs_sim_param_snapshot，字段如下：
  - id: 主键，自增、bigint
  - model_file_id: 模型库记录ID
  - snapshot_at: 快照时间，时间戳
  - parameter_tables: JSON, 参数表列表
  - mcp_rows: JSON, MCP表中的参数表信息
  - tables: JSON，存储各参数表的内容（二维数组或对象数组）
  - missing_tables: JSON，存储缺失的参数表列表
  - created_at: datetime, 创建时间
  - updated_at: datetime, 更新时间
- [x] 生成DDL, 保存在docs/fscloud/fs_sim_param_snapshot_mysql.sql
### 保存参数快照
  - [x] 在snowy-admin-web/src/views/fscloud/simparam/index.vue 中“从模型重新读取参数”按钮的右边加一个“保存快照”按钮。
  - [x] 点击后调用js api接口 fsSimSaveParameterSnapshot ，该接口再调用后端接口saveParameterSnapshot保存快照到数据库表 fs_sim_param_snapshot 中。
### 快照列表、快照查看、快照删除
  - [x] 新增快照列表页面，显示所有保存的参数快照
  - [x] 新增快照查看功能，允许用户查看特定快照的详细信息

## step 5 - 参数回写到模型
### UI修改
- [x] snowy-admin-web/src/views/fscloud/simparam/index.vue 新增一个按钮"快照写模型"
- [x] 点击后调用saveParamSnapshotToModel，先准备数据（见下文说明）, 后调用js api接口 fsSaveParamSnapshotToModel ，该接口再调用后端的api，后端最终调用MCP工具batchUpdateRemoteSimulationParameterTable 
### 准备数据 & MCP工具调用
- [x] 组织 myModel.snapshotData 中的参数表数据作为入参，以便调用 MCP 工具 batchUpdateRemoteSimulationParameterTable 写入模型

  > ### 实施备注（2026-07-22）
  >
  > **新增文件：**
  > - `SaveParamSnapshotToModelParam.java` — 入参（modelId, tablesJson, bSaveModel）
  > - `SaveParamSnapshotToModelDto.java` — 出参（7 个字段匹配 MCP 返回）
  >
  > **修改文件：**
  > - `McpClientService.java` — 新增 `batchUpdateParameterTables` 方法
  > - `SimController.java` — 新增 `saveParamSnapshotToModel` endpoint，带 `@SaCheckPermission`
  > - `fsSimApi.js` — 新增 `fsSaveParamSnapshotToModel` 函数
  > - `simparam/index.vue` — 实现 `saveSnapshotToModel`：确认对话框 → 前端组装 tablesJson → 调用 API → 成功后自动刷新快照
  >
  > **决策说明：**
  > - bSaveModel 默认 true（保存到 .fs 文件）
  > - 前端组装 tablesJson（从 snapshotData 转 MCP 格式）
  > - 写入成功后自动调用 loadSnapshot() 刷新参数
  > - 数据库影响：无
 
# 动画播放功能开发日志
## step 6 - 在simparam/index.vue中新增动画播放a-card
- [x] class="animation-card"的a-card，将用于显示动画
- [x] 仿真加载后，自动显示封面 方法和snowy-admin-web/src/views/fscloud/modelfile/index.vue的模型预览一致
## step 7 - 动画播放功能的实现
- [x] 参照"snowy-admin-web/src/views/fscloud/docs/fs-mcp-ref/05_MQTT浏览器动画接口说明.md"的文档说明进行实现
- [x] 订阅主题改为`factory/sim/${userId}/${modelId}/*`，兼顾之前的主题，也支持动画播放相关的主题
- [x] 其余的处理逻辑参照"snowy-admin-web/src/views/fscloud/docs/fs-mcp-ref/05_MQTT浏览器动画接口说明.md"实现
## step 8 - 动画开/关功能
- [x] 按 snowy-admin-web/src/views/fscloud/docs/fs-mcp-ref/01_MCP接口说明与调用示例.md 7.4.3 setRemoteSimulationAnimationEnabled 所述，添加开关动画的接口。
- [x] 当snowy-admin-web/src/views/fscloud/simcontrol/index.vue的‘开动画’ checkbox 有变化时，就调用 这个接口。
## step 9 - 调节fs仿真加速因子
- [x] 按 snowy-admin-web/src/views/fscloud/docs/fs-mcp-ref/01_MCP接口说明与调用示例.md 7.4.4 setRemoteSimulationAccelerateFactor 所述，添加加速因子调节接口。
- [x] 前端在 snowy-admin-web/src/views/fscloud/simcontrol/index.vue 的 ‘关闭’ 按钮的右边，加一个 <a-slide>组件进行控制，变化1-1000, 默认50。当用户滑动此组件时，调用相应的接口进行控制
## step 10 - 部分状态的本地保存与恢复
目的是解决仿真控制页面刷新后，部分状态丢失，显示的初值与仿真实际的状态不一致。主要是1）动画的开/关状态，2）仿真加速因子，3）当前的仿真动画在哪一层等。
- [x] 当用户操作了以上状态，就立即保存到本地存储，保存需要带userid和modelid
- [x] 页面刷新时，从本地存储中恢复以上状态，恢复时需要根据userid和modelid和正在仿真的模型的这两个属性进行匹配。

# 获取结果快照并显示、落库
## step 11 - 从结束或停止的仿真模型中获取结果快照
- [x] snowy-admin-web/src/views/fscloud/simdata/index.vue 新增一个按钮"获取仿真结果"
- [x] 点击时，调用js api接口 fsSimGetResultSnapshot ，该接口再调用后端的api，后端最终调用MCP工具getRemoteSimulationResultSnapshot
- [x] 返回的数据，保存在myModel.resultSnapshot 中
- [x] 根据 “snowy-admin-web/src/views/fscloud/docs/fs-mcp-ref/result_snapshot_log.json”中的结果数据，设计页面，先在页面上部分显示这些内容, 使用a-card组件:
  - modelFileName - 模型文件名
  - status - 仿真状态
  - simTime - 仿真时长
  - startedAt - 仿真开始时间
  - finishedAt - 仿真结束时间
  - totalOutput - 总产量
  - throughputPerHour - 产量/小时
  - currentWip - 当前WIP
  - parameterSnapshotAt - 参数快照时间
  - resultStatus - 结果状态
  - resultSnapshotAt - 结果快照时间
- [x] 然后设计 a-tab 组件，第一维包括：
  - 结果总览，总览第二维包括：`taktTime`、`uph`、`utilRate`、`leanMetrics`、`energyAnalysis`
  - 基础结果，其二维包括：`partCycleTime`、`rbData`、`taktTime`、`uph`、`utilRate`、`wip.timeline`、`wip.distribution`
  - 精益结果，其二维包括：`lean.productLines`、`lean.lt`、`lean.fpy`、`lean.wip`、`lean.wipTimeline`、`lean.capacity`、`lean.cycleTime`、`lean.processEfficiency`、`lean.changeover`、`lean.changeoverRecords`、`lean.oee`、`lean.pplh`
  - 能耗结果，其二维包括：`energy.mediums`、`energy.params`、`energy.rows`、`energy.mediumSummary`、`energy.powerCurve`

  > ### 实施备注（2026-07-22）
  >
  > **新增文件：**
  > - `param/GetResultSnapshotParam.java` — 入参（modelId + 可选 runId）
  > - `dto/GetResultSnapshotDto.java` — 通用 DTO（顶层字段 typed，嵌套用 Map/List）
  > - `simdata/ResultTable.vue` — 通用表格组件（a-table 封装，支持分页）
  >
  > **修改文件：**
  > - `SimController.java` — 新增 `getResultSnapshot` endpoint，带 `@SaCheckPermission`
  > - `fsSimApi.js` — 新增 `fsSimGetResultSnapshot` 函数
  > - `myModel.js` store — 新增 `resultSnapshot` 字段
  > - `simdata/index.vue` — 完整页面：获取按钮 + 概览 card + 双层 tab（结果总览/基础/精益/能耗）+ ResultTable 组件
  >
  > **决策说明：**
  > - runId 优先从 myModel.runId 取，为空时传 ""
  > - DTO 采用通用策略（通用 DTO）
  > - 页面通过已有菜单项访问
  > - 结果总览引用 summaryResults.taktTime/uph/utilRate 的内联数据
  > - 基础/精益/能耗结果显示占位信息（待 step 12 实现分页）
  > - 重复字段 currentWip 移除
  > - 数据库影响：无
- [x] 点击结果总览的第二维数据时，从myModel.resultSnapshot中取对应的数据显示。点击其它时只显示占位信息“under construction”
## step 12 - 分页显示基础结果、精益结果、能耗结果的详细数据
- [x] 点击基础结果、精益结果、能耗结果的第二维数据时，从调用getRemoteSimulationResultDatasetPage接口获取数据, 并显示在表格中，分页25行为一页。

  > ### 实施备注（2026-07-22）
  >
  > **新增文件：**
  > - `param/GetResultDatasetPageParam.java` — 入参（modelId, runId, dataset, limit, offset）
  >
  > **修改文件：**
  > - `McpClientService.java` — 新增 `getResultDatasetPage` 方法
  > - `SimController.java` — 新增 `getResultDatasetPage` endpoint
  > - `fsSimApi.js` — 新增 `fsSimGetResultDatasetPage` 函数
  > - `ResultTable.vue` — 支持外部分页（props: total/pageSize/current/loading, emit: pageChange）
  > - `simdata/index.vue` — 替换占位信息为动态数据集加载 + 分页
  >
  > **设计说明：**
  > - 三个 tab 组（基础/精益/能耗）各自追踪当前选中的 dataset
  > - 切换 dataset tab 时自动调用 API 加载第一页（25条）
  > - 分页切换时重新调用 API（服务器端分页）
  > - 列从返回数据首行动态推断
  > - 后端当前返回数组不含 total 字段，前端用 `res.length < pageSize` 估算
  > - 数据库影响：无

## step 13 - 浏览器动画放大时可选择看高清图片
- [x] 在 snowy-admin-web/src/views/fscloud/simcontrol/index.vue 中“直播?fps”信息之后加一个 checkbox 组件，label为“显示高清静态图”
- [x] myModel.animationStreaming为true时才显示这个 checkbox 组件
- [x] 点击 checkbox 组件且为checked时，调用 "snowy-admin-web/src/views/fscloud/docs/fs-mcp-ref/01_MCP接口说明与调用示例.md" 中所述的 "getSvgPicture" 接口，取高清图片（svg格式）
- [x] 返回的图片替代目前的jpeg图片流显示
- [x] 点击 checkbox 组件且为unchecked时，显示目前的jpeg图片流
- [x] 注意：svg图片也需要可缩放、移动，操作与目前的jpeg图片流一致

## step 14 - 结果快照数据的单位和中文名称映射表
- [x] 需要在独立文件（TableTitleKeyMap.js）中定义一个结果快照数据的单位和中文名称映射表，给
snowy-admin-web/src/views/fscloud/simdata/ResultTable.vue 使用，可以根据 groupKey、datesetKey、column.key 查询这些
信息： 单位（件/毫秒秒/%...）、title（标题）、数据类型（s/t/i/f...）、小数位数。
其中的columns将统一从数据rows[0].key（第一行的k-v对的key）

需要根据下列信息和数据设计这个映射表程序(TableTitleKeyMap.js):
### overview/taktTime
- 两级tab名称：结果总览-节拍
- 目前columns由父组件（simdata/index.vue）传值(taktTimeColumns)给子组件(simdata/ResultTable.vue)
- 将来改成从数据中取key
- 数据示例（rows[0]）:
```json
  {
    "objectName": "预装(Device)",
    "objectType": "Device",
    "unit": "0",
    "firstProcessTime": 1800300,
    "lastProcessTime": 21546339,
    "effectiveTime": 17946039,
    "processedCount": 400,
    "taktTime": 44865.0975,
    "uph": 80.24054778884633
  }
```

### overview/uph
- 两级tab名称：结果总览-产量/小时
- 目前columns由父组件（simdata/index.vue）传值(uphColumns)给子组件(simdata/ResultTable.vue)
- 将来改成从数据中取key
- 数据示例（rows[0]）:
```json
  {
    "objectName": "预装(Device)",
    "objectType": "Device",
    "unit": "0",
    "startTime": 1800300,
    "endTime": 21546339,
    "processedCount": 400,
    "uph": 72.92601822573124
  }
```
### overview/utilRate
- 两级tab名称：结果总览-设备利用率
- 目前columns由父组件（simdata/index.vue）传值(utilRateColumns)给子组件(simdata/ResultTable.vue)
- 将来改成从数据中取key
- 数据示例（rows[0]）:
```json
  {
    "objectName": "预装(Device)",
    "objectType": "Device",
    "unit": 0,
    "actualSimTime": 127800000,
    "waitingTime": 39653661,
    "waitingRate": 0.31027903755868547,
    "workingTime": 17943939,
    "workingRate": 0.14040640845070423,
    "settingUpTime": 2400,
    "settingUpRate": 0.000018779342723004694,
    "blockedTime": 0,
    "blockedRate": 0,
    "failedTime": 0,
    "failedRate": 0,
    "unplannedTime": 70200000,
    "unplannedRate": 0.5492957746478874
  }
```

### overview/leanMetrics
- 两级tab名称：结果总览-精益指标
- 目前没有使用simdata/ResultTable.vue子组件
- 需要结合以下数据特点重新设计子组件 simdata/LeanMetricsTable.vue
- 以下数据在 snowy-admin-web/src/views/fscloud/simdata/index.vue 中的语句 “<pre> {{result.summaryResults.leanMetrics || null}}</pre>”得到
```json
 {
	"source": "LeanMetricsComponent",
	"componentCount": 1,
	"rowCount": 1,
	"rows": [
		{
			"componentId": "53cefa330c53",
			"componentNo": "精益指标1",
			"objectType": "Table",
			"productLines": [
				{
					"lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
					"entranceName": "订单入口(Entrance)",
					"exitName": "合格品出口(Exit)",
					"availableWorkHours": "36h",
					"customerDemand": "400",
					"workerPoolNames": "",
					"reworkExitName": "合格品出口(Exit)",
					"scrapExitName": "报废出口(Exit)"
				}
			],
			"summary": {
				"lineCount": 1,
				"qualityConfiguredLineCount": 1,
				"ltCount": 398,
				"avgLTMs": 57818270,
				"maxLTMs": 115903664,
				"minLTMs": 2383891,
				"totalInput": 798,
				"firstPassQualifiedCount": 398,
				"reworkQualifiedCount": 398,
				"scrapCount": 2,
				"factoryFPY": 49.87468671679198,
				"factoryScrapRate": 0.2506265664160401,
				"qualifiedOutput": 398,
				"currentWIP": 2,
				"peakWIP": 400,
				"avgProductionCycleMs": 51424684,
				"actualUPH": 11.055555555555555,
				"theoreticalUPH": 30.419279063086204,
				"availabilityRate": 98.08958333333334,
				"performanceRate": 27.8586361673357,
				"qualityRate": 99.74937343358395,
				"oee": 27.25793287037037,
				"workerCount": 0,
				"busyLaborMs": 0,
				"pplh": 0,
				"avgProcessPTMs": 345157,
				"avgBalanceRate": 48.60845881285947,
				"avgWaitingMs": 11476088,
				"avgValueAddedRatio": 0.5969687436168533,
				"avgChangeoverMs": 0
			}
		}
	],
	"success": true,
	"message": "success"
} 
```
### overview/energyAnalysis
- 两级tab名称：结果总览-能耗分析
- 目前没有使用simdata/ResultTable.vue子组件
- 需要结合以下数据特点重新设计子组件 simdata/EnergyAnalysis.vue
- 以下数据在 snowy-admin-web/src/views/fscloud/simdata/index.vue 中的语句 “<pre> {{result.summaryResults.energyAnalysis || null}}</pre>”得到
```json
 {
	"source": "EnergyAnalysisComponent",
	"componentCount": 1,
	"rowCount": 1,
	"rows": [
		{
			"componentId": "14debc960f36",
			"componentNo": "能耗1",
			"objectType": "Table",
			"summary": {
				"mediumCount": 1,
				"deviceCount": 7,
				"rowCount": 7,
				"totalEnergy": 170.4212528611111,
				"totalByMedium": {
					"1": 170.4212528611111
				},
				"totalByDevice": {
					"预装(Device)": 7.187608666666667,
					"压装(Device)": 110.64132277777777,
					"锁付1(Device)": 6.276352166666667,
					"电检(Device)": 33.287564861111115,
					"包装(Device)": 6.758941999999999,
					"返修工位(Device)": 0,
					"锁付2(Device)": 6.269462388888888
				}
			}
		}
	],
	"success": true,
	"message": "success"
}
```

### basic/partCycleTime
- 两级tab名称：基础结果-循环时间
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "muNo": "1000300",
    "muType": "PartB",
    "name": "汽车门锁总成B",
    "productionStartTime": 120000,
    "createTime": 120000,
    "createPosition": ".订单入口",
    "productionEndTime": 100560197,
    "finishPosition": ".合格品出口",
    "cycleTimeFromProductionStart": 100440197,
    "cycleTimeFromCreate": 100440197,
    "workingTime": 327059,
    "waitingTime": 98433138,
    "transportTime": 0
  }
```
### basic/rbData
- 两级tab名称：基础结果-缓冲区
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json

```
### basic/taktTime
- 两级tab名称：基础结果-节拍
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "objectName": "预装(Device)",
    "objectType": "Device",
    "unit": "0",
    "firstProcessTime": 1800300,
    "lastProcessTime": 21546339,
    "effectiveTime": 17946039,
    "processedCount": 400,
    "taktTime": 44865.0975,
    "uph": 80.24054778884633
  }
```
### basic/uph
- 两级tab名称：基础结果-UPH
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "objectName": "预装(Device)",
    "objectType": "Device",
    "unit": "0",
    "startTime": 1800300,
    "endTime": 21546339,
    "processedCount": 400,
    "uph": 72.92601822573124
  }
```

### basic/utilRate
- 两级tab名称：基础结果-利用率
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
	"objectName": "预装(Device)",
	"objectType": "Device",
	"unit": 0,
	"actualSimTime": 127800000,
	"waitingTime": 39653661,
	"waitingRate": 0.31027903755868547,
	"workingTime": 17943939,
	"workingRate": 0.14040640845070423,
	"settingUpTime": 2400,
	"settingUpRate": 0.000018779342723004694,
	"blockedTime": 0,
	"blockedRate": 0,
	"failedTime": 0,
	"failedRate": 0,
	"unplannedTime": 70200000,
	"unplannedRate": 0.5492957746478874
  }
```
### basic/wip.timeline
- 两级tab名称：基础结果-WIP趋势
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
```
### basic/wip.distribution
- 两级tab名称：基础结果-WIP分布
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
```
### lean/lean.productLines
- 两级tab名称：精益结果-产线
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "entranceName": "订单入口(Entrance)",
    "exitName": "合格品出口(Exit)",
    "availableWorkHours": "36h",
    "customerDemand": "400",
    "workerPoolNames": "",
    "reworkExitName": "合格品出口(Exit)",
    "scrapExitName": "报废出口(Exit)"
  }
```

### lean/lean.lt
- 两级tab名称：精益结果-LT
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "entranceName": "订单入口(Entrance)",
    "exitName": "合格品出口(Exit)",
    "muNo": "1000001",
    "muType": "PartA",
    "createTime": 0,
    "endTime": 2383891,
    "ltMs": 2383891
  }
```

### lean/lean.fpy
- 两级tab名称：精益结果-FPY
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "reworkExitName": "合格品出口(Exit)",
    "scrapExitName": "报废出口(Exit)",
    "hasQualityExitConfig": true,
    "totalInput": 102,
    "qualifiedCount": 51,
    "reworkQualifiedCount": 51,
    "scrapCount": 0,
    "fpy": 50,
    "scrapRate": 0
  }
```

### lean/lean.wip
- 两级tab名称：精益结果-WIP
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "entranceName": "订单入口(Entrance)",
    "exitName": "合格品出口(Exit)",
    "currentWIP": 349,
    "maxWIP": 400,
    "avgWIP": 377.69953061564195
  }
```

### lean/lean.wipTimeline
- 两级tab名称：精益结果-WIP时序
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "time": 0,
    "wip": 100
  }
```

### lean/lean.capacity
- 两级tab名称：精益结果-产能
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "availableWorkHours": 0,
    "customerDemand": 400,
    "hasTT": false,
    "ttMs": 0,
    "qualifiedOutput": 51,
    "hasActualUPH": true,
    "actualUPH": 21.67479077806124,
    "bottleneckProcess": "压装(Device)",
    "bottleneckCTMs": 118677,
    "hasTheoreticalUPH": true,
    "theoreticalUPH": 30.334437169797013,
    "hasSimulation": true
  }
```
### lean/lean.cycleTime
- 两级tab名称：精益结果-工序CT
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "completionCount": 51,
    "hasSimulation": true,
    "hasCycleTime": true,
    "avgCTMs": 119278
  }
```

### lean/lean.processEfficiency
- 两级tab名称：精益结果-工艺效率
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "hasSimulation": true,
    "hasProcessData": true,
    "hasWaitingTime": true,
    "hasValueAddedRatio": true,
    "bottleneckProcess": "压装(Device)",
    "bottleneckPTMs": 118677,
    "totalAvgPTMs": 346677,
    "balanceRate": 48.68635034589685,
    "avgWaitingMs": 586307,
    "valueAddedRatio": 6.334739847298338,
    "steps": [
      {
        "processName": "预装(Device)",
        "sampleCount": 148,
        "avgPTMs": 44912
      },
      {
        "processName": "压装(Device)",
        "sampleCount": 53,
        "avgPTMs": 118677
      },
      {
        "processName": "锁付1(Device)",
        "sampleCount": 26,
        "avgPTMs": 36402
      },
      {
        "processName": "电检(Device)",
        "sampleCount": 53,
        "avgPTMs": 71221
      },
      {
        "processName": "包装(Device)",
        "sampleCount": 51,
        "avgPTMs": 40354
      },
      {
        "processName": "锁付2(Device)",
        "sampleCount": 26,
        "avgPTMs": 35111
      }
    ]
  }
```

### lean/lean.changeover
- 两级tab名称：精益结果-换型
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "hasSimulation": true,
    "switchCount": 0,
    "validCount": 0,
    "invalidCount": 0,
    "avgChangeoverMs": 0,
    "maxChangeoverMs": 0,
    "maxFromType": "",
    "maxToType": ""
  }
```
### lean/lean.changeoverRecords
- 两级tab名称：精益结果-换型记录
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
```
### lean/lean.oee
- 两级tab名称：精益结果-OEE
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "hasSimulation": true,
    "equipmentCount": 6,
    "equipmentNames": [
      "预装(Device)",
      "压装(Device)",
      "锁付1(Device)",
      "电检(Device)",
      "包装(Device)",
      "锁付2(Device)"
    ],
    "plannedProductionMs": 40024020,
    "runMs": 39723120,
    "totalOutput": 102,
    "qualifiedOutput": 102,
    "theoreticalMCTMs": 118677,
    "hasEquipment": true,
    "hasStatusTime": true,
    "hasRunTime": true,
    "hasTheoreticalMCT": true,
    "hasQualityData": true,
    "availabilityRate": 99.24820145502626,
    "performanceRate": 30.47357307280999,
    "qualityRate": 100,
    "oee": 30.244473193847096
  }
```
### lean/lean.pplh
- 两级tab名称：精益结果-PPLH
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "53cefa330c53",
    "componentNo": "精益指标1",
    "objectType": "Table",
    "lineName": "汽车门锁执行器总成 A/B 两型号混流装配线",
    "exitName": "合格品出口(Exit)",
    "workerPoolNames": "",
    "qualifiedOutput": 51,
    "workerCount": 0,
    "busyLaborMs": 0,
    "hasSimulation": true,
    "hasWorkerPoolConfig": false,
    "hasLaborHours": false,
    "pplh": 0,
    "missingWorkerPools": []
  }
```
### energy/energy.mediums
- 两级tab名称：能耗结果-介质
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "14debc960f36",
    "componentNo": "能耗1",
    "objectType": "Table",
    "mediumId": "1",
    "mediumName": "电力",
    "unit": "kWh"
  }
```
### energy/energy.params
- 两级tab名称：能耗结果-参数
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "14debc960f36",
    "componentNo": "能耗1",
    "objectType": "Table",
    "deviceName": "包装",
    "mediumId": "1",
    "statusPowers": [
      {
        "status": "WORKING",
        "statusLabel": "加工中",
        "power": 1
      },
      {
        "status": "WAITING",
        "statusLabel": "空闲/等待",
        "power": 0.2
      },
      {
        "status": "BLOCKED",
        "statusLabel": "阻塞",
        "power": 0.2
      },
      {
        "status": "SETTINGUP",
        "statusLabel": "换型/准备",
        "power": 0.3
      },
      {
        "status": "FAILED",
        "statusLabel": "故障停机",
        "power": 0.1
      }
    ]
  }
```
### energy/energy.rows
- 两级tab名称：能耗结果-明细
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "14debc960f36",
    "componentNo": "能耗1",
    "objectType": "Table",
    "key": "预装(Device)::1",
    "deviceName": "预装(Device)",
    "mediumId": "1",
    "mediumName": "电力",
    "unit": "kWh",
    "totalEnergy": 1.852788888888889,
    "statusDetails": [
      {
        "status": "WORKING",
        "statusLabel": "加工中",
        "statusTimeMs": 6669770,
        "percentage": 78.73958022210758,
        "power": 1,
        "energy": 1.852713888888889
      },
      {
        "status": "WAITING",
        "statusLabel": "空闲/等待",
        "statusTimeMs": 0,
        "percentage": 0,
        "power": 0.2,
        "energy": 0
      },
      {
        "status": "BLOCKED",
        "statusLabel": "阻塞",
        "statusTimeMs": 0,
        "percentage": 0,
        "power": 0.2,
        "energy": 0
      },
      {
        "status": "SETTINGUP",
        "statusLabel": "换型/准备",
        "statusTimeMs": 900,
        "percentage": 0.010624897440226098,
        "power": 0.3,
        "energy": 0.000075
      },
      {
        "status": "FAILED",
        "statusLabel": "故障停机",
        "statusTimeMs": 0,
        "percentage": 0,
        "power": 0.1,
        "energy": 0
      }
    ]
  }
```

### energy/energy.mediumSummary
- 两级tab名称：能耗结果-介质汇总
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "14debc960f36",
    "componentNo": "能耗1",
    "objectType": "Table",
    "mediumId": "1",
    "mediumName": "电力",
    "unit": "kWh",
    "mediumTotalEnergy": 22.660736347222223,
    "deviceName": "预装(Device)",
    "deviceEnergy": 1.852788888888889,
    "percentage": 8.17620778292143
  }
```
### energy/energy.powerCurve
- 两级tab名称：能耗结果-功率曲线
- 目前columns没有从父组件（simdata/index.vue）传值，子组件simdata/ResultTable.vue自己从rows传值的数据中取rows[0]的key
- 数据示例（rows[0]）:
```json
  {
    "componentId": "14debc960f36",
    "componentNo": "能耗1",
    "objectType": "Table",
    "scopeName": "Line",
    "mediumId": "1",
    "mediumName": "电力",
    "unit": "kWh",
    "hasEvents": true,
    "simTimeMs": 8470670,
    "peakPower": 14.85,
    "reconstructedEnergy": 22.6607363472222,
    "timeMs": 0,
    "power": 0
  }
```
## step 15 - 根据映射表TableTitleKeyMap.js处理title和数据
- [x] 手动调整影射表的内容
  - 信息： 单位（件/毫秒秒/%...）、title（标题）、数据类型（s/t/i/f...）、小数位数。
- [x] 在 simdata/ResultTable.vue中根据影射表的定义，用适合的标题（之前使用key作为标题）、必要时标题附上单位（例如ms、%）、对t的数据转换为人眼友好的格式/对f的数据通过四舍五入进行合适的小数位数显示。

  > ### 实施备注（2026-07-29）
  >
  > **新增文件：**
  > - `simdata/TableTitleKeyMap.js` — 完整映射表，覆盖 overview/basic/lean/energy 四个 group 下所有 dataset 的字段映射（25259 bytes，约 200+ 字段）
  > - `simdata/LeanMetricsTable.vue` — 精益摘要展示组件（descriptions 展示 OEE/LT/FPY/WIP/平衡率等关键指标）
  > - `simdata/EnergyAnalysis.vue` — 能耗分析展示组件（介质汇总 descriptions + 按设备能耗表格）
  >
  > **修改文件：**
  > - `simdata/ResultTable.vue` — 接入映射表：导入 TableTitleKeyMap，根据 groupKey/datasetKey 查字段元数据；动态列标题（含单位）；按 type 格式化（f→toFixed, i→round, t→hh:mm:ss, s→原样）；steps 嵌套数组转为 tooltip 字符串
  > - `simdata/index.vue` — 移除硬编码 taktTimeColumns/uphColumns/utilRateColumns；overview tab 传 groupKey/datasetKey 替代 columns；leanMetrics/energyAnalysis 改用 LeanMetricsTable/EnergyAnalysis 组件
  >
  > **数据库影响：** 无

## step 16 - 结果快照数据落库
主要需求：
- 每次仿真结束（包括自然结束、手动强制结束），都有对应的参数和结果快照
- 参数快照和结果快照，可选择落库（即保存到数据库），或不落库
- 参数快照可单独落库，已在 snowy-admin-web/src/views/fscloud/simparam/index.vue 中实现
- 但结果快照落库时，必须将对应的参数快照也落库，也即成对落库，两者用 runId 进行关联，又分别通过 recordId 与模型库中模型记录的id进行关联
- 单独落库的参数快照，以及成对落库的参数快照，到保存在数据库表`fs_sim_param_snapshot`中，其中runId为null表示单独落库的参数快照，runId不为null表示该结录是与结果快照成对落库的参数快照
- 需要设计如“## step 14 - 结果快照数据的单位和中文名称映射表”中各类示例数据的存储方式，并在 snowy-admin-web/src/views/fscloud/simdata/index.vue 中实现结果快照落库的功能。
- 结果快照的信息保存在数据库表`fs_sim_result_snapshot`中，recordId为模型库中模型记录的id，runId为仿真运行的唯一标识, 其它的信息包括
  - 模型文件名，例如“YH-F01-F06-F08-Base-v1-260731.fs”
  - 仿真时长，例如“3,588,979,903”，单位为毫秒
  - 仿真开始时间，例如“2026-08-05 10:47:31”
  - 仿真结束时间，例如“2026-08-05 10:50:00”
  - 运行Id, 例如“279a17ad-49ba-4ea5-8132-cd3d4fa2947f”
  - 其它使用JSON格式保存的数据，参照“## step 14 - 结果快照数据的单位和中文名称映射表”中各类示例数据的存储方式，其中一个例外是“basic/partCycleTime”,因为这个数据可能会非常大，所以不保存在数据库中，而是选择更适合的方式保存，例如保存为csv、json这类文件，文件名在数据库表`fs_sim_result_snapshot`对应的字段，以便做关联。
- 结果快照的落库是为后续的分析对比做准备，需要考虑：
  - 快照列表显示
  - 选中某个快照时能按页检索和显示该快结果快照的数据，显示方式有表格、图表等
  - 选中某两个（或更多）快照时，能找出它们的差异，按因、果方式进行对比，因就是参数快照，果就是结果快照，显示方式有表格、图表等。
  - 因和果分别对应的数据很多时，可人工进行筛选，只显示关心的参数和结果数据，或只显示关心的产线、工序、设备等。
- [x] 根据以上需求，设计数据库表fs_sim_result_snapshot的 DDL
- [x] 确定用什么方式保存“basic/partCycleTime”数据
- [x] 设计前后端数据落库处理的接口和代码

> ### 设计评审补充（2026-08-05 修订）
>
> **一、fs_sim_param_snapshot 表需要 ALTER，不是只加概念字段**
> - 现有表结构见 `docs/fscloud/fs_sim_param_snapshot_mysql.sql`，实体 `FsSimParamSnapshot` 无 `runId` 字段。
> - 需新增：`RUN_ID varchar(64) NULL COMMENT '仿真运行ID，NULL=单独落库，非NULL=与结果快照成对落库'`，并加索引 `IDX_FS_SIM_PARAM_SNAPSHOT_RUN (RUN_ID)`。
>
> **二、fs_sim_result_snapshot 表 DDL 草案**
> ```sql
> CREATE TABLE `fs_sim_result_snapshot` (
>   `ID` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
>   `RUN_ID` varchar(64) NOT NULL COMMENT '仿真运行ID（MCP runId）',
>   `MODEL_FILE_ID` varchar(64) NOT NULL COMMENT '模型库记录ID（MCP recordId）',
>   `MODEL_FILE_NAME` varchar(255) DEFAULT NULL COMMENT '模型文件名',
>   `SIM_TIME` bigint DEFAULT NULL COMMENT '仿真总时长(ms)',
>   `STARTED_AT` bigint DEFAULT NULL COMMENT '仿真开始时间戳',
>   `FINISHED_AT` bigint DEFAULT NULL COMMENT '仿真结束时间戳',
>   `RESULT_STATUS` varchar(32) DEFAULT NULL COMMENT '结果状态(READY等)',
>   `SNAPSHOT_AT` bigint DEFAULT NULL COMMENT '结果快照时间戳',
>   `SUMMARY_RESULTS` json DEFAULT NULL COMMENT '总览结果(taktTime/uph/utilRate/leanMetrics/energyAnalysis)',
>   `DATASETS` json DEFAULT NULL COMMENT '数据集元信息列表(名称/行数，不含数据)',
>   `PART_CYCLE_TIME_FILE` varchar(255) DEFAULT NULL COMMENT 'partCycleTime 数据文件名(大表落文件)',
>   `CREATE_TIME` datetime DEFAULT NULL,
>   `UPDATE_TIME` datetime DEFAULT NULL,
>   PRIMARY KEY (`ID`),
>   UNIQUE KEY `UK_FS_SIM_RESULT_SNAPSHOT_RUN` (`RUN_ID`),
>   KEY `IDX_FS_SIM_RESULT_SNAPSHOT_MODEL` (`MODEL_FILE_ID`, `SNAPSHOT_AT`)
> ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仿真结果快照';
> ```
> - 时间字段统一用 bigint 毫秒时间戳，与 `fs_sim_param_snapshot.SNAPSHOT_AT` 及 MCP 返回的 `startedAt/finishedAt` 一致。
>
> **三、partCycleTime 存储方式建议：CSV 文件 + 流式分页读取**
> - 行数据是扁平对象数组，CSV 每行一条记录、列头取该 dataset 的 columns 顺序（无 columns 时取首行 key 顺序），便于按行分页读取，避免整文件 JSON 解析进内存。
> - 文件命名：`{runId}_partCycleTime.csv`；存放目录建议配置项（如 `fscloud.snapshot.file-dir`，默认 `./fscloud-snapshot/`），表内只存相对文件名。
> - 读取接口按 `PART_CYCLE_TIME_FILE` 打开文件流式分页返回；删除快照时同步删除文件。
> - 幂等覆盖时，旧文件先删除再写入新文件（或直接覆盖写同名文件）。
> - 后续若发现 `wip.timeline`、`energy.powerCurve` 等时序表数据量也可观，可按同一机制扩展（建议预留 `FILE_` 前缀字段命名空间或单独大表清单）。
>
> **四、落库数据获取方式：后端直连 MCP，前端不中转大数据**
> - 前端只传 `{ modelId, runId, bSaveParam: true }`，由后端 SimController 调 `McpClientService.getResultSnapshot` 拿总览，再对每个 dataset 循环 `getResultDatasetPage`（offset 递增直到 hasMore=false）拉全量。
> - 总览级 `summaryResults` 直接入 `SUMMARY_RESULTS`；每个 dataset 的完整 rows 按 dataset 维度序列化：小表合并进 `DATASETS` 明细或单独 json 列，partCycleTime 写 CSV 文件。
> - 好处：避免前端拼装超大 JSON 再 POST 的两次网络开销和内存峰值。
>
> **五、触发时机：在“历史数据”页面（simdata）手动触发，不做自动落库、不做结束事件弹窗**
> - 仿真控制与仿真历史分属不同页面，落库入口只放在“历史数据”页面（simdata/index.vue）。
> - 触发条件（按钮 enabled 判定）：用户手动点击过“获取仿真结果”且返回的 `resultStatus === 'READY'`（即仿真已自然停止或手动停止、结果可取）时，“保存结果快照”按钮才可用；否则置灰。
> - 点击“保存结果快照”即触发成对落库（参数快照 + 结果快照）。
> - 风险提示：MCP 服务器侧旧 runId 的快照可能被后续运行覆盖，未落库的历史结果将丢失；列表/页面提示用户及时保存。
> - 后端接口清单（草案）：
>   - `POST /fscloud/sim/saveResultSnapshot`（成对落库，事务内先落参数快照[带 runId]再落结果快照，按 (MODEL_FILE_ID, RUN_ID) upsert）
>   - `POST /fscloud/sim/listResultSnapshot`（按 modelFileId 倒序，不返回大数据）
>   - `POST /fscloud/sim/getResultSnapshotById`（详情：SUMMARY_RESULTS + DATASETS 元信息）
>   - `POST /fscloud/sim/getResultDatasetPageInDb`（从库/文件按页读某 dataset）
>   - `POST /fscloud/sim/deleteResultSnapshot`（删结果记录 + 成对参数记录 + partCycleTime 文件）
> - 落库数据不允许含超大字段（如 partCycleTime 全量 JSON）进表，遵循大表落文件原则。
>
> **六、幂等与去重（成对落库按 (MODEL_FILE_ID, RUN_ID) upsert）**
> - `fs_sim_result_snapshot` 以 `RUN_ID` 为唯一键（DDL 中 UNIQUE KEY `UK_FS_SIM_RESULT_SNAPSHOT_RUN`），重复保存同一 runId 时 UPDATE 覆盖对应记录的全部字段值（SIM_TIME/SUMMARY_RESULTS/DATASETS/SNAPSHOT_AT 等），不新增行。
> - 成对参数快照同理：按 `(MODEL_FILE_ID, RUN_ID)` 幂等，单独落库（RUN_ID 为 NULL）仍每次新增。
> - 大表文件（partCycleTime CSV）随 upsert 一起替换：先删旧文件再写新文件，保持表内 `PART_CYCLE_TIME_FILE` 文件名不变（同名覆盖）。
> - 列表显示：同一 runId 只有一行；再次保存后列表时间（SNAPSHOT_AT）更新为本次保存时间。
## step 17 - 参数-结果快照的横向对比分析
- [x] 快照列表显示: 在 snowy-admin-web/src/views/fscloud/simdata/index.vue 中"simdata-tabs"的左边加a-list组件，显示快照时间，具体方案可参照snowy-admin-web/src/views/fscloud/simparam/index.vue 中的listParamSnapshot列表。
- [x] 列表的第一行显示“当前仿真的快照”，后续的行显示历史快照，按时间倒序排列。即便当前仿真运行的快照，列表也保留第一行，此时选中当前仿真的快照时，判断myModel中是否有模型在仿真（runId必须有）, 有则调用函数 getResultSnapshotData(runId) 获取当前仿真运行的结果快照数据并显示a-tabs组件，而不是从数据库中获取。 没有模型在仿真则不显示a-tabs组件，只“当前没有模型的仿真结果数据”。
- [x] 选择其它历史快照时，调用函数 getResultSnapshotDataInDb(runId) 获取该历史快照的结果快照数据并显示a-tabs组件。
- [x] 横向对比分析：在a-tabs组件的右上角添加一个数据分析模式切换的radio组，默认是“单快照模式”，可切换为“横向对比模式/参数快照”、“横向对比模式/结果快照”，也即有3中显示模式，此时可以在左边的快照列表中选择两个或更多的快照进行对比分析。暂时只对比两个快照。
- [x] 对比分析时，a-tabs中有些类别适合做横向对比，有些不适合，例如“基础结果-循环时间”数据量可能非常大，不适合做横向对比，而“精益结果-产能”数据量较小，适合做横向对比。需要在TableTitleKeyMap.js中对每个类别进行标注，哪些类别适合做横向对比，哪些不适合。
- [x] 表格对比：在横向对比模式下，分对照表（compare to）、主体表(current snapshot)，其中，a-tabs中显示的表格显示主体表的数据，单元格用颜色标注差异，例如红色表示主体快照的数据大于对照快照的数据（后跟向上箭头和差值），绿色表示小于（后跟向下箭头和差值），黑色表示两个快照的数据相等。
- [ ] 图表对比：在横向对比模式下，a-tabs中中折线图、柱状图等同时表示两个快照的数据，折线图、柱状图等的颜色不同，图例中标注快照的时间。

> ### 实施备注（2026-08-05）
>
> **数据库影响（已执行）：**
> - 新表 `fs_sim_result_snapshot`（DDL：`docs/fscloud/fs_sim_result_snapshot_mysql.sql`），已在 13306 的 `gh_mysql`（应用库）与 `fp_mysql` 建表。
> - `fs_sim_param_snapshot` 已存在 `RUN_ID` 列（成对落库标识），无需重复 ALTER。
>
> **后端（新增/修改）：**
> - 实体：`FsSimResultSnapshot`、`FsSimParamSnapshot`（+runId）
> - Mapper：`FsSimResultSnapshotMapper`
> - Service：`FsSimResultSnapshotService(+Impl)`：saveResultSnapshot（后端直连 MCP 拉总览+参数快照+逐 dataset 全量；partCycleTime 写 CSV，其余入 DATASETS JSON；按 RUN_ID upsert）、listByModelFileId、getById、getDatasetPage（CSV 流式分页/JSON 分页）、deleteById（连带删参数快照与文件）
> - `FsSimParamSnapshotService(+Impl)`：savePairedSnapshot（按 (modelFileId, runId) upsert）、getByRunId、deleteByModelAndRunId
> - `SnapshotDto`（+runId）
> - SimController 新增 5 接口：saveResultSnapshot / listResultSnapshots / getResultSnapshotById / getResultDatasetPageInDb / deleteResultSnapshot
>
> **前端：**
> - `fsSimApi.js` 新增 5 个 API 函数
> - `TableTitleKeyMap.js` 全部 29 个 dataset 增加 `compareKey`：20 个有对齐键（objectName/componentId/mediumId/deviceName），9 个空串不对比（partCycleTime、wip.timeline、wip.distribution、lean.wipTimeline、lean.changeoverRecords、energy.rows、energy.powerCurve、overview.leanMetrics、overview.energyAnalysis）
> - `ResultTable.vue` 支持对比模式：compareRows + compareKey 对齐；红=主体>对照（↑差值）、绿=主体<对照（↓差值）、橙=字符串不同；主体有对照无→整行浅蓝底（rt-row-missing）
> - `simdata/index.vue`：保存结果快照按钮（enabled=已获取结果且 resultStatus===READY）、左侧快照列表（当前仿真+历史）、三种模式 radio（单快照/横向对比-参数/横向对比-结果）、对比勾选（第1主体第2对照）、结果对比（对照按 dataset 拉全量缓存）、参数对比（按 runId 匹配成对参数快照，表级+行号对齐 diff 标色）
>
> **验证：**
> - 后端 `mvn -pl snowy-plugin/snowy-plugin-fscloud -am compile -o` 通过
> - 前端 `npm run build` 通过
> - 数据库建表/查表验证通过
>
> **Code review 修复（2026-08-06）：**
> - 后端：runId 白名单校验（`[A-Za-z0-9_-]+`，防路径穿越）；fetchAllRows 以 hasMore 为主判据（防静默截断）
> - 前端：修复未定义函数 `loadDatasetFromDb`（改为 loadDataset，切换历史快照时清空旧数据）；删除死代码 compareRefId；参数 diff 数值宽松比较（1 与 '1' 视为相等）；对照全量拉取上限提到 500 页并 warn 截断；n/a 行不参与差值计算
>
> **Bug 修复（2026-08-06，log_error.log）**
> - 现象：`GET/POST /fscloud/sim/listResultSnapshots` 抛 `UnsupportedOperationException: Unsupported toMap value type: java.lang.String`，`getResultSnapshotById` 存在同类隐患
> - 根因：`FsSimResultSnapshotServiceImpl.listByModelFileId` / `getById` 用 `BeanUtil.toBean(entity, ResultSnapshotDto.class)` 转换，实体 `summaryResults`/`datasets` 为 String(JSON)，DTO 对应字段为 `Map`/`List<Map>`，hutool 转换 String→Map 时 MapConverter 抛异常
> - 修复：改为手动字段拷贝 `toDto(entity, withSummary)`（列表不返回 summary/datasets，详情解析 summaryResults 为 Map），删除无用 BeanUtil import
>
> **剩余（后续 step）：**
> - 图表对比（chartType 标注 + echarts 折线/柱状双序列）未实现
> - 对照数据按 compareKey 值集合精确拉取（当前为整 dataset 全量缓存，后续可优化）
> - 权限码未注册（与现有 fscloud 接口一致，超管环境放行；如需细分权限再补 SYS_RESOURCE/SYS_RELATION）

> ### 设计评审补充（2026-08-05 修订）
>
> **一、快照列表数据源与去重（runId 唯一）**
> - 列表数据源：`listResultSnapshot`（按 `MODEL_FILE_ID` 过滤、`SNAPSHOT_AT` 倒序），第一行固定“当前仿真的快照”。
> - 去重：`fs_sim_result_snapshot` 中 runId 唯一（DDL UNIQUE KEY），同一 runId 再次保存按 `(MODEL_FILE_ID, RUN_ID)` 幂等覆盖并更新列表时间，因此列表中同一 runId 永远只有一行。
> - 当前 runId 已落库时，实时行与历史行是同一条——历史行命中当前 runId 时标注“（已保存）”。
> - 列表项建议展示：快照时间、仿真时长、总产量；并提供删除入口（删除需二次确认，提示会连带删除成对参数快照与 partCycleTime 文件）。
> - 当前行“实时”判定：`myModel.isLoaded && myModel.runId 非空` 才显示 a-tabs；快照时间取实时拉取结果的 `resultSnapshotAt`，避免显示空白。
>
> **二、三种显示模式与角色分配**
> - a-tabs 右上角 radio 组，3 种模式互斥（同一时间只显示参数或结果之一）：
>   1. **单快照模式**（默认）：只显示选中快照，参数/结果随 tab 展示
>   2. **横向对比模式-参数快照**：显示两组“参数-结果”对中的**参数**对比
>   3. **横向对比模式-结果快照**：显示两组“参数-结果”对中的**结果**对比
> - 对比模式下列表项显示 checkbox，勾选 2 个快照 = 选中两组“参数-结果”对；勾选第 1 个为主体（current snapshot）、第 2 个为对照（compare to），第 3 个起禁用或提示“暂时只支持对比两个快照”。
> - 未勾满 2 个时，a-tabs 区域提示“请勾选两个快照进行对比”。
> - 主体数据来源：当前仿真行→MCP 实时；历史行→数据库。对照数据来源：必须是数据库（历史快照）。若主体是实时行，对比前建议提示用户先落库，否则对照侧无参数快照可比。
>
> **三、compareKey 对比键（空串=不对比）**
> - `TableTitleKeyMap.js` 中每个 dataset 增加 `compareKey`：
>   - `compareKey === ''`（空串）：该类别**不做横向对比**，对比模式下只显示主体快照的数据（如 partCycleTime、wip.timeline 等大数据/时序表）。
>   - `compareKey` 有值：值为该表的某列名（column key），例如 `overview/taktTime` 的 `compareKey: 'objectName'`、`utilRate` 的 `compareKey: 'lineNo'`、`energy.mediumSummary` 的 `compareKey: 'mediumId'`。对比时按该列的值对齐两侧行。
> - 不采用按下标（行号）对比——行序不可靠，必须按 compareKey 对齐。
> - 对齐规则：主体行存在、对照缺失 → 该行整行**浅蓝底**（表示“主体有，对照无”）；对照行存在、主体缺失 → 对照侧该行同样浅蓝底（不参与差值计算）；两侧都有 → 正常计算差值。
> - 对比模式下 `compareKey === ''` 的 dataset 子 tab 置灰（保留 tab，点击提示“该类别暂不支持横向对比”），避免 tab 数量跳变。
>
> **四、compareable 与 chartType 标注**
> - `TableTitleKeyMap.js` 增加 `compareable: true|false`（与 `compareKey === ''` 等价，二选一或互为冗余）：大数据/时序类（partCycleTime、wip.timeline、wip.distribution、lean.wipTimeline、energy.powerCurve）建议 false；小表（taktTime、uph、utilRate、lean.capacity、lean.oee、lean.lt 等）建议 true。
> - 图表对比建议增加 `chartType: 'line'|'bar'|'none'`：时序类用折线（wip.timeline、energy.powerCurve），分类对比用柱状（utilRate、lean.capacity）；两序列颜色不同，图例标注各自快照时间。图表库用项目已装的 `echarts@5.5.1` 或 `@antv/g2plot`（已有使用先例）。
>
> **五、参数对比与结果对比互斥显示**
> - 因（参数）果（结果）同时显示排版不好处理，故同一时间只显示参数或结果中的一个，由模式 radio 切换（见二）。
> - “横向对比模式-参数快照”：展示两个快照的参数表 diff（复用 simparam 的参数表结构，差异单元格标色，runId 为空的历史参数快照不可作对照——无对应结果）。
> - “横向对比模式-结果快照”：展示结果数据 diff（颜色语义：红=主体>对照（↑差值）、绿=主体<对照（↓差值）、黑=相等；字符串列不同则高亮）。
> - 参数对比与结果对比共用同一对快照选择，切换模式不改变选择。
>
> **六、性能与分页（以主体分页为准）**
> - 对比模式下分页以**主体**为控制：先按页取主体数据（当前仿真行→MCP 分页，历史行→数据库分页），再根据本页主体的 compareKey 值集合，按 compareKey 批量取对应的对照数据（对照数据从数据库按 compareKey 查询，不分页拉全量）。
> - 差值在渲染层计算，不落库；两侧页大小保持一致（25/50/100）。
 
## step 18 - debug: 落库之后的快照数据显示时表头顺序不一致
当前仿真的快照数据是从 MCP 直接获取的，历史快照数据是从数据库中获取的，两个来源的数据表头顺序不一致，导致在横向对比模式下，表格显示的列顺序不一致。需要在 snowy-admin-web/src/views/fscloud/simdata/index.vue 中对两个来源的数据进行统一处理，使得表头顺序一致。
tips： 可根据 TableTitleKeyMap.js 中的 columns 顺序进行统一处理。

- [x] 根据 TableTitleKeyMap.js 的 columns 顺序统一表头

  > ### 实施备注（2026-08-06）
  >
  > **根因**：`simdata/ResultTable.vue` 的 `antColumns` 之前用 `Object.keys(rows[0])`（数据首行 key 顺序）决定列序；MCP 实时与数据库落库两个来源的 row key 顺序不同，导致列序不一致。
  >
  > **修复**（仅改 `simdata/ResultTable.vue`，新增 `orderedColumnKeys`）：
  > - 列顺序优先取 `tableTitleKeyMap[groupKey][datasetKey].columns` 的键顺序
  > - 数据有而映射表没有的列追加到末尾（不漏列）
  > - 映射表有而数据没有的列跳过（不显示空列）
  > - 无映射表定义时回退原逻辑（`Object.keys(rows[0])`）
  >
  > **验证**：`npm run build` 通过。overview/basic/lean/energy 四条数据通路（MCP 实时、DB 历史、partCycleTime CSV、summary）统一走该逻辑，横向对比列序一致。

## step 19 - debug: 横向对比模式下，主体快照和对照快照的数据是整数或小数时，按字符串处理了
参数费照和结果快照都有这个bug。参数的数据类型是在表头那里用s/t/i/f表示了，结果快照的数据类型是在TableTitleKeyMap.js中用type表示了。
- [x] 数字的，需要主体数据加上对照数据，计算差值，显示差值和箭头（↑/↓），并用颜色标注差异，红色表示主体快照的数据大于对照快照的数据，绿色表示主体快照的数据小于对照快照的数据，黑色表示两者相等。
- [x] 字符串的，需要主体数据和对照数据进行比较，显示差异，并用颜色标注差异，橙色表示两者不相等，黑色表示两者相等。不相等的这样显示：“主体数据[对照数据]”，也就是在主体数据后面加上对照数据，并用中括号括起来。

  > ### 实施备注（2026-08-06）
  >
  > **结果对比**（`simdata/ResultTable.vue`）：`diffForCell` 字符串分支由"仅变橙"改为返回 `[对照值]`，customRender 追加在主体值后，显示"主体数据[对照数据]"（橙 `#d46b08`）。数字列原本已按 i/f/t 类型算差值+红/绿箭头，不变。
  >
  > **参数对比**（`simdata/index.vue`）：
  > - `normalizeParamTables` 保留列类型：MCP 原生 columns 每项存 `{ name, type }`；keyed 保存格式无类型 → `type: ''`
  > - 新增 `paramDiffForCell`：数字列（type 为 i/f/t，或值可转数值）按类型算差值（↑红 `#cf1322` / ↓绿 `#389e0d`，t 类型差值用 `fmtTsShort` 转 h:m:s）；字符串列不等显示 `[对照值]`（橙 `#d46b08`）；相等均不标注（黑）
  > - `paramDiffTables` 的 `_diffMap` 由 `true` 改为 diff 对象；`rowToObj` 用 `c.name` 取值
  > - 模板差异单元格由整格橙黄背景改为"主体值 + 差异标注 span"（与结果对比风格统一），删除 `.param-cell-diff` 样式
  >
  > **补充修复（2026-08-06）**：MCP 参数快照的 `columns[].type` 为完整写法（`String`/`Int`/`Time`/`Float`/`Double`），`paramDiffForCell` 改为**取首字母并忽略大小写**判断类型（s-string / i-int / t-time / f-float / d-double），数字列（i/f/t/d）才计算差值+红/绿箭头，避免数字被当字符串处理。
  >
  > **验证**：`npm run build` 通过。

## step 20 - fine tune: 横向对比时，在快照列表中，代表主体费照的行，时间加前缀“主体”，代表对照快照的行，时间加前缀“对照”，以便直观区分。
- [x] 主体/对照行加前缀标识（含颜色增强）

  > ### 实施备注（2026-08-06）
  >
  > 仅改 `simdata/index.vue`：
  > - `snapshotListItems` computed：对比模式（mode !== 'single'）下按 `checkedIds` 勾选顺序给行加 `role` 标记（第 1 个 `main`、第 2 个 `ref`）
  > - 模板标题前渲染彩色标签：主体蓝 `#1677ff`（底 `#e6f4ff`）、对照紫 `#722ed1`（底 `#f9f0ff`）
  > - 切换回单快照模式或取消勾选后标记自动消失；不影响 key/勾选/数据加载逻辑
  >
  > **验证**：`npm run build` 通过。

## step 21 - refactor: 对照参数和结果现在是分开选快照，逻辑需要调整
现状：
- 单快照模式、横向对比模式-参数快照、横向对比模式-结果快照，三种模式下，左边的快照列表都是一样的
- 横向对比模式-参数快照、横向对比模式-结果快照, 都是选中两个快照进行对比。
重构目标：
- radio button 只分单快照模式、横向对比模式
- 横向对比模式下，先选择两个快照
- 选择完两个快照后，同时构建参数对照的页面和结果对照的页面，默认显示参数对照的页面，用户可以切换到结果对照的页面。
- 切换参数和结果对照页面时，左边的快照列表不变，仍然是选择的两个快照。同时，只是隐藏和显示参数对照页面和结果对照页面而已，数据不需要重新获取。
- [x] radio 收为 2 项（单快照 / 横向对比），对比模式下二级 tabs（参数对照/结果对照）切换

  > ### 实施备注（2026-08-06）
  >
  > 仅改 `simdata/index.vue`：
  > - radio 组 3 项 → 2 项：`single` / `compare`
  > - 新增 `compareView`（'param' | 'result'）：对比模式下内容区顶部二级 `a-tabs`（class `compare-view-tabs`，蓝色系 14px 加粗、蓝下划线 3px，与内层结果 tabs 默认白色区分），默认显示"参数对照"
  > - 勾满 2 个快照 → `loadCompareBoth()` 一次性构建两页：结果侧（主体数据 + 对照元信息）、参数侧（两快照参数表）；切换参数/结果页仅显示切换，零请求
  > - `showResultView` computed：单快照模式 或 对比模式结果对照页 才显示总览+结果 a-tabs
  > - 勾选规则：当前仿真行可作主体（第1个），不能作对照（第2个，提示"当前仿真未落库，不能作为对照快照"）；对照必须是历史快照
  > - 取消勾选/切换模式时清空两页缓存与结果数据；`compareRowsOf`/`ensureCompareRows` 模式判断改为 `compare`；删除死代码 `loadResultCompare`
  >
  > **验证**：`npm run build` 通过。

## step 22 - MCP接口有新増工具、也有修改
### MQTT主题增加
现在的MQTT主题有：
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
其中`factory/sim/{username}/{modelId}/events` 是订阅事件的主题，是新增的。
而`factory/sim/{username}/{modelId}/animation/{layerId}/objects` 是订阅动画对象的主题，对应fs模型较大时，数据量比jpeg图片流还要大，本demo工程不使用它。
- [x] 在snowy-admin-web/src/views/fscloud/eventplayback/index.vue之前订阅后者（objects），现在需要改成订阅前者（events）。
- [ ] 在snowy-admin-web/src/views/fscloud/simcontrol/index.vue中开启仿真成功后，需要将snowy-admin-web/src/views/fscloud/eventplayback/index.vue订阅events主题，所需的信息保存在myModel中，所需的信息包括runId。

## step 23 - feat: 仿真结果快照的图形显示（对照 pyqt_app 实现）

需求：pyqt_app（`ui/sim_tabs/result_tab.py` + `result_chart.py` + `result_tab_map.json`）已实现部分结果快照数据的图形显示，
`simdata/index.vue` 只有表格。要求对照实现，且不修改 pyqt_app，Java+Vue 既有功能（表格/分页/横向对比/快照保存）全部保留。

- [x] `TableTitleKeyMap.js` 为 15 个 dataset 增加 `chart` 配置（语义与 pyqt 的 `result_tab_map.json` 逐字对齐）：
  - overview 3（taktTime/uph/utilRate，utilRate 为 stacked 堆叠柱）
  - basic 3（rbData、wip.timeline、wip.distribution）
  - lean 6（lean.fpy、lean.wipTimeline、lean.cycleTime、lean.processEfficiency、lean.oee、lean.pplh）
  - energy 3（energy.rows、energy.mediumSummary、energy.powerCurve）
- [x] 新增 `simdata/ResultChart.vue`（ECharts 按需引入）：bar/line、stacked、groupBy 多序列、t 轴按 `fmtTimestamp` 格式化、
      类别过多自动 dataZoom、降采样提示、ResizeObserver 自适应
- [x] 新增 `simdata/DataViewPanel.vue`：仅「有 chart 配置」的 dataset 才出现「表格 / 图表」切换（与 pyqt 一致）；
      overview 的 leanMetrics/energyAnalysis 摘要子页不受影响
- [x] 新增 `simdata/utils/format.js`（公共 `fmtTimestamp`/`toNumber`），`ResultTable.vue` 改用，消除两份时间格式化实现
- [x] 后端新增 `POST /fscloud/sim/getResultChartData`（`GetResultChartDataParam` / `ChartDataDto` /
      `FsSimResultSnapshotServiceImpl.getChartData`）：实时数据源走 MCP 循环分页取全量，历史快照从
      DATASETS JSON / partCycleTime CSV 读全量，统一按 `maxPoints`（默认 2000，硬上限 20000）等距抽样后返回
      `{ dataset, rows, total, returned, sampled }`
- [x] `index.vue` 接入：`viewMode` + `chartCache`（按 dataset 懒加载并缓存）；实时快照传 `modelId/runId`、
      历史快照传 `id`；取结果/切快照/切模式时清缓存回到表格；**横向对比模式本期不支持图表**（未选中的方案）

  > ### 实施备注（2026-08-08）
  >
  > **设计要点**
  > - 图表元数据（type/x/y/stacked/groupBy/title）全部走 `TableTitleKeyMap.js`，与 pyqt 的 JSON 配置表一一对应，
  >   两边以后可对照维护；新增图表只需加配置，不改组件。
  > - 数据不再是「当前分页页」，而是服务端返回的全量（降采样）——`wip.timeline`、`energy.powerCurve` 这类时序表
  >   在 25 行/页下画不出趋势，这是与 pyqt 的差异点（pyqt 用当前页 rows）。
  > - `t` 类型值 `-1`（n/a）在图表中按空值跳过，与 `ResultTable.vue` 的表格口径一致。
  > - 权限码 `/fscloud/sim/getResultChartData` 与既有 fscloud 接口一样未在菜单表注册（超管环境放行）。
  > - 图表仅在单快照模式可用；对比模式仍为表格 + 差值标注。
  >
  > **验证**：`npm run build` 通过（产物含 `ResultChart-*.js`）；`mvn -pl snowy-plugin/snowy-plugin-fscloud -am -DskipTests compile` 通过。
  >
  > **遗留**：对比模式的双序列图表（devlog step 17 的建议项）、`getRemoteSimulationEnergyPowerCurvePage` 专用过滤接口
  > （`McpClientService.getEnergyPowerCurvePage` 已封装但 controller 未暴露）未实现。

## step 24 - fix: 图表降采样把「同一 x 上的多点」拆散，功率曲线垂直跳变丢失

现象：`energy.powerCurve` 会在同一个时间点（同一个 `timeMs`）给出多个点，用于形成垂直跳变（阶梯）显示。
step 23 的「统一等距抽样降采样」按**行**抽样，把这些同 x 的点拆散，阶梯退化成斜线（pyqt 不分页抽样，整页原样绘制，所以没这个问题）。

- [x] 后端 `getResultChartData` 新增 `xColumn` 入参；给了 xColumn 时，降采样以「同一个 x 值的**全部行**」为最小不可拆单位：
      先按 x 值分组（`LinkedHashMap` 保持 x 首次出现顺序；数值统一归一为 `Double`，避免 `0` 与 `0.0` 被当成两个时间点），
      再对「x 值」等距抽样、命中组的全部行整体保留；某组行数异常多时按行抽样兜底（`maxPoints * 2` 上限）
- [x] 未传 xColumn 时保持原「按行等距抽样」行为不变
- [x] 前端 `ensureChartData` 请求体带 `xColumn = chart.x`（取自 `TableTitleKeyMap.js`）
- [x] 前端 `ResultChart.vue`：line 数据点带原始序号并按 `(x, 序号)` 排序，保证同一 x 上的多点连线顺序与数据原始顺序一致
      （否则「先降后升」会被连成斜线、跳变方向翻转）；传给 ECharts 前再裁回 `[x, y]`
- [x] 新增单元测试 `snowy-plugin-fscloud/src/test/.../ChartDownsampleTest.java`（该模块首个测试类，6 例）

  > ### 实施备注（2026-08-08）
  >
  > **行为语义**：`maxPoints` 是点数预算。同 x 多点的图表按「x 值」整组抽样，返回行数约为预算量级（如功率曲线每组 2 点，
  > 预算 2000 即约 1000 个时间点），宁可少画时间点，也不拆散跳变。
  >
  > **测试基建踩坑**：根 pom 的 `dependencyManagement` 把 `junit-jupiter-api` 锁在 5.10.1，与 `spring-boot-starter-test`
  > 带来的 `junit-platform/engine`(1.12.2 / 5.12.2) 版本错配，surefire forked JVM 以
  > `TestEngine with ID 'junit-jupiter' failed to discover tests` / `NoClassDefFoundError` 失败。
  > 修法：在 `snowy-plugin-fscloud` 模块内显式声明 `junit-jupiter-api` 为 `${junit-jupiter.version}`（继承自
  > `spring-boot-starter-parent`，随升级自动跟随），**未改动根 pom 与其他模块**。若其它模块要加测试，需同样对齐。
  >
  > **验证**：`mvn -pl snowy-plugin/snowy-plugin-fscloud -am test -Dtest=ChartDownsampleTest` → Tests run: 6, Failures: 0, Errors: 0；
  > `npm run build` 通过。
  >
  > **仍需人工验收**：真实功率曲线数据下，缩放/抽样后是否仍完整呈现阶梯（需运行中的 FS + MCP 环境）。

## step 25 - fix: 稀疏区被抽样整段跳过，功率曲线中间出现跨时间窗的斜线

现象（用户截图）：功率曲线 4128 点抽到 2000 点后，曲线中段只有一条从 12kW 斜降到 0 的直线，横跨约 21 小时；
正确形态应当只有垂直跳变 + 水平段（阶梯）。

- [x] 后端抽样策略由「按 x 值**索引**等距抽样」改为「按 x **范围分桶**抽样，每桶保留首末组、空桶直接跳过」
- [x] 仅对数值型 x 启用分桶（时间序列）；非数值 x（字符串类目）退回原按 x 等距抽样
- [x] 新增单元测试 `keepsSparsePlateauPoints`：构造「密集段 + 中间只有平台入口/出口两个采样点 + 密集段」的数据，
      断言平台入口（降至 0）与出口（升回）的时间点必须保留

  > ### 实施备注（2026-08-09）
  >
  > **根因**：功率曲线是**事件驱动**数据——功率恒定时几乎没有采样点，只有跳变（事件）前后各一组。
  > step 24 的「按组索引等距抽样」是按**序号**均匀取组，与数据在时间轴上的疏密无关：
  > 前段/后段的密集波动占满了组索引，中段孤立的平台跳变点（索引恰好落在取样间隔之外）被整体跳过，
  > 于是图上只剩 (05:33, 12) → (1天03:46, 0) 两点连成的斜线。pyqt 因为只画当前页（连续一小段）看不到该现象。
  >
  > **修法**：把 x 轴范围等分 `buckets`（≈ 可保留组数 / 2）个桶，每个桶取**首组与末组**（含各自同 x 的全部行）、
  > 空桶不补点。这样：
  > - 稀疏区：平台入口/出口各自落在所属桶内被保留 → 图上为垂直跳变 + 水平段（不再有斜线）；
  > - 密集区：每桶仍取首末两组 → 保持阶梯感且行数受预算约束；
  > - 首末点必然保留（首桶首组、末桶末组）。
  > 实现用两个 int 数组做单遍分桶定位（O(n)），不做桶×组 的嵌套扫描。
  >
  > **验证**：`mvn -pl snowy-plugin/snowy-plugin-fscloud -am test -Dtest=ChartDownsampleTest` → Tests run: 7, Failures: 0；
  > `npm run build` 通过。
  >
  > **仍需人工验收**：真实功率曲线上「长时间恒定功率」段是否呈现为水平线（若该时段数据中确实一个点都没有，
  > 则任何抽样都无法还原，只能显示为连线——这需要真实数据确认）。

## step 26 - feat: 二次采样间隔改为可调（当前关闭，数据原样返回）

需求：功率曲线仍有斜线问题，先把「二次采样」关掉验证——间隔做成可调（放在采样函数体最前面，方便以后改），
当前设为 1 即数据原样返回；抽样机制保留。

- [x] `FsSimResultSnapshotServiceImpl.downsampleRows(rows, maxPoints, xColumn)` 函数体第一行即
      `final int sampleRatio = 1;`（改这一处即可调整间隔），随后转发给 4 参重载
- [x] 4 参 `downsampleRows(rows, maxPoints, xColumn, sampleRatio)`：`sampleRatio <= 1` 时**原样返回**；
      `n > 1` 时按 x 范围分桶抽样，保留组数 = `组数 / n`（桶内取首末组，稀疏区跳变点仍尽量保留）
- [x] `maxPoints` 语义下调为「数据量本来就小则不抽样」的门槛（不再作为抽样目标），去掉 `maxPoints * 2` 行数兜底
- [x] 单测同步：抽样用例显式传 sampleRatio（2/3/10），新增 `returnsRowsAsIsWhenSampleRatioIsOne`

  > ### 实施备注（2026-08-09）
  >
  > **当前行为**：`sampleRatio = 1` → 后端把该 dataset 的**全量数据**原样返回（`sampled=false`，前端不显示抽样提示），
  > 不再对功率曲线做任何加工。若图形仍出现斜线，即可排除抽样因素，问题在数据本身
  > （该时段确实没有采样点，或 x 值跳变）。
  >
  > **代价**：返回体与渲染量随数据量线性增长（当前样例 4128 点无压力）。数据量再上一个量级时，
  > 把 `sampleRatio` 调成 2~5 即可恢复压缩（仍按 x 分桶、保留同 x 全部点）。
  >
  > **验证**：`mvn -pl snowy-plugin/snowy-plugin-fscloud -am test -Dtest=ChartDownsampleTest` → Tests run: 8, Failures: 0。

