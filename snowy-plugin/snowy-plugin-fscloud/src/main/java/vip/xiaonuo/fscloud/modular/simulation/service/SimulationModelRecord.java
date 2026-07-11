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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 仿真模型跟踪记录
 * <p>
 * 对应 SimulationModelTrackingMap 中每个 value 的结构，
 * 用于跟踪已加载到 FS 云仿真服务的模型状态。
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationModelRecord {

    /** fs_model_file 表主键 ID */
    private Long recordId;

    /** 用户标识 */
    private String userId;

    /** MCP 模型 ID */
    private String modelId;

    /** 模型文件名 */
    private String modelFileName;

    /** 模型文件完整路径 */
    private String modelFilePath;

    /** 模型状态：LOADED / RUNNING / PAUSED / FINISHED / STOPPED / FAILED */
    private String status;

    /** 运行 ID */
    private String runId;

    /** 仿真时间（微秒） */
    private Long simTime;

    /** 仿真进度百分比（0-100） */
    private Double simProgress;

    /** 状态描述消息 */
    private String message;

    /** 加载时间戳 */
    private Long loadedAt;

    /** 启动时间戳 */
    private Long startedAt;

    /** 结束时间戳 */
    private Long finishedAt;

    /** 控制台日志，最多保留 100 条 */
    private List<ConsoleLogEntry> consoleLog;

    /** 控制台日志条目 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsoleLogEntry {
        /** 日志级别：INFO / WARN / ERROR */
        private String level;
        /** 日志内容 */
        private String content;
        /** 时间戳 */
        private Long timestamp;
    }

    /** 添加一条控制台日志，超过 100 条删除最旧的 */
    public void addConsoleLog(String level, String content, Long timestamp) {
        if (consoleLog == null) {
            consoleLog = new ArrayList<>();
        }
        consoleLog.add(new ConsoleLogEntry(level, content, timestamp));
        if (consoleLog.size() > 100) {
            consoleLog.remove(0);
        }
    }
}
