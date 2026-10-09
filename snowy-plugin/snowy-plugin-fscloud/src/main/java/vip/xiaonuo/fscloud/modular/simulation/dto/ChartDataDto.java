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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结果图表数据 DTO（全量数据经降采样后的图表用行）
 *
 * @author fengyuhe
 * @date  2026/08/08
 */
@Data
public class ChartDataDto {

    @Schema(description = "数据集名称")
    private String dataset;

    @Schema(description = "图表数据行（已按 maxPoints 降采样）")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Schema(description = "源数据总行数")
    private Long total = 0L;

    @Schema(description = "实际返回行数")
    private Integer returned = 0;

    @Schema(description = "是否发生了降采样")
    private Boolean sampled = false;
}
