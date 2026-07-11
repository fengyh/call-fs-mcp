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

/**
 * 已加载模型信息 DTO
 * <p>
 * 由 getLoadedModel 接口返回，合并 tracking map 和 DB 中的模型信息。
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Data
public class GetLoadedModelDto {

    /** fs_model_file 表主键 ID */
    @Schema(description = "模型库记录 ID")
    private Long recordId;

    /** 模型名称（来自 DB） */
    @Schema(description = "模型名称")
    private String name;

    /** 模型文件路径（来自 DB） */
    @Schema(description = "模型文件路径")
    private String model;

    /** MCP 模型 ID（来自 tracking map） */
    @Schema(description = "MCP 模型 ID")
    private String modelId;

    /** MCP 状态（来自 tracking map） */
    @Schema(description = "MCP 状态")
    private String status;

    /** 本地仿真状态（由 MCP status 映射而来） */
    @Schema(description = "本地仿真状态")
    private String simStatus;

    /** 仿真次数（来自 DB） */
    @Schema(description = "仿真次数")
    private Integer simTimes;

    /** 运行 ID（来自 tracking map） */
    @Schema(description = "运行 ID")
    private String runId;

    /** 仿真时间（来自 tracking map） */
    @Schema(description = "仿真时间")
    private Long simTime;

    /** 仿真进度百分比（来自 tracking map） */
    @Schema(description = "仿真进度百分比")
    private Double simProgress;
}
