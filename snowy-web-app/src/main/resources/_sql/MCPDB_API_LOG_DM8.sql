-- ----------------------------
-- 达梦V8, 建表: API_LOG
--     数据源: 在snowy-web-app/src/main/resources/application-test.properties中配置
-- ----------------------------
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


