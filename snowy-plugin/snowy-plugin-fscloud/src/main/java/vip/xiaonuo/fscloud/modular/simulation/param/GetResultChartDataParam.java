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
 * 获取结果图表数据入参
 *
 * <p>两种数据源二选一：
 * <ul>
 *   <li>实时（当前仿真）：传 modelId（+ runId），后端走 MCP 循环分页拉全量；</li>
 *   <li>历史（已落库快照）：传 id，后端从结果快照的 DATASETS JSON / partCycleTime CSV 读全量。</li>
 * </ul>
 *
 * @author fengyuhe
 * @date  2026/08/08
 */
@Data
public class GetResultChartDataParam {

    @Schema(description = "结果快照主键 ID（历史快照数据源）；为空时按 modelId/runId 走 MCP 实时数据")
    private Long id;

    @Schema(description = "MCP 模型 ID（实时数据源必填）")
    private String modelId;

    @Schema(description = "运行 ID，为空时获取最后一次")
    private String runId;

    @Schema(description = "数据集名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "dataset不能为空")
    private String dataset;

    @Schema(description = "图表最大点数，超出按等距抽样降采样（默认 2000，范围 2-20000）")
    private Integer maxPoints = 2000;

    @Schema(description = "图表 x 轴列名（如 timeMs）。给定后降采样以「同一个 x 值的全部行」为最小单位，" +
            "保证同一时间点上的多个点（功率曲线等时间序列的垂直跳变）不被拆散")
    private String xColumn;
}
