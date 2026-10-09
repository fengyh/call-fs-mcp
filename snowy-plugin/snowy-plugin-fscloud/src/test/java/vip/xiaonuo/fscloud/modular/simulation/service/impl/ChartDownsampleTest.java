/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 *
 * Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *
 * 1.请不要删除和修改根目录下的LICENSE文件。
 * 2.请不要删除和修改Snowy源码头部的版权声明。
 * 3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 * 4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 * 5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 * 6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
package vip.xiaonuo.fscloud.modular.simulation.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结果图表降采样测试
 *
 * <p>重点：时间序列（如 energy.powerCurve 功率曲线）会在同一个时间点（同一个 x）上给出多个点，
 * 用于形成垂直跳变（阶梯）效果。按行等距抽样会把这些点拆散、跳变退化成斜线，
 * 因此传 xColumn 时必须按「同一个 x 的全部分行」整组抽样。
 */
class ChartDownsampleTest {

    private final FsSimResultSnapshotServiceImpl service = new FsSimResultSnapshotServiceImpl();

    /** 阶梯数据：pointCount 个时间点，每个时间点两行（跳变前的值 + 跳到的新值） */
    private static List<Map<String, Object>> stepRows(int pointCount) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < pointCount; i++) {
            rows.add(row((long) i * 1000, i * 10.0));
            rows.add(row((long) i * 1000, (i + 1) * 10.0));
        }
        return rows;
    }

    private static Map<String, Object> row(Object timeMs, double power) {
        Map<String, Object> map = new HashMap<>();
        map.put("timeMs", timeMs);
        map.put("power", power);
        return map;
    }

    @Test
    @DisplayName("按 x 抽样：同一个 x 上的多个点整组保留，垂直跳变不丢失")
    void keepsAllRowsOfSameX() {
        List<Map<String, Object>> rows = stepRows(1000);      // 2000 行 / 1000 个时间点
        List<Map<String, Object>> sampled = service.downsampleRows(rows, 500, "timeMs", 2);

        assertTrue(sampled.size() < rows.size(), "应发生二次采样：" + sampled.size());
        assertTrue(sampled.size() >= 4, "应保留多个时间点：" + sampled.size());

        Map<Object, List<Object>> byX = new LinkedHashMap<>();
        for (Map<String, Object> r : sampled) {
            byX.computeIfAbsent(r.get("timeMs"), k -> new ArrayList<>()).add(r.get("power"));
        }
        assertTrue(byX.size() > 1, "抽样后应保留多个时间点");
        for (Map.Entry<Object, List<Object>> entry : byX.entrySet()) {
            List<Object> powers = entry.getValue();
            assertEquals(2, powers.size(), "同一个 x 上的两个点必须同时保留：x=" + entry.getKey());
            assertEquals(10.0, (Double) powers.get(1) - (Double) powers.get(0), 1e-9,
                    "同一 x 上的两点顺序不得颠倒（跳变方向不能翻转）：x=" + entry.getKey());
        }
    }

    @Test
    @DisplayName("同一个 x 上的多点保持原始先后顺序（先低后高不变成斜线）")
    void keepsOrderInsideSameX() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(0L, 5.0));          // 同一 x：先 5
        rows.add(row(0L, 20.0));         // 后 20（向上跳变）
        for (int i = 1; i < 100; i++) {
            rows.add(row((long) i * 1000, 20.0));
            rows.add(row((long) i * 1000, 20.0));
        }
        List<Map<String, Object>> sampled = service.downsampleRows(rows, 20, "timeMs", 10);

        assertEquals(0L, ((Number) sampled.get(0).get("timeMs")).longValue(), "首个时间点必须保留");
        assertEquals(5.0, (Double) sampled.get(0).get("power"));
        assertEquals(20.0, (Double) sampled.get(1).get("power"));
    }

    @Test
    @DisplayName("未给 xColumn：退回按行等距抽样（保持原有行为）")
    void fallsBackToRowSampling() {
        List<Map<String, Object>> rows = stepRows(1000);
        List<Map<String, Object>> sampled = service.downsampleRows(rows, 500, "", 2);

        assertEquals(500, sampled.size());
        assertEquals(rows.get(0), sampled.get(0), "保留首行");
        assertEquals(rows.get(rows.size() - 1), sampled.get(sampled.size() - 1), "保留末行");
    }

    @Test
    @DisplayName("行数不超过预算：原样返回同一引用")
    void returnsAsIsWithinBudget() {
        List<Map<String, Object>> rows = stepRows(10);
        assertSame(rows, service.downsampleRows(rows, 2000, "timeMs"));
    }

    @Test
    @DisplayName("二次采样间隔为 1：数据原样返回（不抽样、不丢点）")
    void returnsRowsAsIsWhenSampleRatioIsOne() {
        // 默认入口当前把 sampleRatio 设为 1
        List<Map<String, Object>> rows = stepRows(1000);       // 2000 行，远超 maxPoints
        assertSame(rows, service.downsampleRows(rows, 400, "timeMs"));

        // 阶梯平台数据同样原样返回：稀疏区的跳变点一个都不会被加工掉
        List<Map<String, Object>> plateau = new ArrayList<>();
        plateau.add(row(0L, 5.0));
        plateau.add(row(1_000_000L, 0.0));
        plateau.add(row(9_000_000L, 0.0));
        assertSame(plateau, service.downsampleRows(plateau, 2, "timeMs"));
    }

    @Test
    @DisplayName("数值 x 归一：0（Long）与 0.0（Double）属于同一个时间点")
    void normalizesNumericXKey() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(0L, 1.0));
        rows.add(row(0.0, 2.0));

        assertEquals(1, service.groupRowsByX(rows, "timeMs").size());
        assertEquals(service.xKey(0), service.xKey(0.0));
    }

    @Test
    @DisplayName("x 值全同或无有效 x：退化为按行抽样且不出现除零")
    void degeneratesWhenSingleGroup() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(row(null, i));
        }
        assertEquals(20, service.downsampleRows(rows, 20, "timeMs", 2).size());
    }

    @Test
    @DisplayName("稀疏区（长时间恒定功率）的跳变点必须保留，不得被抽样整段跳过")
    void keepsSparsePlateauPoints() {
        List<Map<String, Object>> rows = new ArrayList<>();
        // 前段 0~299s：每 1s 一组的密集波动
        for (int i = 0; i < 300; i++) {
            rows.add(row((long) i * 1000, 5.0 + (i % 3)));
            rows.add(row((long) i * 1000, 5.0 + ((i + 1) % 3)));
        }
        // 稀疏平台：1000s 处降到 0，长时间恒定，9000s 处升回 5（中间没有采样点）
        rows.add(row(1_000_000L, 5.0));
        rows.add(row(1_000_000L, 0.0));
        rows.add(row(9_000_000L, 0.0));
        rows.add(row(9_000_000L, 5.0));
        // 后段 10000~10299s：又密集
        for (int i = 0; i < 300; i++) {
            rows.add(row(10_000_000L + i * 1000L, 5.0 + (i % 3)));
            rows.add(row(10_000_000L + i * 1000L, 5.0 + ((i + 1) % 3)));
        }

        List<Map<String, Object>> sampled = service.downsampleRows(rows, 400, "timeMs", 3);

        Set<Object> xs = new LinkedHashSet<>();
        for (Map<String, Object> r : sampled) {
            xs.add(r.get("timeMs"));
        }
        assertTrue(xs.contains(0L), "首点必须保留");
        assertTrue(xs.contains(1_000_000L), "平台入口（降至 0）必须保留，否则会连出斜线");
        assertTrue(xs.contains(9_000_000L), "平台出口（升回 5）必须保留，否则会连出斜线");
        assertTrue(xs.contains(10_299_000L), "末点必须保留");
        assertTrue(sampled.size() <= 800, "抽样后行数应仍在预算量级：" + sampled.size());
        assertTrue(sampled.size() < rows.size(), "确实发生了抽样");
    }
}
