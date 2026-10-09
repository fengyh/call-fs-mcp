# -*- coding: utf-8 -*-
"""页签4：配置寻优（多配置扫描 + 帕累托前沿分析）"""
import csv
import importlib.util
import json
import os

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QFrame, QSpinBox, QDoubleSpinBox, QCheckBox, QProgressBar,
    QPlainTextEdit, QTableWidget, QTableWidgetItem, QMessageBox,
    QGridLayout, QAbstractItemView,
)
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QColor

import matplotlib
matplotlib.use("QtAgg")
matplotlib.rcParams["font.sans-serif"] = [
    "Microsoft YaHei", "SimHei", "DejaVu Sans"]
matplotlib.rcParams["axes.unicode_minus"] = False
from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg
from matplotlib.figure import Figure

from app_config import ALGO_DIR, SCRIPT_CPY, OUTPUT_ROOT, save_config
from ui.planning_module import build_sim_command
from utils.subprocess_runner import SweepRunner


def _import_c_module():
    """按文件路径导入 ALGO_DIR/c.py（import 安全，纯 stdlib 顶层）"""
    spec = importlib.util.spec_from_file_location("algo_c", SCRIPT_CPY)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class ParetoCanvas(FigureCanvasQTAgg):
    """帕累托前沿画布（暗色，三目标 3D）"""

    def __init__(self, parent=None):
        self.fig = Figure(figsize=(5, 4), facecolor="#1E293B")
        super().__init__(self.fig)
        self.ax = self.fig.add_subplot(111, projection='3d')
        self._style_ax()

    def _style_ax(self):
        ax = self.ax
        ax.set_facecolor("#0F172A")
        if hasattr(ax, "spines"):
            for side in ("top", "right", "left", "bottom"):
                try:
                    ax.spines[side].set_color("#334155")
                except Exception:
                    pass
        for axis in (ax.xaxis, ax.yaxis, getattr(ax, "zaxis", None)):
            if axis is None:
                continue
            try:
                axis.pane.set_facecolor("#0F172A")
                axis.pane.set_edgecolor("#334155")
            except Exception:
                pass
            axis.label.set_color("#E2E8F0")
        ax.tick_params(colors="#94A3B8")
        ax.title.set_color("#F1F5F9")

    def plot(self, rows, pareto):
        self.fig.clear()
        self.ax = self.fig.add_subplot(111, projection='3d')
        self._style_ax()
        ax = self.ax
        ok_rows = [r for r in rows if r.get("ok")]
        if ok_rows:
            times = [r["time"] for r in ok_rows]
            fuels = [r["fuel"] for r in ok_rows]
            confs = [r.get("conflicts", 0) for r in ok_rows]
            ax.scatter(times, fuels, confs, c="#F4726A", s=50, alpha=0.8,
                       edgecolors="white", depthshade=False, label="非前沿方案")
        if pareto:
            front = sorted(pareto, key=lambda x: x["time"])
            ft = [r["time"] for r in front]
            ff = [r["fuel"] for r in front]
            fc = [r.get("conflicts", 0) for r in front]
            ax.scatter(ft, ff, fc, c="#2E9E5B", s=80, edgecolors="white",
                       depthshade=False, label="帕累托最优解")
            for r in front:
                ax.text(r["time"], r["fuel"], r.get("conflicts", 0),
                        "%dv%d" % (r["loaders"], r["dozers"]),
                        fontsize=8, color="#A7F3D0", fontweight="bold")
        ax.set_xlabel("完工时间 (h)", fontsize=8)
        ax.set_ylabel("总油耗 (L)", fontsize=8)
        ax.set_zlabel("冲突消解次数", fontsize=8)
        ax.set_title("三目标帕累托前沿（时间×油耗×冲突）")
        ax.view_init(elev=22, azim=-150)
        if ok_rows or pareto:
            leg = ax.legend(loc="upper right", fontsize=8)
            leg.get_frame().set_facecolor("#1E293B")
            for text in leg.get_texts():
                text.set_color("#E2E8F0")
        self.fig.tight_layout()
        self.draw_idle()

    def save_png(self, path):
        try:
            self.fig.savefig(path, dpi=200, bbox_inches="tight",
                             facecolor=self.fig.get_facecolor())
            return True
        except Exception:
            return False


class OptimizationModule(QWidget):
    """页签4：配置寻优"""

    def __init__(self, main_window):
        super().__init__()
        self.main = main_window
        self.runner = None
        self.rows = []           # [{n, m, ok, time, fuel, score}]
        self.pareto_keys = set()  # {(n, m)}
        self._build_ui()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 16, 20, 16)
        layout.setSpacing(12)

        # ---- 控制卡片 ----
        card = QFrame()
        card.setObjectName("card")
        form = QGridLayout(card)
        form.setContentsMargins(16, 12, 16, 12)
        form.setHorizontalSpacing(8)
        form.setVerticalSpacing(8)

        self.loader_min = QSpinBox()
        self.loader_min.setRange(1, 5)
        self.loader_min.setValue(1)
        self.loader_max = QSpinBox()
        self.loader_max.setRange(1, 5)
        self.loader_max.setValue(5)
        self.dozer_min = QSpinBox()
        self.dozer_min.setRange(1, 5)
        self.dozer_min.setValue(1)
        self.dozer_max = QSpinBox()
        self.dozer_max.setRange(1, 5)
        self.dozer_max.setValue(5)
        form.addWidget(QLabel("装载机数:"), 0, 0)
        form.addWidget(self.loader_min, 0, 1)
        form.addWidget(QLabel("~"), 0, 2)
        form.addWidget(self.loader_max, 0, 3)
        form.addWidget(QLabel("推土机数:"), 0, 4)
        form.addWidget(self.dozer_min, 0, 5)
        form.addWidget(QLabel("~"), 0, 6)
        form.addWidget(self.dozer_max, 0, 7)

        self.jobs_spin = QSpinBox()
        self.jobs_spin.setRange(1, 4)
        self.jobs_spin.setValue(2)
        form.addWidget(QLabel("并发数:"), 0, 8)
        form.addWidget(self.jobs_spin, 0, 9)

        self.reuse_check = QCheckBox("复用已有结果")
        self.reuse_check.setChecked(True)
        self.reuse_check.setToolTip("output.json 已存在的配置直接复用，不重新仿真")
        form.addWidget(self.reuse_check, 1, 0, 1, 3)

        self.time_weight = QDoubleSpinBox()
        self.time_weight.setRange(0.0, 100.0)
        self.time_weight.setValue(1.0)
        self.fuel_weight = QDoubleSpinBox()
        self.fuel_weight.setRange(0.0, 100.0)
        self.fuel_weight.setValue(1.0)
        self.conflict_weight = QDoubleSpinBox()
        self.conflict_weight.setRange(0.0, 100.0)
        self.conflict_weight.setValue(1.0)
        for w in (self.time_weight, self.fuel_weight, self.conflict_weight):
            w.setToolTip("加权得分 = 各目标归一化(0~1)后按权重求和，仅用于排序参考；\n"
                         "不影响 Pareto 最优判定。1:1:1 表示三个目标同等重要")
        form.addWidget(QLabel("时间权重:"), 1, 4)
        form.addWidget(self.time_weight, 1, 5)
        form.addWidget(QLabel("油耗权重:"), 1, 6)
        form.addWidget(self.fuel_weight, 1, 7)
        form.addWidget(QLabel("冲突权重:"), 2, 4)
        form.addWidget(self.conflict_weight, 2, 5)

        self.run_btn = QPushButton("▶ 开始扫描")
        self.run_btn.setObjectName("primaryBtn")
        self.run_btn.clicked.connect(self._run)
        form.addWidget(self.run_btn, 1, 8, 1, 2)

        layout.addWidget(card)

        # ---- 进度条 ----
        self.progress = QProgressBar()
        self.progress.setRange(0, 1)
        self.progress.setValue(0)
        layout.addWidget(self.progress)

        # ---- 中部：表格 + 帕累托图 ----
        mid = QHBoxLayout()
        mid.setSpacing(12)

        self.table = QTableWidget(0, 7)
        self.table.setHorizontalHeaderLabels(
            ["装载机数", "推土机数", "总工期(h)", "总油耗(L)", "冲突消解次数",
             "加权得分↓", "状态"])
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.horizontalHeader().setStretchLastSection(True)
        mid.addWidget(self.table, 3)

        self.canvas = ParetoCanvas()
        mid.addWidget(self.canvas, 2)
        layout.addLayout(mid, 1)

        # ---- 底部：应用按钮 + 日志 ----
        btn_row = QHBoxLayout()
        self.apply_btn = QPushButton("✔ 应用此配置")
        self.apply_btn.setEnabled(False)
        self.apply_btn.clicked.connect(self._apply_selected)
        btn_row.addWidget(self.apply_btn)
        self.open_btn = QPushButton("📂 打开输出目录")
        self.open_btn.clicked.connect(self._open_output_root)
        btn_row.addWidget(self.open_btn)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        self.log.setMaximumHeight(120)
        layout.addWidget(self.log)

        self.table.itemSelectionChanged.connect(self._on_selection)

    # ==================== 扫描 ====================

    def _run(self):
        if self.runner is not None and self.runner.isRunning():
            return
        l_min, l_max = self.loader_min.value(), self.loader_max.value()
        d_min, d_max = self.dozer_min.value(), self.dozer_max.value()
        if l_min > l_max or d_min > d_max:
            QMessageBox.warning(self, "参数错误", "最小值不能大于最大值")
            return

        # 扫描参数与页签3一致（设备参数 + 取土源 + Excel + 入口网格）
        combos = [(n, m)
                  for n in range(l_min, l_max + 1)
                  for m in range(d_min, d_max + 1)]
        reuse = self.reuse_check.isChecked()

        jobs = []
        try:
            for n, m in combos:
                out_json = os.path.join(OUTPUT_ROOT, "%dv%d" % (n, m), "output.json")
                if reuse and os.path.isfile(out_json):
                    jobs.append({"n": n, "m": m, "cmd": None})
                    continue
                cmd, _ = build_sim_command(n, m, self.main.config)
                jobs.append({"n": n, "m": m, "cmd": cmd})
        except ValueError as e:
            QMessageBox.warning(self, "参数错误", str(e))
            return

        save_config(self.main.config)
        total = len(jobs)
        n_run = sum(1 for j in jobs if j["cmd"])
        self._append_log(">>> 共 %d 组配置：新跑 %d 组，复用 %d 组"
                         % (total, n_run, total - n_run))

        self.progress.setRange(0, total)
        self.progress.setValue(0)
        self.run_btn.setEnabled(False)
        self.run_btn.setText("⏳ 扫描中...")
        self.apply_btn.setEnabled(False)
        self.main.set_status("配置扫描中 (0/%d)…" % total)

        self.runner = SweepRunner(jobs, cwd=ALGO_DIR,
                                  max_parallel=self.jobs_spin.value(),
                                  parent=self)
        self.runner.progress.connect(self._on_progress)
        self.runner.config_done.connect(self._on_config_done)
        self.runner.log_line.connect(self._append_log)
        self.runner.all_finished.connect(self._on_all_finished)
        self.runner.start()

    def _on_progress(self, done, total):
        self.progress.setValue(done)
        self.main.set_status("配置扫描中 (%d/%d)…" % (done, total))

    def _on_config_done(self, n, m, ok):
        self._append_log("%s %s" % ("%dv%d" % (n, m), "✔ 完成" if ok else "✘ 失败"))

    def _on_all_finished(self, results):
        self.run_btn.setEnabled(True)
        self.run_btn.setText("▶ 开始扫描")
        ok = sum(1 for r in results if r.get("ok"))
        self.main.set_status("扫描完成：成功 %d / %d" % (ok, len(results)))
        self._append_log("=== 扫描结束: 成功 %d, 失败 %d ==="
                         % (ok, len(results) - ok))
        self._analyze(results)

    # ==================== 分析 ====================

    def _analyze(self, results):
        """汇总 output.json → 表格 + 帕累托图"""
        combos = [(r["n"], r["m"]) for r in results]
        ok_map = {(r["n"], r["m"]): bool(r.get("ok")) for r in results}

        rows = []
        for n, m in combos:
            row = {"n": n, "m": m, "ok": False, "time": None, "fuel": None}
            json_path = os.path.join(OUTPUT_ROOT, "%dv%d" % (n, m), "output.json")
            if ok_map.get((n, m)) and os.path.isfile(json_path):
                try:
                    with open(json_path, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    row["ok"] = True
                    row["time"] = float(data.get("total_time_hour", 0.0))
                    row["fuel"] = float(data.get("fuel_stats", {})
                                        .get("total_fuel_liters", 0.0))
                    tsum = data.get("temporal_summary", {})
                    row["conflicts"] = (tsum.get("node_conflicts_resolved", 0)
                                        + tsum.get("edge_conflicts_resolved", 0))
                except Exception:
                    pass
            rows.append(row)

        # 加权得分（各目标归一化到 0~1 后按权重求和，越小越好；仅用于排序参考）
        wt = self.time_weight.value()
        wf = self.fuel_weight.value()
        wc = self.conflict_weight.value()
        ok_rows = [r for r in rows if r["ok"]]
        if ok_rows:
            def _norm(key):
                vals = [r[key] for r in ok_rows]
                lo, hi = min(vals), max(vals)
                span = hi - lo
                for r in ok_rows:
                    r["_n_" + key] = (r[key] - lo) / span if span > 0 else 0.0
            _norm("time")
            _norm("fuel")
            _norm("conflicts")
        for r in rows:
            r["score"] = (wt * r["_n_time"] + wf * r["_n_fuel"]
                          + wc * r["_n_conflicts"]) if r["ok"] else None
        self.rows = rows

        # 帕累托前沿（进程内 import c.py）
        pareto = []
        try:
            c = _import_c_module()
            pareto = c.get_pareto_front_from_summary(OUTPUT_ROOT, combos)
            c.export_summary_table(OUTPUT_ROOT)
            self._append_log("[汇总] 已生成 %s" % os.path.join(OUTPUT_ROOT, "summary.csv"))
        except Exception as e:
            self._append_log("[警告] c.py 调用失败: %s" % e)
        self.pareto_keys = {(p["loaders"], p["dozers"]) for p in pareto}

        if pareto:
            self._append_log("帕累托前沿: " + ", ".join(
                "%dv%d(%.1fh/%.0fL/%d次)" % (p["loaders"], p["dozers"],
                                             p["time"], p["fuel"], p["conflicts"])
                for p in pareto))

        self.canvas.plot(rows, pareto)
        png_path = os.path.join(OUTPUT_ROOT, "pareto_front.png")
        if self.canvas.save_png(png_path):
            self._append_log("[图] 已保存 %s" % png_path)
        self._write_pareto_csv(pareto)
        self._fill_table()

    def _write_pareto_csv(self, pareto):
        if not pareto:
            return
        try:
            path = os.path.join(OUTPUT_ROOT, "pareto_front.csv")
            with open(path, "w", newline="", encoding="utf-8-sig") as f:
                writer = csv.writer(f)
                writer.writerow(["装载机数", "推土机数", "完工时间(h)", "油耗(L)", "冲突消解次数"])
                for p in pareto:
                    writer.writerow([p["loaders"], p["dozers"],
                                     round(p["time"], 3), round(p["fuel"], 2),
                                     p["conflicts"]])
        except Exception as e:
            self._append_log("[警告] pareto_front.csv 写入失败: %s" % e)

    def _fill_table(self):
        green = QColor("#14432A")
        gray = QColor("#3A3F4A")
        self.table.setRowCount(len(self.rows))
        for i, r in enumerate(self.rows):
            if r["ok"]:
                cells = [str(r["n"]), str(r["m"]),
                         "%.3f" % r["time"], "%.2f" % r["fuel"],
                         str(r.get("conflicts", 0)),
                         "%.3f" % r["score"],
                         "帕累托最优" if (r["n"], r["m"]) in self.pareto_keys else "有效"]
            else:
                cells = [str(r["n"]), str(r["m"]), "--", "--", "--", "--", "失败"]
            for j, text in enumerate(cells):
                item = QTableWidgetItem(text)
                item.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
                if not r["ok"]:
                    item.setBackground(gray)
                    item.setForeground(QColor("#94A3B8"))
                elif (r["n"], r["m"]) in self.pareto_keys:
                    item.setBackground(green)
                    item.setForeground(QColor("#A7F3D0"))
                self.table.setItem(i, j, item)
        self.table.resizeColumnsToContents()

    # ==================== 应用配置 ====================

    def _on_selection(self):
        rows = self.table.selectionModel().selectedRows()
        ok = False
        if rows:
            r = self.rows[rows[0].row()]
            ok = r["ok"]
        self.apply_btn.setEnabled(ok)

    def _apply_selected(self):
        rows = self.table.selectionModel().selectedRows()
        if not rows:
            return
        r = self.rows[rows[0].row()]
        if not r["ok"]:
            return
        self.main.set_selected_config(r["n"], r["m"])
        self.main.switch_tab(2)
        self.main.set_status("已应用配置 %dv%d" % (r["n"], r["m"]))

    def _open_output_root(self):
        os.makedirs(OUTPUT_ROOT, exist_ok=True)
        if hasattr(os, "startfile"):
            os.startfile(OUTPUT_ROOT)

    def _append_log(self, line):
        self.log.appendPlainText(line)

    def on_data_changed(self):
        pass
