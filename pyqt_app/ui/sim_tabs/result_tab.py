# -*- coding: utf-8 -*-
"""「仿真结果」子页签：按配置显示仿真结果数据集（分页表格 + 图表）

对标 `snowy-admin-web/src/views/fscloud/simdata/index.vue` 的单快照模式：

- 类别 / 子类显示由 `result_tab_map.json` 控制（对标 `TableTitleKeyMap.js`），
  每个子类可选配置 `chart` 决定「表格 / 图表」表现
- 结果总览（overview）取自 `getRemoteSimulationResultSnapshot` 的 `summaryResults`（本地分页）
- 第二层数据集（basic / lean / energy）走 `getRemoteSimulationResultDatasetPage` 分页（limit ≤ 1000）
- 图表由 `result_chart.py` 绘制（matplotlib 折线 / 柱状）
"""
import json
import os

from PyQt6.QtCore import QAbstractTableModel, QModelIndex, Qt, pyqtSignal
from PyQt6.QtWidgets import (
    QAbstractItemView, QComboBox, QFrame, QHBoxLayout, QHeaderView, QLabel, QPushButton,
    QScrollArea, QStackedWidget, QTableView, QVBoxLayout, QWidget,
)

from mcp_client.mcp_runner import McpCallRunner
from ui.sim_tabs.result_chart import ResultChart, fmt_timestamp

MAP_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "result_tab_map.json")
GROUP_ORDER = ("overview", "basic", "lean", "energy", "custom")
GROUP_LABELS = {"overview": "结果总览", "basic": "基础结果",
                "lean": "精益结果", "energy": "能耗结果", "custom": "自定义结果"}
CUSTOM_GROUP = "custom"     # 运行时发现的“自定义结果”（参数表中 remark=Out 的输出表）
OVERVIEW_SUMMARY_DATASETS = ("leanMetrics", "energyAnalysis")   # 需要特殊摘要展示的子类
PAGE_SIZES = (25, 50, 100, 200)
MAX_LIMIT = 1000            # MCP 约束：1 <= limit <= 1000


def format_cell(value, meta):
    """按映射表的列定义格式化单元格（对齐前端 ResultTable.vue 的 formatValue）"""
    meta = meta or {}
    kind = meta.get("type")
    if value is None or value == "":
        return "-"
    if kind == "t":
        return fmt_timestamp(value)
    if kind == "i":
        try:
            return str(int(round(float(value))))
        except (TypeError, ValueError):
            return str(value)
    if kind == "f":
        decimals = meta.get("decimals")
        if decimals is None:
            return str(value)      # 未配置小数位（如参数表动态列）时保留原始值
        try:
            decimals = int(decimals)
        except (TypeError, ValueError):
            decimals = 0
        try:
            return ("%%.%df" % decimals) % float(value)
        except (TypeError, ValueError):
            return str(value)
    return str(value)


def _fmt_any(value):
    if value is None:
        return "-"
    if isinstance(value, float):
        return ("%.4f" % value).rstrip("0").rstrip(".")
    return str(value)


class ResultTableModel(QAbstractTableModel):
    """只读结果表模型（列定义来自映射表，行号支持分页偏移）"""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._cols = []          # [(key, meta)]
        self._rows = []
        self._row_offset = 0

    def set_data(self, columns_meta, rows, row_offset=0):
        self.beginResetModel()
        self._rows = list(rows or [])
        self._row_offset = int(row_offset)
        columns_meta = columns_meta or {}
        keys = [k for k in columns_meta if not str(k).startswith("_")]
        for row in self._rows[:1]:
            if not isinstance(row, dict):
                continue
            for key in row:
                if not str(key).startswith("_") and key not in keys:
                    keys.append(key)
        self._cols = [(key, columns_meta.get(key) or {}) for key in keys]
        self.endResetModel()

    def rowCount(self, parent=QModelIndex()):
        return 0 if parent.isValid() else len(self._rows)

    def columnCount(self, parent=QModelIndex()):
        return 0 if parent.isValid() else len(self._cols)

    def headerData(self, section, orientation, role=Qt.ItemDataRole.DisplayRole):
        if role != Qt.ItemDataRole.DisplayRole:
            return None
        if orientation == Qt.Orientation.Vertical:
            return str(self._row_offset + section + 1)
        if section < 0 or section >= len(self._cols):
            return None
        key, meta = self._cols[section]
        title = meta.get("title") or key
        unit = meta.get("unit") or ""
        return "%s(%s)" % (title, unit) if unit else title

    def data(self, index, role=Qt.ItemDataRole.DisplayRole):
        if not index.isValid() or role != Qt.ItemDataRole.DisplayRole:
            return None
        row = self._rows[index.row()]
        key, meta = self._cols[index.column()]
        if not isinstance(row, dict):
            # 防御：Qt 虚函数里抛异常会让进程直接 abort，这里对非字典行返回空串
            return ""
        return format_cell(row.get(key), meta)


class ResultTab(QWidget):
    """仿真结果（配置驱动的分页表格 + 图表）"""

    log_line = pyqtSignal(str)

    def __init__(self, main_window=None, state=None, mcp=None, parent=None):
        super().__init__(parent)
        self.main = main_window
        self.state = state
        self.mcp = mcp

        self.tab_map = self._load_map()
        self.user_id = ""
        self.model_id = ""
        self.result = None          # getResultSnapshot 的 data
        self.active_group = ""
        self.active_dataset = ""
        self.view_mode = "table"    # table | chart
        self.page = 1
        self.page_size = PAGE_SIZES[1]
        self.total = 0
        self.rows = []
        self.custom_tables = {}      # 自定义结果：{tableName: {"columns":…, "rows":…}}
        self.custom_metas = {}       # 自定义结果：动态生成的展示元信息
        self.custom_loaded = False
        self._busy = False
        self._runner = None

        self._build_ui()
        self._refresh_view()

    # ==================== 配置 ====================

    def _load_map(self):
        try:
            with open(MAP_PATH, "r", encoding="utf-8") as fh:
                data = json.load(fh)
            if not isinstance(data, dict):
                raise ValueError("顶层不是对象")
            return data
        except Exception as exc:      # 配置缺失时给出提示而不是崩溃
            self.log_line.emit("[结果] 读取 result_tab_map.json 失败：%r" % exc)
            return {}

    def _group_visible(self, group):
        if group == CUSTOM_GROUP:
            return True           # 「自定义结果」始终可选，进入时懒加载
        return any(self._dataset_visible(group, ds) for ds in (self.tab_map.get(group) or {}))

    def _dataset_visible(self, group, dataset):
        meta = (self.tab_map.get(group) or {}).get(dataset) or {}
        return meta.get("show") is not False

    def _datasets(self, group):
        if group == CUSTOM_GROUP:
            return list(self.custom_tables.keys())
        return [ds for ds in (self.tab_map.get(group) or {}) if self._dataset_visible(group, ds)]

    def _meta(self, group, dataset):
        if group == CUSTOM_GROUP:
            return self.custom_metas.get(dataset) or {}
        return ((self.tab_map.get(group) or {}).get(dataset) or {})

    def _chart_cfg(self, group, dataset):
        chart = self._meta(group, dataset).get("chart")
        return chart if isinstance(chart, dict) and chart.get("y") else None

    # ==================== UI ====================

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        tool_row = QHBoxLayout()
        tool_row.setSpacing(8)
        self.load_btn = QPushButton("⟳ 获取仿真结果")
        self.load_btn.setObjectName("primaryBtn")
        self.load_btn.setFixedHeight(30)
        self.load_btn.setToolTip("getRemoteSimulationResultSnapshot（仿真结束、结果就绪后可读）")
        self.load_btn.clicked.connect(self.load_result)
        tool_row.addWidget(self.load_btn)
        self.overview_label = QLabel("未加载模型")
        self.overview_label.setStyleSheet("color:#94A3B8; font-size:11px;")
        tool_row.addWidget(self.overview_label)
        tool_row.addStretch()
        self.status_label = QLabel("")
        self.status_label.setStyleSheet("color:#64748B; font-size:11px;")
        tool_row.addWidget(self.status_label)
        layout.addLayout(tool_row)

        self.group_scroll, self.group_bar_layout = self._make_bar()
        layout.addWidget(self.group_scroll)
        self.dataset_scroll, self.dataset_bar_layout = self._make_bar()
        layout.addWidget(self.dataset_scroll)

        control_row = QHBoxLayout()
        control_row.setSpacing(8)
        self.table_btn = QPushButton("表格")
        self.chart_btn = QPushButton("图表")
        for btn in (self.table_btn, self.chart_btn):
            btn.setCheckable(True)
            btn.setFixedHeight(26)
            btn.setMinimumWidth(56)
            control_row.addWidget(btn)
        self.table_btn.clicked.connect(lambda: self._set_view_mode("table"))
        self.chart_btn.clicked.connect(lambda: self._set_view_mode("chart"))
        control_row.addSpacing(8)
        self.total_label = QLabel("共 0 条")
        self.total_label.setStyleSheet("color:#64748B; font-size:11px;")
        control_row.addWidget(self.total_label)
        control_row.addStretch()
        self.prev_btn = QPushButton("上一页")
        self.next_btn = QPushButton("下一页")
        for btn in (self.prev_btn, self.next_btn):
            btn.setFixedHeight(26)
            control_row.addWidget(btn)
        self.prev_btn.clicked.connect(lambda: self._goto_page(-1))
        self.next_btn.clicked.connect(lambda: self._goto_page(1))
        self.page_label = QLabel("0 / 0")
        self.page_label.setStyleSheet("color:#94A3B8; font-size:11px;")
        control_row.addWidget(self.page_label)
        size_label = QLabel("每页")
        size_label.setStyleSheet("color:#64748B; font-size:11px;")
        control_row.addWidget(size_label)
        self.page_size_box = QComboBox()
        for size in PAGE_SIZES:
            self.page_size_box.addItem(str(size), size)
        self.page_size_box.setCurrentText(str(self.page_size))
        self.page_size_box.setFixedHeight(26)
        self.page_size_box.currentIndexChanged.connect(self._on_page_size_changed)
        control_row.addWidget(self.page_size_box)
        layout.addLayout(control_row)

        self.stack = QStackedWidget()
        self.table_model = ResultTableModel(self)
        self.table = QTableView()
        self.table.setModel(self.table_model)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.verticalHeader().setDefaultSectionSize(22)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        self.table.horizontalHeader().setMinimumHeight(28)
        self.chart = ResultChart()
        self.stack.addWidget(self.table)
        self.stack.addWidget(self.chart)
        layout.addWidget(self.stack, 1)

    def _make_bar(self):
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFixedHeight(46)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        bar = QWidget()
        bar_layout = QHBoxLayout(bar)
        bar_layout.setContentsMargins(0, 0, 0, 0)
        bar_layout.setSpacing(4)
        scroll.setWidget(bar)
        scroll.setVisible(False)
        return scroll, bar_layout

    @staticmethod
    def _clear_bar(bar_layout):
        while bar_layout.count():
            item = bar_layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()

    # ==================== 对外入口 ====================

    def on_model_loaded(self, user_id, model_id):
        """模型加载成功后登记（结果需仿真结束、停止后手动获取）"""
        self.user_id = user_id or ""
        self.model_id = model_id or ""
        self.result = None
        self.custom_tables, self.custom_metas, self.custom_loaded = {}, {}, False
        self._set_status("已登记模型；仿真结束后点「获取仿真结果」")
        self._refresh_view()

    def reset(self):
        """模型卸载后清空：数据、显示与按钮状态"""
        self.user_id = ""
        self.model_id = ""
        self.result = None
        self.rows = []
        self.total = 0
        self.page = 1
        self.active_group = ""
        self.active_dataset = ""
        self.view_mode = "table"
        self.custom_tables, self.custom_metas, self.custom_loaded = {}, {}, False
        self.table_model.set_data({}, [], 0)     # 清空表格内容
        self.chart.clear()
        self._set_status("")                     # 「获取仿真结果」使能由 _refresh_pager 统一维护
        self._refresh_view()

    # ==================== 取结果 ====================

    def load_result(self):
        if not (self.mcp and self.user_id and self.model_id):
            self._set_status("尚无已加载模型")
            return
        if self._busy:
            self._log("[结果] 上一次调用尚未结束，已忽略")
            return
        user_id, model_id = self.user_id, self.model_id
        self._log("[结果] 请求结果快照: userId=%s modelId=%s" % (user_id, model_id))
        self._start("result_snapshot", lambda: self.mcp.getResultSnapshot(user_id, model_id, ""))

    def _handle_result(self, result):
        if not result.get("success", False):
            self._log("[结果] 取结果快照失败 → %s: %s"
                      % (result.get("code", "?"), result.get("message", "")))
            self._set_status("取结果快照失败：%s" % result.get("code", "?"))
            return
        data = result.get("data") or {}
        status = data.get("resultStatus")
        if status != "READY":
            self.result = None
            self.rows, self.total = [], 0
            self._set_status("结果未就绪（resultStatus=%s）：请在仿真结束后再获取" % status)
            self._log("[结果] resultStatus=%s，未就绪" % status)
            self._refresh_view()
            return

        self.result = data
        self.custom_tables, self.custom_metas, self.custom_loaded = {}, {}, False
        self._log("[结果] 就绪：runId=%s simTime=%s totalOutput=%s datasets=%d"
                  % (data.get("runId"), data.get("simTime"), data.get("totalOutput"),
                     len(data.get("datasets") or [])))
        self._set_status("结果就绪（runId=%s）" % (data.get("runId") or "-"))
        groups = [g for g in GROUP_ORDER if g in self.tab_map and self._group_visible(g)]
        group = self.active_group if self.active_group in groups else (groups[0] if groups else "")
        self.select_group(group)

    # ==================== 选择与加载 ====================

    def _refresh_groups(self):
        self._clear_bar(self.group_bar_layout)
        groups = [g for g in GROUP_ORDER if g in self.tab_map and self._group_visible(g)]
        self.group_scroll.setVisible(bool(groups))
        for group in groups:
            btn = QPushButton(GROUP_LABELS.get(group, group))
            btn.setCheckable(True)
            btn.setChecked(group == self.active_group)
            btn.setFixedHeight(30)
            btn.setMinimumWidth(70)
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            btn.clicked.connect(lambda _checked=False, g=group: self.select_group(g))
            self.group_bar_layout.addWidget(btn)
        self.group_bar_layout.addStretch()

    def _refresh_datasets(self):
        self._clear_bar(self.dataset_bar_layout)
        datasets = self._datasets(self.active_group) if self.active_group else []
        if self.active_group != CUSTOM_GROUP and self.result is None:
            datasets = []      # 未取到结果（或已关闭模型）时不显示子类
        self.dataset_scroll.setVisible(bool(datasets))
        for dataset in datasets:
            meta = self._meta(self.active_group, dataset)
            btn = QPushButton(meta.get("tabBrief") or dataset)
            btn.setCheckable(True)
            btn.setChecked(dataset == self.active_dataset)
            btn.setFixedHeight(30)
            btn.setMinimumWidth(60)
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            btn.setToolTip("%s\n%s" % (dataset, meta.get("tabTitle") or ""))
            btn.clicked.connect(lambda _checked=False, d=dataset: self.select_dataset(d))
            self.dataset_bar_layout.addWidget(btn)
        self.dataset_bar_layout.addStretch()

    def select_group(self, group):
        if not group:
            return
        self.active_group = group
        datasets = self._datasets(group)
        self.active_dataset = datasets[0] if datasets else ""
        self.page = 1
        self._refresh_groups()
        self._refresh_datasets()
        self._load_current()

    def select_dataset(self, dataset):
        if not dataset or dataset == self.active_dataset:
            return
        self.active_dataset = dataset
        self.page = 1
        self._refresh_datasets()
        self._load_current()

    def _load_current(self):
        """按当前组 / 子类加载：总览与自定义结果本地分页，其余走数据集分页接口"""
        if self.active_group == CUSTOM_GROUP:
            self._load_custom()          # 尚未拉取时触发懒加载（此时可能还没有子类）
            return
        if not self.active_dataset:
            self._show_rows({}, [], 0, 0)
            return
        if self.active_group == "overview":
            self._load_overview()
            return
        if self.result is None:
            self._show_rows({}, [], 0, 0)
            return
        if self._busy:
            return
        user_id, model_id = self.user_id, self.model_id
        run_id = self.result.get("runId") or ""
        dataset = self.active_dataset
        offset = (self.page - 1) * self.page_size
        limit = min(self.page_size, MAX_LIMIT)
        self._log("[结果] 读取数据集 %s（offset=%d limit=%d）" % (dataset, offset, limit))
        self._start("result_page",
                    lambda: self.mcp.getResultDatasetPage(user_id, model_id, run_id, dataset,
                                                          limit, offset))

    def _load_local(self, columns, rows):
        """本地分页显示（结果总览摘要 / 自定义结果）"""
        total = len(rows)
        offset = (self.page - 1) * self.page_size
        self._show_rows(columns, rows[offset:offset + self.page_size], offset, total)

    def _load_overview(self):
        """结果总览：taktTime/uph/utilRate 用 summaryResults 的行；leanMetrics/energyAnalysis 用摘要"""
        if self.result is None:
            self._show_rows({}, [], 0, 0)
            return
        dataset = self.active_dataset
        if dataset in OVERVIEW_SUMMARY_DATASETS:
            columns, rows = self._summary_rows(dataset)
            self._load_local(columns, rows)
            return
        block = ((self.result or {}).get("summaryResults") or {}).get(dataset) or {}
        self._load_local(self._meta("overview", dataset).get("columns") or {},
                         block.get("rows") or [])

    def _load_custom(self):
        """自定义结果：按需拉取参数表快照中 remark=Out 的输出表（只读展示）"""
        if not self.custom_loaded:
            if not (self.mcp and self.user_id and self.model_id):
                self._set_status("尚无已加载模型")
                self._show_rows({}, [], 0, 0)
                return
            if self._busy:
                return
            self.custom_loaded = True       # 先占位，失败时复位以便重试
            self._set_status("正在读取自定义结果（参数表中 remark=Out 的输出表）…")
            user_id, model_id = self.user_id, self.model_id
            self._start("param_snapshot",
                        lambda: {"list": self.mcp.listParameterTables(user_id, model_id),
                                 "snapshot": self.mcp.getParameterSnapshot(user_id, model_id, "")})
            return
        meta = self.custom_metas.get(self.active_dataset) or {}
        rows = (self.custom_tables.get(self.active_dataset) or {}).get("rows") or []
        self._load_local(meta.get("columns") or {}, rows)

    def _handle_custom_tables(self, result):
        """解析参数表清单 + 快照，把 remark=Out 的表登记为「自定义结果」子类"""
        listing = result.get("list") or {}
        snapshot = result.get("snapshot") or {}
        if not listing.get("success", False):
            self.custom_loaded = False
            self._set_status("读取自定义结果失败：%s" % listing.get("code", "?"))
            return
        list_data = listing.get("data") or {}
        if not list_data.get("mcpTableFound"):
            self.custom_tables, self.custom_metas = {}, {}
            self.custom_loaded = True
            self._refresh_datasets()
            self._show_rows({}, [], 0, 0)
            self._set_status("模型中未配置 MCP 表，无自定义结果")
            return

        remarks = {}
        for row in (list_data.get("parameterTables") or []):
            remarks[self._normalize(row.get("tableName"))] = (row.get("remark") or "").strip()
        snap = snapshot.get("data") or {}
        for row in (snap.get("mcpRows") or []):        # 兜底：清单缺 remark
            key = self._normalize(row.get("tableName"))
            if key not in remarks:
                remarks[key] = (row.get("remark") or "").strip()

        tables, metas = {}, {}
        for table in (snap.get("tables") or []):
            name = table.get("tableName") or ""
            if not name or remarks.get(self._normalize(name), "").lower() != "out":
                continue
            columns = {}
            for col in (table.get("columns") or []):
                col_name = col.get("name") or ""
                if not col_name:
                    continue
                columns[col_name] = {"title": col_name, "unit": "",
                                     "type": self._kind_of(col.get("type")), "decimals": None}
            col_names = [col.get("name") for col in (table.get("columns") or [])
                         if col.get("name")]
            row_dicts = []
            for raw in (table.get("rows") or []):
                # 参数表的行是二维数组，这里转成"列名 → 值"的行字典（结果页模型按字典行取值）
                values = list(raw) if isinstance(raw, (list, tuple)) else [raw]
                row_dicts.append({col_names[i]: (values[i] if i < len(values) else "")
                                  for i in range(len(col_names))})
            tables[name] = {"columns": columns, "rows": row_dicts}
            metas[name] = {"show": True, "tabTitle": name, "tabBrief": name, "columns": columns}

        self.custom_tables, self.custom_metas = tables, metas
        self.custom_loaded = True
        self._log("[结果] 自定义结果（remark=Out）：%s" % ("、".join(tables) if tables else "无"))
        self._refresh_datasets()
        datasets = self._datasets(CUSTOM_GROUP)
        self.active_dataset = datasets[0] if datasets else ""
        if not datasets:
            self._set_status("未发现 remark=Out 的参数表（自定义结果为空）")
            self._show_rows({}, [], 0, 0)
            return
        self._set_status("自定义结果 %d 张表（remark=Out）：%s"
                         % (len(datasets), "、".join(datasets)))
        self.page = 1
        self._load_current()

    @staticmethod
    def _normalize(name):
        """表名归一化（清单给 `.结论`、快照给 `结论`）"""
        name = (name or "").strip().lstrip(".")
        if name.lower().startswith("param_"):
            name = name[6:]
        return name

    @staticmethod
    def _kind_of(raw_type):
        """参数表列类型（int/double/String…）→ 映射表类型码（i/f/s）"""
        kind = str(raw_type or "").lower()
        if kind in ("int", "integer", "long", "short", "byte", "i"):
            return "i"
        if kind in ("double", "float", "number", "decimal", "f"):
            return "f"
        return "s"

    def _summary_rows(self, dataset):
        """把 leanMetrics / energyAnalysis 的 summary 摊平成「项目 / 数值」两列"""
        columns = {"name": {"title": "项目", "unit": "", "type": "s"},
                   "value": {"title": "数值", "unit": "", "type": "s"}}
        block = ((self.result or {}).get("summaryResults") or {}).get(dataset) or {}
        rows_raw = block.get("rows") or []
        if not rows_raw:
            return columns, []
        summary = rows_raw[0].get("summary") or {}
        out = []
        if dataset == "energyAnalysis":
            for key, title in (("mediumCount", "介质数量"), ("deviceCount", "设备数量"),
                               ("totalEnergy", "总能耗")):
                if key in summary:
                    out.append({"name": title, "value": _fmt_any(summary.get(key))})
            for device, energy in (summary.get("totalByDevice") or {}).items():
                out.append({"name": device, "value": _fmt_any(energy)})
        else:
            for key, value in summary.items():
                if isinstance(value, (dict, list)):
                    out.append({"name": key, "value": json.dumps(value, ensure_ascii=False)})
                else:
                    out.append({"name": key, "value": _fmt_any(value)})
        return columns, out

    def _handle_dataset_page(self, result):
        if not result.get("success", False):
            code = result.get("code", "?")
            message = result.get("message", "")
            if code == "RESULT_NOT_READY":
                message = "结果未就绪，请在仿真结束后再获取"
            self._log("[结果] 读取数据集失败 → %s: %s" % (code, message))
            self._set_status("读取数据集失败：%s" % message)
            self._show_rows({}, [], 0, 0)
            return
        data = result.get("data") or {}
        rows = data.get("rows") or []
        total = int(data.get("total") or len(rows))
        offset = int(data.get("offset") or 0)
        self._log("[结果] %s 返回 %d 行（total=%s hasMore=%s）"
                  % (data.get("dataset"), len(rows), total, data.get("hasMore")))
        self._set_status("%s：本页 %d 行 / 共 %s 行"
                         % (data.get("dataset"), len(rows), total))
        columns = self._meta(self.active_group, self.active_dataset).get("columns") or {}
        self._show_rows(columns, rows, offset, total)

    # ==================== 展示 ====================

    def _show_rows(self, columns_meta, rows, offset, total):
        self.rows = rows
        self.total = total
        self.table_model.set_data(columns_meta, rows, offset)
        self.table.resizeColumnsToContents()
        self._refresh_pager()
        self._apply_view()

    def _refresh_pager(self):
        pages = max(1, (self.total + self.page_size - 1) // self.page_size) if self.total else 0
        self.total_label.setText("共 %d 条" % self.total)
        self.page_label.setText("%d / %d" % (self.page if pages else 0, pages))
        self.prev_btn.setEnabled(self.page > 1 and not self._busy)
        self.next_btn.setEnabled(pages > 0 and self.page < pages and not self._busy)
        self.page_size_box.setEnabled(not self._busy)
        # 「获取仿真结果」使能统一在此维护：有已加载模型且当前无调用即可点（模型关闭后禁用）
        self.load_btn.setEnabled(bool(self.user_id and self.model_id) and not self._busy)

    def _apply_view(self):
        chart_cfg = self._chart_cfg(self.active_group, self.active_dataset)
        has_chart = chart_cfg is not None
        self.chart_btn.setVisible(has_chart)
        self.table_btn.setVisible(has_chart)
        if not has_chart:
            self.view_mode = "table"
        self.table_btn.setChecked(self.view_mode == "table")
        self.chart_btn.setChecked(self.view_mode == "chart")
        if has_chart and self.view_mode == "chart":
            columns = self._meta(self.active_group, self.active_dataset).get("columns") or {}
            self.chart.plot(chart_cfg, self.rows, columns)
            self.stack.setCurrentWidget(self.chart)
        else:
            self.stack.setCurrentWidget(self.table)

    def _set_view_mode(self, mode):
        self.view_mode = mode
        self._apply_view()

    def _goto_page(self, delta):
        if self._busy:
            return
        pages = max(1, (self.total + self.page_size - 1) // self.page_size) if self.total else 0
        target = self.page + delta
        if target < 1 or (pages and target > pages):
            return
        self.page = target
        self._load_current()

    def _on_page_size_changed(self):
        size = self.page_size_box.currentData()
        if not size:
            return
        self.page_size = int(size)
        self.page = 1
        self._load_current()

    def _refresh_view(self):
        self.overview_label.setText(self._overview_text())   # 概览随模型/结果变化
        self._refresh_groups()
        self._refresh_datasets()
        self._refresh_pager()
        self._apply_view()

    def _set_status(self, text):
        self.status_label.setText(text or "")

    def _overview_text(self):
        data = self.result or {}
        if not data:
            return "未加载模型" if not self.model_id else "尚无结果（点「获取仿真结果」）"
        return ("runId=%s · 时长 %s · 总产量 %s · UPH %s · 当前WIP %s"
                % (data.get("runId") or "-", fmt_timestamp(data.get("simTime")),
                   data.get("totalOutput"), data.get("throughputPerHour"), data.get("currentWip")))

    # ==================== 后台调用 ====================

    def _start(self, label, fn):
        if self._busy:
            return
        self._busy = True
        self.load_btn.setEnabled(False)
        self._refresh_pager()
        runner = McpCallRunner(label, fn, parent=self)
        runner.log_line.connect(self._log)
        runner.done.connect(self._on_done)
        runner.failed.connect(self._on_failed)
        self._runner = runner
        runner.start()

    def _on_done(self, label, result):
        self._busy = False
        try:
            if label == "result_snapshot":
                self._handle_result(result)
            elif label == "result_page":
                self._handle_dataset_page(result)
            elif label == "param_snapshot":
                self._handle_custom_tables(result)
        finally:
            self.load_btn.setEnabled(bool(self.user_id and self.model_id))
            self.overview_label.setText(self._overview_text())
            self._refresh_pager()
            self._apply_view()

    def _on_failed(self, label, message):
        self._busy = False
        if label == "param_snapshot":
            self.custom_loaded = False      # 允许重新拉取
        self._log("[结果] 调用失败 → %s" % message)
        self._set_status("MCP 调用失败：%s" % message)
        self.load_btn.setEnabled(bool(self.user_id and self.model_id))
        self._refresh_pager()

    # ==================== 工具 ====================

    def _log(self, message):
        self.log_line.emit(message)
