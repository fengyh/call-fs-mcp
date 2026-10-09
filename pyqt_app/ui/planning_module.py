# -*- coding: utf-8 -*-
"""页签3：单次仿真（子进程跑 5.py 单配置）"""
import json
import os

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QLineEdit, QComboBox, QSpinBox, QPlainTextEdit,
    QFileDialog, QMessageBox, QGridLayout, QTabWidget,
)

from app_config import (
    ALGO_DIR, SCRIPT_5PY, OUTPUT_ROOT, DEFAULT_EXCEL,
    algo_python, resource_cli_args, save_config,
)
from ui.grid_module import ScaledImageLabel, StatCard
from utils.subprocess_runner import CommandRunner, SweepRunner


def _parse_coord(text, name):
    """坐标支持数字或 auto/自动/留空（由 5.py 按场地自动计算）"""
    v = text.strip().lower()
    if v in ("auto", "自动", ""):
        return "auto"
    try:
        float(v)
    except ValueError:
        raise ValueError("取土源 %s 坐标不是有效数字（可填 auto 自动计算）: %r" % (name, text))
    return v


def build_sim_command(n, m, config):
    """组装 5.py 单配置仿真命令（页签3/页签4共用）。

    config: main_window.config（含 resources / planning 两个 section）
    返回 (cmd_list, out_dir)；非法输入抛 ValueError。
    """
    res_args = resource_cli_args(config.get("resources", {}))
    pcfg = config.get("planning", {})

    excel = str(pcfg.get("excel", "")).strip()
    if not excel:
        raise ValueError("请填写施工网格 Excel 路径")
    if not os.path.isfile(excel):
        raise ValueError("施工网格 Excel 不存在:\n%s" % excel)

    cmd = [algo_python(), SCRIPT_5PY,
           "--loaders", str(n), "--dozers", str(m),
           "--excel", excel]
    cmd += res_args

    sx = _parse_coord(str(pcfg.get("source_x", "")), "X")
    sy = _parse_coord(str(pcfg.get("source_y", "")), "Y")
    cmd += ["--source-pos", "%s,%s" % (sx, sy)]
    if pcfg.get("source_type", "取土带") == "取土点":
        cmd.append("--source-point")
    else:
        try:
            slen = float(str(pcfg.get("strip_length", "")).strip())
        except ValueError:
            raise ValueError("取土带长度不是有效数字")
        cmd += ["--source-strip-length", str(slen)]

    entry = str(pcfg.get("entry_grids", "")).strip()
    if entry:
        grids = ",".join(g.strip() for g in entry.replace("，", ",").split(",") if g.strip())
        if grids:
            cmd += ["--entry-grids", grids]

    out_dir = os.path.join(OUTPUT_ROOT, "%dv%d" % (n, m))
    os.makedirs(out_dir, exist_ok=True)
    cmd += ["--output_img", os.path.join(out_dir, "output.png"),
            "--output_json", os.path.join(out_dir, "output.json")]
    return cmd, out_dir


class PlanningModule(QWidget):
    """页签3：单次仿真"""

    def __init__(self, main_window):
        super().__init__()
        self.main = main_window
        self.runner = None
        self.sweep_runner = None
        self._build_ui()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QHBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 左侧：参数 + 日志 ----
        left = QVBoxLayout()
        left.setSpacing(10)

        card = QFrame()
        card.setObjectName("card")
        form = QGridLayout(card)
        form.setContentsMargins(16, 12, 16, 12)
        form.setHorizontalSpacing(8)
        form.setVerticalSpacing(8)

        pcfg = self.main.config.get("planning", {})

        self.loaders_spin = QSpinBox()
        self.loaders_spin.setRange(1, 5)
        self.loaders_spin.setValue(int(pcfg.get("loaders", 1)))
        self.dozers_spin = QSpinBox()
        self.dozers_spin.setRange(1, 5)
        self.dozers_spin.setValue(int(pcfg.get("dozers", 1)))
        form.addWidget(QLabel("装载机数:"), 0, 0)
        form.addWidget(self.loaders_spin, 0, 1)
        form.addWidget(QLabel("推土机数:"), 0, 2)
        form.addWidget(self.dozers_spin, 0, 3)

        self.excel_edit = QLineEdit(str(pcfg.get("excel", DEFAULT_EXCEL)))
        form.addWidget(QLabel("施工网格 Excel:"), 1, 0)
        form.addWidget(self.excel_edit, 1, 1, 1, 3)
        btn = QPushButton("浏览…")
        btn.clicked.connect(self._browse_excel)
        form.addWidget(btn, 1, 4)

        self.source_type = QComboBox()
        self.source_type.addItems(["取土带", "取土点"])
        idx = self.source_type.findText(str(pcfg.get("source_type", "取土带")))
        if idx >= 0:
            self.source_type.setCurrentIndex(idx)
        form.addWidget(QLabel("取土方式:"), 2, 0)
        form.addWidget(self.source_type, 2, 1)
        self.source_x = QLineEdit(str(pcfg.get("source_x", "95.0")))
        self.source_x.setFixedWidth(70)
        self.source_y = QLineEdit(str(pcfg.get("source_y", "auto")))
        self.source_y.setFixedWidth(70)
        src_row = QHBoxLayout()
        src_row.addWidget(QLabel("X:"))
        src_row.addWidget(self.source_x)
        src_row.addWidget(QLabel("Y:"))
        src_row.addWidget(self.source_y)
        src_row.addStretch()
        form.addLayout(src_row, 2, 2, 1, 3)

        self.strip_len = QLineEdit(str(pcfg.get("strip_length", "50.0")))
        self.strip_len.setFixedWidth(70)
        form.addWidget(QLabel("取土带长度 (m):"), 3, 0)
        form.addWidget(self.strip_len, 3, 1)
        hint = QLabel("坐标可填 auto（自动计算）")
        hint.setStyleSheet("color:#64748B; font-size:11px; border:none;")
        form.addWidget(hint, 3, 2, 1, 2)

        self.entry_edit = QLineEdit(str(pcfg.get("entry_grids", "")))
        self.entry_edit.setPlaceholderText("留空使用 5.py 默认入口网格")
        form.addWidget(QLabel("入口网格:"), 4, 0)
        form.addWidget(self.entry_edit, 4, 1, 1, 3)

        left.addWidget(card)

        btn_row = QHBoxLayout()
        self.run_btn = QPushButton("▶ 开始仿真")
        self.run_btn.setObjectName("primaryBtn")
        self.run_btn.clicked.connect(self._run)
        btn_row.addWidget(self.run_btn)
        self.run_all_btn = QPushButton("🚀 运行全部配置")
        self.run_all_btn.setToolTip("运行 1~5 台装载机 × 1~5 台推土机共 25 种配置，4 组并行；\n"
                                    "已有 output.json 的配置直接复用（如需重跑请删除对应 output 子目录）")
        self.run_all_btn.clicked.connect(self._run_all)
        btn_row.addWidget(self.run_all_btn)
        self.open_btn = QPushButton("📂 打开输出目录")
        self.open_btn.clicked.connect(self._open_out_dir)
        btn_row.addWidget(self.open_btn)
        btn_row.addStretch()
        left.addLayout(btn_row)

        left.addWidget(QLabel("运行日志"))
        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        left.addWidget(self.log, 1)

        left_widget = QWidget()
        left_widget.setLayout(left)
        left_widget.setFixedWidth(430)
        layout.addWidget(left_widget)

        # ---- 右侧：KPI + 结果图/报告 ----
        right = QVBoxLayout()
        right.setSpacing(10)

        kpi_row = QHBoxLayout()
        kpi_row.setSpacing(10)
        self.kpi_time = StatCard("总工期 (h)")
        self.kpi_fuel = StatCard("总油耗 (L)")
        self.kpi_trips = StatCard("装载机总趟数")
        self.kpi_grids = StatCard("覆盖网格数")
        for c in (self.kpi_time, self.kpi_fuel, self.kpi_trips, self.kpi_grids):
            kpi_row.addWidget(c, 1)
        right.addLayout(kpi_row)

        self.tabs = QTabWidget()
        self.result_img = ScaledImageLabel("结果图 (output.png)")
        self.tabs.addTab(self.result_img, "结果图")
        self.report = QPlainTextEdit()
        self.report.setReadOnly(True)
        self.tabs.addTab(self.report, "文本报告")
        right.addWidget(self.tabs, 1)

        right_widget = QWidget()
        right_widget.setLayout(right)
        layout.addWidget(right_widget, 1)

    # ==================== 逻辑 ====================

    def _browse_excel(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "选择施工网格 Excel", ALGO_DIR,
            "Excel 文件 (*.xlsx *.xls);;所有文件 (*)")
        if path:
            self.excel_edit.setText(path)

    def _collect_planning_config(self):
        pcfg = self.main.config.setdefault("planning", {})
        pcfg.update({
            "loaders": self.loaders_spin.value(),
            "dozers": self.dozers_spin.value(),
            "excel": self.excel_edit.text().strip(),
            "source_type": self.source_type.currentText(),
            "source_x": self.source_x.text().strip(),
            "source_y": self.source_y.text().strip(),
            "strip_length": self.strip_len.text().strip(),
            "entry_grids": self.entry_edit.text().strip(),
        })
        return pcfg

    def _run(self):
        if self.runner is not None and self.runner.isRunning():
            return
        if self.sweep_runner is not None and self.sweep_runner.isRunning():
            return
        self._collect_planning_config()
        n, m = self.loaders_spin.value(), self.dozers_spin.value()
        try:
            cmd, _out_dir = build_sim_command(n, m, self.main.config)
        except ValueError as e:
            QMessageBox.warning(self, "参数错误", str(e))
            return
        save_config(self.main.config)

        self.log.clear()
        self.run_btn.setEnabled(False)
        self.run_btn.setText("⏳ 计算中...")
        self.main.set_status("仿真运行中 (%dv%d)…" % (n, m))

        self.runner = CommandRunner(cmd, cwd=ALGO_DIR, parent=self)
        self.runner.log_line.connect(self._append_log)
        self.runner.finished_rc.connect(lambda rc: self._on_finished(rc, n, m))
        self.runner.start()

    def _append_log(self, line):
        self.log.appendPlainText(line)

    # ==================== 全部配置批量运行 ====================

    def _run_all(self):
        """4 路并行跑完全部 25 种配置（已有 output.json 的直接复用）"""
        if self.runner is not None and self.runner.isRunning():
            return
        if self.sweep_runner is not None and self.sweep_runner.isRunning():
            return
        self._collect_planning_config()

        combos = [(n, m) for n in range(1, 6) for m in range(1, 6)]
        jobs = []
        try:
            for n, m in combos:
                out_json = os.path.join(OUTPUT_ROOT, "%dv%d" % (n, m), "output.json")
                if os.path.isfile(out_json):
                    jobs.append({"n": n, "m": m, "cmd": None})
                    continue
                cmd, _ = build_sim_command(n, m, self.main.config)
                jobs.append({"n": n, "m": m, "cmd": cmd})
        except ValueError as e:
            QMessageBox.warning(self, "参数错误", str(e))
            return
        save_config(self.main.config)

        n_run = sum(1 for j in jobs if j["cmd"])
        self.log.clear()
        self._append_log(">>> 共 25 组配置：新跑 %d 组，复用 %d 组（4 路并行）"
                         % (n_run, len(jobs) - n_run))
        self.run_btn.setEnabled(False)
        self.run_all_btn.setEnabled(False)
        self.run_all_btn.setText("⏳ 批量运行中...")
        self.main.set_status("批量运行全部配置中 (0/%d)…" % len(jobs))

        self.sweep_runner = SweepRunner(jobs, cwd=ALGO_DIR, max_parallel=4,
                                        parent=self)
        self.sweep_runner.log_line.connect(self._append_log)
        self.sweep_runner.progress.connect(self._on_sweep_progress)
        self.sweep_runner.all_finished.connect(self._on_sweep_finished)
        self.sweep_runner.start()

    def _on_sweep_progress(self, done, total):
        self.main.set_status("批量运行全部配置中 (%d/%d)…" % (done, total))

    def _on_sweep_finished(self, results):
        self.run_btn.setEnabled(True)
        self.run_all_btn.setEnabled(True)
        self.run_all_btn.setText("🚀 运行全部配置")
        ok = sum(1 for r in results if r.get("ok"))
        self._append_log("=== 全部结束: 成功 %d, 失败 %d ==="
                         % (ok, len(results) - ok))
        self.main.set_status("批量运行完成：成功 %d / %d" % (ok, len(results)),
                             ok=(ok == len(results)))
        # 刷新当前所选配置的结果展示
        self._load_results(self.loaders_spin.value(), self.dozers_spin.value())

    def _out_dir(self, n, m):
        return os.path.join(OUTPUT_ROOT, "%dv%d" % (n, m))

    def _open_out_dir(self):
        out_dir = self._out_dir(self.loaders_spin.value(), self.dozers_spin.value())
        os.makedirs(out_dir, exist_ok=True)
        if hasattr(os, "startfile"):
            os.startfile(out_dir)

    def _on_finished(self, rc, n, m):
        self.run_btn.setEnabled(True)
        self.run_btn.setText("▶ 开始仿真")
        if rc != 0:
            self.main.set_status("仿真失败 (exit %d)" % rc, ok=False)
            self._append_log("!!! 进程退出码 %d" % rc)
            return
        self.main.set_status("仿真完成 (%dv%d)" % (n, m))
        self._load_results(n, m)

    def _load_results(self, n, m):
        """读取 output.json KPI、output.png、output_report.txt"""
        out_dir = self._out_dir(n, m)

        json_path = os.path.join(out_dir, "output.json")
        if os.path.isfile(json_path):
            try:
                with open(json_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                fs = data.get("fuel_stats", {})
                self.kpi_time.set_value("%.2f" % data.get("total_time_hour", 0.0))
                self.kpi_fuel.set_value("%.1f" % fs.get("total_fuel_liters", 0.0))
                self.kpi_trips.set_value(data.get("total_loader_trips", 0))
                self.kpi_grids.set_value(data.get("total_grids", 0))
                unfinished = data.get("unfinished_grids", [])
                if unfinished:
                    self._append_log("[警告] 未完工网格数: %d" % len(unfinished))
            except Exception as e:
                self._append_log("[警告] output.json 解析失败: %s" % e)

        self.result_img.set_image(os.path.join(out_dir, "output.png"))

        report_path = os.path.join(out_dir, "output_report.txt")
        if os.path.isfile(report_path):
            try:
                with open(report_path, "r", encoding="utf-8-sig") as f:
                    self.report.setPlainText(f.read())
            except Exception as e:
                self.report.setPlainText("报告读取失败: %s" % e)
        else:
            self.report.setPlainText("（未找到 output_report.txt）")

    def on_data_changed(self):
        """页签4“应用此配置”后同步装载机/推土机数量"""
        sel = self.main.selected_config
        if sel:
            n, m = sel
            self.loaders_spin.setValue(n)
            self.dozers_spin.setValue(m)
            self._collect_planning_config()
            save_config(self.main.config)
            self._load_results(n, m)
