# -*- coding: utf-8 -*-
"""页签2：设备参数表单（保存到 app_config.json，供仿真页签使用）"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QLineEdit, QScrollArea, QGridLayout, QMessageBox,
)

from app_config import (
    RESOURCE_FIELDS, DEFAULT_RESOURCE_VALUES, save_config,
)


class EquipmentModule(QWidget):
    """页签2：设备参数"""

    def __init__(self, main_window):
        super().__init__()
        self.main = main_window
        self.edits = {}
        self._build_ui()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 顶部操作栏 ----
        top_bar = QHBoxLayout()
        title = QLabel("设备 / 资源参数")
        title.setStyleSheet("font-size:16px; font-weight:bold; color:#F1F5F9;")
        top_bar.addWidget(title)
        top_bar.addStretch()

        hint = QLabel("参数修改后自动保存，仿真与寻优时作为 5.py 命令行参数传入")
        hint.setStyleSheet("color:#64748B; font-size:12px;")
        top_bar.addWidget(hint)

        btn_reset = QPushButton("🔄 恢复默认")
        btn_reset.clicked.connect(self._reset_default)
        top_bar.addWidget(btn_reset)
        layout.addLayout(top_bar)

        # ---- 参数卡片 ----
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("QScrollArea { border:none; background:transparent; }")

        card = QFrame()
        card.setObjectName("card")
        grid = QGridLayout(card)
        grid.setContentsMargins(16, 14, 16, 14)
        grid.setHorizontalSpacing(10)
        grid.setVerticalSpacing(8)

        saved = self.main.config.get("resources", {})
        for i, (key, label, default, ref, _flag) in enumerate(RESOURCE_FIELDS):
            row, col = divmod(i, 2)
            base = col * 3

            lbl = QLabel(label + ":")
            lbl.setStyleSheet("border:none;")
            grid.addWidget(lbl, row, base)

            edit = QLineEdit(str(saved.get(key, default)))
            edit.setFixedWidth(110)
            edit.editingFinished.connect(lambda k=key, e=edit: self._on_edited(k, e))
            grid.addWidget(edit, row, base + 1)
            self.edits[key] = edit

            ref_lbl = QLabel(ref)
            ref_lbl.setStyleSheet("color:#64748B; font-size:11px; border:none;")
            grid.addWidget(ref_lbl, row, base + 2)

        grid.setColumnStretch(2, 1)
        grid.setColumnStretch(5, 1)
        scroll.setWidget(card)
        layout.addWidget(scroll, 1)

    # ==================== 逻辑 ====================

    def _on_edited(self, key, edit):
        """值变更：校验数字并自动保存"""
        raw = edit.text().strip()
        try:
            float(raw)
            edit.setStyleSheet("")
        except ValueError:
            edit.setStyleSheet("border: 1px solid #EF4444;")
            self.main.set_status("参数 %s 不是有效数字，未保存" % key, ok=False)
            return
        self.main.config.setdefault("resources", {})[key] = raw
        save_config(self.main.config)
        self.main.set_status("参数已保存")

    def _reset_default(self):
        reply = QMessageBox.question(
            self, "确认恢复默认", "将全部参数恢复为默认值，当前修改将丢失。确定继续？",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)
        if reply != QMessageBox.StandardButton.Yes:
            return
        for key, _label, default, _ref, _flag in RESOURCE_FIELDS:
            self.edits[key].setText(default)
        self.main.config["resources"] = dict(DEFAULT_RESOURCE_VALUES)
        save_config(self.main.config)
        self.main.set_status("已恢复默认参数")
