# -*- coding: utf-8 -*-
"""「仿真动画」子页签：MQTT JPEG 帧显示 + 层级切换 + 高清 SVG + 程序内全屏

操作逻辑参照 `snowy-admin-web/src/views/fscloud/simcontrol/index.vue`：

- 层级条：一层一个按钮，`root` 显示为「主模型」，按 `root` 最前的顺序在一行内排列
- 帧：MQTT `factory/sim/{userId}/{modelId}/animation/{layerId}/frame`（二进制 JPEG），
  只显示当前活动层的帧
- 高清：勾选「显示高清静态图」→ 先继续显示 JPEG，异步 `getSvgPicture` 取到后替换显示
- 切换层：`selectRemoteSimulationAnimationLayer(userId, modelId, layerId, 5)`，高清开启时重取 SVG
- 全屏：单击动画区域（或用工具行按钮）进入程序内全屏，Esc 恢复
"""
from PyQt6.QtCore import QByteArray, QSize, Qt, pyqtSignal
from PyQt6.QtGui import QColor, QKeySequence, QPainter, QPixmap, QShortcut
from PyQt6.QtSvg import QSvgRenderer
from PyQt6.QtWidgets import (
    QCheckBox, QFrame, QHBoxLayout, QLabel, QPushButton, QScrollArea, QVBoxLayout, QWidget,
)

from mcp_client.mcp_runner import McpCallRunner
from ui.sim_tabs.animation_view import BACK_BLACK, BACK_LIGHT, AnimationView

DEFAULT_FRAME_RATE = 5    # 与前端一致（selectAnimationLayer 的 frameRate）


class AnimationTab(QWidget):
    """仿真动画（MQTT 动画流 + 层级切换 + 高清 SVG + 全屏）"""

    log_line = pyqtSignal(str)      # 转发到 MCP 调用日志

    def __init__(self, main_window=None, state=None, mcp=None, mqtt=None, parent=None):
        super().__init__(parent)
        self.main = main_window
        self.state = state
        self.mcp = mcp              # McpClient（取层目录 / 切层 / 取 SVG）
        self.mqtt = mqtt            # MqttSubscriber（动画帧与状态）

        self.user_id = ""
        self.model_id = ""
        self.layers = []
        self.active_layer = "root"
        self.streaming = False
        self.frame_rate = DEFAULT_FRAME_RATE

        self._jpeg_pixmap = None    # 最近一帧 JPEG
        self._svg_pixmap = None     # 最近一张高清 SVG 渲染结果
        self._layers_runner = None
        self._select_runner = None
        self._svg_runner = None
        self._fullscreen = False
        self._esc_shortcut = None

        self._build_ui()
        self._connect_mqtt()
        self._update_stream_label()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        tool_row = QHBoxLayout()
        tool_row.setSpacing(8)
        self.stream_label = QLabel("未开始")
        self.stream_label.setStyleSheet("color:#64748B; font-size:11px;")
        tool_row.addWidget(self.stream_label)
        tool_row.addStretch()
        self.hd_chk = QCheckBox("显示高清静态图")
        self.hd_chk.setToolTip("勾选后取 FS 当前层的 SVG 高清图；取消则回到 JPEG 动画帧")
        self.hd_chk.toggled.connect(self._on_hd_toggled)
        tool_row.addWidget(self.hd_chk)
        self.full_btn = QPushButton("🗖 全屏")
        self.full_btn.setFixedHeight(24)
        self.full_btn.setToolTip("单击动画区域也可全屏；全屏时 Esc 恢复")
        self.full_btn.clicked.connect(self._toggle_fullscreen)
        tool_row.addWidget(self.full_btn)
        layout.addLayout(tool_row)

        # 层级条：一行内排列（超出横向滚动）
        self.layer_scroll = QScrollArea()
        self.layer_scroll.setWidgetResizable(True)
        self.layer_scroll.setFixedHeight(36)
        self.layer_scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.layer_scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.layer_scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.layer_bar = QWidget()
        self.layer_bar_layout = QHBoxLayout(self.layer_bar)
        self.layer_bar_layout.setContentsMargins(0, 0, 0, 0)
        self.layer_bar_layout.setSpacing(4)
        self.layer_scroll.setWidget(self.layer_bar)
        self.layer_scroll.setVisible(False)
        layout.addWidget(self.layer_scroll)

        self.view = AnimationView()
        self.view.clicked.connect(self.enter_fullscreen)
        self.view.fullscreen_changed.connect(self._on_fullscreen_changed)
        layout.addWidget(self.view, 1)

    def _connect_mqtt(self):
        if self.mqtt is None:
            return
        self.mqtt.animation_frame.connect(self._on_frame)
        self.mqtt.animation_layers.connect(self._on_layers)
        self.mqtt.animation_state.connect(self._on_anim_state)

    # ==================== 对外入口 ====================

    def on_simulation_started(self, user_id, model_id):
        """仿真启动成功后调用（mcp_module 的「开始」成功分支）"""
        self.user_id = user_id or ""
        self.model_id = model_id or ""
        self.active_layer = "root"
        self.streaming = False
        self._jpeg_pixmap = None
        self._svg_pixmap = None
        self.view.clear()
        self._log("[动画] 开始接收动画流: userId=%s modelId=%s（默认 root 层）"
                  % (self.user_id, self.model_id))
        self.refresh_layers()
        self._update_stream_label()

    def reset(self):
        """模型卸载后清空动画状态"""
        if self._fullscreen:
            self.exit_fullscreen()
        self.user_id = ""
        self.model_id = ""
        self.layers = []
        self.active_layer = "root"
        self.streaming = False
        self.frame_rate = DEFAULT_FRAME_RATE
        self._jpeg_pixmap = None
        self._svg_pixmap = None
        self._rebuild_layer_bar()
        self._update_stream_label()
        self.view.clear()

    # ==================== MCP 调用（后台线程） ====================

    def refresh_layers(self):
        """取层目录：listRemoteSimulationAnimationLayers"""
        if not (self.mcp and self.user_id and self.model_id):
            return
        if self._layers_runner is not None and self._layers_runner.isRunning():
            return
        user_id, model_id = self.user_id, self.model_id
        self._layers_runner = McpCallRunner(
            "anim_layers", lambda: self.mcp.listAnimationLayers(user_id, model_id), parent=self)
        self._layers_runner.done.connect(self._on_layers_loaded)
        self._layers_runner.failed.connect(
            lambda _label, msg: self._log("[动画] 取层目录失败：%s" % msg))
        self._layers_runner.start()

    def switch_layer(self, layer_id):
        """切换活动层：selectRemoteSimulationAnimationLayer(..., frameRate=5)"""
        if not (self.mcp and self.user_id and self.model_id and layer_id):
            return
        if self._select_runner is not None and self._select_runner.isRunning():
            return
        if layer_id == self.active_layer:
            if self.hd_chk.isChecked():
                self.load_svg()
            return
        user_id, model_id = self.user_id, self.model_id
        self._log("[动画] 切换层 → %s" % layer_id)
        self._select_runner = McpCallRunner(
            "anim_layer",
            lambda: self.mcp.selectAnimationLayer(user_id, model_id, layer_id,
                                                  DEFAULT_FRAME_RATE),
            parent=self)
        self._select_runner.done.connect(
            lambda _label, result, lid=layer_id: self._on_layer_selected(lid, result))
        self._select_runner.failed.connect(
            lambda _label, msg: self._log("[动画] 切换层失败：%s" % msg))
        self._select_runner.start()

    def load_svg(self):
        """取当前层的高清 SVG 静态图：getSvgPicture"""
        if not (self.mcp and self.user_id and self.model_id):
            return
        if self._svg_runner is not None and self._svg_runner.isRunning():
            return
        user_id, model_id, layer_id = self.user_id, self.model_id, self.active_layer
        self._svg_runner = McpCallRunner(
            "anim_svg", lambda: self.mcp.getSvgPicture(user_id, model_id, layer_id), parent=self)
        self._svg_runner.done.connect(self._on_svg_loaded)
        self._svg_runner.failed.connect(
            lambda _label, msg: self._log("[动画] 取高清静态图失败：%s" % msg))
        self._svg_runner.start()

    # ==================== MCP 结果处理 ====================

    def _on_layers_loaded(self, _label, result):
        if not result.get("success", False):
            self._log("[动画] 取层目录失败 → %s: %s"
                      % (result.get("code", "?"), result.get("message", "")))
            return
        data = result.get("data") or {}
        self.layers = data.get("layers") or []
        self.frame_rate = int(data.get("frameRate") or DEFAULT_FRAME_RATE)
        active = data.get("activeLayerId") or "root"
        self.active_layer = active
        self._rebuild_layer_bar()
        self._update_stream_label()
        self._log("[动画] 层目录 %d 个，当前层=%s" % (len(self.layers), active))
        if self.hd_chk.isChecked():
            self.load_svg()

    def _on_layer_selected(self, layer_id, result):
        if not result.get("success", False):
            self._log("[动画] 切换层失败 → %s: %s"
                      % (result.get("code", "?"), result.get("message", "")))
            return
        data = result.get("data") or {}
        self.active_layer = data.get("layerId") or layer_id
        if data.get("frameRate"):
            self.frame_rate = int(data["frameRate"])
        self._svg_pixmap = None       # 换层后旧的高清图失效
        self._rebuild_layer_bar()
        self._update_stream_label()
        self._log("[动画] 已切换到层 %s（等待该层帧）" % self.active_layer)
        if self.hd_chk.isChecked():
            self.load_svg()

    def _on_svg_loaded(self, _label, result):
        if not result.get("success", False):
            self._log("[动画] 取高清静态图失败 → %s: %s"
                      % (result.get("code", "?"), result.get("message", "")))
            return
        data = result.get("data") or {}
        layer_id = data.get("layerId")
        if layer_id and layer_id != self.active_layer:
            return                    # 结果已过期（期间切了层）
        svg_text = data.get("svg")
        if not svg_text:
            self._log("[动画] 响应中没有 svg 字段")
            return
        pixmap = self._render_svg(svg_text)
        if pixmap is None:
            self._log("[动画] SVG 渲染失败（%s 字节）" % data.get("byteLength"))
            return
        self._svg_pixmap = pixmap
        self.view.set_pixmap(pixmap, BACK_LIGHT)
        self._log("[动画] 已显示高清静态图（层=%s, %dx%d）"
                  % (self.active_layer, pixmap.width(), pixmap.height()))

    # ==================== MQTT 消息 ====================

    def _on_frame(self, data, layer_id):
        """JPEG 帧：只处理当前活动层"""
        if not self.model_id or layer_id != self.active_layer:
            return
        pixmap = QPixmap()
        if not pixmap.loadFromData(data):
            self._log("[动画] JPEG 帧解码失败（%d 字节）" % len(data))
            return
        self._jpeg_pixmap = pixmap
        if not self.hd_chk.isChecked() or self._svg_pixmap is None:
            self.view.set_pixmap(pixmap, BACK_BLACK)

    def _on_layers(self, payload):
        """animation/layers 消息（QoS1 Retain）：层目录"""
        layers = (payload or {}).get("layers") or []
        if not layers:
            return
        self.layers = layers
        active = (payload or {}).get("activeLayerId")
        if active:
            self.active_layer = active
        self._rebuild_layer_bar()

    def _on_anim_state(self, payload):
        """animation/state 消息：推流状态与帧率"""
        payload = payload or {}
        self.streaming = bool(payload.get("animationStreaming"))
        if payload.get("frameRate"):
            self.frame_rate = int(payload["frameRate"])
        layer_id = payload.get("activeLayerId")
        known = {l.get("layerId") for l in self.layers}
        if layer_id and layer_id != self.active_layer and (not known or layer_id in known):
            self.active_layer = layer_id
            self._rebuild_layer_bar()
        self._update_stream_label()

    def _on_hd_toggled(self, checked):
        """高清开关：参照前端——先继续显示 JPEG，SVG 取到后替换；取消则回到 JPEG"""
        if checked:
            self._svg_pixmap = None
            if self._jpeg_pixmap is not None:
                self.view.set_pixmap(self._jpeg_pixmap, BACK_BLACK)
            self.load_svg()
            return
        self._svg_pixmap = None
        if self._jpeg_pixmap is not None:
            self.view.set_pixmap(self._jpeg_pixmap, BACK_BLACK)
        else:
            self.view.clear()

    # ==================== 层级条 / 状态标签 ====================

    def _ordered_layers(self):
        root = [l for l in self.layers if l.get("layerId") == "root"]
        others = [l for l in self.layers if l.get("layerId") != "root"]
        return root + others

    def _rebuild_layer_bar(self):
        while self.layer_bar_layout.count():
            item = self.layer_bar_layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()
        ordered = self._ordered_layers()
        self.layer_scroll.setVisible(bool(ordered))
        for layer in ordered:
            layer_id = layer.get("layerId") or ""
            text = ("主模型" if layer_id == "root"
                    else (layer.get("displayName") or layer.get("name") or layer_id))
            btn = QPushButton(text)
            btn.setCheckable(True)
            btn.setChecked(layer_id == self.active_layer)
            btn.setFixedHeight(24)
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            parent_name = layer.get("parentLayerId")
            btn.setToolTip("layerId=%s%s" % (layer_id,
                                             "" if not parent_name else "，父层=%s" % parent_name))
            btn.clicked.connect(lambda _checked=False, lid=layer_id: self.switch_layer(lid))
            self.layer_bar_layout.addWidget(btn)
        self.layer_bar_layout.addStretch()

    def _update_stream_label(self):
        if self.streaming:
            self.stream_label.setText("● 直播 %d fps" % self.frame_rate)
            color = "#22C55E"
        elif self.model_id:
            self.stream_label.setText("未推流（%d fps）" % self.frame_rate)
            color = "#64748B"
        else:
            self.stream_label.setText("未开始")
            color = "#64748B"
        self.stream_label.setStyleSheet("color:%s; font-size:11px;" % color)

    # ==================== 全屏 ====================

    def _toggle_fullscreen(self):
        if self._fullscreen:
            self.exit_fullscreen()
        else:
            self.enter_fullscreen()

    def enter_fullscreen(self):
        if self._fullscreen:
            return
        if self._esc_shortcut is None:
            self._esc_shortcut = QShortcut(QKeySequence(Qt.Key.Key_Escape), self.window())
            self._esc_shortcut.activated.connect(self.exit_fullscreen)
        self._fullscreen = True
        self._esc_shortcut.setEnabled(True)
        self.view.enter_fullscreen()

    def exit_fullscreen(self):
        if not self._fullscreen:
            return
        self._fullscreen = False
        if self._esc_shortcut is not None:
            self._esc_shortcut.setEnabled(False)
        self.view.exit_fullscreen()

    def _on_fullscreen_changed(self, fullscreen):
        self._fullscreen = fullscreen
        self.full_btn.setText("🗕 恢复" if fullscreen else "🗖 全屏")

    # ==================== 工具 ====================

    def _render_svg(self, svg_text):
        renderer = QSvgRenderer(QByteArray(svg_text.encode("utf-8")))
        if not renderer.isValid():
            return None
        size = renderer.defaultSize()
        if size.isEmpty():
            size = QSize(1280, 720)
        pixmap = QPixmap(size)
        pixmap.fill(QColor(BACK_LIGHT))
        painter = QPainter(pixmap)
        renderer.render(painter)
        painter.end()
        return pixmap

    def _log(self, message):
        self.log_line.emit(message)
