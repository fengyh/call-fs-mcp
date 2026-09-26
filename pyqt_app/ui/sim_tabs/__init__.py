# -*- coding: utf-8 -*-
"""页签5 右下区域的四个子页签

- 「控制台日志」：MQTT console 消息（自包含，直接连接 SimulationState）
- 「仿真动画」「仿真参数」「仿真结果」：占位，具体功能留待后续需求实现
"""
from ui.sim_tabs.console_log_tab import ConsoleLogTab
from ui.sim_tabs.animation_tab import AnimationTab
from ui.sim_tabs.parameter_tab import ParameterTab
from ui.sim_tabs.result_tab import ResultTab

#: 子页签标题（顺序与 TAB_CLASSES 一致）
TAB_TITLES = ("控制台日志", "仿真动画", "仿真参数", "仿真结果")
#: 子页签类
TAB_CLASSES = (ConsoleLogTab, AnimationTab, ParameterTab, ResultTab)

__all__ = ["ConsoleLogTab", "AnimationTab", "ParameterTab", "ResultTab",
           "TAB_TITLES", "TAB_CLASSES"]
