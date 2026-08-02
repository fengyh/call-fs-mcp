package vip.xiaonuo.fscloud.modular.simulation.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetAnimationEnabledParam {
    @Schema(description = "模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelId不能为空")
    private String modelId;

    @Schema(description = "是否开启动画", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Boolean enabled;
}
