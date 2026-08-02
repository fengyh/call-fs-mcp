/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 */
package vip.xiaonuo.fscloud.modular.simulation.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 快照 ID 参数
 */
@Data
public class SnapshotIdParam {
    @Schema(description = "快照 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id不能为空")
    private Long id;
}
