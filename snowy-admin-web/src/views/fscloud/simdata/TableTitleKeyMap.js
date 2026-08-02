/**
 * 结果快照数据的单位和中文名称映射表
 *
 * 结构: tableTitleKeyMap[groupKey][datasetKey] = {
 *   show: 是否显示该 dataset 子tab,
 *   tabTitle: tab tooltip 完整标题,
 *   tabBrief: tab 上显示的短名称,
 *   columns: { [columnKey]: { title, unit, type, decimals } }
 * }
 *
 * type: s=字符串, i=整型, f=浮点, t=时间戳(ms)
 * decimals: f 类型的小数位数
 */
export const tableTitleKeyMap = {
    overview: {
      taktTime: {
        show: true,
        tabTitle: "节拍时间",
        tabBrief: "节拍",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单元",
            unit: "",
            type: "s",
            decimals: 0
          },
          firstProcessTime: {
            title: "首件时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          lastProcessTime: {
            title: "末件时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          effectiveTime: {
            title: "有效时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          processedCount: {
            title: "加工数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          taktTime: {
            title: "节拍",
            unit: "h:m:s",
            type: "t",
            decimals: 2
          },
          uph: {
            title: "UPH",
            unit: "件/时",
            type: "f",
            decimals: 2
          }
        }
      },
      uph: {
        show: true,
        tabTitle: "小时产出量(UPH)",
        tabBrief: "产量/小时",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单元",
            unit: "",
            type: "s",
            decimals: 0
          },
          startTime: {
            title: "开始时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          endTime: {
            title: "结束时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          processedCount: {
            title: "加工数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          uph: {
            title: "UPH",
            unit: "件/时",
            type: "f",
            decimals: 2
          }
        }
      },
      utilRate: {
        show: true,
        tabTitle: "设备利用率",
        tabBrief: "设备利用率",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单元",
            unit: "",
            type: "s",
            decimals: 0
          },
          actualSimTime: {
            title: "仿真时长",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          waitingTime: {
            title: "等待时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          waitingRate: {
            title: "等待率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          workingTime: {
            title: "工作时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          workingRate: {
            title: "工作率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          settingUpTime: {
            title: "换型时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          settingUpRate: {
            title: "换型率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          blockedTime: {
            title: "阻塞时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          blockedRate: {
            title: "阻塞率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          failedTime: {
            title: "故障时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          failedRate: {
            title: "故障率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          unplannedTime: {
            title: "非计划停机",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          unplannedRate: {
            title: "非计划停机率",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      },
      leanMetrics: {
        show: true,
        tabTitle: "精益指标",
        tabBrief: "精益指标",
        columns: {}
      },
      energyAnalysis: {
        show: true,
        tabTitle: "能耗分析",
        tabBrief: "能耗分析",
        columns: {}
      }
    },
    basic: {
      partCycleTime: {
        show: true,
        tabTitle: "单品循环时间",
        tabBrief: "循环时间",
        columns: {
          muNo: {
            title: "物料编号",
            unit: "",
            type: "s",
            decimals: 0
          },
          muType: {
            title: "物料类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          name: {
            title: "物料名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          productionStartTime: {
            title: "生产开始时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          createTime: {
            title: "创建时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          createPosition: {
            title: "创建位置",
            unit: "",
            type: "s",
            decimals: 0
          },
          productionEndTime: {
            title: "生产结束时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          finishPosition: {
            title: "完成位置",
            unit: "",
            type: "s",
            decimals: 0
          },
          cycleTimeFromProductionStart: {
            title: "生产周期时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          cycleTimeFromCreate: {
            title: "创建开始周期",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          workingTime: {
            title: "加工时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          waitingTime: {
            title: "等待时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          transportTime: {
            title: "运输时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          }
        }
      },
      rbData: {
        show: true,
        tabTitle: "资源缓冲区数据",
        tabBrief: "缓冲区",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          failureCount: {
            title: "故障次数",
            unit: "次",
            type: "i",
            decimals: 0
          },
          mttr: {
            title: "MTTR",
            unit: "ms",
            type: "f",
            decimals: 2
          },
          mtbf: {
            title: "MTBF",
            unit: "ms",
            type: "f",
            decimals: 2
          },
          availability: {
            title: "可用率",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      },
      taktTime: {
        show: true,
        tabTitle: "节拍时间",
        tabBrief: "节拍",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单元",
            unit: "",
            type: "s",
            decimals: 0
          },
          firstProcessTime: {
            title: "首件时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          lastProcessTime: {
            title: "末件时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          effectiveTime: {
            title: "有效时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          processedCount: {
            title: "加工数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          taktTime: {
            title: "节拍",
            unit: "h:m:s",
            type: "t",
            decimals: 2
          },
          uph: {
            title: "UPH",
            unit: "件/时",
            type: "f",
            decimals: 2
          }
        }
      },
      uph: {
        show: true,
        tabTitle: "小时产出量(UPH)",
        tabBrief: "UPH",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单元",
            unit: "",
            type: "s",
            decimals: 0
          },
          startTime: {
            title: "开始时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          endTime: {
            title: "结束时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          processedCount: {
            title: "加工数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          uph: {
            title: "UPH",
            unit: "件/时",
            type: "f",
            decimals: 2
          }
        }
      },
      utilRate: {
        show: true,
        tabTitle: "资源/设备利用率",
        tabBrief: "利用率",
        columns: {
          objectName: {
            title: "对象名",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单元",
            unit: "",
            type: "s",
            decimals: 0
          },
          actualSimTime: {
            title: "仿真时长",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          waitingTime: {
            title: "等待时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          waitingRate: {
            title: "等待率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          workingTime: {
            title: "工作时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          workingRate: {
            title: "工作率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          settingUpTime: {
            title: "换型时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          settingUpRate: {
            title: "换型率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          blockedTime: {
            title: "阻塞时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          blockedRate: {
            title: "阻塞率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          failedTime: {
            title: "故障时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          failedRate: {
            title: "故障率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          unplannedTime: {
            title: "非计划停机",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          unplannedRate: {
            title: "非计划停机率",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      },
      "wip.timeline": {
        show: true,
        tabTitle: "在制品时间趋势",
        tabBrief: "WIP趋势",
        columns: {
          time: {
            title: "时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          wip: {
            title: "WIP",
            unit: "件",
            type: "i",
            decimals: 0
          }
        }
      },
      "wip.distribution": {
        show: true,
        tabTitle: "在制品分布",
        tabBrief: "WIP分布",
        columns: {
          minTime: {
            title: "最小时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          maxTime: {
            title: "最大时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          count: {
            title: "数量",
            unit: "件",
            type: "i",
            decimals: 0
          },
          percentage: {
            title: "占比",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      }
    },
    lean: {
      "lean.productLines": {
        show: true,
        tabTitle: "产线信息",
        tabBrief: "产线",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          entranceName: {
            title: "入口",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          availableWorkHours: {
            title: "可用工时",
            unit: "h",
            type: "s",
            decimals: 0
          },
          customerDemand: {
            title: "客户需求",
            unit: "件",
            type: "i",
            decimals: 0
          },
          reworkExitName: {
            title: "返修出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          scrapExitName: {
            title: "报废出口",
            unit: "",
            type: "s",
            decimals: 0
          }
        }
      },
      "lean.lt": {
        show: true,
        tabTitle: "生产前置时间(LT)",
        tabBrief: "LT",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          entranceName: {
            title: "入口",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          muNo: {
            title: "物料编号",
            unit: "",
            type: "s",
            decimals: 0
          },
          muType: {
            title: "物料类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          createTime: {
            title: "创建时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          endTime: {
            title: "结束时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          ltMs: {
            title: "提前期",
            unit: "h:m:s",
            type: "t",
            decimals: 2
          }
        }
      },
      "lean.fpy": {
        show: true,
        tabTitle: "一次合格率(FPY)",
        tabBrief: "FPY",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          reworkExitName: {
            title: "返修出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          scrapExitName: {
            title: "报废出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasQualityExitConfig: {
            title: "质检配置",
            unit: "",
            type: "s",
            decimals: 0
          },
          totalInput: {
            title: "总投入",
            unit: "件",
            type: "i",
            decimals: 0
          },
          qualifiedCount: {
            title: "合格数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          reworkQualifiedCount: {
            title: "返修合格数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          scrapCount: {
            title: "报废数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          fpy: {
            title: "直通率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          scrapRate: {
            title: "报废率",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      },
      "lean.wip": {
        show: true,
        tabTitle: "在制品存量",
        tabBrief: "WIP",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          entranceName: {
            title: "入口",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          currentWIP: {
            title: "当前WIP",
            unit: "件",
            type: "i",
            decimals: 0
          },
          maxWIP: {
            title: "最大WIP",
            unit: "件",
            type: "i",
            decimals: 0
          },
          avgWIP: {
            title: "平均WIP",
            unit: "件",
            type: "f",
            decimals: 2
          }
        }
      },
      "lean.wipTimeline": {
        show: true,
        tabTitle: "在制品时序曲线",
        tabBrief: "WIP时序",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          time: {
            title: "时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          wip: {
            title: "WIP",
            unit: "件",
            type: "i",
            decimals: 0
          }
        }
      },
      "lean.capacity": {
        show: true,
        tabTitle: "产能",
        tabBrief: "产能",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          availableWorkHours: {
            title: "可用工时",
            unit: "h",
            type: "f",
            decimals: 2
          },
          customerDemand: {
            title: "客户需求",
            unit: "件",
            type: "i",
            decimals: 0
          },
          hasTT: {
            title: "有节拍",
            unit: "",
            type: "s",
            decimals: 0
          },
          ttMs: {
            title: "节拍",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          qualifiedOutput: {
            title: "合格产出",
            unit: "件",
            type: "i",
            decimals: 0
          },
          hasActualUPH: {
            title: "有实际UPH",
            unit: "",
            type: "s",
            decimals: 0
          },
          actualUPH: {
            title: "实际UPH",
            unit: "件/h",
            type: "f",
            decimals: 2
          },
          bottleneckProcess: {
            title: "瓶颈工序",
            unit: "",
            type: "s",
            decimals: 0
          },
          bottleneckCTMs: {
            title: "瓶颈CT",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          hasTheoreticalUPH: {
            title: "有理论UPH",
            unit: "",
            type: "s",
            decimals: 0
          },
          theoreticalUPH: {
            title: "理论UPH",
            unit: "件/h",
            type: "f",
            decimals: 2
          },
          hasSimulation: {
            title: "已仿真",
            unit: "",
            type: "s",
            decimals: 0
          }
        }
      },
      "lean.cycleTime": {
        show: true,
        tabTitle: "工序循环时间",
        tabBrief: "工序CT",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          completionCount: {
            title: "完成数",
            unit: "件",
            type: "i",
            decimals: 0
          },
          hasSimulation: {
            title: "已仿真",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasCycleTime: {
            title: "有周期时间",
            unit: "",
            type: "s",
            decimals: 0
          },
          avgCTMs: {
            title: "平均CT",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          }
        }
      },
      "lean.processEfficiency": {
        show: true,
        tabTitle: "工艺效率",
        tabBrief: "工艺效率",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasSimulation: {
            title: "已仿真",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasProcessData: {
            title: "有工艺数据",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasWaitingTime: {
            title: "有等待时间",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasValueAddedRatio: {
            title: "有增值比",
            unit: "",
            type: "s",
            decimals: 0
          },
          bottleneckProcess: {
            title: "瓶颈工序",
            unit: "",
            type: "s",
            decimals: 0
          },
          bottleneckPTMs: {
            title: "瓶颈PT",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          totalAvgPTMs: {
            title: "总平均PT",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          balanceRate: {
            title: "平衡率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          avgWaitingMs: {
            title: "平均等待",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          valueAddedRatio: {
            title: "增值比",
            unit: "%",
            type: "f",
            decimals: 2
          },
          steps: {
            title: "工序明细",
            unit: "",
            type: "s",
            decimals: 0
          }
        }
      },
      "lean.changeover": {
        show: true,
        tabTitle: "换型切换",
        tabBrief: "换型",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasSimulation: {
            title: "已仿真",
            unit: "",
            type: "s",
            decimals: 0
          },
          switchCount: {
            title: "切换次数",
            unit: "次",
            type: "i",
            decimals: 0
          },
          validCount: {
            title: "有效次数",
            unit: "次",
            type: "i",
            decimals: 0
          },
          invalidCount: {
            title: "无效次数",
            unit: "次",
            type: "i",
            decimals: 0
          },
          avgChangeoverMs: {
            title: "平均换型时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          maxChangeoverMs: {
            title: "最长换型时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          }
        }
      },
      "lean.changeoverRecords": {
        show: true,
        tabTitle: "换型记录",
        tabBrief: "换型记录",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          fromType: {
            title: "切换前",
            unit: "",
            type: "s",
            decimals: 0
          },
          toType: {
            title: "切换后",
            unit: "",
            type: "s",
            decimals: 0
          },
          changeoverMs: {
            title: "换型时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          }
        }
      },
      "lean.oee": {
        show: true,
        tabTitle: "设备综合效率(OEE)",
        tabBrief: "OEE",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          plannedProductionMs: {
            title: "计划生产时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          runMs: {
            title: "运行时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          totalOutput: {
            title: "总产出",
            unit: "件",
            type: "i",
            decimals: 0
          },
          qualifiedOutput: {
            title: "合格产出",
            unit: "件",
            type: "i",
            decimals: 0
          },
          availabilityRate: {
            title: "可用率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          performanceRate: {
            title: "性能率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          qualityRate: {
            title: "质量率",
            unit: "%",
            type: "f",
            decimals: 2
          },
          oee: {
            title: "OEE",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      },
      "lean.pplh": {
        show: true,
        tabTitle: "人时产出量(PPLH)",
        tabBrief: "PPLH",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          lineName: {
            title: "产线名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          exitName: {
            title: "出口",
            unit: "",
            type: "s",
            decimals: 0
          },
          workerCount: {
            title: "工人数",
            unit: "人",
            type: "i",
            decimals: 0
          },
          qualifiedOutput: {
            title: "合格产出",
            unit: "件",
            type: "i",
            decimals: 0
          },
          hasLaborHours: {
            title: "有工时数据",
            unit: "",
            type: "s",
            decimals: 0
          },
          pplh: {
            title: "PPLH",
            unit: "件/人时",
            type: "f",
            decimals: 2
          }
        }
      }
    },
    energy: {
      "energy.mediums": {
        show: true,
        tabTitle: "能源介质",
        tabBrief: "介质",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumId: {
            title: "介质ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumName: {
            title: "介质名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单位",
            unit: "",
            type: "s",
            decimals: 0
          }
        }
      },
      "energy.params": {
        show: true,
        tabTitle: "能耗参数",
        tabBrief: "参数",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          deviceName: {
            title: "设备名",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumId: {
            title: "介质ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          status: {
            title: "状态",
            unit: "",
            type: "s",
            decimals: 0
          },
          statusLabel: {
            title: "状态说明",
            unit: "",
            type: "s",
            decimals: 0
          },
          power: {
            title: "功率",
            unit: "kW",
            type: "f",
            decimals: 2
          }
        }
      },
      "energy.rows": {
        show: true,
        tabTitle: "能耗明细数据行",
        tabBrief: "明细",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          deviceName: {
            title: "设备名",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumId: {
            title: "介质ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumName: {
            title: "介质名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单位",
            unit: "",
            type: "s",
            decimals: 0
          },
          totalEnergy: {
            title: "总能耗",
            unit: "kWh",
            type: "f",
            decimals: 2
          },
          status: {
            title: "状态",
            unit: "",
            type: "s",
            decimals: 0
          },
          statusLabel: {
            title: "状态说明",
            unit: "",
            type: "s",
            decimals: 0
          },
          statusTimeMs: {
            title: "状态时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          percentage: {
            title: "占比",
            unit: "%",
            type: "f",
            decimals: 2
          },
          power: {
            title: "功率",
            unit: "kW",
            type: "f",
            decimals: 2
          },
          energy: {
            title: "能耗",
            unit: "kWh",
            type: "f",
            decimals: 4
          }
        }
      },
      "energy.mediumSummary": {
        show: true,
        tabTitle: "能源介质汇总",
        tabBrief: "介质汇总",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumId: {
            title: "介质ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumName: {
            title: "介质名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单位",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumTotalEnergy: {
            title: "介质总能耗",
            unit: "kWh",
            type: "f",
            decimals: 2
          },
          deviceName: {
            title: "设备名",
            unit: "",
            type: "s",
            decimals: 0
          },
          deviceEnergy: {
            title: "设备能耗",
            unit: "kWh",
            type: "f",
            decimals: 2
          },
          percentage: {
            title: "占比",
            unit: "%",
            type: "f",
            decimals: 2
          }
        }
      },
      "energy.powerCurve": {
        show: true,
        tabTitle: "功率曲线",
        tabBrief: "功率曲线",
        columns: {
          componentId: {
            title: "组件ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          componentNo: {
            title: "组件名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          objectType: {
            title: "类型",
            unit: "",
            type: "s",
            decimals: 0
          },
          scopeName: {
            title: "范围",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumId: {
            title: "介质ID",
            unit: "",
            type: "s",
            decimals: 0
          },
          mediumName: {
            title: "介质名称",
            unit: "",
            type: "s",
            decimals: 0
          },
          unit: {
            title: "单位",
            unit: "",
            type: "s",
            decimals: 0
          },
          hasEvents: {
            title: "有事件",
            unit: "",
            type: "s",
            decimals: 0
          },
          simTimeMs: {
            title: "仿真时间",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          peakPower: {
            title: "峰值功率",
            unit: "kW",
            type: "f",
            decimals: 2
          },
          reconstructedEnergy: {
            title: "重构能耗",
            unit: "kWh",
            type: "f",
            decimals: 4
          },
          timeMs: {
            title: "时间点",
            unit: "h:m:s",
            type: "t",
            decimals: 0
          },
          power: {
            title: "功率",
            unit: "kW",
            type: "f",
            decimals: 2
          }
        }
      }
    }
  };
