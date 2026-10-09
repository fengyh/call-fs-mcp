# -*- coding: utf-8 -*-
"""后台命令运行器：QThread 中跑子进程，逐行输出经信号送到界面"""
import os
import queue
import subprocess
import threading

from PyQt6.QtCore import QThread, pyqtSignal

_NO_WINDOW = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def _make_env():
    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"
    return env


class CommandRunner(QThread):
    """运行单个子进程命令，逐行推 stdout"""

    log_line = pyqtSignal(str)
    finished_rc = pyqtSignal(int)

    def __init__(self, cmd, cwd=None, parent=None):
        super().__init__(parent)
        self.cmd = cmd
        self.cwd = cwd

    def run(self):
        self.log_line.emit("$ " + " ".join(self.cmd))
        rc = -1
        try:
            proc = subprocess.Popen(
                self.cmd, cwd=self.cwd,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace",
                bufsize=1, env=_make_env(), creationflags=_NO_WINDOW)
            for line in iter(proc.stdout.readline, ""):
                self.log_line.emit(line.rstrip())
            rc = proc.wait()
        except Exception as e:
            self.log_line.emit("运行异常: %s" % e)
        self.finished_rc.emit(rc)


class SweepRunner(QThread):
    """多配置扫描：按并发数跑多个 5.py 子进程（完成一个补一个）。

    jobs: [{"n": int, "m": int, "cmd": list 或 None}]
    cmd 为 None 表示复用已有结果，直接记为成功。
    """

    progress = pyqtSignal(int, int)              # done, total
    config_done = pyqtSignal(int, int, bool)     # n, m, ok
    log_line = pyqtSignal(str)
    all_finished = pyqtSignal(list)              # jobs 列表（带 ok 字段）

    def __init__(self, jobs, cwd=None, max_parallel=2, parent=None):
        super().__init__(parent)
        self.jobs = jobs
        self.cwd = cwd
        self.max_parallel = max(1, max_parallel)

    @staticmethod
    def _reader(proc, tag, q):
        try:
            for line in iter(proc.stdout.readline, ""):
                q.put(("log", tag, line.rstrip()))
        finally:
            q.put(("done", tag, proc.wait()))

    def run(self):
        total = len(self.jobs)
        done = 0
        results = []
        pending = []
        for it in self.jobs:
            it.setdefault("ok", False)
            if it["cmd"] is None:
                it["ok"] = True
                it["skipped"] = True
                results.append(it)
                done += 1
                self.config_done.emit(it["n"], it["m"], True)
                self.progress.emit(done, total)
            else:
                pending.append(it)

        env = _make_env()
        q = queue.Queue()
        running = {}  # tag -> (proc, job)

        def launch(it):
            tag = "%dv%d" % (it["n"], it["m"])
            proc = subprocess.Popen(
                it["cmd"], cwd=self.cwd,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace",
                bufsize=1, env=env, creationflags=_NO_WINDOW)
            running[tag] = (proc, it)
            threading.Thread(target=self._reader, args=(proc, tag, q),
                             daemon=True).start()
            self.log_line.emit(">>> 启动 %s（并行 %d/%d）"
                               % (tag, len(running), self.max_parallel))

        while pending and len(running) < self.max_parallel:
            launch(pending.pop(0))
        while running:
            kind, tag, payload = q.get()
            if kind == "log":
                continue  # 扫描模式不逐行转发子进程输出
            _proc, it = running.pop(tag)
            it["ok"] = (payload == 0)
            results.append(it)
            done += 1
            self.config_done.emit(it["n"], it["m"], it["ok"])
            self.progress.emit(done, total)
            self.log_line.emit("<<< %s %s（%d/%d）"
                               % (tag, "完成" if it["ok"] else "失败 exit %s" % payload,
                                  done, total))
            if pending:
                launch(pending.pop(0))

        self.all_finished.emit(results)
