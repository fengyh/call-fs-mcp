# -*- coding: utf-8 -*-
"""「控制台日志」子页签：显示 FS 仿真的 MQTT console 消息

自包含实现：直接连接 `SimulationState` 的信号（追加一条 / 清空 / 条数变化），
外部（`ui/mcp_module.py`）只需构造并挂载到 QTabWidget。
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QPlainTextEdit,
)

from mcp_client.sim_state import SimulationState


class ConsoleLogTab(QWidget):
    """FS 控制台日志（MQTT console 消息）"""

    def __init__(self, main_window=None, state=None, parent=None):
        super().__init__(parent)
        self.main = main_window
        self.state = state if state is not None else SimulationState(self)

        self._build_ui()

        # 数据 → 界面（SimulationState 的信号已在 UI 线程投递）
        self.state.console_appended.connect(self._append_line)
        self.state.console_cleared.connect(self.clear)
        self.state.changed.connect(self._refresh_count)
        self._refresh_count()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        head = QHBoxLayout()
        self.count_label = QLabel("0 条")
        self.count_label.setStyleSheet("color:#64748B; font-size:11px;")
        head.addWidget(self.count_label)
        head.addStretch()
        self.clear_btn = QPushButton("清除")
        self.clear_btn.setFixedHeight(24)
        self.clear_btn.clicked.connect(self._on_clear_clicked)
        head.addWidget(self.clear_btn)
        layout.addLayout(head)

        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        layout.addWidget(self.log, 1)

    # ==================== 行为 ====================

    def _on_clear_clicked(self):
        """清空显示并清空 state.console_logs（与改造前按钮行为一致）"""
        if self.state.console_logs:
            self.state.clear_console()   # 触发 console_cleared → self.clear()
        else:
            self.clear()

    def _append_line(self, line):
        self.log.appendPlainText(line)

    def clear(self):
        self.log.clear()
        self._refresh_count()

    def _refresh_count(self):
        self.count_label.setText("%d 条" % len(self.state.console_logs))
