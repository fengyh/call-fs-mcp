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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 查询时间轴对象位置快照参数
 *
 * @author fengyuhe
 * @date  2026/08/10
 */
@Data
public class GetObjectPositionSnapshotParam {

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
}
