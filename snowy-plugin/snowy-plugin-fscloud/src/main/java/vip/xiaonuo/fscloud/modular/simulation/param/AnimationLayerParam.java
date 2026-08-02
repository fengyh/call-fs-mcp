package vip.xiaonuo.fscloud.modular.simulation.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnimationLayerParam {
    @Schema(description = "模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelId不能为空")
    private String modelId;
}
