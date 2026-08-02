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
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加载模型参数
 * <p>
 * 前端传入 fs_model_file 表的 id，controller 根据 id 查询模型文件名称，
 * 并使用 fscloudUploadStaticPath 拼接完整路径，最终调用 MCP 加载模型。
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Data
public class LoadModelParam {

    /** fs_model_file 表主键 ID */
    @Schema(description = "fs_model_file 表主键 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id不能为空")
    private Long id;
    @Schema(description = "是否加载模型到仿真引擎UI", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "loadInUI不能为空")
    private Boolean loadInUI;
}
