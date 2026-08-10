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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 仿真结果快照 DTO（列表项与详情）
 *
 * @author fengyuhe
 * @date  2026/08/05
 */
@Data
public class ResultSnapshotDto {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "仿真运行 ID（MCP runId）")
    private String runId;

    @Schema(description = "模型库记录 ID")
    private String modelFileId;

    @Schema(description = "模型文件名")
    private String modelFileName;

    @Schema(description = "仿真总时长(毫秒)")
    private Long simTime;

    @Schema(description = "仿真开始时间戳")
    private Long startedAt;

    @Schema(description = "仿真结束时间戳")
    private Long finishedAt;

    @Schema(description = "结果状态")
    private String resultStatus;

    @Schema(description = "结果快照时间戳（落库/更新时间）")
    private Long snapshotAt;

    @Schema(description = "总览结果（详情返回；列表可能为空）")
    private Map<String, Object> summaryResults;

    @Schema(description = "数据集元信息列表（名称/行数；详情返回，不含数据）")
    private List<Map<String, Object>> datasets;

    @Schema(description = "partCycleTime 数据文件名")
    private String partCycleTimeFile;

    @Schema(description = "创建时间")
    private Date createTime;
}
