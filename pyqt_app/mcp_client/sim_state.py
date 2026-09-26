# -*- coding: utf-8 -*-
"""FS 仿真运行状态模型（不依赖 Widget，便于单独测试与复用）

数据来源三条，共同写入同一份状态：

1. 仿真状态：MCP 接口返回的 `status`（loadModel / start / pause / resume / stop / reset）+ MQTT `status` 消息
2. 仿真进度：MQTT `progress` 消息的 `simTime`（仿真毫秒）→ `format_sim_time` 格式化为 `x天 hh:MM:ss.sss`；
   真实 `progress` 消息（见 `docs/McpClientTest.mqttx.export.json`）形如
   `{"event":"simulationProgress","status":"RUNNING","simTime":99159500,"msgType":"progress"}`
3. 控制台日志：MQTT `console` 消息

注：`docs/MqttClientTest.log.md` 里的 `progress`/`simTimeStep`/`totalStep` 是测试客户端伪造字段，
真实 FS 消息不含，本模块不使用。

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


def format_sim_time(ms):
    """仿真毫秒 → 时间串：从最高非零单位开始显示，前面为 0 的单位不显示

    毫秒固定 3 位，分/秒/时各 2 位；天数非零时带「天」字前缀。

    >>> format_sim_time(0)          # 全零，只留秒.毫秒
    '00.000'
    >>> format_sim_time(20000)      # 只有秒
    '20.000'
    >>> format_sim_time(72750)      # 只有分秒
    '01:12.750'
    >>> format_sim_time(3600000)    # 有时无天
    '01:00:00.000'
    >>> format_sim_time(99159500)   # 有天
    '1天 03:32:39.500'
    """
    try:
        total = int(ms)
    except (TypeError, ValueError):
        total = 0
    if total < 0:
        total = 0
    days, rem = divmod(total, 86_400_000)
    hours, rem = divmod(rem, 3_600_000)
    minutes, rem = divmod(rem, 60_000)
    seconds, millis = divmod(rem, 1_000)
    if days:
        return "%d天 %02d:%02d:%02d.%03d" % (days, hours, minutes, seconds, millis)
    if hours:
        return "%02d:%02d:%02d.%03d" % (hours, minutes, seconds, millis)
    if minutes:
        return "%02d:%02d.%03d" % (minutes, seconds, millis)
    return "%02d.%03d" % (seconds, millis)


class SimulationState(QObject):
    """FS 仿真运行状态：状态机、仿真时间进度、控制台日志"""

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
        self.sim_time = 0               # 仿真时间（仿真毫秒），来自 MQTT progress 消息
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
        """复位仿真进度（不清控制台日志）"""
        self.sim_time = 0
        self.last_message_at = 0

    def reset_sim_time(self):
        """只把仿真进度归零（重置仿真成功后调用），保留最近消息时间"""
        if self.sim_time == 0:
            return False
        self.sim_time = 0
        self.changed.emit()
        return True

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

    # ==================== 进度（仿真时间） ====================

    def update_from_mqtt_progress(self, payload):
        """处理 MQTT progress 消息，只取 simTime（仿真毫秒）作为进度

        真实消息形如：

        {"username":"pyqt-local","recordId":"pyqt-d9c045ae","modelId":"223fb9269181",
         "runId":"52cf3b86-...","uiLoaded":true,"event":"simulationProgress",
         "timestamp":1790342395550,"status":"STOPPED","simTime":72750,"msgType":"progress"}

        仅使用 simTime；仿真状态由 status 消息驱动，不在此处更新。
        """
        sim_time = payload.get("simTime")
        if isinstance(sim_time, (int, float)):
            self.sim_time = int(sim_time)
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
    def sim_time_text(self):
        """仿真进度显示：未加载模型为 '-'，否则为仿真时间串（x天 hh:MM:ss.sss）"""
        if not self.loaded:
            return "-"
        return format_sim_time(self.sim_time)

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
