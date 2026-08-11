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
 * 数据库结果快照数据集分页读取 DTO
 *
 * @author fengyuhe
 * @date  2026/08/05
 */
@Data
public class ResultDatasetPageDto {

    @Schema(description = "数据集名称")
    private String dataset;

    @Schema(description = "本页数据行")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Schema(description = "总行数")
    private Long total = 0L;

    @Schema(description = "本页起始偏移")
    private Integer offset = 0;

    @Schema(description = "本页大小")
    private Integer limit = 25;

    @Schema(description = "列元信息（columns 顺序与 title，可用于渲染表头）")
    private List<Map<String, Object>> columns;
}
