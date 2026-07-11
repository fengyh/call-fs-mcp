/*
 Navicat Premium Dump SQL

 Source Server         : mysql-8.0
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:13306
 Source Schema         : gh_mysql

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 11/07/2026 15:32:23
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
) ENGINE = InnoDB AUTO_INCREMENT = 2074056518734450691 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仿真模型文件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of fs_model_file
-- ----------------------------
INSERT INTO `fs_model_file` VALUES (2073362175811944449, '测试fscloud', '测试fscloud的模型', '5.1.0', 'FS_USAGE_GENERAL', 1543837863788879871, '超管', 'step-by-step.jpg', 'step-by-step.fs', NULL, NULL, NULL, NULL, 'MODEL_SHARE_PUBLIC', '2026-07-04 19:03:58', '2026-07-04 19:03:58', 'SIM_STATUS_ALREADY', 37, '389a125effc3');
INSERT INTO `fs_model_file` VALUES (2073730755808202753, '<html><u>仿真模型</u></html>', '全自动化加工线+立体仓库+AGV运输系统', '5.1.0', 'FS_USAGE_SELF', 1543837863788879871, '超管', 'ProductionLine.jpg', 'ProductionLine.fs', NULL, NULL, NULL, NULL, 'MODEL_SHARE_COMPANY', '2026-07-05 19:28:34', '2026-07-05 19:28:34', 'SIM_STATUS_GOING', 9, 'b8c05d3be9e6');
INSERT INTO `fs_model_file` VALUES (2073774368411881474, '测试模型库、仿真控制', '测试模型库、仿真控制', '5.1.0', 'FS_USAGE_TEST', 1543837863788879871, '超管', 'Conveyor.jpg', 'Conveyor.fs', NULL, NULL, NULL, NULL, 'MODEL_SHARE_PUBLIC', '2026-07-05 22:21:52', '2026-07-05 22:21:52', 'SIM_STATUS_ALREADY', 4, '5433bcb9437c');
INSERT INTO `fs_model_file` VALUES (2074043302742593537, '测试模型-UI', '', '5.1.0', 'FS_USAGE_TEST', 1543837863788879871, '超管', 'UI.jpg', 'UI.fs', NULL, NULL, NULL, NULL, 'MODEL_SHARE_PUBLIC', '2026-07-06 16:10:31', '2026-07-06 16:10:31', 'SIM_STATUS_NOTYET', 0, NULL);
INSERT INTO `fs_model_file` VALUES (2074056518734450690, '仿真模型6666666666', '', '5.1.0', 'FS_USAGE_GENERAL', 1543837863788879871, '超管', 'CarProductionLine.jpg', 'CarProductionLine.fs', NULL, NULL, NULL, NULL, 'MODEL_SHARE_COMPANY', '2026-07-06 17:03:02', '2026-07-06 17:03:02', 'SIM_STATUS_NOTYET', 0, NULL);

SET FOREIGN_KEY_CHECKS = 1;
