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
package vip.xiaonuo.fscloud.modular.simulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 仿真结果快照实体
 *
 * @author fengyuhe
 * @date  2026/08/05
 */
@Data
@TableName("fs_sim_result_snapshot")
public class FsSimResultSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仿真运行 ID（MCP runId），唯一 */
    private String runId;

    /** 模型库记录 ID（MCP recordId） */
    private String modelFileId;

    /** 模型文件名 */
    private String modelFileName;

    /** 仿真总时长（毫秒） */
    private Long simTime;

    /** 仿真开始时间戳（毫秒） */
    private Long startedAt;

    /** 仿真结束时间戳（毫秒） */
    private Long finishedAt;

    /** 结果状态（READY等） */
    private String resultStatus;

    /** 结果快照时间戳（毫秒），落库/更新时间 */
    private Long snapshotAt;

    /** 总览结果（JSON：taktTime/uph/utilRate/leanMetrics/energyAnalysis） */
    private String summaryResults;

    /** 数据集明细（JSON：{ [dataset]: { total, columns, rows } }，不含 partCycleTime） */
    private String datasets;

    /** partCycleTime 数据文件名（大表落文件，相对路径） */
    private String partCycleTimeFile;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
