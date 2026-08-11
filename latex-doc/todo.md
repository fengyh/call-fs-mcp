本会话主要是用latex写WEBAPP通过MCP接口与FS集成开发的指南文档。目前的latex-doc中的内容只是模板，内容与我要写的开发指南无关，仅作为格式参考即可。

## step 1: 分析工程，写public-brief.tex章节

分析D:\works\factory-planner\call-fs-mcp工程，将以MCP的接口使用为核心，结合snowy框架前后端实现MCP接口开发的重点事项，在public-sections\public-brief.tex中写简介。分这几个部分：

- MCP介绍
- 本文对应的FS仿真控制MCP接口介绍
- 在snowy框架基础上开发通过MCP接口进行WEBAPP与FS仿真集成的步骤
- 

## step 2: 写public-sections\interface.tex章节

根据snowy-plugin/snowy-plugin-fscloud/src/main/java/vip/xiaonuo/fscloud/modular/simulation/service/McpClientService.java和 docs/remote_simulation_mcp_docs中的文档、docs/devlog中的开发日志文档、docs/McpClientTest.log.md的MCP测试打印日志，写public-sections\interface.tex章节，内容包括:
- 按以下分类列出MCP接口的功能点和方法名，按功能点分组，列出每个方法的入参和出参类型与举例，以及简要说明每个方法的作用。
模型管理
loadRemoteSimulationModel、unloadRemoteSimulationModel、
listRemoteSimulationModels
运行控制
startRemoteSimulation、pauseRemoteSimulation、resumeRemoteSimulation、
resetRemoteSimulation、stopRemoteSimulation
状态查询
getRemoteSimulationStatus
结果获取
getRemoteSimulationResultSnapshot、getRemoteSimulationResultDatasetPage
参数表读写
listRemoteSimulationParameterTables、getRemoteSimulationParameterSnapshot、
updateRemoteSimulationParameterTable、updateRemoteSimulationParameterCells、
batchUpdateRemoteSimulationParameterTable
动画与画面
listRemoteSimulationAnimationLayers、selectRemoteSimulationAnimationLayer、
setRemoteSimulationAnimationEnabled、setRemoteSimulationAccelerateFactor、
getSvgPicture
对象位置快照与时间轴事件
getRemoteSimulationObjectPositionSnapshot、getRemoteSimulationEvents

- 每个方法都需要描述这些内容：
  - 方法名
  - 方法描述
  - 入参类型与举例
  - 出参类型与举例
- 以上MCP工具的调用顺序推荐
- 注意事项

## step 3: “call-fs-mcp”demo为例介绍系统架构
- 该demo程序用于演示如何通过MCP接口与FS仿真集成开发。
- 系统逻辑架构
  - 前端：snowy框架，Vue3+TypeScript+Vite
  - 后端：snowy框架，SpringBoot+Java
  - FS桌面程序: FS建模器、仿真引擎，在本方案中亦可作为FS云服务的仿真引擎使用
  - FS-Cloud-Plugin程序（真实应用可选）: 用于上传模型文件（.fs文件）,带封面图片、用途和共享设置
  - FS云服务和接口：FS仿真引擎, 内部MCP接口、MQTT Broker
  - 数据库：mysql8、redis
  - 辅助工具：
    - MQTTX客户端软件，用于监听MQTT消息，辅助应用开发调试
    - Reasonix或Cherry Studio桌面软件，用问答方式调用MCP接口，辅助应用开发调试 
- 根据以上架构，写public-sections\architecture.tex章节，内容包括：
  - 系统逻辑架构图
  - 各模块的功能说明
  - 各模块之间的交互说明
  - MCP接口在系统中的调用流程示意图
  - 前端与后端的交互流程示意图
  - FS仿真引擎与MCP接口的交互流程示意图
tips: 可以使用plantUML绘制架构图、流程图、序列图等、泳道图等, 再导出为latex格式, reasonix不方便自动绘图和导出，可以后续手动添加。
