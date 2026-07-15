/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 *
 * Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *
 * 1.请不要删除和修改根目录下的LICENSE文件。
 * 2.请不要删除和修改Snowy源码头部的版权声明。
 * 3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 * 4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 * 5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 * 6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
package vip.xiaonuo.fscloud.modular.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 获取参数快照返回 DTO
 * <p>
 * 对应 MCP getRemoteSimulationParameterSnapshot 接口返回的 data 字段结构。
 *
 * @author fengyuhe
 * @date  2026/07/13
 */
@Data
public class GetParameterSnapshotDto {

    /** 模型库记录 ID */
    @Schema(description = "模型库记录 ID")
    private String recordId;

    /** 模型 ID */
    @Schema(description = "模型 ID")
    private String modelId;

    /** 模型文件名 */
    @Schema(description = "模型文件名")
    private String modelFileName;

    /** 模型状态 */
    @Schema(description = "模型状态")
    private String status;

    /** 快照时间戳 */
    @Schema(description = "快照时间戳")
    private Long snapshotAt;

    /** MCP 配置表名称（默认 "mcp"） */
    @Schema(description = "MCP 配置表名称")
    private String mcpTableName;

    /** 是否找到 MCP 配置表 */
    @Schema(description = "是否找到 MCP 配置表")
    private Boolean mcpTableFound;

    /** MCP 配置表行数据 */
    @Schema(description = "MCP 配置表行数据")
    private List<Object> mcpRows;

    /** 参数表数据列表 */
    @Schema(description = "参数表数据列表")
    private List<Object> tables;

    /** 缺失的表名称列表 */
    @Schema(description = "缺失的表名称列表")
    private List<String> missingTables;
}
