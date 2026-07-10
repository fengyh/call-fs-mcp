## 开发者必读
### 读框架文档
https://xiaonuo.vip/doc?catalogueId=1574674577108783105&menuId=1574676642119831554&lineIndex=1 按快速启动部分运行代码

### mysql数据库名和端口号用户密码约定
- 数据库名：fp_mysql
- 数据库编码：utf-8
- mysql版本：8.0
- 端口号：
  - prod: 3306
  - test: 13306
- 用户名：root
- 密码：rootpassword

### redis的安装和运行
- 较新版本
- 免密码

注： 建好数据库库后，按快速启动的方法导入数据库脚本，然后启动前端和后端代码

## 框架介绍
详见 [SNOWY.md 框架介绍](./SNOWY.md)
## ChangeLog 维护规则

## 开发日志
### 接口测试代码
测试代码主要用于测试MCP接口和MQTT接口，以及如何使用这两类接口开发应用，供开发者参考。
#### MCP接口测试代码
- 代码：snowy-web-app/src/test/java/fs/McpClientTest.java
- 依赖包： (snowy-web-app/pom.xml) 
  - fastjson2
- 模型文件：snowy-web-app/src/test/resources/step-by-step.fs
- 测试代码运行步骤：
  - 运行fs-mcp5.1.0版 （可作桌面端也可作服务端）
  - 运行mqttx、连接fs-mcp的内置mqtt broker、订阅主题：factory/sim/+/+/+
  - 在IDEA(或其它IDE)中运行McpClientTest.java测试代码(代码有main方法，直接运行即可)，如果一切顺利，可以在mqttx中看到订阅主题的消息，且IDEA控制台中打印出测试代码调试信息。
- 注： docs/McpClientTest.log.md 中记录了测试代码运行的调试信息、MQTTX的订阅主题消息，供开发者参考。
#### MQTT接口测试代码
- 代码：snowy-web-app/src/test/java/fs/MqttClientTest.java
- 依赖包：snowy-web-app/pom.xml 
  - org.eclipse.paho.client.mqttv3
- 测试代码运行步骤：
  - 运行fs-mcp5.1.0版 （可作桌面端也可作服务端）
  - 运行mqttx、连接fs-mcp的内置mqtt broker、订阅主题：factory/sim/+/+/+
  - 在IDEA(或其它IDE)中运行MqttClientTest.java测试代码(代码有main方法，直接运行即可)，如果一切顺利，可以在mqttx中看到订阅主题的消息，且IDEA控制台中打印出测试代码调试信息。
- 注： docs/MqttClientTest.log.md 中记录了测试代码运行的调试信息、MQTTX的订阅主题消息，供开发者参考。
