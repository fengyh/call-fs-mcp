package vip.xiaonuo.fscloud.modular.simulation.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelectAnimationLayerParam {
    @Schema(description = "模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelId不能为空")
    private String modelId;

    @Schema(description = "层 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "layerId不能为空")
    private String layerId;

    @Schema(description = "帧率 1-10", defaultValue = "5")
    @NotNull
    @Min(1)
    private Integer frameRate = 5;
}
