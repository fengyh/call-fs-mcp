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
package vip.xiaonuo.fscloud.modular.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 获取仿真结果快照返回 DTO
 * <p>
 * 对应 MCP getRemoteSimulationResultSnapshot 接口返回的 data 字段。
 * 顶层字段使用具体类型，嵌套结构使用通用 Map/List 兜底。
 *
 * @author fengyuhe
 * @date  2026/07/22
 */
@Data
public class GetResultSnapshotDto {

    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "运行 ID")
    private String runId;

    @Schema(description = "模型库记录 ID")
    private String recordId;

    @Schema(description = "MCP 模型 ID")
    private String modelId;

    @Schema(description = "模型文件名")
    private String modelFileName;

    @Schema(description = "仿真状态")
    private String status;

    @Schema(description = "仿真总时长")
    private Long simTime;

    @Schema(description = "仿真启动时间戳")
    private Long startedAt;

    @Schema(description = "仿真结束时间戳")
    private Long finishedAt;

    @Schema(description = "总产出数")
    private Integer totalOutput;

    @Schema(description = "每小时产出率")
    private Double throughputPerHour;

    @Schema(description = "当前在制品数量")
    private Integer currentWip;

    @Schema(description = "参数快照时间戳")
    private Long parameterSnapshotAt;

    @Schema(description = "结果状态")
    private String resultStatus;

    @Schema(description = "结果快照时间戳")
    private Long resultSnapshotAt;

    @Schema(description = "汇总结果（taktTime/uph/utilRate/leanMetrics/energyAnalysis）")
    private Map<String, Object> summaryResults;

    @Schema(description = "可用数据集列表")
    private List<Map<String, Object>> datasets;
}
