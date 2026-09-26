# -*- coding: utf-8 -*-
"""页签5：通过 MCP 加载/关闭 FS 模型 + FS 仿真控制（MCP Server – Python 应用 最小验证）

- 左侧：本地 .fs 文件获取与关闭、MCP 会话状态、MCP 调用日志（小高度只读区）
- 右侧：MCP 模型操作 → FS 仿真控制（开始/暂停/继续、停止、重置、加速因子、开动画）
        → 实时状态与进度 → FS 控制台日志（MQTT 订阅）

模块职责划分（本文件只做界面装配与事件绑定）：

- 仿真数据处理：`mcp_client.sim_state.SimulationState`（状态机 / 进度 / 控制台缓存 / 按钮派生量）
- MQTT 订阅：`mcp_client.mqtt_client.MqttSubscriber`
- MCP 调用：`mcp_client.mcpClient.McpClient`，经 `mcp_client.mcp_runner.McpCallRunner` 在后台线程执行
  （读超时可达 60s，不能占用 UI 线程）
"""
import os
import uuid

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox, QFileDialog, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QPlainTextEdit, QMessageBox, QGridLayout, QSlider, QTabWidget,
)

from app_config import APP_DIR, mcp_settings, mqtt_settings, save_config
from mcp_client import McpClient
from mcp_client.mcp_runner import McpCallRunner
from mcp_client.mqtt_client import MqttSubscriber
from mcp_client.sim_state import SimulationState
from ui.sim_tabs import (
    TAB_TITLES, AnimationTab, ConsoleLogTab, ParameterTab, ResultTab,
)

# 后台调用 label → 中文动作名（日志与状态栏用）
ACTION_LABEL = {
    "load": "加载模型",
    "unload": "关闭模型",
    "refresh": "刷新已加载",
    "start": "启动仿真",
    "pause": "暂停仿真",
    "resume": "继续仿真",
    "stop": "停止仿真",
    "reset": "重置仿真",
    "accel": "设置加速因子",
    "anim": "开关动画",
    "sync": "读取动画状态",
}


class MCPModule(QWidget):
    """页签5：MCP 与 FS 集成验证 + 仿真控制"""

    mcp_log = pyqtSignal(str)   # McpClient / MqttSubscriber 日志回调 → 界面线程

    def __init__(self, main_window):
        super().__init__()
        self.model_file_name: str = ""
        self.main = main_window

        self.settings = mcp_settings(main_window.config)
        self.mqtt_config = mqtt_settings(main_window.config)
        self.runner = None
        self._busy = False

        self.state = SimulationState(self)   # 仿真状态/进度/控制台（含按钮派生量）

        # MCP 客户端（Java McpClientService 的 Python 移植，构造时注入连接配置）
        self.mcp = McpClient(
            endpoint_url=self.settings["endpoint_url"],
            api_key=self.settings["api_key"],
            log_hook=self.mcp_log.emit,
        )

        # MQTT 订阅（status / progress / console / animation/*）
        self.mqtt = MqttSubscriber(
            broker_host=self.mqtt_config["broker_host"],
            broker_port=self.mqtt_config["broker_port"],
            client_id_prefix=self.mqtt_config["client_id_prefix"],
            topic_prefix=self.mqtt_config["topic_prefix"],
            keepalive=self.mqtt_config["keepalive"],
            log_hook=self.mcp_log.emit,
            parent=self,
        )

        self._build_ui()                 # 子页签需要注入 mcp / mqtt
        self.mcp_log.connect(self._append_log)
        self.animation_tab.log_line.connect(self._append_log)

        self.mqtt.connected.connect(self._on_mqtt_connected)
        self.mqtt.disconnected.connect(self._on_mqtt_disconnected)
        self.mqtt.failed.connect(self._on_mqtt_failed)
        self.mqtt.status_received.connect(self.state.update_from_mqtt_status)
        self.mqtt.progress_received.connect(self.state.update_from_mqtt_progress)
        self.mqtt.console_received.connect(self.state.update_from_mqtt_console)

        # 状态模型 → 界面
        self.state.changed.connect(self._refresh_sim_ui)

        # 初始值（来自 app_config.json，可持久化）
        self.load_in_ui_chk.setChecked(bool(self.settings["load_in_ui"]))
        # 初始勾选不下发 MCP 调用（blockSignals 屏蔽 _on_animation_toggled）
        self.animate_chk.blockSignals(True)
        self.animate_chk.setChecked(bool(self.settings["show_animation"]))
        self.animate_chk.blockSignals(False)
        self.accelerate_slider.setValue(int(self.settings["accelerate_factor"]))
        self._on_accelerate_value(self.accelerate_slider.value())
        self.info_labels["user"].setText(str(self.settings["user_id"]))
        self.info_labels["endpoint"].setText(str(self.settings["endpoint_url"]))
        self._refresh_sim_ui()
        self._refresh_buttons()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 左侧：固定宽度 400（上：模型卡片固定高 200；下：MCP 调用日志）----
        left_widget = QWidget()
        left_widget.setFixedWidth(500)
        left = QVBoxLayout(left_widget)
        left.setContentsMargins(0, 0, 0, 0)
        left.setSpacing(10)

        # 模型卡片（固定高度 200）：本地文件 + MCP 会话状态
        card_model = QFrame()
        card_model.setObjectName("card")
        card_model.setFixedHeight(200)
        form = QGridLayout(card_model)
        form.setContentsMargins(16, 12, 16, 12)
        form.setHorizontalSpacing(8)
        form.setVerticalSpacing(8)
        form.setAlignment(Qt.AlignmentFlag.AlignTop)

        self.open_btn = QPushButton("📂加载模型")
        # 全局 QPushButton 有 16px 左右内边距，去掉左侧内边距才能和下方各行的标题左对齐
        self.open_btn.setStyleSheet("padding-left: 0px;")
        self.open_btn.clicked.connect(self._open_model)
        form.addWidget(self.open_btn, 0, 0,
                       Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.model_file_show = QLabel("尚未加载模型")
        self.model_file_show.setStyleSheet(
            "background: #334155; color: #E2E8F0; border-radius: 4px; padding: 4px 6px;")
        form.addWidget(self.model_file_show, 0, 1, 1, 3)

        self.info_labels = {
            "status": self._info_row(form, 1, "FS 状态"),
            "record": self._info_row(form, 2, "recordId"),
            "model": self._info_row(form, 3, "modelId"),
            "user": self._info_row(form, 4, "userId"),
            "endpoint": self._info_row(form, 5, "endpoint"),
        }
        left.addWidget(card_model)

        # 左侧下方：MCP 调用日志（小高度只读区）
        card_log = QFrame()
        card_log.setObjectName("card")
        log_box = QVBoxLayout(card_log)
        log_box.setContentsMargins(12, 10, 12, 10)
        log_box.setSpacing(6)
        log_head = QHBoxLayout()
        log_title = QLabel("MCP 调用日志")
        log_title.setStyleSheet("color:#94A3B8; font-size:12px;")
        log_head.addWidget(log_title)
        log_head.addStretch()
        clear_log_btn = QPushButton("清除")
        clear_log_btn.setFixedHeight(24)
        clear_log_btn.clicked.connect(lambda: self.log.clear())
        log_head.addWidget(clear_log_btn)
        log_box.addLayout(log_head)
        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        # self.log.setMaximumHeight(150)
        log_box.addWidget(self.log, 1)
        left.addWidget(card_log, 1)

        layout.addWidget(left_widget)

        # ---- 右侧：MCP 操作 + 仿真控制 + 实时状态 + 控制台日志 ----
        right_widget = QWidget()
        right = QVBoxLayout(right_widget)
        right.setContentsMargins(0, 0, 0, 0)
        right.setSpacing(8)

        title = QLabel("FS 仿真控制（MCP Server – Python 应用）")
        title.setStyleSheet("color:#94A3B8; font-size:12px;")
        right.addWidget(title)

        # 第一行：MCP 连接与模型操作
        btn_row = QHBoxLayout()
        btn_row.setSpacing(8)
        self.test_btn = QPushButton("🔌测试连接")
        self.test_btn.clicked.connect(self._test_connection)
        self.load_fs_btn = QPushButton("▶加载到 FS")
        self.load_fs_btn.setObjectName("primaryBtn")
        self.load_fs_btn.clicked.connect(self._load_to_fs)
        self.unload_fs_btn = QPushButton("■关闭 FS 模型")
        self.unload_fs_btn.clicked.connect(self._unload_from_fs)
        self.refresh_btn = QPushButton("↻刷新已加载")
        self.refresh_btn.clicked.connect(self._refresh_models)
        for btn in (self.test_btn, self.load_fs_btn, self.unload_fs_btn, self.refresh_btn):
            btn.setFixedHeight(34)
            btn_row.addWidget(btn)
        self.load_in_ui_chk = QCheckBox("加载到 FS 桌面 UI")
        self.load_in_ui_chk.toggled.connect(
            lambda checked: self._save_pref("load_in_ui", bool(checked)))
        btn_row.addWidget(self.load_in_ui_chk)
        btn_row.addStretch()
        right.addLayout(btn_row)

        # 第二行：仿真控制（开始/暂停/继续 共用 button1，停止 button2，重置 button3）
        ctrl_row = QHBoxLayout()
        ctrl_row.setSpacing(8)
        self.run_btn = QPushButton("▶ 开始")
        self.run_btn.setObjectName("primaryBtn")
        self.run_btn.clicked.connect(self._on_run_clicked)
        self.stop_btn = QPushButton("■ 停止")
        self.stop_btn.clicked.connect(self._stop_simulation)
        self.reset_btn = QPushButton("↻ 重置")
        self.reset_btn.clicked.connect(self._reset_simulation)
        for btn in (self.run_btn, self.stop_btn, self.reset_btn):
            btn.setFixedHeight(34)
            ctrl_row.addWidget(btn)

        self.animate_chk = QCheckBox("开动画")
        self.animate_chk.setToolTip(
            "改变时立即调用 setRemoteSimulationAnimationEnabled；\n"
            "启动仿真时同时作为 showAnimation 传入")
        self.animate_chk.toggled.connect(self._on_animation_toggled)
        ctrl_row.addWidget(self.animate_chk)

        ctrl_row.addSpacing(8)
        accel_title = QLabel("加速因子")
        accel_title.setStyleSheet("color:#94A3B8; font-size:12px;")
        ctrl_row.addWidget(accel_title)
        self.accelerate_slider = QSlider(Qt.Orientation.Horizontal)
        self.accelerate_slider.setMinimum(1)      # 与前端 a-slider 一致：1-1000
        self.accelerate_slider.setMaximum(1000)
        self.accelerate_slider.setFixedWidth(160)
        self.accelerate_slider.setToolTip("松开鼠标时下发 setRemoteSimulationAccelerateFactor")
        self.accelerate_slider.valueChanged.connect(self._on_accelerate_value)
        self.accelerate_slider.sliderReleased.connect(self._on_accelerate_released)
        ctrl_row.addWidget(self.accelerate_slider)
        self.accelerate_label = QLabel("50x")
        self.accelerate_label.setFixedWidth(48)
        self.accelerate_label.setStyleSheet("color:#E2E8F0; font-size:12px;")
        ctrl_row.addWidget(self.accelerate_label)
        ctrl_row.addStretch()
        right.addLayout(ctrl_row)

        # 第三行：实时状态（MQTT 推送）
        card_state = QFrame()
        card_state.setObjectName("card")
        state_grid = QGridLayout(card_state)
        state_grid.setContentsMargins(16, 10, 16, 10)
        state_grid.setHorizontalSpacing(16)
        state_grid.setVerticalSpacing(4)
        self.sim_info_labels = {
            "status": self._info_row(state_grid, 0, "仿真状态", column=0),
            "progress": self._info_row(state_grid, 0, "仿真进度", column=2),
            "mqtt": self._info_row(state_grid, 1, "MQTT", column=0),
            "last": self._info_row(state_grid, 1, "最近消息", column=2),
        }
        # right.addWidget(card_state)
        left.addWidget(card_state)

        # 第四行：右下四个子页签（控制台日志 / 仿真动画 / 仿真参数 / 仿真结果）
        self.sim_tabs = QTabWidget()
        tab_widgets = [
            ConsoleLogTab(self.main, self.state),
            AnimationTab(self.main, self.state, mcp=self.mcp, mqtt=self.mqtt),
            ParameterTab(self.main, self.state),
            ResultTab(self.main, self.state),
        ]
        self.tabs = []
        for title, tab in zip(TAB_TITLES, tab_widgets):
            self.sim_tabs.addTab(tab, title)
            self.tabs.append(tab)
        self.console_tab = self.tabs[0]      # 「控制台日志」，自包含处理 MQTT console 消息
        self.animation_tab = self.tabs[1]    # 「仿真动画」，注入 MCP / MQTT 依赖
        right.addWidget(self.sim_tabs, 1)

        layout.addWidget(right_widget, 1)

    def _info_row(self, grid, row, title, column=0):
        """状态信息一行：标题 + 值（值可选中复制）"""
        key = QLabel(title)
        key.setStyleSheet("color:#94A3B8; font-size:12px;")
        value = QLabel("-")
        value.setWordWrap(True)
        value.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        grid.addWidget(key, row, column, Qt.AlignmentFlag.AlignTop)
        grid.addWidget(value, row, column + 1, 1, 3)
        return value

    # ==================== 本地模型文件 ====================

    def _open_model(self):
        if self.open_btn.text() == "❌关闭模型":
            if self.state.loaded:
                QMessageBox.information(
                    self, "请先关闭 FS 模型",
                    "模型仍加载在 FS 中（modelId=%s）。\n"
                    "请先点击「■关闭 FS 模型」，再关闭本地文件。" % self.state.model_id)
                return
            self.model_file_name = ""
            self.model_file_show.setText("尚未加载模型")
            self.open_btn.setText("📂加载模型")
            self._append_log("[本地] 已关闭模型文件")
        else:
            path, _ = QFileDialog.getOpenFileName(
                self, "FS仿真模型", APP_DIR, "FS 文件 (*.fs);")
            if path:
                self.model_file_name = path
                self.model_file_show.setText(os.path.basename(path))
                self.open_btn.setText("❌关闭模型")
                self._append_log("[本地] 已选择模型文件: %s" % path)
        self._refresh_buttons()

    # ==================== MCP 模型操作 ====================

    def _test_connection(self):
        """initialize + notifications/initialized + tools/list（无副作用）"""
        self._append_log("[测试] 连接 %s …" % self.settings["endpoint_url"])
        self._run("test", lambda: (self.mcp.initializeSession(), self.mcp.listTools()))

    def _load_to_fs(self):
        """在 FS 中加载所选模型：loadModel(userId, recordId, modelFileName, loadInUI)"""
        if not self.model_file_name:
            QMessageBox.warning(self, "参数错误", "请先选择本地 .fs 模型文件")
            return
        if self.state.loaded:
            QMessageBox.warning(self, "已加载模型",
                                "FS 中已有本用户加载的模型（modelId=%s），请先关闭。"
                                % self.state.model_id)
            return
        path = os.path.abspath(self.model_file_name)
        if not os.path.isfile(path):
            QMessageBox.warning(self, "参数错误", "模型文件不存在:\n%s" % path)
            return

        # 本地无模型库，recordId 由 Python 侧生成（Java 版来自 fs_model_file 表主键）
        record_id = "pyqt-" + uuid.uuid4().hex[:8]
        user_id = self.settings["user_id"]
        load_in_ui = self.load_in_ui_chk.isChecked()
        self._append_log("[加载] 请求 FS 加载: userId=%s recordId=%s loadInUI=%s"
                         % (user_id, record_id, load_in_ui))
        self._run("load",
                  lambda: self.mcp.loadModel(user_id, record_id, path, load_in_ui))

    def _unload_from_fs(self):
        """在 FS 中卸载模型：unloadModel(userId, modelId)"""
        if not self.state.loaded:
            return
        user_id = self.settings["user_id"]
        model_id = self.state.model_id
        self._append_log("[关闭] 请求 FS 卸载: userId=%s modelId=%s" % (user_id, model_id))
        self._run("unload", lambda: self.mcp.unloadModel(user_id, model_id))

    def _refresh_models(self):
        """listModels(userId)：列出 FS 进程内已加载模型，并同步本地状态"""
        user_id = self.settings["user_id"]
        self._append_log("[刷新] 查询 FS 已加载模型 …")
        self._run("refresh", lambda: self.mcp.listModels(user_id))

    # ==================== 仿真控制（MCP） ====================

    def _require_loaded(self, action):
        """仿真控制前置检查：未加载模型或调用进行中时给出提示（不再静默忽略）"""
        if not self.state.loaded:
            self._append_log("[%s] 未加载模型，已忽略" % action)
            self.main.set_status("请先「加载到 FS」再执行%s" % action, ok=False)
            return False
        if self._busy:
            self._append_log("[%s] 上一次 MCP 调用未结束，已忽略" % action)
            self.main.set_status("MCP 调用中，请稍候", ok=False)
            return False
        return True

    def _on_run_clicked(self):
        """button1：按当前状态分派 启动/暂停/继续（同前端 btn1Click 按文本分派）"""
        if not self._require_loaded("启动/暂停/继续"):
            return
        user_id = self.settings["user_id"]
        model_id = self.state.model_id
        action = self.state.btn1_action
        if action == "pause":
            self._append_log("[暂停] 请求 FS 暂停仿真: userId=%s modelId=%s" % (user_id, model_id))
            self._run("pause", lambda: self.mcp.pauseSimulation(user_id, model_id))
        elif action == "resume":
            self._append_log("[继续] 请求 FS 继续仿真: userId=%s modelId=%s" % (user_id, model_id))
            self._run("resume", lambda: self.mcp.resumeSimulation(user_id, model_id))
        else:
            # 需求 1.5：启动前先按界面值同步 FS 的动画与加速因子，再启动仿真
            show_animation = self.animate_chk.isChecked()
            factor = int(self.accelerate_slider.value())
            self._append_log("[启动] 请求 FS 同步动画/加速因子并启动: userId=%s modelId=%s "
                             "showAnimation=%s accelerateFactor=%s"
                             % (user_id, model_id, show_animation, factor))
            self._run("start", lambda: self._start_sequence(
                user_id, model_id, show_animation, factor))

    def _start_sequence(self, user_id, model_id, show_animation, factor):
        """启动仿真前置序列（需求 1.5）：①同步动画 → ②同步加速因子 → ③启动仿真

        三步在同一后台线程内串行执行，保证先后顺序（`_busy` 机制下无法连续发起多次调用）；
        前置两步业务失败不阻断启动，仅连接层异常（McpServiceException）会中断序列。
        """
        return {
            "animation": self.mcp.setAnimationEnabled(user_id, model_id, show_animation),
            "accelerate": self.mcp.setAccelerateFactor(user_id, model_id, factor),
            "start": self.mcp.startSimulation(user_id, model_id, show_animation),
        }

    def _stop_simulation(self):
        """button2：停止仿真"""
        if not self._require_loaded("停止仿真"):
            return
        user_id = self.settings["user_id"]
        model_id = self.state.model_id
        self._append_log("[停止] 请求 FS 停止仿真: userId=%s modelId=%s" % (user_id, model_id))
        self._run("stop", lambda: self.mcp.stopSimulation(user_id, model_id))

    def _reset_simulation(self):
        """button3：重置仿真（对齐前端 simcontrol 的 doReset）"""
        if not self._require_loaded("重置仿真"):
            return
        user_id = self.settings["user_id"]
        model_id = self.state.model_id
        self._append_log("[重置] 请求 FS 重置仿真: userId=%s modelId=%s" % (user_id, model_id))
        self._run("reset", lambda: self.mcp.resetSimulation(user_id, model_id))

    def _on_accelerate_value(self, value):
        self.accelerate_label.setText("%dx" % value)

    def _on_accelerate_released(self):
        """加速因子 slider：鼠标放开时下发（同前端 a-slider 的 afterChange）"""
        value = self.accelerate_slider.value()
        self._save_pref("accelerate_factor", int(value))
        if not self.state.loaded:
            self._append_log("[加速因子] 未加载模型，仅记录本地值 %dx" % value)
            return
        user_id = self.settings["user_id"]
        model_id = self.state.model_id
        self._run("accel", lambda: self.mcp.setAccelerateFactor(user_id, model_id, int(value)))

    # ==================== 动画开关（MCP） ====================

    def _on_animation_toggled(self, checked):
        """「开动画」勾选变化：保存本地偏好；已加载模型时立即调用 MCP 开关动画

        对应前端 snowy-admin-web/src/views/fscloud/simcontrol/index.vue 565-573
        的 watch(showAnimationEnabled)。
        """
        self._save_pref("show_animation", bool(checked))
        if not self.state.loaded:
            self._append_log("[动画] 未加载模型，仅记录本地值 enabled=%s" % bool(checked))
            return
        user_id = self.settings["user_id"]
        model_id = self.state.model_id
        self._append_log("[动画] 请求 FS 开关动画: userId=%s modelId=%s enabled=%s"
                         % (user_id, model_id, bool(checked)))
        self._run("anim",
                  lambda: self.mcp.setAnimationEnabled(user_id, model_id, bool(checked)))

    def _sync_animation_from(self, data, source):
        """从 FS 返回的数据回读 animationEnabled，同步界面勾选（不再下发 MCP 调用）"""
        if not isinstance(data, dict) or "animationEnabled" not in data:
            return False
        enabled = bool(data.get("animationEnabled"))
        if self.animate_chk.isChecked() != enabled:
            self._append_log("[动画] %s：FS 当前动画=%s，同步界面勾选" % (source, enabled))
        self.animate_chk.blockSignals(True)
        self.animate_chk.setChecked(enabled)
        self.animate_chk.blockSignals(False)
        self._save_pref("show_animation", enabled)
        return True

    def _save_pref(self, key, value):
        """把界面偏好写回 app_config.json 的 mcp 段"""
        self.main.config.setdefault("mcp", {})[key] = value
        save_config(self.main.config)

    # ==================== MQTT ====================

    def _sync_mqtt_subscription(self):
        """按当前是否已加载模型启动/停止 MQTT 订阅"""
        user_id = self.settings["user_id"]
        if self.state.loaded:
            if (not self.mqtt.running) or self.mqtt.model_id != self.state.model_id:
                self.mqtt.start(user_id, self.state.model_id)
        else:
            if self.mqtt.running:
                self.mqtt.stop()

    def _on_mqtt_connected(self):
        self.state.mqtt_connected = True
        self.state.changed.emit()

    def _on_mqtt_disconnected(self):
        self.state.mqtt_connected = False
        self.state.changed.emit()

    def _on_mqtt_failed(self, message):
        self._append_log("[MQTT] %s" % message)
        self.main.set_status("MQTT 异常", ok=False)

    # ==================== 后台调用 ====================

    def _run(self, label, fn):
        if self._busy:
            self._append_log("[忙] 上一次 MCP 调用未结束，本次「%s」已忽略"
                             % ACTION_LABEL.get(label, label))
            return
        if self.runner is not None:
            self.runner.wait(1000)   # 确保上一个后台线程已退出，避免被回收时仍在运行
        self._busy = True
        self._refresh_buttons()
        self.main.set_status("MCP 调用中…（%s）" % ACTION_LABEL.get(label, label))
        self.runner = McpCallRunner(label, fn, parent=self)
        self.runner.log_line.connect(self._append_log)
        self.runner.done.connect(self._on_done)
        self.runner.failed.connect(self._on_failed)
        self.runner.start()

    def _on_done(self, label, result):
        self._busy = False
        try:
            if label == "test":
                self._handle_test(result)
            elif label == "load":
                self._handle_load(result)
            elif label == "unload":
                self._handle_unload(result)
            elif label == "refresh":
                self._handle_refresh(result)
            elif label == "start":
                self._handle_start_sequence(result)
            elif label in ("pause", "resume", "stop", "reset"):
                self._handle_sim_control(label, result)
            elif label == "accel":
                self._handle_accelerate(result)
            elif label == "anim":
                self._handle_animation(result)
            elif label == "sync":
                self._handle_sync(result)
        finally:
            self._sync_mqtt_subscription()
            self._refresh_sim_ui()
            self._refresh_buttons()

    def _on_failed(self, label, message):
        self._busy = False
        self._append_log("[%s] 调用失败 → %s" % (ACTION_LABEL.get(label, label), message))
        self.main.set_status("MCP 调用失败", ok=False)
        self._refresh_buttons()

    # ==================== 结果处理 ====================

    def _handle_test(self, result):
        init_resp, tools_resp = result
        server = ((init_resp or {}).get("result") or {}).get("serverInfo") or {}
        tools = ((tools_resp or {}).get("result") or {}).get("tools") or []
        names = [t.get("name") for t in tools]
        self._append_log("[测试] MCP Server: %s %s，工具数 %d"
                         % (server.get("name", "?"), server.get("version", "?"), len(names)))
        for want in ("loadRemoteSimulationModel", "unloadRemoteSimulationModel",
                     "startRemoteSimulation", "pauseRemoteSimulation",
                     "resumeRemoteSimulation", "stopRemoteSimulation",
                     "resetRemoteSimulation", "setRemoteSimulationAccelerateFactor"):
            self._append_log("  %s %s" % ("✔" if want in names else "✘", want))
        self.main.set_status("MCP 连接正常（%d 个工具）" % len(names))

    def _handle_load(self, result):
        if not result.get("success", False):
            self._append_log("[加载] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "加载失败")))
            self.main.set_status("FS 模型加载失败", ok=False)
            return
        data = result.get("data") or {}
        model_id = data.get("modelId") or ""
        self.state.set_model(model_id, data.get("recordId") or "", data.get("status") or "LOADED")
        self._append_log("[加载] 成功 → modelId=%s status=%s"
                         % (model_id, self.state.mcp_status))
        self._append_log("        modelFilePath=%s" % data.get("modelFilePath", ""))
        if not model_id:
            self._append_log("[警告] 响应未返回 modelId，无法执行仿真控制与卸载")
            self.main.set_status("FS 模型已加载（缺少 modelId）", ok=False)
            return
        self.main.set_status("FS 模型已加载: %s" % os.path.basename(
            data.get("modelFilePath") or self.model_file_name))
        # 回读 FS 侧动画状态；loadModel 未返回 animationEnabled 时补一次 getStatus
        user_id = self.settings["user_id"]
        if not self._sync_animation_from(data, "加载模型"):
            self._run("sync", lambda: self.mcp.getStatus(user_id, model_id))

    def _handle_unload(self, result):
        if not result.get("success", False):
            self._append_log("[关闭] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "卸载失败")))
            self.main.set_status("FS 模型卸载失败", ok=False)
            return
        self._append_log("[关闭] 成功 → 已从 FS 卸载 modelId=%s" % self.state.model_id)
        self.state.clear_model()
        self.animation_tab.reset()
        self.main.set_status("FS 模型已关闭")

    def _handle_refresh(self, result):
        if not result.get("success", False):
            self._append_log("[刷新] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "查询失败")))
            self.main.set_status("查询已加载模型失败", ok=False)
            return
        models = (result.get("data") or {}).get("models") or []
        if not models:
            self._append_log("[刷新] FS 当前没有已加载模型")
        for model in models:
            self._append_log("[刷新] userId=%s recordId=%s modelId=%s status=%s"
                             % (model.get("userId"), model.get("recordId"),
                                model.get("modelId"), model.get("status")))
        # FS 侧状态可能与本地不一致（被其他客户端卸载/加载）
        ids = {m.get("modelId") for m in models}
        if self.state.loaded and self.state.model_id not in ids:
            self._append_log("[刷新] 本地记录的模型已不在 FS 中，状态复位")
            self.state.clear_model()
        elif not self.state.loaded and models:
            first = models[0]
            self.state.set_model(first.get("modelId") or "", first.get("recordId") or "",
                                 first.get("status") or "LOADED")
            self._append_log("[刷新] 已同步本地状态到 FS 中的模型")
        if models:
            self._sync_animation_from(models[0], "刷新已加载")
        self.main.set_status("已加载模型 %d 个" % len(models))

    def _handle_sim_control(self, label, result):
        """start / pause / resume / stop / reset 的业务响应处理"""
        action = ACTION_LABEL.get(label, label)
        if not result.get("success", False):
            self._append_log("[%s] 失败 → %s: %s"
                             % (action, result.get("code", "?"),
                                result.get("message", "操作失败")))
            self.main.set_status("%s 失败" % action, ok=False)
            return
        data = result.get("data") or {}
        status = data.get("status")
        if data.get("runId"):
            self.state.run_id = data["runId"]
        if status:
            self.state.update_mcp_status(status)
        else:
            self.state.changed.emit()
        if label == "reset":
            # 重置成功后本地仿真进度立即归零（进度只由 MQTT progress.simTime 驱动）
            self.state.reset_sim_time()
        if label == "start":
            # 需求 1.7：启动成功后开始接收该模型的动画流（层目录 + root 层帧）
            self.animation_tab.on_simulation_started(self.settings["user_id"],
                                                     self.state.model_id)
        self._append_log("[%s] 成功 → status=%s message=%s"
                         % (action, status or "-", data.get("message", "")))
        self.main.set_status("%s 完成（%s）" % (action, self.state.status_label))

    def _handle_start_sequence(self, results):
        """启动序列结果：先在日志呈现前置两步，再按 start 的业务响应更新状态机"""
        animation = results.get("animation") or {}
        accelerate = results.get("accelerate") or {}
        anim_data = animation.get("data") or {}
        accel_data = accelerate.get("data") or {}
        self._log_start_step("①动画", animation,
                             "animationEnabled=%s" % anim_data.get("animationEnabled"))
        self._log_start_step("②加速因子", accelerate,
                             "accelerateFactor=%s" % accel_data.get("accelerateFactor"))
        self._handle_sim_control("start", results.get("start") or {})

    def _log_start_step(self, name, result, detail):
        """打印启动前置步骤结果；失败只警告、不阻断启动"""
        if result.get("success", False):
            self._append_log("[启动] 前置%s → 成功（%s）" % (name, detail))
        else:
            self._append_log("[启动] 前置%s → 失败 %s: %s（忽略，继续启动）"
                             % (name, result.get("code", "?"),
                                result.get("message", "设置失败")))

    def _handle_accelerate(self, result):
        if not result.get("success", False):
            self._append_log("[加速因子] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "设置失败")))
            self.main.set_status("加速因子设置失败", ok=False)
            return
        data = result.get("data") or {}
        factor = data.get("accelerateFactor", self.accelerate_slider.value())
        self._append_log("[加速因子] 已设置为 %sx" % factor)
        self.main.set_status("加速因子 %sx" % factor)

    def _handle_animation(self, result):
        """setRemoteSimulationAnimationEnabled 的业务响应处理"""
        if not result.get("success", False):
            self._append_log("[动画] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "开关动画失败")))
            self.main.set_status("动画开关失败", ok=False)
            return
        data = result.get("data") or {}
        enabled = data.get("animationEnabled", self.animate_chk.isChecked())
        self._append_log("[动画] 成功 → FS 动画=%s streaming=%s"
                         % (enabled, data.get("animationStreaming")))
        self._sync_animation_from(data, "开关动画")
        self.main.set_status("动画已%s" % ("开启" if enabled else "关闭"))

    def _handle_sync(self, result):
        """getStatus 回读：加载模型后 FS 未返回 animationEnabled 时补充读取"""
        if not result.get("success", False):
            self._append_log("[动画] 回读 FS 动画状态失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "查询失败")))
            return
        self._sync_animation_from(result.get("data") or {}, "读取状态")

    # ==================== 界面同步 ====================

    def _update_info(self):
        self.info_labels["status"].setText(self.state.mcp_status or "未加载")
        color = "#22C55E" if self.state.loaded else "#94A3B8"
        self.info_labels["status"].setStyleSheet("color:%s; font-weight:600;" % color)
        self.info_labels["record"].setText(self.state.record_id or "-")
        self.info_labels["model"].setText(self.state.model_id or "-")

    def _refresh_sim_ui(self):
        """仿真状态变化 → 刷新状态行与三个控制按钮（文案 + 使能）"""
        busy = self._busy
        self.run_btn.setText(self.state.btn1_text)
        self.run_btn.setEnabled(not busy and self.state.btn1_enabled)
        self.stop_btn.setEnabled(not busy and self.state.btn2_enabled)
        self.reset_btn.setEnabled(not busy and self.state.btn3_enabled)
        # 禁用原因可视化（前端规则：RUNNING/PAUSED 不可重置，需先「停止」）
        self.run_btn.setToolTip("" if self.state.btn1_enabled else "请先「加载到 FS」")
        self.stop_btn.setToolTip("" if self.state.btn2_enabled else "仅仿真中/已暂停时可停止")
        self.reset_btn.setToolTip(
            "" if self.state.btn3_enabled
            else "仅待仿真/仿真结束时可重置；仿真中/已暂停请先「停止」")

        self.sim_info_labels["status"].setText(self.state.status_label)
        self.sim_info_labels["progress"].setText(self.state.sim_time_text)
        self.sim_info_labels["mqtt"].setText("已连接" if self.state.mqtt_connected else "未连接")
        self.sim_info_labels["last"].setText(self.state.last_message_text)
        status_color = {
            "仿真中": "#22C55E",
            "已暂停": "#EAB308",
            "仿真结束": "#38BDF8",
        }.get(self.state.status_label, "#94A3B8")
        self.sim_info_labels["status"].setStyleSheet("color:%s; font-weight:600;" % status_color)
        self.sim_info_labels["mqtt"].setStyleSheet(
            "color:%s;" % ("#22C55E" if self.state.mqtt_connected else "#94A3B8"))
        self._update_info()

    def _refresh_buttons(self):
        busy = self._busy
        has_file = bool(self.model_file_name)
        loaded = self.state.loaded
        self.test_btn.setEnabled(not busy)
        self.load_fs_btn.setEnabled(not busy and has_file and not loaded)
        self.unload_fs_btn.setEnabled(not busy and loaded)
        self.refresh_btn.setEnabled(not busy)
        self.load_in_ui_chk.setEnabled(not busy)
        self.animate_chk.setEnabled(not busy)
        self.accelerate_slider.setEnabled(not busy)
        self._refresh_sim_ui()

    def _append_log(self, line):
        self.log.appendPlainText(line)

    # ==================== 退出清理 ====================

    def shutdown(self):
        """关闭窗口时断开 MQTT、等待后台线程退出"""
        try:
            self.mqtt.stop()
        except Exception:
            pass
        if self.runner is not None and self.runner.isRunning():
            self.runner.wait(2000)
