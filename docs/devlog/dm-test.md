---
target: 测试mysql8和dm8（达梦）数据库共存
description: 目前已安装好dm8, 保留原来的mysql8数据库，同时测试使用dm8保存api访问的信息
---
## Step 1: 在test配置中添加dm8的datasource
- 修改`snowy-web-app/src/main/resources/application-test.properties`，添加访问dm8的datasource
  - 主机名：LOCALHOST
  - 端口：5236
  - 数据库名：MCPDB
  - 用户名：MCPDB
  - 密码：Dameng@2026
- 表API_LOG的DDL:
```json
CREATE TABLE "MCPDB"."API_LOG"
(
"ID" NUMBER(19,0) NOT NULL,
"ACCESSTIME" DATETIME(0) NOT NULL,
"ENDPOINT" VARCHAR(256) NOT NULL,
NOT CLUSTER PRIMARY KEY("ID")) STORAGE(ON "MCP", CLUSTERBTR) ;

COMMENT ON TABLE MCPDB.API_LOG IS '记录API访问日志';
COMMENT ON COLUMN MCPDB.API_LOG."ACCESSTIME" IS '访问时间';
COMMENT ON COLUMN MCPDB.API_LOG."ENDPOINT" IS '访问端口';
COMMENT ON COLUMN MCPDB.API_LOG."ID" IS '主键';
```
- 修改pom.xml安装必要的package
- 在前端访问后端API时，将访问的URL保存到dm8数据库的API_LOG表中

### 实现说明（2026-10-29）

- **驱动**：`com.dameng:DmJdbcDriver18:8.1.3.62`，已在根 `pom.xml` 的 `dependencyManagement` 与 `snowy-web-app/pom.xml` 中启用；`snowy-plugin-fscloud/pom.xml` 增加 `dynamic-datasource-spring-boot3-starter`（`@DS` 注解所需）。
- **数据源**：仅在 `application-test.properties` 增加 `dm` 数据源（`jdbc:dm://LOCALHOST:5236?schema=MCPDB`），master 仍为 mysql 13306，两库共存；`spring.datasource.dynamic.strict=true` 不变。
- **开关**：`fscloud.api-log.enabled=true`（仅 test），队列参数为 `queue-capacity=10000`、`batch-size=200`、`flush-interval-ms=1000`、`max-endpoint-bytes=256`。local/prod 未配置 dm 数据源，相关代码通过 `@ConditionalOnProperty` 完全不加载。
- **表结构**：`ID` 由 `NUMBER(10,0)` 调整为 `NUMBER(19,0)`。MyBatis-Plus 全局 `ASSIGN_ID` 生成 19 位雪花 ID，原精度实测会报 `[-6102] Data overflow`。
- **记录方式**：后端 `ApiLogInterceptor` 拦截所有请求，记录 `request.getRequestURI()`；前端无需改动。排除 `/static/**`、`/mobile/**`、`/favicon.ico`、`/doc.html`、`/webjars/**`、`/v3/api-docs/**`、`/druid/**`、`/error` 及 `OPTIONS` 预检。业务线程只入有界队列，后台单线程每 1 秒或满 200 条批量写入 dm8，写库失败仅告警，不影响业务。
- **代码位置**：`snowy-plugin-fscloud/src/main/java/vip/xiaonuo/fscloud/` 下的 `modular/apilog/*`（实体/Mapper/Service）、`core/apilog/*`（拦截器/队列）、`config/ApiLogProperties.java`、`config/ApiLogConfigure.java`。
- **验证**：`mvn -DskipTests package` → `java -jar snowy-web-app/target/snowy-web-app-3.0.0.jar --spring.profiles.active=test` → 调用 `/auth/b/isLogin`、`/dev/config/sysBaseList` → 用 `disql` 查询 `API_LOG`，确认 URL 与访问时间已写入。注意 `application.properties` 默认 `spring.profiles.active=local`，验证时必须显式指定 `test`。
