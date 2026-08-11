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

import vip.xiaonuo.fscloud.modular.simulation.dto.SnapshotDto;
import vip.xiaonuo.fscloud.modular.simulation.entity.FsSimParamSnapshot;
import vip.xiaonuo.fscloud.modular.simulation.param.SaveSnapshotParam;

import java.util.List;

/**
 * 仿真参数快照 Service
 *
 * @author fengyuhe
 * @date  2026/07/13
 */
public interface FsSimParamSnapshotService {

    /** 保存快照 */
    Long saveSnapshot(SaveSnapshotParam param);

    /** 成对落库（与结果快照成对）：按 (modelFileId, runId) upsert，返回快照 ID */
    Long savePairedSnapshot(String modelFileId, String runId, String parameterTables,
                            String mcpRows, String tables, String missingTables);

    /** 按 runId 查询成对参数快照（无则返回 null） */
    FsSimParamSnapshot getByRunId(String modelFileId, String runId);

    /** 查询某模型的所有快照列表（不含 tables 内容） */
    List<SnapshotDto> listByModelFileId(String modelFileId);

    /** 查询单个快照的完整内容 */
    SnapshotDto getById(Long id);

    /** 删除快照 */
    void deleteById(Long id);

    /** 按模型+运行删除成对参数快照（删除结果快照时连带） */
    void deleteByModelAndRunId(String modelFileId, String runId);
}
