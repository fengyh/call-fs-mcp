# -*- coding: utf-8 -*-
"""「仿真参数」子页签（占位）

具体功能留待后续需求实现。
"""
from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import QLabel, QVBoxLayout, QWidget


class ParameterTab(QWidget):
    """仿真参数（占位）"""

    def __init__(self, main_window=None, state=None, parent=None):
        super().__init__(parent)
        self.main = main_window
        self.state = state

        layout = QVBoxLayout(self)
        label = QLabel("仿真参数：待后续需求实现")
        label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        label.setStyleSheet("color:#64748B; font-size:12px;")
        layout.addWidget(label)
