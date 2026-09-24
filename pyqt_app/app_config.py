# -*- coding: utf-8 -*-
"""路径/解释器/资源参数定义 + app_config.json 读写"""
import json
import os
import sys

# ==================== 路径 ====================
APP_DIR = os.path.dirname(os.path.abspath(__file__))
ALGO_DIR = os.path.dirname(APP_DIR)  # v3_blade_adjust 算法目录

SCRIPT_5PY = os.path.join(ALGO_DIR, "5.py")
SCRIPT_CPY = os.path.join(ALGO_DIR, "c.py")
GRID_DIR = os.path.join(ALGO_DIR, "grid")
SCRIPT_GRID_DEPTH = os.path.join(GRID_DIR, "grid_depth.py")
OUTPUT_ROOT = os.path.join(ALGO_DIR, "output")
CONFIG_PATH = os.path.join(APP_DIR, "app_config.json")

_grid_excel = os.path.join(GRID_DIR, "施工网格数据表.xlsx")
_root_excel = os.path.join(ALGO_DIR, "施工网格数据表.xlsx")
DEFAULT_EXCEL = _root_excel if os.path.exists(_root_excel) else _grid_excel

DEFAULT_PRECISE_TXT = os.path.join(GRID_DIR, "精确区.txt")
DEFAULT_FULL_TXT = os.path.join(GRID_DIR, "完整区域.txt")

# ==================== 算法子进程解释器 ====================
ALGO_PYTHON_CANDIDATES = [
    r"D:\Anaconda\envs\pothill\python.exe",
    sys.executable,
]


def algo_python():
    """取第一个存在的候选解释器"""
    for p in ALGO_PYTHON_CANDIDATES:
        if p and os.path.exists(p):
            return p
    return sys.executable


# ==================== 资源参数定义 ====================
# (参数键, 标签, 默认值, 参考提示, 5.py 命令行参数)
RESOURCE_FIELDS = [
    ("loader_cap", "装载机容量 (m³)", "2.5",
     "参考：单斗容量，每车固定运量", "--loader-cap"),
    ("loader_load_time", "装车时间 (s)", "30",
     "参考：取土源装车约30s", "--loader-load-time"),
    ("loader_dump_time", "卸料/调头时间 (s)", "30",
     "参考：目标网格卸料动作约30s", "--loader-dump-time"),
    ("loader_speed_loaded", "装载机重车速度 (km/h)", "12.5",
     "参考：前进 0-39 km/h", "--loader-speed-loaded-kmh"),
    ("loader_speed_empty", "装载机空车速度 (km/h)", "17.5",
     "参考：后退 0-15 km/h，空车按前进计", "--loader-speed-empty-kmh"),
    ("bulldozer_speed", "推土机行驶速度 (km/h)", "10",
     "参考：0-10 km/h", "--bulldozer-speed-kmh"),
    ("bulldozer_work_time", "推土机作业时间 (s)", "30",
     "参考：单趟推土作业时间", "--bulldozer-work-time"),
    ("loader_fuel", "装载机油耗 (L/h)", "20",
     "参考：装载机小时油耗", "--loader-fuel-rate"),
    ("dozer_fuel", "推土机油耗 (L/h)", "25",
     "参考：推土机小时油耗", "--dozer-fuel-rate"),
    ("clearance", "网格安全间隔 (s)", "2.0",
     "参考：时序规划节点安全间隔", "--temporal-clearance"),
    ("dozer_blade_cap", "推土机铲刀容量 (m³/趟)", "2.5",
     "参考：一趟铲刀固定推土量", "--dozer-blade-cap"),
    ("dozer_blade_height_cm", "铲刀高度/松铺厚度 (cm/趟)", "8",
     "参考：4.2x7.5m 网格下默认 8cm", "--dozer-blade-height-cm"),
]

DEFAULT_RESOURCE_VALUES = {key: default
                           for key, _label, default, _hint, _flag in RESOURCE_FIELDS}

# ==================== MCP 远程仿真连接 ====================
# 对应 Java 侧 fscloud.mcp.*（McpProperties / application-{profile}.properties）
MCP_DEFAULTS = {
    "endpoint_url": "http://127.0.0.1:8081/mcp",
    "api_key": "7sK9p2G@2026",
    "user_id": "pyqt-local",
    "load_in_ui": True,
}

DEFAULT_CONFIG = {
    "mcp": dict(MCP_DEFAULTS),
    "resources": dict(DEFAULT_RESOURCE_VALUES),
    "grid": {
        "precise": DEFAULT_PRECISE_TXT,
        "full": DEFAULT_FULL_TXT,
        "cell_x": "4.2",
        "cell_y": "7.5",
        "out_dir": GRID_DIR,
    },
    "planning": {
        "excel": DEFAULT_EXCEL,
        "source_type": "取土带",
        "source_x": "95.0",
        "source_y": "auto",
        "strip_length": "50.0",
        "entry_grids": "",
        "loaders": 1,
        "dozers": 1,
    },
}


def load_config():
    """读取 app_config.json，缺失字段用默认值补齐"""
    cfg = json.loads(json.dumps(DEFAULT_CONFIG, ensure_ascii=False))
    try:
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            saved = json.load(f)
        for section, values in saved.items():
            if isinstance(values, dict) and isinstance(cfg.get(section), dict):
                cfg[section].update(values)
            else:
                cfg[section] = values
    except Exception:
        pass
    return cfg


def save_config(cfg):
    """写入 app_config.json"""
    try:
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
    except Exception:
        pass


def mcp_settings(cfg=None):
    """取 MCP 连接配置；空值字段回落到 DEFAULT_CONFIG 的 mcp 段"""
    mcpc = (cfg or load_config()).get("mcp") or {}
    settings = dict(MCP_DEFAULTS)
    for key, value in mcpc.items():
        if value is None or str(value).strip() == "":
            continue
        settings[key] = value
    return settings


def resource_cli_args(resources):
    """把资源参数 dict 转成 5.py 命令行参数列表；非法数字抛 ValueError"""
    args = []
    for key, label, _default, _hint, flag in RESOURCE_FIELDS:
        raw = str(resources.get(key, "")).strip()
        if not raw:
            continue
        try:
            float(raw)
        except ValueError:
            raise ValueError("%s 不是有效数字: %r" % (label, raw))
        args += [flag, raw]
    return args
