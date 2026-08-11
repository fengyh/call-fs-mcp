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

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.text.csv.CsvWriter;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.xiaonuo.common.exception.CommonException;
import vip.xiaonuo.fscloud.modular.simulation.dto.ResultDatasetPageDto;
import vip.xiaonuo.fscloud.modular.simulation.dto.ResultSnapshotDto;
import vip.xiaonuo.fscloud.modular.simulation.entity.FsSimResultSnapshot;
import vip.xiaonuo.fscloud.modular.simulation.mapper.FsSimResultSnapshotMapper;
import vip.xiaonuo.fscloud.modular.simulation.service.FsSimParamSnapshotService;
import vip.xiaonuo.fscloud.modular.simulation.service.FsSimResultSnapshotService;
import vip.xiaonuo.fscloud.modular.simulation.service.McpClientService;

import java.io.File;
import java.util.*;

/**
 * 仿真结果快照 Service 实现
 *
 * @author fengyuhe
 * @date  2026/08/05
 */
@Slf4j
@Service
public class FsSimResultSnapshotServiceImpl
        extends ServiceImpl<FsSimResultSnapshotMapper, FsSimResultSnapshot>
        implements FsSimResultSnapshotService {

    /** partCycleTime 数据集名（大表落 CSV 文件） */
    private static final String PART_CYCLE_TIME = "partCycleTime";

    /** 快照文件子目录（相对 fscloud.sim.static-path） */
    private static final String SNAPSHOT_DIR = "simsnapshot";

    /** 单次从 MCP 拉取的分页大小 */
    private static final int FETCH_PAGE_SIZE = 1000;

    @Resource
    private McpClientService mcpClientService;

    @Resource
    private FsSimParamSnapshotService fsSimParamSnapshotService;

    @Value("${fscloud.sim.static-path:${user.dir}${file.separator}static}")
    private String fscloudSimStaticPath;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveResultSnapshot(String userId, String modelId, String runId) {
        // runId 直接拼入文件路径，必须先做白名单校验，防止路径穿越
        if (StrUtil.isBlank(runId) || !runId.matches("[A-Za-z0-9_-]+")) {
            throw new CommonException("runId 非法：仅允许字母/数字/下划线/连字符");
        }
        // ---------- 1. 结果总览 ----------
        JSONObject mcpResult = mcpClientService.getResultSnapshot(userId, modelId, runId);
        JSONObject data = mcpResult.getJSONObject("data");
        if (data == null) {
            throw new CommonException("获取结果快照失败：返回数据为空");
        }
        String recordId = data.getStr("recordId");
        if (StrUtil.isBlank(recordId)) {
            throw new CommonException("结果快照缺少 recordId，无法落库");
        }
        String resultStatus = data.getStr("resultStatus");
        if (!"READY".equals(resultStatus)) {
            throw new CommonException("结果状态非 READY（当前={}），无法保存结果快照", resultStatus);
        }

        // ---------- 2. 成对参数快照（RUN_ID 非空，upsert） ----------
        JSONObject paramMcp = mcpClientService.getParameterSnapshot(userId, modelId, runId);
        JSONObject paramData = paramMcp.getJSONObject("data");
        if (paramData == null) {
            throw new CommonException("获取参数快照失败：返回数据为空，无法成对落库");
        }
        String parameterTables = jsonOrNull(paramData.get("parameterTables"));
        String mcpRows = jsonOrNull(paramData.get("mcpRows"));
        String tables = jsonOrNull(paramData.get("tables"));
        String missingTables = jsonOrNull(paramData.get("missingTables"));
        fsSimParamSnapshotService.savePairedSnapshot(recordId, runId, parameterTables, mcpRows, tables, missingTables);

        // ---------- 3. 逐 dataset 拉取全量数据 ----------
        JSONObject datasetsStore = new JSONObject();   // { dataset: { total, rows } }
        List<Map<String, Object>> datasetMetaList = new ArrayList<>();
        for (String ds : resolveDatasetNames(data.getJSONArray("datasets"))) {
            List<Map<String, Object>> allRows = fetchAllRows(userId, modelId, runId, ds);
            if (PART_CYCLE_TIME.equals(ds)) {
                // 大表落 CSV 文件
                writePartCycleTimeCsv(runId, allRows);
            } else {
                JSONObject dsObj = new JSONObject();
                dsObj.put("total", allRows.size());
                dsObj.put("rows", allRows);
                datasetsStore.set(ds, dsObj);
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", ds);
            meta.put("total", allRows.size());
            datasetMetaList.add(meta);
        }

        // ---------- 4. 结果快照 upsert（按 RUN_ID 幂等） ----------
        FsSimResultSnapshot entity = getByRunId(runId);
        Date now = new Date();
        if (entity == null) {
            entity = new FsSimResultSnapshot();
            entity.setRunId(runId);
            entity.setCreateTime(now);
        }
        entity.setModelFileId(recordId);
        entity.setModelFileName(data.getStr("modelFileName"));
        entity.setSimTime(data.getLong("simTime"));
        entity.setStartedAt(data.getLong("startedAt"));
        entity.setFinishedAt(data.getLong("finishedAt"));
        entity.setResultStatus(resultStatus);
        entity.setSnapshotAt(System.currentTimeMillis());
        JSONObject summary = data.getJSONObject("summaryResults");
        entity.setSummaryResults(summary != null ? summary.toString() : null);
        entity.setDatasets(datasetsStore.toString());
        entity.setPartCycleTimeFile(SNAPSHOT_DIR + File.separator + runId + "_" + PART_CYCLE_TIME + ".csv");
        entity.setUpdateTime(now);
        if (entity.getId() == null) {
            this.save(entity);
        } else {
            this.updateById(entity);
        }
        log.info("Result snapshot upserted: id={}, runId={}, modelFileId={}, datasets={}",
                entity.getId(), runId, recordId, datasetMetaList.size());
        return entity.getId();
    }

    @Override
    public List<ResultSnapshotDto> listByModelFileId(String modelFileId) {
        LambdaQueryWrapper<FsSimResultSnapshot> qw = new LambdaQueryWrapper<>();
        qw.eq(FsSimResultSnapshot::getModelFileId, modelFileId);
        qw.orderByDesc(FsSimResultSnapshot::getSnapshotAt);
        List<FsSimResultSnapshot> list = this.list(qw);
        return list.stream().map(e -> {
            // 列表场景不返回大数据字段（summaryResults/datasets）
            return toDto(e, false);
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public ResultSnapshotDto getById(Long id) {
        FsSimResultSnapshot entity = super.getById(id);
        if (entity == null) {
            throw new CommonException("结果快照不存在，id={}", id);
        }
        ResultSnapshotDto dto = toDto(entity, true);
        // 数据集元信息（名称/行数）
        List<Map<String, Object>> metaList = new ArrayList<>();
        if (StrUtil.isNotBlank(entity.getDatasets())) {
            JSONObject dsObj = JSONUtil.parseObj(entity.getDatasets());
            for (Map.Entry<String, Object> entry : dsObj.entrySet()) {
                if (entry.getValue() instanceof JSONObject) {
                    JSONObject inner = (JSONObject) entry.getValue();
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("name", entry.getKey());
                    meta.put("total", inner.getLong("total", 0L));
                    metaList.add(meta);
                }
            }
        }
        dto.setDatasets(metaList);
        return dto;
    }

    /**
     * 实体转 DTO（手动拷贝，避免 hutool BeanUtil 将 String(JSON) 转 Map 抛异常）。
     *
     * @param withSummary 是否解析 summaryResults 为 Map（列表场景传 false，仅置 null）
     */
    private ResultSnapshotDto toDto(FsSimResultSnapshot e, boolean withSummary) {
        ResultSnapshotDto dto = new ResultSnapshotDto();
        dto.setId(e.getId());
        dto.setRunId(e.getRunId());
        dto.setModelFileId(e.getModelFileId());
        dto.setModelFileName(e.getModelFileName());
        dto.setSimTime(e.getSimTime());
        dto.setStartedAt(e.getStartedAt());
        dto.setFinishedAt(e.getFinishedAt());
        dto.setResultStatus(e.getResultStatus());
        dto.setSnapshotAt(e.getSnapshotAt());
        dto.setPartCycleTimeFile(e.getPartCycleTimeFile());
        dto.setCreateTime(e.getCreateTime());
        if (withSummary && StrUtil.isNotBlank(e.getSummaryResults())) {
            dto.setSummaryResults(JSONUtil.parseObj(e.getSummaryResults()));
        }
        return dto;
    }

    @Override
    public ResultDatasetPageDto getDatasetPage(Long id, String dataset, int limit, int offset) {
        FsSimResultSnapshot entity = super.getById(id);
        if (entity == null) {
            throw new CommonException("结果快照不存在，id={}", id);
        }
        if (limit <= 0 || limit > 1000) {
            limit = 25;
        }
        if (offset < 0) {
            offset = 0;
        }
        ResultDatasetPageDto dto = new ResultDatasetPageDto();
        dto.setDataset(dataset);
        dto.setOffset(offset);
        dto.setLimit(limit);

        if (PART_CYCLE_TIME.equals(dataset)) {
            readPartCycleTimePage(entity, dto, limit, offset);
        } else {
            readJsonDatasetPage(entity, dataset, dto, limit, offset);
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        FsSimResultSnapshot entity = super.getById(id);
        if (entity == null) {
            throw new CommonException("结果快照不存在，id={}", id);
        }
        // 删除 partCycleTime 文件
        if (StrUtil.isNotBlank(entity.getPartCycleTimeFile())) {
            FileUtil.del(resolveSnapshotFile(entity.getPartCycleTimeFile()));
        }
        // 删除成对参数快照
        if (StrUtil.isNotBlank(entity.getModelFileId()) && StrUtil.isNotBlank(entity.getRunId())) {
            fsSimParamSnapshotService.deleteByModelAndRunId(entity.getModelFileId(), entity.getRunId());
        }
        this.removeById(id);
        log.info("Result snapshot deleted: id={}, runId={}", id, entity.getRunId());
    }

    // ==================== 私有辅助 ====================

    /** 从 MCP 返回的 datasets 数组中解析数据集名称列表（兼容对象/字符串两种形式） */
    private List<String> resolveDatasetNames(JSONArray datasets) {
        List<String> names = new ArrayList<>();
        if (datasets == null) {
            return names;
        }
        for (int i = 0; i < datasets.size(); i++) {
            Object item = datasets.get(i);
            if (item instanceof JSONObject) {
                JSONObject obj = (JSONObject) item;
                String name = obj.getStr("name");
                if (StrUtil.isBlank(name)) {
                    name = obj.getStr("dataset");
                }
                if (StrUtil.isNotBlank(name)) {
                    names.add(name);
                }
            } else if (item instanceof CharSequence) {
                names.add(item.toString());
            }
        }
        return names;
    }

    /** 循环分页拉取某 dataset 的全量数据 */
    private List<Map<String, Object>> fetchAllRows(String userId, String modelId, String runId, String dataset) {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int offset = 0;
        while (true) {
            JSONObject page = mcpClientService.getResultDatasetPage(userId, modelId, runId, dataset,
                    FETCH_PAGE_SIZE, offset);
            JSONObject pageData = page.getJSONObject("data");
            if (pageData == null) {
                break;
            }
            JSONArray rows = pageData.getJSONArray("rows");
            int fetched = 0;
            if (rows != null) {
                fetched = rows.size();
                for (int i = 0; i < rows.size(); i++) {
                    Object row = rows.get(i);
                    if (row instanceof JSONObject) {
                        allRows.add(((JSONObject) row).toBean(Map.class));
                    }
                }
            }
            // 以 hasMore 为主判据；rows 为空兜底退出，防止死循环
            boolean hasMore = pageData.getBool("hasMore", false);
            offset += fetched;
            if (!hasMore || fetched == 0) {
                break;
            }
        }
        log.info("Fetched all rows for dataset={}, total={}", dataset, allRows.size());
        return allRows;
    }

    /** partCycleTime 写 CSV 文件（列头取首行 key 顺序） */
    private void writePartCycleTimeCsv(String runId, List<Map<String, Object>> rows) {
        File file = resolveSnapshotFile(SNAPSHOT_DIR + File.separator + runId + "_" + PART_CYCLE_TIME + ".csv");
        FileUtil.mkParentDirs(file);
        try (CsvWriter writer = CsvUtil.getWriter(file, CharsetUtil.CHARSET_UTF_8)) {
            List<String> headers = null;
            for (Map<String, Object> row : rows) {
                if (headers == null) {
                    headers = new ArrayList<>(row.keySet());
                    writer.writeLine(headers.toArray(new String[0]));
                }
                List<String> values = new ArrayList<>(headers.size());
                for (String h : headers) {
                    Object v = row.get(h);
                    values.add(v != null ? String.valueOf(v) : "");
                }
                writer.writeLine(values.toArray(new String[0]));
            }
        }
        log.info("PartCycleTime csv written: file={}, rows={}", file.getName(), rows.size());
    }

    /** partCycleTime 从 CSV 文件流式分页读取 */
    private void readPartCycleTimePage(FsSimResultSnapshot entity, ResultDatasetPageDto dto, int limit, int offset) {
        if (StrUtil.isBlank(entity.getPartCycleTimeFile())) {
            dto.setTotal(0L);
            return;
        }
        File file = resolveSnapshotFile(entity.getPartCycleTimeFile());
        if (!file.exists()) {
            dto.setTotal(0L);
            return;
        }
        CsvReader reader = CsvUtil.getReader();
        List<String> headers = null;
        List<Map<String, Object>> pageRows = new ArrayList<>();
        long total = 0;
        int lineNo = 0;   // 0=表头行
        for (CsvRow csvRow : reader.read(file, CharsetUtil.CHARSET_UTF_8)) {
            if (lineNo == 0) {
                headers = new ArrayList<>(csvRow);
                lineNo++;
                continue;
            }
            lineNo++;
            total++;
            if (lineNo - 1 <= offset) {
                continue;   // 跳过 offset 条数据行
            }
            if (pageRows.size() >= limit) {
                continue;   // 已取满，仅继续计数
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                map.put(headers.get(i), i < csvRow.size() ? csvRow.get(i) : "");
            }
            pageRows.add(map);
        }
        dto.setRows(pageRows);
        dto.setTotal(total);
    }

    /** 小表 dataset 从 DATASETS JSON 分页读取 */
    private void readJsonDatasetPage(FsSimResultSnapshot entity, String dataset, ResultDatasetPageDto dto,
                                     int limit, int offset) {
        if (StrUtil.isBlank(entity.getDatasets())) {
            return;
        }
        JSONObject dsObj = JSONUtil.parseObj(entity.getDatasets());
        JSONObject inner = dsObj.getJSONObject(dataset);
        if (inner == null) {
            dto.setTotal(0L);
            return;
        }
        JSONArray rows = inner.getJSONArray("rows");
        long total = inner.getLong("total", rows != null ? rows.size() : 0L);
        dto.setTotal(total);
        if (rows == null) {
            return;
        }
        int start = Math.min(offset, rows.size());
        int end = Math.min(start + limit, rows.size());
        List<Map<String, Object>> pageRows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Object row = rows.get(i);
            if (row instanceof JSONObject) {
                pageRows.add(((JSONObject) row).toBean(Map.class));
            }
        }
        dto.setRows(pageRows);
    }

    private FsSimResultSnapshot getByRunId(String runId) {
        LambdaQueryWrapper<FsSimResultSnapshot> qw = new LambdaQueryWrapper<>();
        qw.eq(FsSimResultSnapshot::getRunId, runId);
        qw.last("LIMIT 1");
        return this.getOne(qw);
    }

    /** 快照文件绝对路径（相对 fscloud.sim.static-path 解析） */
    private File resolveSnapshotFile(String relativePath) {
        File base = FileUtil.file(fscloudSimStaticPath);
        return FileUtil.file(base, relativePath);
    }

    /** JSON 值转字符串，null 返回 null */
    private String jsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        return JSONUtil.toJsonStr(value);
    }
}
