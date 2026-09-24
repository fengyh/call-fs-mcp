
# 1. Python与FS集成最小验证系统构建
## 1.1 需求
- 在pyqt_app/ui/mcp_module.py中

# 2. 移植接口封装类java->python
## 2.1 需求
- 原封装：java springboot的service: snowy-plugin/snowy-plugin-fscloud/src/main/java/vip/xiaonuo/fscloud/modular/simulation/service/McpClientService.java
- 新封装：pyqt_app/mcp_client/mcpClient.py
- 目的：将原来的java封装转换为python封装，以便在PyQt应用中使用。
## 2.2 约束条件
- 之前的封装是基于Java的Spring Boot框架，架构为“MCP Server - Spring Boot 后端 - Vue前端”，新的封装需要适应Python的PyQt框架，架构简化为“MCP Server - Python 应用程序”。
- 新封装需要保持与原封装相同的功能和接口。
## 2.3 实现细节
