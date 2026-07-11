package vip.xiaonuo.fscloud.modular.simulation;

import lombok.Getter;

public enum SimStatusEnum {
    SIM_STATUS_NOTYET("SIM_STATUS_NOTYET", "未加载"),
    SIM_STATUS_WAITING("SIM_STATUS_WAITING", "待仿真"),
    SIM_STATUS_GOING("SIM_STATUS_GOING", "仿真中"),
    SIM_STATUS_PAUSED("SIM_STATUS_PAUSED", "已暂停"),
    SIM_STATUS_FINISHED("SIM_STATUS_FINISHED", "仿真结束"),
    SIM_STATUS_STOPPED("SIM_STATUS_STOPPED", "已停止"),
    SIM_STATUS_FAILED("SIM_STATUS_FAILED", "仿真出错"),
    SIM_STATUS_ALREADY("SIM_STATUS_ALREADY", "已仿真");

    @Getter
    private final String code;

    @Getter
    private final String desc;

    SimStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
