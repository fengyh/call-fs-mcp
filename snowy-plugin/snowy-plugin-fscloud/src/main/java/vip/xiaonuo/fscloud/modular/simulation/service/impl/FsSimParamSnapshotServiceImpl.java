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
package vip.xiaonuo.fscloud.modular.simulation.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.fscloud.modular.simulation.dto.SnapshotDto;
import vip.xiaonuo.fscloud.modular.simulation.entity.FsSimParamSnapshot;
import vip.xiaonuo.fscloud.modular.simulation.mapper.FsSimParamSnapshotMapper;
import vip.xiaonuo.fscloud.modular.simulation.param.SaveSnapshotParam;
import vip.xiaonuo.fscloud.modular.simulation.service.FsSimParamSnapshotService;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 仿真参数快照 Service 实现
 *
 * @author fengyuhe
 * @date  2026/07/13
 */
@Slf4j
@Service
public class FsSimParamSnapshotServiceImpl
        extends ServiceImpl<FsSimParamSnapshotMapper, FsSimParamSnapshot>
        implements FsSimParamSnapshotService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSnapshot(SaveSnapshotParam param) {
        FsSimParamSnapshot entity = new FsSimParamSnapshot();
        entity.setModelFileId(param.getModelFileId());
        entity.setSnapshotAt(System.currentTimeMillis());
        entity.setParameterTables(param.getParameterTables());
        entity.setMcpRows(param.getMcpRows());
        entity.setTables(param.getTables());
        entity.setMissingTables(param.getMissingTables());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        this.save(entity);
        log.info("Snapshot saved: id={}, modelFileId={}", entity.getId(), param.getModelFileId());
        return entity.getId();
    }

    @Override
    public List<SnapshotDto> listByModelFileId(String modelFileId) {
        LambdaQueryWrapper<FsSimParamSnapshot> qw = new LambdaQueryWrapper<>();
        qw.eq(FsSimParamSnapshot::getModelFileId, modelFileId);
        qw.orderByDesc(FsSimParamSnapshot::getSnapshotAt);
        List<FsSimParamSnapshot> list = this.list(qw);
        return list.stream().map(e -> {
            SnapshotDto dto = BeanUtil.toBean(e, SnapshotDto.class);
            // 列表场景不返回 tables 内容（减少传输量）
            dto.setTables(null);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public SnapshotDto getById(Long id) {
        FsSimParamSnapshot entity = super.getById(id);
        if (entity == null) {
            throw new CommonException("快照不存在，id={}", id);
        }
        SnapshotDto dto = new SnapshotDto();
        dto.setId(entity.getId());
        dto.setModelFileId(entity.getModelFileId());
        dto.setSnapshotAt(entity.getSnapshotAt());
        dto.setParameterTables(entity.getParameterTables());
        dto.setMcpRows(entity.getMcpRows());
        dto.setTables(entity.getTables());
        dto.setMissingTables(entity.getMissingTables());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        if (super.getById(id) == null) {
            throw new CommonException("快照不存在，id={}", id);
        }
        this.removeById(id);
        log.info("Snapshot deleted: id={}", id);
    }
}
