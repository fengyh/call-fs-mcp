# -*- coding: utf-8 -*-
"""「仿真参数」子页签：读取 / 编辑 / 回写 MCP 参数表

对标 `snowy-admin-web/src/views/fscloud/simparam/index.vue`（本实现不做“快照写库”）：

- 取参数：`listRemoteSimulationParameterTables`（清单 + remark）+ `getRemoteSimulationParameterSnapshot`（tables）
- 过滤：`remark` 为 `Out` 的表不显示（留给「仿真结果」页处理）
- 编辑：单元格与列名（表头）可编辑，脏标记高亮，可一键还原到上次取到的内容
- 回写：`batchUpdateRemoteSimulationParameterTable(..., bSaveModel=True)`：回写内存模型并保存 `.fs` 文件
"""
import copy
import json

from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QBrush, QColor
from PyQt6.QtWidgets import (
    QAbstractItemView, QFrame, QHBoxLayout, QHeaderView, QInputDialog, QLabel, QMessageBox,
    QPushButton, QScrollArea, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget,
)

from mcp_client.mcp_runner import McpCallRunner

DIRTY_BG = "#FDE68A"        # 脏单元格底色（浅黄，同前端 .pte-cell-dirty）
DIRTY_FG = "#1F2937"
OUT_REMARK = "out"          # 该 remark 的表属于输出表，不在参数页显示（留给仿真结果页）


class ParameterTab(QWidget):
    """仿真参数表（读取 / 编辑 / 回写模型）"""

    log_line = pyqtSignal(str)      # 转发到 MCP 调用日志

    def __init__(self, main_window=None, state=None, mcp=None, parent=None):
        super().__init__(parent)
        self.main = main_window
        self.state = state
        self.mcp = mcp

        self.user_id = ""
        self.model_id = ""
        self.tables = {}            # {tableName: {"columns": [...], "rows": [[...]]}}
        self.backup = {}            # 上次取到的原始内容（脏标记比较 + 还原基准）
        self.table_names = []       # 显示的表名（已过滤 remark=Out）
        self.active_table = ""
        self.mcp_table_name = ""
        self.dropped_tables = []    # 因 remark=Out 被过滤的表名
        self.dirty_cells = set()    # {(tableName, row, col)}
        self.dirty_headers = set()  # {(tableName, col)}

        self._loading = False       # 填充表格期间抑制脏标记
        self._busy = False
        self._load_runner = None
        self._save_runner = None

        self._build_ui()
        if self.state is not None:
            self.state.changed.connect(self._refresh_buttons)
        self._refresh_view()

    # ==================== UI ====================

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        tool_row = QHBoxLayout()
        tool_row.setSpacing(8)
        self.load_btn = QPushButton("⟳ 取模型参数")
        self.load_btn.setFixedHeight(30)
        self.load_btn.setToolTip("从模型读取参数表清单与内容（会丢弃未保存的修改）")
        self.load_btn.clicked.connect(self.load_parameters)
        self.save_btn = QPushButton("💾 保存到模型")
        self.save_btn.setObjectName("primaryBtn")
        self.save_btn.setFixedHeight(30)
        self.save_btn.setToolTip("batchUpdateRemoteSimulationParameterTable(..., bSaveModel=true)，"
                                 "写入内存模型并保存 .fs 文件")
        self.save_btn.clicked.connect(self.save_to_model)
        self.restore_btn = QPushButton("↩ 还原")
        self.restore_btn.setFixedHeight(30)
        self.restore_btn.setToolTip("放弃本地修改，回到上次取到的内容")
        self.restore_btn.clicked.connect(self.restore)
        for btn in (self.load_btn, self.save_btn, self.restore_btn):
            tool_row.addWidget(btn)
        self.dirty_label = QLabel("")
        self.dirty_label.setStyleSheet("color:#F59E0B; font-size:11px;")
        tool_row.addWidget(self.dirty_label)
        tool_row.addStretch()
        self.summary_label = QLabel("未加载模型")       # 参数表概览（随视图刷新）
        self.summary_label.setStyleSheet("color:#64748B; font-size:11px;")
        tool_row.addWidget(self.summary_label)
        self.status_label = QLabel("")                  # 操作反馈（不会被视图刷新覆盖）
        self.status_label.setStyleSheet("color:#64748B; font-size:11px;")
        tool_row.addWidget(self.status_label)
        layout.addLayout(tool_row)

        # 参数表选择条（一行排列，超出横向滚动）
        self.table_scroll = QScrollArea()
        self.table_scroll.setWidgetResizable(True)
        self.table_scroll.setFixedHeight(46)
        self.table_scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.table_scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self.table_scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.table_bar = QWidget()
        self.table_bar_layout = QHBoxLayout(self.table_bar)
        self.table_bar_layout.setContentsMargins(0, 0, 0, 0)
        self.table_bar_layout.setSpacing(4)
        self.table_scroll.setWidget(self.table_bar)
        self.table_scroll.setVisible(False)
        layout.addWidget(self.table_scroll)

        self.table = QTableWidget()
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.DoubleClicked
                                   | QAbstractItemView.EditTrigger.EditKeyPressed
                                   | QAbstractItemView.EditTrigger.AnyKeyPressed)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectItems)
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        header.setMinimumHeight(28)
        header.setSectionsClickable(True)
        header.setToolTip("双击列名可修改")
        header.sectionDoubleClicked.connect(self.rename_column)
        self.table.itemChanged.connect(self._on_item_changed)
        layout.addWidget(self.table, 1)

    # ==================== 对外入口 ====================

    def on_model_loaded(self, user_id, model_id):
        """模型加载成功后调用：登记模型并自动取一次参数"""
        self.user_id = user_id or ""
        self.model_id = model_id or ""
        self._clear_data()
        self._log("[参数] 已登记模型 modelId=%s，开始读取参数表" % self.model_id)
        self.load_parameters(silent=True)

    def reset(self):
        """模型卸载后清空"""
        self.user_id = ""
        self.model_id = ""
        self._clear_data()
        self._refresh_view()

    def has_dirty(self):
        return bool(self.dirty_cells or self.dirty_headers)

    def confirm_discard(self, action="该操作"):
        """有未保存修改时弹确认，返回是否可以继续（供 mcp_module 卸载前调用）"""
        if not self.has_dirty():
            return True
        box = QMessageBox(self)
        box.setWindowTitle("参数表有未保存的修改")
        box.setText("参数表存在未保存的修改，%s 将丢弃这些修改。\n是否继续？" % action)
        yes = box.addButton("继续", QMessageBox.ButtonRole.AcceptRole)
        box.addButton("取消", QMessageBox.ButtonRole.RejectRole)
        box.exec()
        return box.clickedButton() is yes

    # ==================== 取参数 ====================

    def load_parameters(self, silent=False):
        """读取参数表清单与快照，并按 remark 过滤输出表"""
        if not (self.mcp and self.user_id and self.model_id):
            self._log("[参数] 尚无已加载模型，无法读取参数表")
            self._set_status("尚无已加载模型")
            return
        if self._busy:
            self._log("[参数] 上一次调用尚未结束，已忽略")
            return
        if not silent and not self.confirm_discard("重新取参数"):
            return
        self._start("param_load", self._fetch_parameters)

    def _fetch_parameters(self):
        """后台线程：清单 + 快照（两次 MCP 调用，保证顺序）"""
        user_id, model_id = self.user_id, self.model_id
        return {
            "list": self.mcp.listParameterTables(user_id, model_id),
            "snapshot": self.mcp.getParameterSnapshot(user_id, model_id, ""),
        }

    def _handle_parameters(self, result):
        listing = result.get("list") or {}
        snapshot = result.get("snapshot") or {}
        if not listing.get("success", False):
            self._log("[参数] 取参数表清单失败 → %s: %s"
                      % (listing.get("code", "?"), listing.get("message", "")))
            self._set_status("取参数表失败")
            return

        list_data = listing.get("data") or {}
        self.mcp_table_name = list_data.get("mcpTableName") or ""
        if not list_data.get("mcpTableFound"):
            self._log("[参数] 模型中未找到 MCP 参数表（mcpTableName=%s）" % self.mcp_table_name)
            self._clear_data()
            self._refresh_view()
            self._set_status("模型中未配置 MCP 参数表")
            return

        # remark 映射：清单给出 `.结论`，快照给出 `结论`，按归一化表名匹配
        remarks = {}
        for row in (list_data.get("parameterTables") or []):
            remarks[self._normalize(row.get("tableName"))] = (row.get("remark") or "").strip()
        snap_data = snapshot.get("data") or {}
        for row in (snap_data.get("mcpRows") or []):     # 兜底：清单缺 remark 时用 mcpRows
            key = self._normalize(row.get("tableName"))
            if key not in remarks:
                remarks[key] = (row.get("remark") or "").strip()

        kept, dropped, tables = [], [], {}
        for table in (snap_data.get("tables") or []):
            name = table.get("tableName") or ""
            if not name:
                continue
            if remarks.get(self._normalize(name), "").lower() == OUT_REMARK:
                dropped.append(name)
                continue
            columns = table.get("columns") or []
            rows = [list(r) for r in (table.get("rows") or [])]
            tables[name] = {"columns": copy.deepcopy(columns), "rows": rows}
            kept.append(name)

        self.tables = tables
        self.backup = copy.deepcopy(tables)
        self.table_names = kept
        self.dropped_tables = dropped
        self.dirty_cells.clear()
        self.dirty_headers.clear()
        if self.active_table not in kept:
            self.active_table = kept[0] if kept else ""
        self._log("[参数] 已读取 %d 张参数表%s"
                  % (len(kept), ("，已过滤 remark=Out：%s" % "、".join(dropped)) if dropped else ""))
        self._refresh_view()

    @staticmethod
    def _normalize(name):
        """表名归一化：去前导点与 `Param_` 前缀（清单与快照的表名格式不一致）"""
        name = (name or "").strip().lstrip(".")
        if name.lower().startswith("param_"):
            name = name[6:]
        return name

    # ==================== 编辑与保存 ====================

    def _on_item_changed(self, item):
        if self._loading or not self.active_table:
            return
        data = self.tables.get(self.active_table)
        base = self.backup.get(self.active_table)
        if data is None or base is None:
            return
        col = item.column()
        if item is self.table.horizontalHeaderItem(col):
            return      # 表头（列名）用双击 + 输入框修改，见 rename_column()
        row = item.row()
        if row >= len(data["rows"]) or col >= len(data["rows"][row]):
            return
        original = ""
        if row < len(base["rows"]) and col < len(base["rows"][row]):
            original = str(base["rows"][row][col])
        value = item.text()
        data["rows"][row][col] = value
        key = (self.active_table, row, col)
        if value != original:
            self.dirty_cells.add(key)
        else:
            self.dirty_cells.discard(key)
        # 只刷新该单元格样式；期间屏蔽信号，避免 setBackground 再次触发本回调形成递归
        prev_loading, self._loading = self._loading, True
        blocked = self.table.blockSignals(True)
        try:
            self._style_cell(item, row, col)
        finally:
            self.table.blockSignals(blocked)
            self._loading = prev_loading
        self._refresh_buttons()

    def rename_column(self, col):
        """双击表头修改列名（QTableWidget 表头不支持原地编辑，用输入框）"""
        if self._loading or not self.active_table:
            return
        data = self.tables.get(self.active_table)
        if not data or col < 0 or col >= len(data["columns"]):
            return
        current = str(data["columns"][col].get("name") or "")
        text, ok = QInputDialog.getText(self, "修改列名", "列名：", text=current)
        text = (text or "").strip()
        if not ok or text == current:
            return
        data["columns"][col]["name"] = text
        item = self.table.horizontalHeaderItem(col)
        if item is not None:
            self._loading = True
            item.setText(text)
            self._loading = False
        base_cols = (self.backup.get(self.active_table) or {}).get("columns") or []
        base_name = str(base_cols[col].get("name") or "") if col < len(base_cols) else ""
        key = (self.active_table, col)
        if text != base_name:
            self.dirty_headers.add(key)
        else:
            self.dirty_headers.discard(key)
        prev_loading, self._loading = self._loading, True
        blocked = self.table.blockSignals(True)
        try:
            if item is not None:
                self._style_header(item, col)
        finally:
            self.table.blockSignals(blocked)
            self._loading = prev_loading
        self._refresh_buttons()
        self._log("[参数] 列名已修改：%s → %s" % (current, text))

    def restore(self):
        """放弃本地修改，回到上次取到的内容"""
        if not self.has_dirty():
            self._set_status("没有需要还原的修改")
            return
        self.tables = copy.deepcopy(self.backup)
        self.dirty_cells.clear()
        self.dirty_headers.clear()
        self._fill_table()
        self._refresh_buttons()
        self._log("[参数] 已还原到上次取到的参数内容")

    def save_to_model(self):
        """回写模型：batchUpdateRemoteSimulationParameterTable(..., bSaveModel=True)"""
        if not (self.mcp and self.user_id and self.model_id and self.tables):
            self._set_status("没有可写入的参数表")
            return
        if self._busy:
            self._log("[参数] 上一次调用尚未结束，已忽略")
            return
        if not self.has_dirty():
            self._set_status("没有修改，无需保存")
            return
        if self.state is not None and self.state.mcp_status in ("RUNNING", "PAUSED"):
            QMessageBox.warning(self, "仿真运行中", "仿真运行中不允许写入参数表，请先「停止」仿真。")
            return
        names = list(self.tables.keys())
        if not self._confirm_write(names):
            return

        tables = []
        for name in names:
            data = self.tables[name]
            columns = data.get("columns") or []
            tables.append({
                "tableName": name,
                "columns": columns,
                "columnCount": len(columns),
                "rows": data.get("rows") or [],
            })
        payload = json.dumps(tables, ensure_ascii=False)
        self._log("[参数] 请求写入模型：%d 张表（bSaveModel=true）" % len(tables))
        self._start("param_save",
                    lambda: self.mcp.batchUpdateParameterTables(self.user_id, self.model_id,
                                                                payload, True))

    def _confirm_write(self, names):
        """写入前的确认对话框（独立方法，便于替换/测试）"""
        box = QMessageBox(self)
        box.setWindowTitle("确认将参数写入模型")
        box.setText("将 %d 张参数表写入仿真模型。\n修改将保存到服务器上的 .fs 模型文件，确定继续？" % len(names))
        ok = box.addButton("确认写入", QMessageBox.ButtonRole.AcceptRole)
        box.addButton("取消", QMessageBox.ButtonRole.RejectRole)
        box.exec()
        return box.clickedButton() is ok

    def _handle_saved(self, result):
        if not result.get("success", False):
            code = result.get("code", "?")
            message = result.get("message", "写入失败")
            if code == "INVALID_STATE":
                message = "仿真运行中不允许写入参数表，请先停止仿真"
            self._log("[参数] 写入模型失败 → %s: %s" % (code, message))
            self._set_status("写入模型失败：%s" % message)
            QMessageBox.warning(self, "写入模型失败", "%s\n%s" % (code, message))
            return
        data = result.get("data") or {}
        self._log("[参数] 写入成功 → %s 张表，%s 行（modelSaved=%s）"
                  % (data.get("updatedTableCount"), data.get("updatedRowCount"),
                     data.get("modelSaved")))
        self._set_status("已写入 %s 张表 / %s 行"
                         % (data.get("updatedTableCount"), data.get("updatedRowCount")))
        self.dirty_cells.clear()
        self.dirty_headers.clear()
        # 写入成功后按前端做法重新取一次最新参数
        self.load_parameters(silent=True)

    # ==================== 后台调用 ====================

    def _start(self, label, fn):
        if self._busy:
            return
        self._busy = True
        self._refresh_buttons()
        runner = McpCallRunner(label, fn, parent=self)
        runner.log_line.connect(self._log)
        runner.done.connect(self._on_done)
        runner.failed.connect(self._on_failed)
        if label == "param_load":
            self._load_runner = runner
        else:
            self._save_runner = runner
        runner.start()

    def _on_done(self, label, result):
        self._busy = False
        try:
            if label == "param_load":
                self._handle_parameters(result)
            elif label == "param_save":
                self._handle_saved(result)
        finally:
            self._refresh_buttons()

    def _on_failed(self, label, message):
        self._busy = False
        self._log("[参数] 调用失败 → %s" % message)
        self._set_status("MCP 调用失败")
        self._refresh_buttons()

    # ==================== 视图同步 ====================

    def _clear_data(self):
        self.tables = {}
        self.backup = {}
        self.table_names = []
        self.active_table = ""
        self.dropped_tables = []
        self.dirty_cells.clear()
        self.dirty_headers.clear()

    def _refresh_view(self):
        self._rebuild_table_bar()
        self._fill_table()
        self._refresh_buttons()

    def _refresh_buttons(self):
        has_model = bool(self.user_id and self.model_id)
        running = bool(self.state is not None
                       and self.state.mcp_status in ("RUNNING", "PAUSED"))
        dirty = self.has_dirty()
        self.load_btn.setEnabled(has_model and not self._busy)
        self.save_btn.setEnabled(has_model and dirty and not self._busy and not running)
        self.restore_btn.setEnabled(dirty and not self._busy)
        if dirty:
            self.dirty_label.setText("● 数据已修改（%d 处）"
                                     % (len(self.dirty_cells) + len(self.dirty_headers)))
        else:
            self.dirty_label.setText("")
        if not has_model:
            self.summary_label.setText("未加载模型")
        elif self.table_names:
            extra = "，已过滤 %d 张 Out 表" % len(self.dropped_tables) if self.dropped_tables else ""
            self.summary_label.setText("参数表 %d 张（MCP 表：%s）%s"
                                       % (len(self.table_names), self.mcp_table_name or "-", extra))
        elif self.mcp_table_name:
            self.summary_label.setText("未取到参数表")

    def _set_status(self, text):
        self.status_label.setText(text)

    def _rebuild_table_bar(self):
        while self.table_bar_layout.count():
            item = self.table_bar_layout.takeAt(0)
            widget = item.widget()
            if widget is not None:
                widget.deleteLater()
        names = list(self.table_names)
        self.table_scroll.setVisible(bool(names))
        for name in names:
            btn = QPushButton(name)
            btn.setCheckable(True)
            btn.setChecked(name == self.active_table)
            btn.setFixedHeight(30)
            btn.setMinimumWidth(60)
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            btn.setToolTip("编辑 %s（切换参数表不会丢失已编辑内容）" % name)
            btn.clicked.connect(lambda _checked=False, n=name: self.switch_table(n))
            self.table_bar_layout.addWidget(btn)
        self.table_bar_layout.addStretch()

    def switch_table(self, name):
        if not name or name == self.active_table:
            return
        self.active_table = name
        self._rebuild_table_bar()
        self._fill_table()
        self._refresh_buttons()

    def _fill_table(self):
        self._loading = True
        blocked = self.table.blockSignals(True)     # 批量填充时不触发 itemChanged
        try:
            data = self.tables.get(self.active_table)
            if not data:
                self.table.clear()
                self.table.setRowCount(0)
                self.table.setColumnCount(0)
                return
            columns = data.get("columns") or []
            rows = data.get("rows") or []
            self.table.clear()
            self.table.setColumnCount(len(columns))
            self.table.setRowCount(len(rows))
            self.table.setHorizontalHeaderLabels([str(c.get("name") or "") for c in columns])
            for ci, col in enumerate(columns):
                item = self.table.horizontalHeaderItem(ci)
                if item is None:
                    item = QTableWidgetItem()
                    self.table.setHorizontalHeaderItem(ci, item)
                item.setToolTip("列名（双击表头可修改）\n类型：%s%s"
                                % (col.get("type") or "-",
                                   "，索引列" if col.get("indexColumn") else ""))
            for ri, row in enumerate(rows):
                for ci in range(len(columns)):
                    value = str(row[ci]) if ci < len(row) else ""
                    self.table.setItem(ri, ci, QTableWidgetItem(value))
            self.table.setVerticalHeaderLabels([str(i) for i in range(len(rows))])
        finally:
            self.table.blockSignals(blocked)
            self._loading = False
        self._apply_dirty_marks()

    def _apply_dirty_marks(self):
        """重刷当前表的全部脏标记（填充/还原后调用）

        期间屏蔽信号：`setBackground/setForeground` 会触发 `itemChanged`，
        若不屏蔽会在 `_on_item_changed` 与 `_apply_dirty_marks` 之间递归，
        递归深度等于单元格数量（实测 120×6 即导致栈溢出崩溃）。
        """
        prev_loading, self._loading = self._loading, True
        blocked = self.table.blockSignals(True)
        try:
            for ri in range(self.table.rowCount()):
                for ci in range(self.table.columnCount()):
                    item = self.table.item(ri, ci)
                    if item is not None:
                        self._style_cell(item, ri, ci)
            for ci in range(self.table.columnCount()):
                item = self.table.horizontalHeaderItem(ci)
                if item is not None:
                    self._style_header(item, ci)
        finally:
            self.table.blockSignals(blocked)
            self._loading = prev_loading

    def _style_cell(self, item, row, col):
        """按脏标记设置单元格样式（调用方需自行屏蔽信号）"""
        if (self.active_table, row, col) in self.dirty_cells:
            item.setBackground(QBrush(QColor(DIRTY_BG)))
            item.setForeground(QBrush(QColor(DIRTY_FG)))
        else:
            item.setBackground(QBrush())
            item.setForeground(QBrush())

    def _style_header(self, item, col):
        """按脏标记设置表头样式（调用方需自行屏蔽信号）"""
        if (self.active_table, col) in self.dirty_headers:
            item.setBackground(QBrush(QColor(DIRTY_BG)))
            item.setForeground(QBrush(QColor(DIRTY_FG)))
        else:
            item.setBackground(QBrush())
            item.setForeground(QBrush())

    # ==================== 工具 ====================

    def _log(self, message):
        self.log_line.emit(message)
