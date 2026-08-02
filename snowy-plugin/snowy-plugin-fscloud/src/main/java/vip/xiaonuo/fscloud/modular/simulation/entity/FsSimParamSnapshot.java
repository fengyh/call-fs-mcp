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
 * 仿真参数快照实体
 *
 * @author fengyuhe
 * @date  2026/07/13
 */
@Data
@TableName("fs_sim_param_snapshot")
public class FsSimParamSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型库记录 ID（对应 MCP recordId） */
    private String modelFileId;

    /** 快照时间（毫秒时间戳） */
    private Long snapshotAt;

    /** 参数表列表（JSON） */
    private String parameterTables;

    /** MCP 表中的参数表信息（JSON） */
    private String mcpRows;

    /** 各参数表内容（JSON） */
    private String tables;

    /** 缺失的参数表列表（JSON） */
    private String missingTables;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}
