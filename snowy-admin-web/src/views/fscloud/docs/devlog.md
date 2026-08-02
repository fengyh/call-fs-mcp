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
