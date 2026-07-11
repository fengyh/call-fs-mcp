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
 * 加载模型返回 DTO
 * <p>
 * 对应 MCP loadRemoteSimulationModel 接口返回的 data 字段结构。
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Data
public class LoadModelDto {

    /** 用户 ID */
    @Schema(description = "用户 ID")
    private String userId;

    /** record ID */
    @Schema(description = "模型库记录 ID")
    private String recordId;

    /** 模型 ID（MCP 分配的唯一标识） */
    @Schema(description = "模型 ID")
    private String modelId;

    /** 模型文件名 */
    @Schema(description = "模型文件名")
    private String modelFileName;

    /** 模型名称（前端传入的原始模型名，用于展示） */
    @Schema(description = "模型名称")
    private String modelName;

    /** 模型文件在 FS 服务器上的完整路径 */
    @Schema(description = "模型文件完整路径")
    private String modelFilePath;

    /** 模型状态（LOADED / RUNNING / PAUSED / FINISHED / STOPPED / FAILED） */
    @Schema(description = "模型状态")
    private String status;

    /** 仿真运行时间（微妙） */
    @Schema(description = "仿真运行时间（微妙）")
    private Long simTime;

    /** 状态描述消息 */
    @Schema(description = "状态描述消息")
    private String message;

    /** 模型加载时间戳 */
    @Schema(description = "模型加载时间戳")
    private Long loadedAt;

    /** 仿真启动时间戳（未启动时为 0） */
    @Schema(description = "仿真启动时间戳")
    private Long startedAt;

    /** 仿真结束时间戳（未结束时为 0） */
    @Schema(description = "仿真结束时间戳")
    private Long finishedAt;
}
