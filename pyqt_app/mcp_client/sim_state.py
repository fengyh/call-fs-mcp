# -*- coding: utf-8 -*-
"""FS 仿真运行状态模型（不依赖 Widget，便于单独测试与复用）

数据来源两条，共同写入同一份状态：

1. MCP 接口返回：`loadModel` 返回的 `status`，以及 start/pause/resume/stop/reset 返回的 `status`
2. MQTT 实时消息：`factory/sim/{userId}/{modelId}/{status|progress|console}`

按钮文案与使能规则照搬前端
`snowy-admin-web/src/views/fscloud/simcontrol/index.vue` 的
`btn1Text` / `btn1Disabled` / `btn2Disabled` / `btn3Disabled`（第 280-311 行）。
"""
import time

from PyQt6.QtCore import QObject, pyqtSignal

# ==================== 状态常量 ====================
# 与前端 store/myModel.js 的 SIM_STATUS_* 取值一致
SIM_STATUS_NOTYET = "SIM_STATUS_NOTYET"
SIM_STATUS_WAITING = "SIM_STATUS_WAITING"
SIM_STATUS_GOING = "SIM_STATUS_GOING"
SIM_STATUS_PAUSED = "SIM_STATUS_PAUSED"
SIM_STATUS_FINISHED = "SIM_STATUS_FINISHED"

SIM_STATUS_LABEL = {
    SIM_STATUS_WAITING: "待仿真",
    SIM_STATUS_GOING: "仿真中",
    SIM_STATUS_PAUSED: "已暂停",
    SIM_STATUS_FINISHED: "仿真结束",
}

# MCP 状态 → 前端本地状态（同 myModel.updateStatus 的 map，另补 RESET：
# 前端映射表缺 RESET 会导致重置后按钮全灰，这里按“重置后回到待仿真”处理）
MCP_TO_SIM_STATUS = {
    "LOADED": SIM_STATUS_WAITING,
    "RUNNING": SIM_STATUS_GOING,
    "PAUSED": SIM_STATUS_PAUSED,
    "FINISHED": SIM_STATUS_FINISHED,
    "STOPPED": SIM_STATUS_FINISHED,
    "FAILED": SIM_STATUS_FINISHED,
    "RESET": SIM_STATUS_WAITING,
}

# MQTT status 消息的 state/status 取值（小写）→ MCP 状态
# 前端 simcontrol 读 data.status，Java MqttClientService 与 docs/MqttClientTest.log.md 读 state，两者都兼容
MQTT_STATE_TO_MCP_STATUS = {
    "loaded": "LOADED",
    "running": "RUNNING",
    "paused": "PAUSED",
    "finished": "FINISHED",
    "stopped": "STOPPED",
    "reset": "RESET",
    "failed": "FAILED",
}

MAX_CONSOLE_LOG = 1000   # 控制台日志条数上限（与前端 MAX_CONSOLE_LOG 一致）
MAX_CONSOLE_LINES = 10   # 单条日志最多保留行数（与前端一致）


def format_clock(timestamp_ms):
    """毫秒时间戳 → HH:MM:SS"""
    try:
        return time.strftime("%H:%M:%S", time.localtime(float(timestamp_ms) / 1000.0))
    except Exception:
        return "--:--:--"


def format_console_line(timestamp_ms, level, content):
    """一条控制台日志的显示文本"""
    return "[%s] [%s] %s" % (format_clock(timestamp_ms), level, content)


class SimulationState(QObject):
    """FS 仿真运行状态：状态机、进度、控制台日志"""

    changed = pyqtSignal()              # 状态/进度等派生量变化
    console_appended = pyqtSignal(str)  # 追加的一行控制台文本
    console_cleared = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.model_id = ""
        self.record_id = ""
        self.run_id = ""
        self.mcp_status = ""            # MCP 状态（大写）；空 = 未加载
        self.sim_status = ""            # SIM_STATUS_*；空 = 无
        self.sim_time_step = 0
        self.total_step = 0
        self.progress = 0.0             # 0-100
        self.last_message_at = 0        # 最近 MQTT 消息时间（ms）
        self.mqtt_connected = False
        self.console_logs = []          # [(timestamp_ms, level, content)]

    # ==================== 模型 ====================

    def set_model(self, model_id, record_id="", mcp_status="LOADED"):
        """加载成功后登记模型；model_id 为空视为未加载"""
        self.model_id = model_id or ""
        self.record_id = record_id or ""
        self.reset_runtime()
        self._apply_mcp_status(mcp_status or "LOADED")
        self.changed.emit()

    def clear_model(self):
        """卸载成功后清空状态"""
        self.model_id = ""
        self.record_id = ""
        self.run_id = ""
        self.mcp_status = ""
        self.sim_status = ""
        self.reset_runtime()
        self.changed.emit()

    def reset_runtime(self):
        """复位进度类数据（不清控制台日志）"""
        self.sim_time_step = 0
        self.total_step = 0
        self.progress = 0.0
        self.last_message_at = 0

    # ==================== 状态 ====================

    def _apply_mcp_status(self, mcp_status):
        mcp_status = str(mcp_status or "").upper()
        changed = mcp_status != self.mcp_status
        self.mcp_status = mcp_status
        self.sim_status = MCP_TO_SIM_STATUS.get(mcp_status, "")
        return changed

    def update_mcp_status(self, mcp_status):
        """用 MCP 接口返回的 status 更新（loadModel / start / pause / resume / stop / reset）"""
        if not self._apply_mcp_status(mcp_status):
            return False
        self.changed.emit()
        return True

    def update_from_mqtt_status(self, payload):
        """处理 MQTT status 消息：{"state"|"status": "running", "timestamp": ...}"""
        raw = str(payload.get("state") or payload.get("status") or "").lower()
        mcp_status = MQTT_STATE_TO_MCP_STATUS.get(raw)
        if not mcp_status:
            return False
        self._apply_mcp_status(mcp_status)
        self._touch_message(payload)
        self.changed.emit()
        return True

    # ==================== 进度 ====================

    def update_from_mqtt_progress(self, payload):
        """处理 MQTT progress 消息

        真实字段：{"progress": 12.5, "simTimeStep": 250, "totalStep": 2000, "timestamp": ...}
        兼容前端 simcontrol 使用的 {"timestamp", "simTime"} 命名。
        """
        step = payload.get("simTimeStep", payload.get("simTime"))
        total = payload.get("totalStep")
        progress = payload.get("progress", payload.get("simProgress"))
        if isinstance(step, (int, float)):
            self.sim_time_step = int(step)
        if isinstance(total, (int, float)):
            self.total_step = int(total)
        if isinstance(progress, (int, float)):
            self.progress = float(progress)
        elif self.total_step > 0:
            self.progress = round(self.sim_time_step * 100.0 / self.total_step, 1)
        self._touch_message(payload)
        self.changed.emit()
        return True

    def _touch_message(self, payload):
        stamp = payload.get("timestamp")
        self.last_message_at = int(stamp) if isinstance(stamp, (int, float)) else int(time.time() * 1000)

    # ==================== 控制台 ====================

    def add_console(self, level, content, timestamp=None):
        """追加一条控制台日志，返回格式化后的显示文本"""
        text = content or ""
        lines = text.splitlines() or [""]
        if len(lines) > MAX_CONSOLE_LINES:
            text = "\n".join(lines[:MAX_CONSOLE_LINES]) + "\n{...以下省略...}"
        level = str(level or "INFO").upper()
        stamp = int(timestamp) if isinstance(timestamp, (int, float)) else int(time.time() * 1000)
        self.console_logs.append((stamp, level, text))
        if len(self.console_logs) > MAX_CONSOLE_LOG:
            del self.console_logs[:len(self.console_logs) - MAX_CONSOLE_LOG]
        self.last_message_at = stamp
        line = format_console_line(stamp, level, text)
        self.console_appended.emit(line)
        self.changed.emit()
        return line

    def update_from_mqtt_console(self, payload):
        """处理 MQTT console 消息：{"level", "content", "timestamp"}"""
        return self.add_console(payload.get("level"), payload.get("content"), payload.get("timestamp"))

    def clear_console(self):
        self.console_logs = []
        self.console_cleared.emit()

    # ==================== 派生显示量 ====================

    @property
    def loaded(self):
        return bool(self.model_id)

    @property
    def status_label(self):
        if not self.loaded:
            return "未加载"
        return SIM_STATUS_LABEL.get(self.sim_status) or (self.mcp_status or "未知")

    @property
    def progress_text(self):
        if self.total_step > 0:
            return "%.1f%% (%d/%d 步)" % (self.progress, self.sim_time_step, self.total_step)
        if self.progress:
            return "%.1f%%" % self.progress
        return "-"

    @property
    def last_message_text(self):
        return format_clock(self.last_message_at) if self.last_message_at else "-"

    # ---- 按钮文案与动作（照搬 simcontrol 的 btn1Text/btn2Text/btn3Text）----

    @property
    def btn1_text(self):
        if self.sim_status == SIM_STATUS_GOING:
            return "⏸ 暂停"
        if self.sim_status == SIM_STATUS_PAUSED:
            return "⏵ 继续"
        return "▶ 开始"

    @property
    def btn1_action(self):
        """button1 当前应触发的动作：start / pause / resume"""
        if self.sim_status == SIM_STATUS_GOING:
            return "pause"
        if self.sim_status == SIM_STATUS_PAUSED:
            return "resume"
        return "start"

    @property
    def btn1_enabled(self):
        # 对应 simcontrol：状态为 NOTYET/ALREADY（本地即“没有已加载模型”）或无 modelId 时禁用
        return self.loaded

    @property
    def btn2_enabled(self):
        # 停止：仅仿真中/已暂停可用
        return self.sim_status in (SIM_STATUS_GOING, SIM_STATUS_PAUSED)

    @property
    def btn3_enabled(self):
        # 重置：仅待仿真/仿真结束可用
        return self.sim_status in (SIM_STATUS_WAITING, SIM_STATUS_FINISHED)
