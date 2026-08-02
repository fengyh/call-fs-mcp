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
import lombok.Data;

/**
 * 参数快照写模型参数
 * <p>
 * 前端传入模型 ID 和 tablesJson，后端调用 MCP 工具 batchUpdateRemoteSimulationParameterTable 写入模型。
 *
 * @author fengyuhe
 * @date  2026/07/15
 */
@Data
public class SaveParamSnapshotToModelParam {

    /** MCP 模型 ID */
    @Schema(description = "MCP 模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelId不能为空")
    private String modelId;

    /** 参数表数据 JSON 字符串（符合 MCP batchUpdateRemoteSimulationParameterTable 的 tablesJson 格式） */
    @Schema(description = "参数表数据 JSON 字符串", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "tablesJson不能为空")
    private String tablesJson;

    /** 是否保存到 .fs 模型文件 */
    @Schema(description = "是否保存到 .fs 模型文件，默认 true")
    private Boolean bSaveModel = true;
}
