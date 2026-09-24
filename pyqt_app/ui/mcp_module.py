# -*- coding: utf-8 -*-
"""页签5：设备参数表单（保存到 app_config.json，供仿真页签使用）"""
import os
from PyQt6.QtCore import Qt
from PyQt6.QtWidgets import (
    QFileDialog, QSpinBox, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QLineEdit, QScrollArea, QGridLayout, QMessageBox,
)

from app_config import (
    RESOURCE_FIELDS, DEFAULT_RESOURCE_VALUES, save_config,
)


class MCPModule(QWidget):
    """页签5：设备参数"""

    def __init__(self, main_window):
        super().__init__()
        self.model_file_name: str = ""
        self.main = main_window
        self.edits = {}
        self._build_ui()

    def _build_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 左侧：固定宽度 400（上：model-card_model 固定高 200；下：自由伸缩）----
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

        left.addWidget(card_model)

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


    def _open_model(self):
        if self.open_btn.text() == "❌关闭模型":
            self.model_file_name = ""
            self.model_file_show.setText("尚未加载模型")
            self.open_btn.setText("📂加载模型")
            return
        else:
            path, _ = QFileDialog.getOpenFileName(
                self, "FS仿真模型", None,"FS 文件 (*.fs);")
            if path:
                self.model_file_name = path
                self.model_file_show.setText(os.path.basename(path))
                self.open_btn.setText("❌关闭模型")
