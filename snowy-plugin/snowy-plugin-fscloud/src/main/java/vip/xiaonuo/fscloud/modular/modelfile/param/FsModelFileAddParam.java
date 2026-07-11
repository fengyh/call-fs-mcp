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
package vip.xiaonuo.fscloud.modular.modelfile.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 仿真模型库添加参数
 *
 * @author fengyuhe
 * @date  2026/06/04 21:42
 **/
@Getter
@Setter
public class FsModelFileAddParam {

    /** 仿真模型名称 */
    @Schema(description = "仿真模型名称")
    private String name;

    /** 模型描述 */
    @Schema(description = "模型描述")
    private String description;

    /** 引擎版本 */
    @Schema(description = "引擎版本")
    private String engineVersion;

    /** 模型用途 */
    @Schema(description = "模型用途")
    private String modelUsage;

    /** 创建人ID */
    @Schema(description = "创建人ID")
    private Long userId;

    /** 创建人名称 */
    @Schema(description = "创建人名称")
    private String userName;

    /** 模型所属公司 */
    @Schema(description = "模型所属公司")
    private String modelCompany;

    /** 所属工厂 */
    @Schema(description = "所属工厂")
    private String modelFactory;

    /** 所属仓库 */
    @Schema(description = "所属仓库")
    private String modelWarehouse;

    /** 模型版本 */
    @Schema(description = "模型版本")
    private String modelVersion;

    /** 分享标识：0-不分享，1-分享 */
    @Schema(description = "分享标识：0-不分享，1-分享")
    private Integer shareFlag;

    /** 仿真状态（字典编码 SIM_STATUS），默认 SIM_STATUS_NOTYET */
    @Schema(description = "仿真状态")
    private String simStatus = "SIM_STATUS_NOTYET";

    /** 仿真次数 */
    @Schema(description = "仿真次数")
    private Integer simTimes = 0;

}
