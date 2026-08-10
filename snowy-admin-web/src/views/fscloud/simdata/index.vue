<template>
	<div class="simdata-container">
		<!-- 顶部操作栏 -->
		<div class="simdata-header">
			<a-button type="primary" @click="fetchResult" :loading="loading">获取仿真结果</a-button>
			<a-button
				type="primary"
				ghost
				:disabled="!canSaveResult"
				:loading="saving"
				@click="saveResultSnapshot"
			>保存结果快照</a-button>
			<span class="simdata-model-name" v-if="myModel.modelFileName">{{ myModel.modelFileName }}</span>
		</div>

		<div class="simdata-body">
			<!-- 左侧：模式 + 快照列表 -->
			<div class="simdata-left">
				<a-card size="small" title="数据分析模式">
					<a-radio-group v-model:value="mode" @change="onModeChange">
						<a-radio value="single">单快照模式</a-radio>
						<a-radio value="compare">横向对比模式</a-radio>
					</a-radio-group>
				</a-card>

				<a-card size="small" title="快照列表" class="snapshot-list-card">
					<a-list :data-source="snapshotListItems" :bordered="false" :pagination="false" size="small">
						<template #renderItem="{ item }">
							<a-list-item
								:class="[{ 'list-item-active': item.key === selectedKey }, { 'list-item-current': item.type === 'current' }]"
								style="cursor: pointer; padding: 6px 8px"
								@click="onSelectSnapshot(item)"
							>
								<a-checkbox
									v-if="mode !== 'single'"
									:checked="checkedIds.includes(item.key)"
									@click.stop
									@change="e => onToggleCheck(item, e.target.checked)"
									style="margin-right: 6px"
								/>
								<div class="snapshot-item">
									<div class="snapshot-item-title">
										<span v-if="item.role === 'main'" class="role-tag role-main">主体</span>
										<span v-else-if="item.role === 'ref'" class="role-tag role-ref">对照</span>
										{{ item.title }}
									</div>
									<div class="snapshot-item-sub" v-if="item.sub">{{ item.sub }}</div>
								</div>
							</a-list-item>
						</template>
					</a-list>
					<div v-if="mode !== 'single' && checkedIds.length < 2" class="compare-hint">
						{{ checkedIds.length === 0 ? '请勾选两个快照进行对比（第1个为主体）' : '请再勾选一个快照作为对照' }}
					</div>
				</a-card>
			</div>

			<!-- 右侧：内容区 -->
			<div class="simdata-right">
				<!-- 总览信息（单快照模式 或 对比模式的结果对照页） -->
				<a-card v-if="result && showResultView" size="small" class="overview-card">
					<a-descriptions :column="3" size="small" bordered>
						<a-descriptions-item label="模型文件名">{{ result.modelFileName || '-' }}</a-descriptions-item>
						<a-descriptions-item label="仿真状态">
							<a-tag :color="statusColor(result.status)">{{ result.status }}</a-tag>
						</a-descriptions-item>
						<a-descriptions-item label="结果状态">
							<a-tag :color="result.resultStatus === 'READY' ? 'green' : 'orange'">{{ result.resultStatus }}</a-tag>
						</a-descriptions-item>
						<a-descriptions-item label="仿真时长">{{ formatTime(result.simTime) }}</a-descriptions-item>
						<a-descriptions-item label="开始时间">{{ formatTs(result.startedAt) }}</a-descriptions-item>
						<a-descriptions-item label="结束时间">{{ formatTs(result.finishedAt) }}</a-descriptions-item>
						<a-descriptions-item label="总产量">{{ result.totalOutput }}</a-descriptions-item>
						<a-descriptions-item label="产量/小时">{{ result.throughputPerHour }}</a-descriptions-item>
						<a-descriptions-item label="当前 WIP">{{ result.currentWip }}</a-descriptions-item>
						<a-descriptions-item label="运行 ID" :span="3">{{ result.runId || '-' }}</a-descriptions-item>
					</a-descriptions>
				</a-card>

				<!-- 横向对比模式：二级 tabs（参数对照 / 结果对照） -->
				<a-tabs v-if="mode === 'compare'" v-model:activeKey="compareView" class="compare-view-tabs" size="small">
					<a-tab-pane key="param" tab="参数对照">
						<a-card size="small" class="param-compare-card">
							<template #title>参数快照对比</template>
							<div v-if="!paramCompareReady" class="tab-placeholder">
								{{ paramCompareMsg || '请勾选两个快照进行参数对比' }}
							</div>
							<div v-else>
								<div v-for="tbl in paramDiffTables" :key="tbl.tableName" class="param-table-block">
									<div class="param-table-title">{{ tbl.tableName }}
										<span v-if="tbl.onlyMain" class="param-table-tag">仅主体有</span>
										<span v-else-if="tbl.onlyRef" class="param-table-tag">仅对照有</span>
										<span v-else class="param-table-tag">共 {{ tbl.rowCount }} 行</span>
									</div>
									<table v-if="tbl.rows.length" class="param-diff-table">
										<thead>
											<tr>
												<th>#</th>
												<th v-for="c in tbl.columns" :key="c.name">{{ c.name }}</th>
											</tr>
										</thead>
										<tbody>
											<tr v-for="(r, ri) in tbl.rows" :key="ri">
												<td class="param-row-num">{{ ri }}</td>
												<td v-for="c in tbl.columns" :key="c.name">
													{{ r[c.name] }}
													<span v-if="r._diffMap && r._diffMap[c.name]" :style="diffStyle(r._diffMap[c.name])">{{ r._diffMap[c.name].text }}</span>
												</td>
											</tr>
										</tbody>
									</table>
									<div v-else class="tab-placeholder">该表无数据</div>
								</div>
							</div>
						</a-card>
					</a-tab-pane>
					<a-tab-pane key="result" tab="结果对照">
						<div v-if="!result" class="simdata-empty">
							<a-empty description="请先勾选两个快照进行对比（第1个为主体，第2个为对照）" />
						</div>
					</a-tab-pane>
				</a-tabs>

				<!-- 结果 a-tabs（单快照 或 对比模式结果对照页） -->
				<div v-if="result && showResultView" class="simdata-tabs">
					<a-card size="small">
						<a-tabs v-model:activeKey="tab1Key" @change="onTab1Change">
							<!-- 结果总览 -->
							<a-tab-pane key="overview" tab="结果总览" v-if="isGroupVisible('overview')">
								<a-tabs v-model:activeKey="tab2Key" size="small">
									<a-tab-pane v-for="ds in overviewDatasets" :key="ds.tabKey" :tab="ds.tabBrief">
										<ResultTable
											v-if="ds.tabKey !== 'leanMetrics' && ds.tabKey !== 'energyAnalysis'"
											:rows="summaryRows(ds.tabKey)"
											groupKey="overview"
											:datasetKey="ds.tabKey"
											:compareRows="compareRowsOf('overview', ds.tabKey)"
											:compareKey="compareKeyOf('overview', ds.tabKey)"
											:local-paging="true"
										/>
										<LeanMetricsTable v-else-if="ds.tabKey === 'leanMetrics'" :summaryResult="summaryResult('leanMetrics')" />
										<EnergyAnalysis v-else :summaryResult="summaryResult('energyAnalysis')" />
									</a-tab-pane>
								</a-tabs>
							</a-tab-pane>
							<!-- 基础结果 -->
							<a-tab-pane key="basic" tab="基础结果" v-if="isGroupVisible('basic')">
								<a-tabs v-model:activeKey="basicTab" size="small" @change="onBasicTabChange">
									<a-tab-pane v-for="ds in basicDatasets" :key="ds.tabKey">
									<template #tab>
										<a-tooltip>
											<template #title>
												<div>{{ ds.tabKey }}</div>
												<div>{{ ds.tabTitle }}</div>
											</template>
											<span>{{ ds.tabBrief }}</span>
										</a-tooltip>
									</template>
									<ResultTable
										:rows="datasetRows"
										:loading="datasetLoading"
										:total="datasetTotal"
										:pageSize="datasetPageSize"
										:current="datasetPage"
										groupKey="basic"
										:datasetKey="ds.tabKey"
										:compareRows="compareRowsOf('basic', ds.tabKey)"
										:compareKey="compareKeyOf('basic', ds.tabKey)"
										@pageChange="onDatasetPageChange"
									/>
									</a-tab-pane>
								</a-tabs>
							</a-tab-pane>
							<!-- 精益结果 -->
							<a-tab-pane key="lean" tab="精益结果" v-if="isGroupVisible('lean')">
								<a-tabs v-model:activeKey="leanTab" size="small" @change="onLeanTabChange">
									<a-tab-pane v-for="ds in leanDatasets" :key="ds.tabKey">
									<template #tab>
										<a-tooltip>
											<template #title>
												<div>{{ ds.tabKey }}</div>
												<div>{{ ds.tabTitle }}</div>
											</template>
											<span>{{ ds.tabBrief }}</span>
										</a-tooltip>
									</template>
									<ResultTable
										:rows="datasetRows"
										:loading="datasetLoading"
										:total="datasetTotal"
										:pageSize="datasetPageSize"
										:current="datasetPage"
										groupKey="lean"
										:datasetKey="ds.tabKey"
										:compareRows="compareRowsOf('lean', ds.tabKey)"
										:compareKey="compareKeyOf('lean', ds.tabKey)"
										@pageChange="onDatasetPageChange"
									/>
									</a-tab-pane>
								</a-tabs>
							</a-tab-pane>
							<!-- 能耗结果 -->
							<a-tab-pane key="energy" tab="能耗结果" v-if="isGroupVisible('energy')">
								<a-tabs v-model:activeKey="energyTab" size="small" @change="onEnergyTabChange">
									<a-tab-pane v-for="ds in energyDatasets" :key="ds.tabKey">
									<template #tab>
										<a-tooltip>
											<template #title>
												<div>{{ ds.tabKey }}</div>
												<div>{{ ds.tabTitle }}</div>
											</template>
											<span>{{ ds.tabBrief }}</span>
										</a-tooltip>
									</template>
									<ResultTable
										:rows="datasetRows"
										:loading="datasetLoading"
										:total="datasetTotal"
										:pageSize="datasetPageSize"
										:current="datasetPage"
										groupKey="energy"
										:datasetKey="ds.tabKey"
										:compareRows="compareRowsOf('energy', ds.tabKey)"
										:compareKey="compareKeyOf('energy', ds.tabKey)"
										@pageChange="onDatasetPageChange"
									/>
									</a-tab-pane>
								</a-tabs>
							</a-tab-pane>
						</a-tabs>
					</a-card>
				</div>

				<div v-if="!result && showResultView" class="simdata-empty">
					<a-empty :description="mode === 'single' ? '点击「获取仿真结果」查看数据' : '请先勾选两个快照进行对比'">
						<a-button v-if="mode === 'single'" type="primary" @click="fetchResult" :loading="loading">获取仿真结果</a-button>
					</a-empty>
				</div>
			</div>
		</div>
	</div>
</template>

<script setup>
	import { ref, computed } from 'vue'
	import { useMyModelStore } from '@/store/myModel'
	import fsSimApi from '@/api/fscloud/fsSimApi'
	import { message, Modal } from 'ant-design-vue'
	import ResultTable from './ResultTable.vue'
	import LeanMetricsTable from './LeanMetricsTable.vue'
	import EnergyAnalysis from './EnergyAnalysis.vue'
	import { tableTitleKeyMap } from './TableTitleKeyMap.js'

	const myModel = useMyModelStore()

	const loading = ref(false)
	const saving = ref(false)
	const result = ref(null)
	const tab1Key = ref('overview')
	const tab2Key = ref('taktTime')
	const basicTab = ref('partCycleTime')
	const leanTab = ref('lean.productLines')
	const energyTab = ref('energy.mediums')

	const datasetRows = ref([])
	const datasetLoading = ref(false)
	const datasetTotal = ref(0)
	const datasetPageSize = ref(25)
	const datasetPage = ref(1)
	const activeDataset = ref('')

	// ====== 快照列表 / 模式 / 对比 ======
	const mode = ref('single')                    // single | compare
	const compareView = ref('param')              // 对比模式下二级页：param 参数对照 | result 结果对照
	const selectedKey = ref('__current__')        // '__current__' 或历史快照 id
	const historyList = ref([])                   // listResultSnapshots 结果
	const checkedIds = ref([])                    // 对比勾选（第1个主体、第2个对照）
	const compareRowsCache = ref({})              // 对照 dataset 全量缓存 { dataset: rows }
	const paramSnapList = ref([])                 // 参数快照列表（含 runId）
	const paramMainTables = ref([])               // 主体参数表
	const paramRefTables = ref([])                // 对照参数表
	const paramCompareMsg = ref('')

	/** 是否显示"总览 + 结果 a-tabs"（单快照模式 或 对比模式的结果对照页） */
	const showResultView = computed(() =>
		mode.value === 'single' || (mode.value === 'compare' && compareView.value === 'result')
	)

	// ====== 映射表驱动 tab ======
	function isDatasetVisible(groupKey, datasetKey) {
		return tableTitleKeyMap[groupKey]?.[datasetKey]?.show !== false
	}
	function isGroupVisible(groupKey) {
		const g = tableTitleKeyMap[groupKey] || {}
		return Object.keys(g).some(dsKey => isDatasetVisible(groupKey, dsKey))
	}
	function makeDatasets(groupKey) {
		const g = tableTitleKeyMap[groupKey] || {}
		return Object.keys(g)
			.filter(dsKey => g[dsKey].show !== false)
			.map(dsKey => ({
				tabKey: dsKey,
				tabTitle: g[dsKey].tabTitle || dsKey,
				tabBrief: g[dsKey].tabBrief || dsKey
			}))
	}
	function compareKeyOf(groupKey, datasetKey) {
		return tableTitleKeyMap[groupKey]?.[datasetKey]?.compareKey || ''
	}
	const overviewDatasets = makeDatasets('overview')
	const basicDatasets = makeDatasets('basic')
	const leanDatasets = makeDatasets('lean')
	const energyDatasets = makeDatasets('energy')

	function summaryResult(name) { return result.value?.summaryResults?.[name] || null }
	function summaryRows(name) { const s = summaryResult(name); return s?.rows || [] }

	// ====== 快照列表 ======
	const snapshotListItems = computed(() => {
		const items = [{ key: '__current__', title: '当前仿真的快照', type: 'current' }]
		historyList.value.forEach(item => {
			items.push({
				key: item.id,
				id: item.id,
				title: formatTs(item.snapshotAt),
				sub: `总产量 ${item.totalOutput ?? '-'} · ${formatTime(item.simTime)}`,
				type: 'history'
			})
		})
		// 对比模式：按勾选顺序标记主体（第1个）/对照（第2个）
		if (mode.value !== 'single' && checkedIds.value.length >= 1) {
			for (const it of items) {
				if (it.key === checkedIds.value[0]) it.role = 'main'
				if (checkedIds.value[1] && it.key === checkedIds.value[1]) it.role = 'ref'
			}
		}
		return items
	})

	function statusColor(status) {
		switch (status) {
			case 'RUNNING': return 'blue'
			case 'PAUSED': return 'orange'
			case 'STOPPED': return 'default'
			case 'LOADED': return 'green'
			default: return 'default'
		}
	}
	function formatTime(simTime) {
		if (!simTime) return '-'
		const sec = Math.floor(simTime / 1000)
		if (sec < 60) return sec + ' 秒'
		const min = Math.floor(sec / 60)
		if (min < 60) return min + ' 分 ' + (sec % 60) + ' 秒'
		const hour = Math.floor(min / 60)
		return hour + ' 时 ' + (min % 60) + ' 分'
	}
	function formatTs(ts) {
		if (!ts) return '-'
		const d = new Date(ts)
		const pad = n => String(n).padStart(2, '0')
		return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
	}

	// ====== 保存结果快照 ======
	const canSaveResult = computed(() =>
		!!myModel.isLoaded && !!myModel.runId && !!result.value && result.value.resultStatus === 'READY'
	)

	async function saveResultSnapshot() {
		if (!canSaveResult.value) return
		saving.value = true
		try {
			const res = await fsSimApi.fsSimSaveResultSnapshot({
				modelId: myModel.modelId,
				runId: myModel.runId
			})
			message.success('结果快照保存成功（参数+结果成对落库）')
			await loadHistoryList()
		} catch (err) {
			message.error('保存结果快照失败：' + (err?.msg || err?.message || '请求异常'))
		} finally { saving.value = false }
	}

	async function loadHistoryList() {
		if (!myModel.modelFileId && !myModel.recordId) return
		try {
			const res = await fsSimApi.fsSimListResultSnapshots({
				modelFileId: myModel.recordId || myModel.modelFileId
			})
			historyList.value = Array.isArray(res) ? res : []
			// 刷新参数快照列表（参数对比用）
			loadParamSnapList()
		} catch (err) {
			historyList.value = []
		}
	}

	async function loadParamSnapList() {
		try {
			const res = await fsSimApi.fsSimListSnapshots({
				modelFileId: myModel.recordId || myModel.modelFileId
			})
			paramSnapList.value = Array.isArray(res) ? res : []
		} catch (err) {
			paramSnapList.value = []
		}
	}

	// ====== 获取结果 ======
	const fetchResult = async () => {
		if (!myModel.isLoaded) { message.warning('请先加载仿真模型'); return }
		loading.value = true
		try {
			const res = await fsSimApi.fsSimGetResultSnapshot({
				modelId: myModel.modelId, runId: myModel.runId || ''
			})
			if (res) {
				result.value = res
				myModel.resultSnapshot = res
				myModel.runId = res.runId
				selectedKey.value = '__current__'
				message.success('获取仿真结果成功')
			} else { message.error('获取仿真结果失败：返回数据为空') }
		} catch (err) {
			message.error('获取仿真结果失败：' + (err?.msg || err?.message || '请求异常'))
		} finally { loading.value = false }
	}

	// 选中历史快照 → 从数据库加载
	async function loadSnapshotFromDb(id) {
		try {
			const res = await fsSimApi.fsSimGetResultSnapshotById({ id })
			if (res) {
				result.value = res
				selectedKey.value = id
				// 清空上一份数据，再切到第一个 dataset 拉取
				datasetRows.value = []
				datasetTotal.value = 0
				const firstDs = basicDatasets[0]?.tabKey
				if (firstDs) loadDataset(firstDs, 1, 25)
			} else { message.warning('快照数据为空') }
		} catch (err) {
			message.error('加载历史快照失败')
		}
	}

	function onSelectSnapshot(item) {
		if (mode.value !== 'single') return   // 对比模式下点击不切换选中
		selectedKey.value = item.key
		if (item.type === 'current') {
			fetchResult()
		} else {
			loadSnapshotFromDb(item.id)
		}
	}

	// ====== 对比模式 ======
	function onModeChange() {
		// 切换模式：清空勾选与两页缓存
		checkedIds.value = []
		compareRowsCache.value = {}
		paramMainTables.value = []
		paramRefTables.value = []
		paramCompareMsg.value = ''
		compareView.value = 'param'
		if (mode.value !== 'compare') {
			// 回到单快照：清掉对比缓存的结果页数据
			datasetRows.value = []
			datasetTotal.value = 0
		}
	}

	function onToggleCheck(item, checked) {
		const key = item.key
		if (checked) {
			if (checkedIds.value.includes(key)) return
			if (checkedIds.value.length >= 2) {
				message.warning('暂时只支持对比两个快照')
				return
			}
			// 当前行：可作主体（第1个），但不能作对照（第2个）——对照必须是已落库历史快照
			if (item.type === 'current' && checkedIds.value.length === 1) {
				message.warning('当前仿真未落库，不能作为对照快照，请选择历史快照作为对照')
				return
			}
			// 当前行作主体：需模型在仿真（有 runId）且已获取结果
			if (item.type === 'current' && !myModel.isLoaded) {
				message.warning('当前没有模型在仿真，不能作为对比快照')
				return
			}
			checkedIds.value.push(key)
		} else {
			checkedIds.value = checkedIds.value.filter(k => k !== key)
			compareRowsCache.value = {}
			paramMainTables.value = []
			paramRefTables.value = []
			paramCompareMsg.value = checkedIds.value.length === 0 ? '请勾选两个快照（第1个为主体）' : ''
			datasetRows.value = []
			datasetTotal.value = 0
		}
		// 勾满 2 个后一次性构建参数对照 + 结果对照
		if (checkedIds.value.length === 2) {
			loadCompareBoth()
		}
	}

	/** 勾满两个快照后，同时构建参数对照与结果对照（切换页面不重新获取） */
	function loadCompareBoth() {
		if (checkedIds.value.length < 2) return
		const mainKey = checkedIds.value[0]
		const refKey = checkedIds.value[1]
		// ---- 结果侧：主体数据 + 对照元信息 ----
		if (mainKey === '__current__') {
			fetchResult()
		} else {
			loadSnapshotFromDb(mainKey)
		}
		compareRefMeta.value = null
		if (refKey !== '__current__') {
			fsSimApi.fsSimGetResultSnapshotById({ id: refKey }).then(res => {
				compareRefMeta.value = res || null
			}).catch(() => { compareRefMeta.value = null })
		}
		// ---- 参数侧：两快照参数表 ----
		loadParamCompare()
		// 默认显示参数对照页
		compareView.value = 'param'
	}

	const compareRefMeta = ref(null)

	// 对照某 dataset 全量数据（缓存）
	function compareRowsOf(groupKey, datasetKey) {
		if (mode.value !== 'compare' || checkedIds.value.length < 2) return []
		const ck = compareKeyOf(groupKey, datasetKey)
		if (!ck) return []   // 空串：不对比
		return compareRowsCache.value[datasetKey] || []
	}

	async function ensureCompareRows(dataset) {
		if (mode.value !== 'compare' || checkedIds.value.length < 2) return
		const ck = compareKeyOf(currentGroupKey.value, dataset)
		if (!ck) return
		if (compareRowsCache.value[dataset]) return
		const refId = checkedIds.value[1]
		if (refId === '__current__') return
		try {
			// 循环分页取对照全量（limit=1000，上限 500 页防失控）
			const all = []
			let offset = 0
			let total = 0
			for (let guard = 0; guard < 500; guard++) {
				const res = await fsSimApi.fsSimGetResultDatasetPageInDb({
					id: refId, dataset, limit: 1000, offset
				})
				if (!res) break
				all.push(...(res.rows || []))
				total = res.total || 0
				offset += (res.rows || []).length
				if (offset >= total || !res.rows || res.rows.length === 0) break
			}
			compareRowsCache.value = { ...compareRowsCache.value, [dataset]: all }
			if (all.length < total) {
				console.warn(`[simdata] 对照数据截断: dataset=${dataset}, 已取 ${all.length}/${total}`)
			}
		} catch (err) {
			// 对照数据拉取失败不阻断主体显示
		}
	}

	const currentGroupKey = ref('basic')

	// ====== 参数对比 ======
	const paramCompareReady = computed(() =>
		(paramMainTables.value.length > 0 || paramRefTables.value.length > 0) && !paramCompareMsg.value
	)

	async function loadParamCompare() {
		paramMainTables.value = []
		paramRefTables.value = []
		paramCompareMsg.value = ''
		if (checkedIds.value.length < 2) {
			paramCompareMsg.value = '请勾选两个快照（第1个为主体，第2个为对照）'
			return
		}
		const mainKey = checkedIds.value[0]
		const refKey = checkedIds.value[1]
		try {
			const [mainTables, refTables] = await Promise.all([
				loadParamTablesOf(mainKey),
				loadParamTablesOf(refKey)
			])
			paramMainTables.value = mainTables
			paramRefTables.value = refTables
			if (mainTables.length === 0 && refTables.length === 0) {
				paramCompareMsg.value = '两个快照均无参数数据'
			}
		} catch (err) {
			paramCompareMsg.value = '加载参数对比失败：' + (err?.msg || err?.message || '请求异常')
		}
	}

	/** 取某快照的参数表列表（[{tableName, columns, rows}]）：当前行→实时 MCP；历史行→成对参数快照 */
	async function loadParamTablesOf(key) {
		if (key === '__current__') {
			const res = await fsSimApi.fsSimGetParameterSnapshot({ modelId: myModel.modelId, runId: myModel.runId || '' })
			return normalizeParamTables(res)
		}
		// 历史：先取该结果快照的 runId，再匹配成对参数快照
		const hist = historyList.value.find(h => h.id === key)
		const runId = hist?.runId || (key === selectedKey.value ? result.value?.runId : null)
		if (!runId) return []
		const snap = paramSnapList.value.find(p => p.runId && p.runId === runId)
		if (!snap) return []
		const detail = await fsSimApi.fsSimGetSnapshot({ id: snap.id })
		return normalizeParamTables(detail)
	}

	function normalizeParamTables(detail) {
		if (!detail) return []
		let tables = detail.tables
		if (typeof tables === 'string') {
			try { tables = JSON.parse(tables) } catch (e) { return [] }
		}
		if (Array.isArray(tables)) {
			// MCP 原生：[{tableName, columns, rows}]，columns 每项可能是 {name,type} 或字符串
			return tables.map(t => {
				const cols = (t.columns || []).map(c =>
					typeof c === 'string' ? { name: c, type: '' } : { name: c.name || c, type: c.type || '' }
				)
				return {
					tableName: t.tableName || t.name || '',
					columns: cols,
					rows: (t.rows || []).map(r => Array.isArray(r) ? r : Object.values(r))
				}
			}).filter(t => t.tableName)
		}
		if (tables && typeof tables === 'object') {
			// keyed 格式：{ 表名: rows[][] }（首行为表头），无类型信息
			return Object.keys(tables).map(name => {
				const rows = tables[name] || []
				if (rows.length === 0) return { tableName: name, columns: [], rows: [] }
				const headers = rows[0]
				return {
					tableName: name,
					columns: headers.map(h => ({ name: h, type: '' })),
					rows: rows.slice(1)
				}
			})
		}
		return []
	}

	/** 时间差值短格式：ms → d天 h:m:s / h:m:s / m:s / s */
	function fmtTsShort(ms) {
		const totalSec = Math.floor(ms / 1000)
		const d = Math.floor(totalSec / 86400)
		const h = Math.floor((totalSec % 86400) / 3600)
		const m = Math.floor((totalSec % 3600) / 60)
		const s = totalSec % 60
		if (d > 0) return `${d}天 ${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
		if (h > 0) return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
		if (m > 0) return `${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
		if (s > 0) return `${s}`
		return '0'
	}

	/** 参数对比单元格 diff：数字列（i/f/t/d 或值可转数值）算差值（红/绿+箭头）；字符串列不等显示 [对照值]（橙） */
	function paramDiffForCell(col, v1, v2) {
		const mv = v1 ?? ''
		const rv = v2 ?? ''
		// MCP 参数表 type 为完整写法（String/Int/Time/Float/Double），取首字母忽略大小写：
		// s-string, i-int, t-time, f-float, d-double
		const type = (col.type || '').trim().charAt(0).toLowerCase()
		const isNumericType = type === 'i' || type === 'f' || type === 't' || type === 'd'
		if (isNumericType || typeof mv === 'number' || typeof rv === 'number') {
			const n1 = Number(mv)
			const n2 = Number(rv)
			if (isNaN(n1) || isNaN(n2)) {
				// 声明数字但值非数字：按字符串比较
				if (String(mv) === String(rv)) return null
				return { cls: 'diff', text: '[' + String(rv) + ']' }
			}
			const d = n1 - n2
			if (d === 0) return null
			const cls = d > 0 ? 'gt' : 'lt'
			const arrow = d > 0 ? '↑' : '↓'
			const diffVal = type === 't' ? fmtTsShort(Math.abs(d)) : String(Math.abs(d))
			return { cls, text: ' ' + arrow + diffVal }
		}
		// 字符串
		if (String(mv) === String(rv)) return null
		return { cls: 'diff', text: '[' + String(rv) + ']' }
	}

	/** 差异标注颜色 */
	function diffStyle(diff) {
		if (!diff) return ''
		if (diff.cls === 'gt') return 'color:#cf1322;font-weight:600;margin-left:4px'
		if (diff.cls === 'lt') return 'color:#389e0d;font-weight:600;margin-left:4px'
		return 'color:#d46b08;font-weight:600;margin-left:4px'
	}

	/** 参数表 diff：按表名配对、行号对齐，差异单元格标记 */
	const paramDiffTables = computed(() => {
		const main = paramMainTables.value
		const ref = paramRefTables.value
		const tables = []
		const refByName = {}
		ref.forEach(t => { refByName[t.tableName] = t })
		const mainNames = new Set(main.map(t => t.tableName))
		const refNames = new Set(ref.map(t => t.tableName))
		const allNames = [...mainNames, ...[...refNames].filter(n => !mainNames.has(n))]
		for (const name of allNames) {
			const m = main.find(t => t.tableName === name)
			const r = refByName[name]
			if (!m) {
				tables.push({ tableName: name, onlyRef: true, columns: r.columns, rowCount: r.rows.length, rows: r.rows.map(row => ({ ...rowToObj(r.columns, row) })) })
				continue
			}
			if (!r) {
				tables.push({ tableName: name, onlyMain: true, columns: m.columns, rowCount: m.rows.length, rows: m.rows.map(row => ({ ...rowToObj(m.columns, row) })) })
				continue
			}
			// 两侧都有：行号对齐
			const maxLen = Math.max(m.rows.length, r.rows.length)
			const columns = m.columns
			const rows = []
			for (let i = 0; i < maxLen; i++) {
				const mRow = rowToObj(m.columns, m.rows[i])
				const rRow = rowToObj(r.columns, r.rows[i])
				const diffMap = {}
				for (const col of columns) {
					const c = col.name
					const diff = paramDiffForCell(col, mRow[c], rRow[c])
					if (diff) diffMap[c] = diff
				}
				rows.push({ ...mRow, _diffMap: diffMap })
			}
			tables.push({ tableName: name, columns, rowCount: maxLen, rows })
		}
		return tables
	})

	function rowToObj(columns, row) {
		if (!row) return {}
		if (Array.isArray(row)) {
			const obj = {}
			columns.forEach((c, i) => { obj[c.name] = row[i] !== undefined ? row[i] : '' })
			return obj
		}
		return { ...row }
	}

	// ====== 数据集分页 ======
	async function loadDataset(dataset, page, pageSize) {
		if (!dataset) return
		const mainIsLive = selectedKey.value === '__current__'
		datasetLoading.value = true
		activeDataset.value = dataset
		currentGroupKey.value = currentGroupFor(dataset)
		// 对比模式：预取对照全量
		if (mode.value === 'compare') {
			ensureCompareRows(dataset)
		}
		try {
			const offset = (page - 1) * pageSize
			if (mainIsLive && mode.value === 'single') {
				// 实时：MCP 分页
				const res = await fsSimApi.fsSimGetResultDatasetPage({
					modelId: myModel.modelId,
					runId: myModel.runId || '',
					dataset, limit: pageSize, offset
				})
				applyDatasetPage(res, page, pageSize)
			} else {
				// 历史（单快照）或对比主体历史：DB 分页
				const id = mainIsLive ? null : selectedKey.value
				if (id === null) {
					const res = await fsSimApi.fsSimGetResultDatasetPage({
						modelId: myModel.modelId,
						runId: myModel.runId || '',
						dataset, limit: pageSize, offset
					})
					applyDatasetPage(res, page, pageSize)
				} else {
					const res = await fsSimApi.fsSimGetResultDatasetPageInDb({ id, dataset, limit: pageSize, offset })
					applyDatasetPage(res, page, pageSize)
				}
			}
		} catch (err) {
			datasetRows.value = []
			datasetTotal.value = 0
		} finally { datasetLoading.value = false }
	}

	function currentGroupFor(dataset) {
		for (const g of ['basic', 'lean', 'energy', 'overview']) {
			if (tableTitleKeyMap[g]?.[dataset]) return g
		}
		return 'basic'
	}

	function applyDatasetPage(res, page, pageSize) {
		if (res) {
			datasetRows.value = res.rows || res
			datasetTotal.value = res.total ?? (Array.isArray(res) ? res.length : 0)
			datasetPage.value = page
			datasetPageSize.value = pageSize
		} else {
			datasetRows.value = []
			datasetTotal.value = 0
		}
	}

	function onDatasetPageChange(page, pageSize) {
		console.log('onDatasetPageChange', page, pageSize)
		loadDataset(activeDataset.value, page, pageSize || 25)
	}
	function onBasicTabChange(dataset) { datasetPage.value = 1; loadDataset(dataset, 1, 25) }
	function onLeanTabChange(dataset) { datasetPage.value = 1; loadDataset(dataset, 1, 25) }
	function onEnergyTabChange(dataset) { datasetPage.value = 1; loadDataset(dataset, 1, 25) }
	function onTab1Change() {}

	// ====== 初始化 ======
	async function initPage() {
		if (!myModel.isLoaded) {
			const data = await fsSimApi.fsSimGetLoadedModel().catch(() => null)
			if (data && data.modelId) {
				myModel.setModel(data)
			} else {
				return
			}
		}
		loadHistoryList()
	}

	initPage()
</script>

<style scoped>
	.simdata-container { padding: 16px; display: flex; flex-direction: column; gap: 12px; height: calc(100vh - 140px); }
	.simdata-header { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
	.simdata-model-name { font-size: 14px; color: #666; }
	.simdata-body { display: flex; gap: 12px; flex: 1; min-height: 0; }
	.simdata-left { width: 220px; flex-shrink: 0; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
	.snapshot-list-card { flex: 1; min-height: 0; }
	.snapshot-list-card :deep(.ant-card-body) { padding: 8px; }
	.list-item-active { background: #e6f4ff; }
	.list-item-current .snapshot-item-title { color: #1677ff; font-weight: 600; }
	.snapshot-item-title { font-size: 13px; }
	.role-tag { font-size: 11px; line-height: 1.6; padding: 0 4px; border-radius: 2px; margin-right: 4px; }
	.role-main { color: #1677ff; background: #e6f4ff; }
	.role-ref { color: #722ed1; background: #f9f0ff; }
	.snapshot-item-sub { font-size: 12px; color: #999; text-align: right; }
	.compare-hint { padding: 8px; font-size: 12px; color: #d46b08; }
	.simdata-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
	.overview-card { flex-shrink: 0; }
	.overview-card :deep(.ant-descriptions-item-label) { font-weight: 600; width: 130px; }
	.simdata-tabs { flex: 1; }
	.simdata-tabs :deep(.ant-card-body) { padding: 12px; }
	/* 对比模式二级 tabs（参数对照/结果对照）：蓝色系，与内层结果 tabs（默认白色）区分 */
	.compare-view-tabs :deep(.ant-tabs-tab) {
		font-weight: 600;
		font-size: 14px;
	}
	.compare-view-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
		color: #1677ff;
	}
	.compare-view-tabs :deep(.ant-tabs-ink-bar) {
		background: #1677ff;
		height: 3px;
	}
	.compare-view-tabs :deep(.ant-card) { margin-top: 4px; }
	.tab-placeholder { padding: 40px 0; text-align: center; color: #999; font-size: 14px; }
	.simdata-empty { display: flex; justify-content: center; align-items: center; height: 300px; }
	.param-compare-card { flex: 1; }
	.param-table-block { margin-bottom: 16px; }
	.param-table-title { font-weight: 600; margin-bottom: 6px; font-size: 14px; }
	.param-table-tag { font-weight: 400; font-size: 12px; color: #999; margin-left: 8px; }
	.param-diff-table { border-collapse: collapse; width: 100%; font-size: 13px; }
	.param-diff-table th, .param-diff-table td { border: 1px solid #e8e8e8; padding: 4px 8px; text-align: left; white-space: nowrap; }
	.param-diff-table th { background: #fafafa; font-weight: 600; }
	.param-row-num { color: #999; width: 40px; text-align: right; }
</style>
