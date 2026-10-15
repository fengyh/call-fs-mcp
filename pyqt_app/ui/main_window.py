# -*- coding: utf-8 -*-
"""主窗口：导航 + QStackedWidget 四页签骨架，暗色主题"""
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QStackedWidget, QPushButton, QFrame,
)
from PyQt6.QtCore import Qt

from app_config import load_config, algo_python, OUTPUT_ROOT
from ui.grid_module import GridModule
from ui.equipment_module import EquipmentModule
from ui.planning_module import PlanningModule
from ui.optimization_module import OptimizationModule
from ui.mcp_module import MCPModule


TABS = [
    # ("grid", "① 网格生成", "点云网格划分"),
    # ("equipment", "② 设备参数", "资源参数配置"),
    # ("planning", "③ 单次仿真", "单配置仿真"),
    # ("optimization", "④ 配置寻优", "多配置扫描与帕累托分析"),
    ("fs_mcp_demo", "通过MCP与FS集成演示", "Python集成FS方法演示"),
]


class MainWindow(QMainWindow):
    """主窗口"""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("多机协同施工调度系统")
        self.setMinimumSize(1280, 800)
        self.resize(1440, 900)

        # 共享状态
        self.active_tab = "grid"
        self.config = load_config()          # 设备参数等（页签2编辑）
        self.selected_config = None          # (装载机数, 推土机数)，页签4写、页签3读

        self._build_ui()
        self._apply_style()
        self._switch_tab("grid")

    # ==================== UI 构建 ====================

    def _build_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        root.addWidget(self._create_title_bar())
        root.addWidget(self._create_navbar())

        self.stack = QStackedWidget()
        self.module_grid = GridModule(self)
        self.module_equip = EquipmentModule(self)
        self.module_plan = PlanningModule(self)
        self.module_optim = OptimizationModule(self)
        self.module_mcp = MCPModule(self)

        self.stack.addWidget(self.module_grid)    # index 0
        self.stack.addWidget(self.module_equip)   # index 1
        self.stack.addWidget(self.module_plan)    # index 2
        self.stack.addWidget(self.module_optim)   # index 3
        self.stack.addWidget(self.module_mcp)     # index 4

        root.addWidget(self.stack, 1)
        self._create_status_bar(root)

    def _create_title_bar(self):
        bar = QFrame()
        bar.setFixedHeight(36)
        bar.setObjectName("titleBar")

        layout = QHBoxLayout(bar)
        layout.setContentsMargins(12, 0, 12, 0)
        layout.setSpacing(8)

        for color in ["#FF5F57", "#FFBD2E", "#28CA41"]:
            dot = QLabel()
            dot.setFixedSize(12, 12)
            dot.setStyleSheet(
                "background:%s; border-radius:6px; border:1px solid rgba(0,0,0,0.1);" % color)
            layout.addWidget(dot)

        layout.addSpacing(16)
        title = QLabel("多机协同施工调度系统")
        title.setStyleSheet("color:#E2E8F0; font-size:13px; font-weight:500;")
        layout.addWidget(title)
        layout.addStretch()

        algo_lbl = QLabel("算法: %s" % algo_python())
        algo_lbl.setStyleSheet("color:#64748B; font-size:11px;")
        layout.addWidget(algo_lbl)

        return bar

    def _create_navbar(self):
        nav = QFrame()
        nav.setFixedHeight(48)
        nav.setObjectName("navbar")

        layout = QHBoxLayout(nav)
        layout.setContentsMargins(20, 4, 20, 4)
        layout.setSpacing(4)

        self.tab_buttons = {}
        for tab_id, label, tooltip in TABS:
            btn = QPushButton(label)
            btn.setToolTip(tooltip)
            btn.setCheckable(True)
            btn.setFixedHeight(38)
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            btn.clicked.connect(lambda checked, t=tab_id: self._switch_tab(t))
            self.tab_buttons[tab_id] = btn
            layout.addWidget(btn)

        layout.addStretch()

        self.badge_label = QLabel()
        self.badge_label.setStyleSheet("color:#94A3B8; font-size:12px;")
        layout.addWidget(self.badge_label)

        return nav

    def _create_status_bar(self, parent_layout):
        bar = QFrame()
        bar.setFixedHeight(28)
        bar.setObjectName("statusBar")

        layout = QHBoxLayout(bar)
        layout.setContentsMargins(16, 0, 16, 0)

        self.status_label = QLabel("● 系统就绪")
        self.status_label.setStyleSheet("color:#22C55E; font-size:11px;")
        layout.addWidget(self.status_label)
        layout.addStretch()

        self.stats_label = QLabel("输出目录: %s" % OUTPUT_ROOT)
        self.stats_label.setStyleSheet("color:#94A3B8; font-size:11px;")
        layout.addWidget(self.stats_label)

        parent_layout.addWidget(bar)

    # ==================== 样式 ====================

    def _apply_style(self):
        self.setStyleSheet("""
            QMainWindow { background: #0F172A; }
            QWidget { font-family: "Microsoft YaHei", "Segoe UI", sans-serif; }
            #titleBar { background: #0A0F1A; border-bottom: 1px solid #1E293B; }
            #navbar { background: #1E293B; border-bottom: 1px solid #334155; }
            #statusBar { background: #0F172A; border-top: 1px solid #1E293B; }
            QPushButton {
                background: transparent; color: #94A3B8; border: none;
                border-radius: 8px; padding: 6px 16px; font-size: 13px;
            }
            QPushButton:hover { background: #334155; color: #E2E8F0; }
            QPushButton:checked { background: #3B82F6; color: white; font-weight: 600; }
            QLabel { color: #E2E8F0; }
            QFrame#card {
                background: #1E293B; border: 1px solid #334155; border-radius: 10px;
            }
            QLineEdit, QComboBox, QSpinBox, QDoubleSpinBox {
                background: #0F172A; color: #E2E8F0; border: 1px solid #334155;
                border-radius: 6px; padding: 5px 8px; font-size: 13px;
            }
            QLineEdit:focus, QComboBox:focus, QSpinBox:focus, QDoubleSpinBox:focus {
                border: 1px solid #3B82F6;
            }
            QPlainTextEdit {
                background: #0A0F1A; color: #CBD5E1; border: 1px solid #334155;
                border-radius: 6px; font-family: "Consolas", monospace; font-size: 12px;
            }
            QTableWidget {
                background: #0F172A; color: #E2E8F0; border: 1px solid #334155;
                gridline-color: #334155; font-size: 12px;
            }
            QHeaderView::section {
                background: #1E293B; color: #94A3B8; border: none;
                border-right: 1px solid #334155; padding: 5px;
            }
            QProgressBar {
                background: #0F172A; border: 1px solid #334155; border-radius: 6px;
                text-align: center; color: #E2E8F0; height: 18px;
            }
            QProgressBar::chunk { background: #3B82F6; border-radius: 5px; }
            QTabWidget::pane { border: 1px solid #334155; border-radius: 6px; }
            QTabBar::tab {
                background: #1E293B; color: #94A3B8; padding: 6px 16px;
                border-top-left-radius: 6px; border-top-right-radius: 6px;
            }
            QTabBar::tab:selected { background: #3B82F6; color: white; }
            QCheckBox { color: #E2E8F0; }
            QPushButton#primaryBtn {
                background: #3B82F6; color: white; font-weight: 600;
                border-radius: 8px; padding: 8px 20px;
            }
            QPushButton#primaryBtn:hover { background: #2563EB; }
            QPushButton#primaryBtn:disabled { background: #334155; color: #64748B; }
        """)

    # ==================== 标签切换 ====================

    def _switch_tab(self, tab_id):
        self.active_tab = tab_id
        for tid, btn in self.tab_buttons.items():
            btn.setChecked(tid == tab_id)
        tab_map = {tid: i for i, (tid, _l, _t) in enumerate(TABS)}
        self.stack.setCurrentIndex(tab_map.get(tab_id, 0))

    def switch_tab(self, index):
        """按页签序号切换（供页签4跳转页签3）"""
        if 0 <= index < len(TABS):
            self._switch_tab(TABS[index][0])

    # ==================== 共享状态 ====================

    def set_selected_config(self, n, m):
        """页签4写入选中的 (装载机数, 推土机数)，页签3随之更新"""
        self.selected_config = (n, m)
        self.module_plan.on_data_changed()

    def set_status(self, text, ok=True):
        color = "#22C55E" if ok else "#EF4444"
        self.status_label.setText("● %s" % text)
        self.status_label.setStyleSheet("color:%s; font-size:11px;" % color)

    # ==================== 退出清理 ====================

    def closeEvent(self, event):
        """关闭窗口前释放页签5 的 MQTT 连接与后台线程"""
        try:
            self.module_mcp.shutdown()
        except Exception:
            pass
        super().closeEvent(event)
