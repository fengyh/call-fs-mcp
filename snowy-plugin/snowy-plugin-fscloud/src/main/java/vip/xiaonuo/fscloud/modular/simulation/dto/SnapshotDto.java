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
import java.util.Date;

/**
 * 参数快照 DTO
 *
 * @author fengyuhe
 * @date  2026/07/13
 */
@Data
public class SnapshotDto {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "模型库记录 ID")
    private String modelFileId;

    @Schema(description = "快照时间戳")
    private Long snapshotAt;

    @Schema(description = "参数表列表（JSON）")
    private String parameterTables;

    @Schema(description = "MCP 表信息（JSON）")
    private String mcpRows;

    @Schema(description = "参数表内容（JSON，列表查询时可能为空）")
    private String tables;

    @Schema(description = "缺失表列表（JSON）")
    private String missingTables;

    @Schema(description = "创建时间")
    private Date createTime;
}
