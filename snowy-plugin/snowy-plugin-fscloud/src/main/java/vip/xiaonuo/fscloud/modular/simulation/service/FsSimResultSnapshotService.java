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
package vip.xiaonuo.fscloud.modular.simulation.service;

import vip.xiaonuo.fscloud.modular.simulation.dto.ResultDatasetPageDto;
import vip.xiaonuo.fscloud.modular.simulation.dto.ResultSnapshotDto;

import java.util.List;

/**
 * 仿真结果快照 Service
 *
 * @author fengyuhe
 * @date  2026/08/05
 */
public interface FsSimResultSnapshotService {

    /**
     * 保存结果快照（成对落库）
     * <p>
     * 后端直连 MCP 拉取结果总览 + 参数快照 + 各 dataset 全量数据，
     * 事务内先落成对参数快照（RUN_ID 非空），再按 RUN_ID upsert 结果快照。
     * partCycleTime 写 CSV 文件，其余 dataset 数据入 DATASETS JSON。
     *
     * @return 结果快照 ID
     */
    Long saveResultSnapshot(String userId, String modelId, String runId);

    /** 查询某模型的所有结果快照列表（不含数据明细） */
    List<ResultSnapshotDto> listByModelFileId(String modelFileId);

    /** 查询单个结果快照详情（含总览与数据集元信息） */
    ResultSnapshotDto getById(Long id);

    /** 按页读取结果快照某 dataset 的数据（小表从 DATASETS JSON，partCycleTime 从 CSV 文件） */
    ResultDatasetPageDto getDatasetPage(Long id, String dataset, int limit, int offset);

    /** 删除结果快照（连带删除成对参数快照与 partCycleTime 文件） */
    void deleteById(Long id);
}
