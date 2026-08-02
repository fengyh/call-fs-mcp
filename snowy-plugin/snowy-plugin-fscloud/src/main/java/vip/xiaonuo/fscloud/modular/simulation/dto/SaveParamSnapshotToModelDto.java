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

import java.util.List;

/**
 * 参数快照写模型返回 DTO
 * <p>
 * 对应 MCP batchUpdateRemoteSimulationParameterTable 接口返回的 data 字段结构。
 *
 * @author fengyuhe
 * @date  2026/07/15
 */
@Data
public class SaveParamSnapshotToModelDto {

    /** 模型库记录 ID */
    @Schema(description = "模型库记录 ID")
    private String recordId;

    /** 模型 ID */
    @Schema(description = "模型 ID")
    private String modelId;

    /** 更新成功的表数量 */
    @Schema(description = "更新成功的表数量")
    private Integer updatedTableCount;

    /** 更新成功的总行数 */
    @Schema(description = "更新成功的总行数")
    private Integer updatedRowCount;

    /** 更新成功的表名列表 */
    @Schema(description = "更新成功的表名列表")
    private List<String> tableNames;

    /** 是否保存到模型文件 */
    @Schema(description = "是否保存到模型文件")
    private Boolean bSaveModel;

    /** 模型文件是否已保存 */
    @Schema(description = "模型文件是否已保存")
    private Boolean modelSaved;

    /** 模型文件路径 */
    @Schema(description = "模型文件路径")
    private String modelFilePath;
}
