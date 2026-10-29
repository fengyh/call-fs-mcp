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
package vip.xiaonuo.fscloud.modular.apilog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * API 访问日志实体（达梦库 MCPDB.API_LOG）
 *
 * @author fengyuhe
 * @date  2026/10/29
 */
@Data
@TableName("API_LOG")
public class ApiLog {

    /** 主键，雪花 ID 需要 DM 列为 NUMBER(19,0) */
    @TableId(value = "ID", type = IdType.ASSIGN_ID)
    private Long id;

    /** 访问时间，DM 列为 ACCESSTIME DATETIME(0) */
    @TableField("ACCESSTIME")
    private Date accessTime;

    /** 访问的接口地址，DM 列为 ENDPOINT VARCHAR(256) */
    @TableField("ENDPOINT")
    private String endpoint;
}
