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
package vip.xiaonuo.fscloud.modular.simulation.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存参数快照入参
 *
 * @author fengyuhe
 * @date  2026/07/13
 */
@Data
public class SaveSnapshotParam {

    /** 模型库记录 ID */
    @Schema(description = "模型库记录 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelFileId不能为空")
    private String modelFileId;

    /** 参数表列表（JSON 字符串） */
    @Schema(description = "参数表列表（JSON）")
    private String parameterTables;

    /** MCP 表信息（JSON 字符串） */
    @Schema(description = "MCP 表信息（JSON）")
    private String mcpRows;

    /** 各参数表内容（JSON 字符串，必填） */
    @Schema(description = "各参数表内容（JSON）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "tables不能为空")
    private String tables;

    /** 缺失表列表（JSON 字符串） */
    @Schema(description = "缺失表列表（JSON）")
    private String missingTables;
}
