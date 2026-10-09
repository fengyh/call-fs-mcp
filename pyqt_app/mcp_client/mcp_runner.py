# -*- coding: utf-8 -*-
"""MCP 后台调用运行器：QThread 中执行一次 McpClient 调用，结果经信号送回界面"""
from PyQt6.QtCore import QThread, pyqtSignal

from mcp_client import McpServiceException


class McpCallRunner(QThread):
    """执行单个 MCP 调用（读超时可达 60s，必须离开 UI 线程）

    label 用于界面分派：调用方按 label 决定如何解读 result。
    """

    log_line = pyqtSignal(str)
    done = pyqtSignal(str, object)   # label, 业务结果
    failed = pyqtSignal(str, str)    # label, 错误描述

    def __init__(self, label, fn, parent=None):
        super().__init__(parent)
        self.label = label
        self.fn = fn

    def run(self):
        try:
            result = self.fn()
        except McpServiceException as exc:
            self.log_line.emit("MCP 调用失败: %s" % exc)
            self.failed.emit(self.label, "MCP 错误 %s: %s" % (exc.code, exc.message))
            return
        except Exception as exc:  # 兜底，避免线程内异常静默丢失
            self.log_line.emit("MCP 调用异常: %r" % exc)
            self.failed.emit(self.label, "异常: %r" % exc)
            return
        self.done.emit(self.label, result)
