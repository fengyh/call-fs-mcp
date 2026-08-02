/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 */
package vip.xiaonuo.fscloud.modular.simulation.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 快照列表查询参数
 */
@Data
public class SnapshotListParam {
    @Schema(description = "模型库记录 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelFileId不能为空")
    private String modelFileId;
}
