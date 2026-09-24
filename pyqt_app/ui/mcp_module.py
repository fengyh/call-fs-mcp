# -*- coding: utf-8 -*-
"""页签5：设备参数表单（保存到 app_config.json，供仿真页签使用）"""
from PyQt6.QtWidgets import (
    QSpinBox, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QLineEdit, QScrollArea, QGridLayout, QMessageBox,
)

from app_config import (
    RESOURCE_FIELDS, DEFAULT_RESOURCE_VALUES, save_config,
)


class MCPModule(QWidget):
    """页签5：设备参数"""

    def __init__(self, main_window):
        super().__init__()
        self.main = main_window
        self.edits = {}
        self._build_ui()

    def _build_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 左侧：固定宽度 400（上：model-card 固定高 200；下：自由伸缩）----
        left_widget = QWidget()
        left_widget.setFixedWidth(400)
        left = QVBoxLayout(left_widget)
        left.setContentsMargins(0, 0, 0, 0)
        left.setSpacing(10)

        # 模型卡片（固定高度 200）
        card = QFrame()
        card.setObjectName("card")
        card.setFixedHeight(200)
        form = QGridLayout(card)
        form.setContentsMargins(16, 12, 16, 12)
        form.setHorizontalSpacing(8)
        form.setVerticalSpacing(8)

        self.loaders_spin = QSpinBox()
        self.loaders_spin.setRange(1, 5)
        self.loaders_spin.setValue(10)
        self.dozers_spin = QSpinBox()
        self.dozers_spin.setRange(1, 5)
        self.dozers_spin.setValue(20)
        form.addWidget(QLabel("装载机数:"), 0, 0)
        form.addWidget(self.loaders_spin, 0, 1)
        form.addWidget(QLabel("推土机数:"), 0, 2)
        form.addWidget(self.dozers_spin, 0, 3)

        left.addWidget(card)

        # 左侧下方：自由伸缩区
        left_bottom = QFrame()
        left_bottom.setObjectName("card")
        left.addWidget(left_bottom, 1)

        layout.addWidget(left_widget)

        # ---- 右侧：自由伸缩 ----
        right_widget = QWidget()
        right = QVBoxLayout(right_widget)
        right.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(right_widget, 1)



