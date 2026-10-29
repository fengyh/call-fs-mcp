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
package vip.xiaonuo.fscloud.modular.apilog.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.xiaonuo.fscloud.modular.apilog.entity.ApiLog;
import vip.xiaonuo.fscloud.modular.apilog.mapper.ApiLogMapper;
import vip.xiaonuo.fscloud.modular.apilog.service.ApiLogService;

import java.util.List;

/**
 * API 访问日志 Service 实现，固定走 dm 数据源
 *
 * @author fengyuhe
 * @date  2026/10/29
 */
@Slf4j
@DS("dm")
@Service
public class ApiLogServiceImpl extends ServiceImpl<ApiLogMapper, ApiLog> implements ApiLogService {

    @Override
    public void saveLogs(List<ApiLog> logs) {
        if (CollectionUtil.isEmpty(logs)) {
            return;
        }
        // saveBatch 内部使用 BatchExecutor 直接走 sqlSessionFactory，因此 @DS 必须标在 Service 上
        this.saveBatch(logs);
    }
}
