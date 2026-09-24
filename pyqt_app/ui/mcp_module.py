# -*- coding: utf-8 -*-
"""页签5：通过 MCP 加载/关闭 FS 模型（MCP Server – Python 应用 最小验证）

- 左侧：本地 .fs 文件的获取与关闭（原有逻辑）+ MCP 会话状态
- 右侧：MCP 操作按钮（测试连接 / 加载到 FS / 关闭 FS 模型 / 刷新已加载）与调用日志

所有 MCP 调用经 utils.mcp_runner.McpCallRunner 在后台线程执行，避免读超时（60s）卡住界面。
"""
import os
import uuid

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox, QFileDialog, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QPlainTextEdit, QMessageBox, QGridLayout,
)

from app_config import APP_DIR, mcp_settings
from mcp_client import McpClient
from mcp_client.mcp_runner import McpCallRunner


class MCPModule(QWidget):
    """页签5：MCP 与 FS 集成验证"""

    mcp_log = pyqtSignal(str)   # McpClient 日志回调 → 界面线程

    def __init__(self, main_window):
        super().__init__()
        self.model_file_name: str = ""
        self.main = main_window

        # MCP 会话状态
        self.settings = mcp_settings(main_window.config)
        self.mcp_record_id = ""
        self.mcp_model_id = ""
        self.mcp_status = "未加载"
        self.runner = None
        self._busy = False

        self._build_ui()
        self.mcp_log.connect(self._append_log)

        # MCP 客户端（Java McpClientService 的 Python 移植，构造时注入连接配置）
        self.mcp = McpClient(
            endpoint_url=self.settings["endpoint_url"],
            api_key=self.settings["api_key"],
            log_hook=self.mcp_log.emit,
        )

        self.load_in_ui_chk.setChecked(bool(self.settings["load_in_ui"]))
        self.info_labels["user"].setText(str(self.settings["user_id"]))
        self.info_labels["endpoint"].setText(str(self.settings["endpoint_url"]))
        self._update_info()
        self._refresh_buttons()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 左侧：固定宽度 400（上：模型卡片固定高 200；下：MCP 状态）----
        left_widget = QWidget()
        left_widget.setFixedWidth(400)
        left = QVBoxLayout(left_widget)
        left.setContentsMargins(0, 0, 0, 0)
        left.setSpacing(10)

        # 模型卡片（固定高度 200）
        card_model = QFrame()
        card_model.setObjectName("card")
        card_model.setFixedHeight(200)
        form = QGridLayout(card_model)
        form.setContentsMargins(16, 12, 16, 12)
        form.setHorizontalSpacing(8)
        form.setVerticalSpacing(8)
        form.setAlignment(Qt.AlignmentFlag.AlignTop)

        self.open_btn = QPushButton("📂加载模型")
        self.open_btn.clicked.connect(self._open_model)
        form.addWidget(self.open_btn, 0, 0)
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

        # 左侧下方：MCP 会话状态

        layout.addWidget(left_widget)
        left_bottom = QFrame()
        left_bottom.setObjectName("card")

        left.addWidget(left_bottom, 1)

        # ---- 右侧：操作按钮 + 日志 ----
        right_widget = QWidget()
        right = QVBoxLayout(right_widget)
        right.setContentsMargins(0, 0, 0, 0)
        right.setSpacing(8)

        title = QLabel("MCP 集成（MCP Server – Python 应用）")
        title.setStyleSheet("color:#94A3B8; font-size:12px;")
        right.addWidget(title)

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
        self.load_in_ui_chk = QCheckBox("加载到 FS 桌面 UI (loadInUI)")
        btn_row.addWidget(self.load_in_ui_chk)
        btn_row.addStretch()
        right.addLayout(btn_row)

        log_title = QLabel("MCP 调用日志")
        log_title.setStyleSheet("color:#94A3B8; font-size:12px;")
        right.addWidget(log_title)
        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        right.addWidget(self.log, 1)

        layout.addWidget(right_widget, 1)

    def _info_row(self, grid, row, title):
        """状态信息一行：标题 + 值（值可选中复制）"""
        key = QLabel(title)
        key.setStyleSheet("color:#94A3B8; font-size:12px;")
        value = QLabel("-")
        value.setWordWrap(True)
        value.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        grid.addWidget(key, row, 0, Qt.AlignmentFlag.AlignTop)
        grid.addWidget(value, row, 1, 1, 3)
        return value

    # ==================== 本地模型文件（原有逻辑） ====================

    def _open_model(self):
        if self.open_btn.text() == "❌关闭模型":
            if self.mcp_model_id:
                QMessageBox.information(
                    self, "请先关闭 FS 模型",
                    "模型仍加载在 FS 中（modelId=%s）。\n"
                    "请先点击「■关闭 FS 模型」，再关闭本地文件。" % self.mcp_model_id)
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

    # ==================== MCP 操作 ====================

    def _test_connection(self):
        """initialize + notifications/initialized + tools/list（无副作用）"""
        self._append_log("[测试] 连接 %s …" % self.settings["endpoint_url"])
        self._run("test", lambda: (self.mcp.initializeSession(), self.mcp.listTools()))

    def _load_to_fs(self):
        """在 FS 中加载所选模型：loadModel(userId, recordId, modelFileName, loadInUI)"""
        if not self.model_file_name:
            QMessageBox.warning(self, "参数错误", "请先选择本地 .fs 模型文件")
            return
        if self.mcp_model_id:
            QMessageBox.warning(self, "已加载模型",
                                "FS 中已有本用户加载的模型（modelId=%s），请先关闭。"
                                % self.mcp_model_id)
            return
        path = os.path.abspath(self.model_file_name)
        if not os.path.isfile(path):
            QMessageBox.warning(self, "参数错误", "模型文件不存在:\n%s" % path)
            return

        # 本地无模型库，recordId 由 Python 侧生成（Java 版来自 fs_model_file 表主键）
        record_id = "pyqt-" + uuid.uuid4().hex[:8]
        self.mcp_record_id = record_id
        self._update_info()
        user_id = self.settings["user_id"]
        load_in_ui = self.load_in_ui_chk.isChecked()
        self._append_log("[加载] 请求 FS 加载: userId=%s recordId=%s loadInUI=%s"
                         % (user_id, record_id, load_in_ui))
        self._run("load",
                  lambda: self.mcp.loadModel(user_id, record_id, path, load_in_ui))

    def _unload_from_fs(self):
        """在 FS 中卸载模型：unloadModel(userId, modelId)"""
        if not self.mcp_model_id:
            return
        user_id = self.settings["user_id"]
        model_id = self.mcp_model_id
        self._append_log("[关闭] 请求 FS 卸载: userId=%s modelId=%s" % (user_id, model_id))
        self._run("unload", lambda: self.mcp.unloadModel(user_id, model_id))

    def _refresh_models(self):
        """listModels(userId)：列出 FS 进程内已加载模型，并同步本地状态"""
        user_id = self.settings["user_id"]
        self._append_log("[刷新] 查询 FS 已加载模型 …")
        self._run("refresh", lambda: self.mcp.listModels(user_id))

    # ==================== 后台调用 ====================

    def _run(self, label, fn):
        if self._busy:
            return
        self._busy = True
        self._refresh_buttons()
        self.main.set_status("MCP 调用中…")
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
        finally:
            self._update_info()
            self._refresh_buttons()

    def _on_failed(self, label, message):
        self._busy = False
        self._append_log("[%s] 调用失败 → %s" % (label, message))
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
        for want in ("loadRemoteSimulationModel", "unloadRemoteSimulationModel"):
            self._append_log("  %s %s" % ("✔" if want in names else "✘", want))
        self.main.set_status("MCP 连接正常（%d 个工具）" % len(names))

    def _handle_load(self, result):
        if not result.get("success", False):
            self._append_log("[加载] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "加载失败")))
            self.main.set_status("FS 模型加载失败", ok=False)
            return
        data = result.get("data") or {}
        self.mcp_record_id = data.get("recordId") or self.mcp_record_id
        self.mcp_model_id = data.get("modelId") or ""
        self.mcp_status = data.get("status") or "LOADED"
        self._append_log("[加载] 成功 → modelId=%s status=%s"
                         % (self.mcp_model_id, self.mcp_status))
        self._append_log("        modelFilePath=%s" % data.get("modelFilePath", ""))
        if not self.mcp_model_id:
            self._append_log("[警告] 响应未返回 modelId，无法执行卸载")
            self.main.set_status("FS 模型已加载（缺少 modelId）", ok=False)
            return
        self.main.set_status("FS 模型已加载: %s" % os.path.basename(
            data.get("modelFilePath") or self.model_file_name))

    def _handle_unload(self, result):
        if not result.get("success", False):
            self._append_log("[关闭] 失败 → %s: %s"
                             % (result.get("code", "?"), result.get("message", "卸载失败")))
            self.main.set_status("FS 模型卸载失败", ok=False)
            return
        self._append_log("[关闭] 成功 → 已从 FS 卸载 modelId=%s" % self.mcp_model_id)
        self.mcp_model_id = ""
        self.mcp_record_id = ""
        self.mcp_status = "未加载"
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
        if self.mcp_model_id and self.mcp_model_id not in ids:
            self._append_log("[刷新] 本地记录的模型已不在 FS 中，状态复位")
            self.mcp_model_id = ""
            self.mcp_record_id = ""
            self.mcp_status = "未加载"
        elif not self.mcp_model_id and models:
            first = models[0]
            self.mcp_record_id = first.get("recordId") or ""
            self.mcp_model_id = first.get("modelId") or ""
            self.mcp_status = first.get("status") or "LOADED"
            self._append_log("[刷新] 已同步本地状态到 FS 中的模型")
        self.main.set_status("已加载模型 %d 个" % len(models))

    # ==================== 界面同步 ====================

    def _update_info(self):
        self.info_labels["status"].setText(self.mcp_status)
        color = "#22C55E" if self.mcp_model_id else "#94A3B8"
        self.info_labels["status"].setStyleSheet("color:%s; font-weight:600;" % color)
        self.info_labels["record"].setText(self.mcp_record_id or "-")
        self.info_labels["model"].setText(self.mcp_model_id or "-")

    def _refresh_buttons(self):
        busy = self._busy
        has_file = bool(self.model_file_name)
        loaded = bool(self.mcp_model_id)
        self.test_btn.setEnabled(not busy)
        self.load_fs_btn.setEnabled(not busy and has_file and not loaded)
        self.unload_fs_btn.setEnabled(not busy and loaded)
        self.refresh_btn.setEnabled(not busy)
        self.load_in_ui_chk.setEnabled(not busy)

    def _append_log(self, line):
        self.log.appendPlainText(line)
