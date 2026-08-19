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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分页查询完整对象位置快照参数
 *
 * @author fengyuhe
 * @date  2026/08/12
 */
@Data
public class GetObjectPositionSnapshotPageParam {

    @Schema(description = "模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "modelId不能为空")
    private String modelId;

    @Schema(description = "运行 ID，空串表示当前运行")
    private String runId = "";

    @Schema(description = "层 ID，默认 root")
    private String layerId = "root";

    @Schema(description = "仿真时间点（仿真毫秒），-1 表示查询当前时刻", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "simTime不能为空")
    @Min(-1)
    private Long simTime;

    @Schema(description = "快照 ID，首次请求传空串，后续分页必须原样回传防止漂移", defaultValue = "")
    private String snapshotId = "";

    @Schema(description = "实体类型：ALL / STATIC_OBJECT / LAYER / MU / WAREHOUSE / STORAGE_LOCATION", defaultValue = "ALL")
    private String entityKind = "ALL";

    @Schema(description = "分页偏移，默认 0", defaultValue = "0")
    @Min(0)
    private Integer offset = 0;

    @Schema(description = "分页大小 1-1000，默认 500", defaultValue = "500")
    @Min(1)
    @Max(1000)
    private Integer limit = 500;
}
