/*
 Navicat Premium Dump SQL

 Source Server         : mysql-8.0
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:13306
 Source Schema         : fp_mysql

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 03/08/2026 09:53:25
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for fs_model_file
-- ----------------------------
DROP TABLE IF EXISTS `fs_model_file`;
CREATE TABLE `fs_model_file`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仿真模型名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '模型描述',
  `engine_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '引擎版本',
  `model_usage` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型用途',
  `user_id` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人名称',
  `image` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '预览图片路径/文件名',
  `model` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型文件路径/文件名',
  `model_company` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型所属公司',
  `model_factory` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属工厂',
  `model_warehouse` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所属仓库',
  `model_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型版本',
  `share_flag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '0' COMMENT '分享标注（见数据字典）',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  `sim_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SIM_STATUS_NOTYET' COMMENT '仿真状态',
  `sim_times` int NOT NULL DEFAULT 0 COMMENT '仿真次数',
  `model_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模型ID（加载模型后生成）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_name`(`name` ASC) USING BTREE,
  INDEX `idx_model_company`(`model_company` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2083912095054118914 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仿真模型文件表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of fs_model_file
-- ----------------------------
INSERT INTO `fs_model_file` VALUES (2083912095054118913, '仿真模型', '测试', '5.1.0', 'FS_USAGE_GENERAL', 1543837863788879871, '超管', '仿真云平台测试专用-v0.1-0723-1655.jpg', '仿真云平台测试专用-v0.1-0723-1655.fs', NULL, NULL, NULL, NULL, 'MODEL_SHARE_COMPANY', '2026-08-02 13:45:35', '2026-08-02 13:45:35', 'SIM_STATUS_STOPPED', 4, '19f100cb0667');

-- ----------------------------
-- Table structure for fs_sim_param_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `fs_sim_param_snapshot`;
CREATE TABLE `fs_sim_param_snapshot`  (
  `ID` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `MODEL_FILE_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型库记录ID（对应 MCP recordId）',
  `SNAPSHOT_AT` bigint NOT NULL COMMENT '快照时间（毫秒时间戳）',
  `PARAMETER_TABLES` json NULL COMMENT '参数表列表',
  `MCP_ROWS` json NULL COMMENT 'MCP 表中的参数表信息',
  `TABLES` json NULL COMMENT '各参数表内容（二维数组或对象数组）',
  `MISSING_TABLES` json NULL COMMENT '缺失的参数表列表',
  `CREATE_TIME` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `UPDATE_TIME` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`ID`) USING BTREE,
  INDEX `IDX_FS_SIM_PARAM_SNAPSHOT_MODEL`(`MODEL_FILE_ID` ASC) USING BTREE,
  INDEX `IDX_FS_SIM_PARAM_SNAPSHOT_AT`(`MODEL_FILE_ID` ASC, `SNAPSHOT_AT` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '仿真参数快照' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of fs_sim_param_snapshot
-- ----------------------------
INSERT INTO `fs_sim_param_snapshot` VALUES (1, '2083912095054118913', 1785680253740, NULL, '[{\"exists\": true, \"remark\": \"\", \"tableId\": \"8eca1be7b67e\", \"wholeNo\": \".订单表\", \"rowCount\": 2, \"rowIndex\": 0, \"tableName\": \".订单表\", \"columnCount\": 5}, {\"exists\": true, \"remark\": \"\", \"tableId\": \"9138d214d65e\", \"wholeNo\": \".工站参数\", \"rowCount\": 7, \"rowIndex\": 1, \"tableName\": \".工站参数\", \"columnCount\": 9}]', '{\"订单表\": {\"rows\": [[\"PartA\", \"汽车门锁总成A\", \"80\", \"0\", \"0\"], [\"PartB\", \"汽车门锁总成B\", \"20\", \"0\", \"0\"]], \"columns\": [{\"name\": \"MU类型\", \"type\": \"String\", \"index\": 0, \"width\": 84, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"名称\", \"type\": \"String\", \"index\": 1, \"width\": 153, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"数量\", \"type\": \"Int\", \"index\": 2, \"width\": 120, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"创建时间\", \"type\": \"Time\", \"index\": 3, \"width\": 160, \"format\": \"dd:hh:mm:ss.SSS\", \"indexColumn\": false}, {\"name\": \"优先级\", \"type\": \"Int\", \"index\": 4, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}]}, \"工站参数\": {\"rows\": [[\".预装\", \"Triangular(40,45,50)\", \"0.99\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"工人1\"], [\".压装\", \"Triangular(60,65,72)\", \"不适用\", \"7200\", \"600\", \"300\", \"不适用\", \"不适用\", \"工人2\"], [\".锁付1\", \"Triangular(32,35,40)\", \"0.995\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"工人3\"], [\".锁付2\", \"Triangular(32,35,40)\", \"0.995\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"工人3\"], [\".电检\", \"Triangular(65,70,78)\", \"0.95\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"工人4\"], [\".包装\", \"Triangular(36,40,45)\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"工人4\"], [\".返修工位\", \"Triangular(100,120,150)\", \"不适用\", \"不适用\", \"不适用\", \"不适用\", \"0.90\", \"1\", \"工人5\"]], \"columns\": [{\"name\": \"工站\", \"type\": \"String\", \"index\": 0, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"加工时长\", \"type\": \"String\", \"index\": 1, \"width\": 228, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"加工合格率\", \"type\": \"String\", \"index\": 2, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"MTBF\", \"type\": \"String\", \"index\": 3, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"MTTR\", \"type\": \"String\", \"index\": 4, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"换型\", \"type\": \"String\", \"index\": 5, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"返修成功率\", \"type\": \"String\", \"index\": 6, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"最多返修次\", \"type\": \"String\", \"index\": 7, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}, {\"name\": \"工人\", \"type\": \"String\", \"index\": 8, \"width\": 100, \"format\": \"General\", \"indexColumn\": false}]}}', '[]', '2026-08-02 22:17:34', '2026-08-02 22:17:34');

SET FOREIGN_KEY_CHECKS = 1;
