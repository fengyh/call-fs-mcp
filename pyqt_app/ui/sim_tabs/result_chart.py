# -*- coding: utf-8 -*-
"""仿真结果图表（matplotlib）：柱状图 / 折线图

- 深色主题与主界面一致；中文字体使用 Microsoft YaHei
- 图表由 `result_tab_map.json` 的 `dataset.chart` 配置驱动：
  `{"type": "bar"|"line", "x": 列名, "y": [列名…], "stacked": bool, "groupBy": 列名, "title": str}`
- 时间类列（`type=t`，仿真毫秒）在坐标轴与标签上按前端规则格式化（见 `fmt_timestamp`）
"""
import matplotlib

matplotlib.use("QtAgg")

from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg
from matplotlib.figure import Figure
from matplotlib.ticker import FuncFormatter
from PyQt6.QtWidgets import QVBoxLayout, QWidget

matplotlib.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
matplotlib.rcParams["axes.unicode_minus"] = False

BG = "#0F172A"          # 画布底色（同主界面）
AX_BG = "#111C2E"       # 绘图区底色
FG = "#E2E8F0"          # 前景色
GRID = "#334155"        # 网格/边框
PALETTE = ["#3B82F6", "#22C55E", "#F59E0B", "#EF4444",
           "#A855F7", "#06B6D4", "#84CC16", "#EC4899"]


def fmt_timestamp(ms):
    """仿真毫秒 → 时间串（对齐前端 ResultTable.vue 的 fmtTimestamp）

    天>0 → "X天 hh:mm:ss"；时>0 → "hh:mm:ss"；分>0 → "mm:ss"；秒>0 → "s"；否则 "0"。
    """
    if ms is None or ms == "":
        return "-"
    try:
        total = int(float(ms))
    except (TypeError, ValueError):
        return "-"
    if total < 0:
        return "n/a"
    total_sec = total // 1000
    days, rem = divmod(total_sec, 86400)
    hours, rem = divmod(rem, 3600)
    minutes, seconds = divmod(rem, 60)
    if days > 0:
        return "%d天 %02d:%02d:%02d" % (days, hours, minutes, seconds)
    if hours > 0:
        return "%02d:%02d:%02d" % (hours, minutes, seconds)
    if minutes > 0:
        return "%02d:%02d" % (minutes, seconds)
    if seconds > 0:
        return "%d" % seconds
    return "0"


def to_number(value):
    """转 float；无法转换或空值返回 None"""
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def axis_label(key, meta):
    """坐标轴/图例名称："标题(单位)" """
    meta = meta or {}
    title = meta.get("title") or key or ""
    unit = meta.get("unit") or ""
    return "%s(%s)" % (title, unit) if unit else title


def value_label(value, meta):
    """类别轴上的取值文本"""
    meta = meta or {}
    if meta.get("type") == "t":
        return fmt_timestamp(value)
    return "-" if value is None else str(value)


class ResultChart(QWidget):
    """结果图表（matplotlib 画布嵌入 Qt）"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.figure = Figure(figsize=(6.0, 4.0), dpi=100, facecolor=BG)
        self.canvas = FigureCanvasQTAgg(self.figure)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.canvas)

    # ==================== 绘制 ====================

    def plot(self, chart, rows, columns):
        """按 chart 配置绘制；rows 为行字典列表，columns 为映射表中的列定义"""
        chart = chart or {}
        columns = columns or {}
        self.figure.clear()
        axes = self.figure.add_subplot(111)
        self._style_axes(axes)

        x_key = chart.get("x")
        y_keys = [k for k in (chart.get("y") or []) if k]
        if not rows or not y_keys:
            axes.text(0.5, 0.5, "暂无数据", ha="center", va="center",
                      color="#64748B", transform=axes.transAxes)
            self.canvas.draw_idle()
            return

        if chart.get("type") == "line":
            self._plot_lines(axes, chart, rows, columns, x_key, y_keys)
        else:
            self._plot_bars(axes, chart, rows, columns, x_key, y_keys)

        title = chart.get("title") or ""
        if title:
            axes.set_title(title, color=FG, fontsize=11)
        self.figure.tight_layout()
        self.canvas.draw_idle()

    def clear(self):
        self.figure.clear()
        axes = self.figure.add_subplot(111)
        self._style_axes(axes)
        axes.text(0.5, 0.5, "暂无数据", ha="center", va="center",
                  color="#64748B", transform=axes.transAxes)
        self.canvas.draw_idle()

    # ==================== 内部实现 ====================

    def _plot_lines(self, axes, chart, rows, columns, x_key, y_keys):
        y_key = y_keys[0]
        x_meta = columns.get(x_key) or {}
        y_meta = columns.get(y_key) or {}
        x_is_time = x_meta.get("type") == "t"
        group_key = chart.get("groupBy")

        groups = {}
        for row in rows:
            groups.setdefault(row.get(group_key) if group_key else None, []).append(row)

        for i, (group, grows) in enumerate(groups.items()):
            points = []
            for row in grows:
                x_val, y_val = to_number(row.get(x_key)), to_number(row.get(y_key))
                if x_val is None or y_val is None:
                    continue
                points.append((x_val, y_val))
            if not points:
                continue
            points.sort(key=lambda p: p[0])
            xs = [(p[0] / 1000.0) if x_is_time else p[0] for p in points]
            ys = [p[1] for p in points]
            label = str(group) if group not in (None, "") else axis_label(y_key, y_meta)
            axes.plot(xs, ys, label=label, linewidth=1.4, color=PALETTE[i % len(PALETTE)])

        if x_is_time:
            axes.xaxis.set_major_formatter(FuncFormatter(lambda v, _pos: fmt_timestamp(v * 1000)))
        axes.set_xlabel(axis_label(x_key, x_meta), color=FG, fontsize=9)
        axes.set_ylabel(axis_label(y_key, y_meta), color=FG, fontsize=9)
        if len(groups) > 1:
            axes.legend(fontsize=8, facecolor=AX_BG, edgecolor=GRID, labelcolor=FG)

    def _plot_bars(self, axes, chart, rows, columns, x_key, y_keys):
        x_meta = columns.get(x_key) or {}
        labels = [value_label(row.get(x_key), x_meta) for row in rows]
        positions = list(range(len(rows)))
        stacked = bool(chart.get("stacked"))
        single = len(y_keys) == 1
        width = 0.7 if (single or stacked) else 0.8 / len(y_keys)
        bottom = [0.0] * len(rows)

        for i, y_key in enumerate(y_keys):
            y_meta = columns.get(y_key) or {}
            values = [to_number(row.get(y_key)) or 0.0 for row in rows]
            if stacked:
                axes.bar(positions, values, width=width, bottom=bottom,
                         label=axis_label(y_key, y_meta), color=PALETTE[i % len(PALETTE)])
                bottom = [b + v for b, v in zip(bottom, values)]
            else:
                offset = 0.0 if single else (i - (len(y_keys) - 1) / 2.0) * width
                axes.bar([p + offset for p in positions], values, width=width * (0.9 if single else 1.0),
                         label=axis_label(y_key, y_meta), color=PALETTE[i % len(PALETTE)])

        axes.set_xticks(positions)
        axes.set_xticklabels(labels, rotation=30, ha="right", fontsize=8)
        if len(y_keys) > 1:
            axes.legend(fontsize=8, facecolor=AX_BG, edgecolor=GRID, labelcolor=FG)

    @staticmethod
    def _style_axes(axes):
        axes.set_facecolor(AX_BG)
        axes.tick_params(colors=FG, labelsize=8)
        for spine in axes.spines.values():
            spine.set_color(GRID)
        axes.grid(True, color=GRID, linestyle="--", linewidth=0.5, alpha=0.5)
