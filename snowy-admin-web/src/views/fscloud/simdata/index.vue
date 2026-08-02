<template>
	<div class="simdata-container">
		<div class="simdata-header">
			<a-button type="primary" @click="fetchResult" :loading="loading">获取仿真结果</a-button>
			<span class="simdata-model-name" v-if="myModel.modelFileName">{{ myModel.modelFileName }}</span>
		</div>

		<a-card v-if="result" size="small" class="overview-card">
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
				<a-descriptions-item label="参数快照时间">{{ formatTs(result.parameterSnapshotAt) }}</a-descriptions-item>
				<a-descriptions-item label="结果快照时间">{{ formatTs(result.resultSnapshotAt) }}</a-descriptions-item>
				<a-descriptions-item label="运行 ID" :span="1">{{ result.runId || '-' }}</a-descriptions-item>
			</a-descriptions>
		</a-card>

		<div v-else class="simdata-empty">
			<a-empty description="点击「获取仿真结果」查看数据">
				<a-button type="primary" @click="fetchResult" :loading="loading">获取仿真结果</a-button>
			</a-empty>
		</div>

		<div v-if="result" class="simdata-tabs">
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
								@pageChange="onDatasetPageChange"
							/>
							</a-tab-pane>
						</a-tabs>
					</a-tab-pane>
				</a-tabs>
			</a-card>
		</div>
	</div>
</template>

<script setup>
	import { ref, computed } from 'vue'
	import { useMyModelStore } from '@/store/myModel'
	import fsSimApi from '@/api/fscloud/fsSimApi'
	import { message } from 'ant-design-vue'
	import ResultTable from './ResultTable.vue'
	import LeanMetricsTable from './LeanMetricsTable.vue'
	import EnergyAnalysis from './EnergyAnalysis.vue'
	import { tableTitleKeyMap } from './TableTitleKeyMap.js'

	const myModel = useMyModelStore()

	const loading = ref(false)
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

	// ====== 由映射表驱动 tab ======

	/** 某 dataset 是否显示（show !== false） */
	function isDatasetVisible(groupKey, datasetKey) {
		return tableTitleKeyMap[groupKey]?.[datasetKey]?.show !== false
	}

	/** 某 group 下是否有可见 dataset（控制父 tab 显示） */
	function isGroupVisible(groupKey) {
		const g = tableTitleKeyMap[groupKey] || {}
		return Object.keys(g).some(dsKey => isDatasetVisible(groupKey, dsKey))
	}

	/** 从映射表生成 dataset 数组（已过滤 show=false） */
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

	const overviewDatasets = makeDatasets('overview')
	const basicDatasets = makeDatasets('basic')
	const leanDatasets = makeDatasets('lean')
	const energyDatasets = makeDatasets('energy')

	function summaryResult(name) { return result.value?.summaryResults?.[name] || null }
	function summaryRows(name) { const s = summaryResult(name); return s?.rows || [] }

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
				message.success('获取仿真结果成功')
			} else { message.error('获取仿真结果失败：返回数据为空') }
		} catch (err) {
			message.error('获取仿真结果失败：' + (err?.msg || err?.message || '请求异常'))
		} finally { loading.value = false }
	}

	async function loadDataset(dataset, page, pageSize) {
		if (!dataset || !result.value?.runId) return
		datasetLoading.value = true
		activeDataset.value = dataset
		try {
			const offset = (page - 1) * pageSize
			const res = await fsSimApi.fsSimGetResultDatasetPage({
				modelId: myModel.modelId,
				runId: result.value.runId,
				dataset: dataset,
				limit: pageSize,
				offset: offset
			})
			if (res) {
				datasetRows.value = res.rows || res
				datasetTotal.value = res.total ?? (Array.isArray(res) ? res.length : 0)
				datasetPage.value = page
				datasetPageSize.value = pageSize
			} else {
				datasetRows.value = []
				datasetTotal.value = 0
			}
		} catch (err) {
			datasetRows.value = []
			datasetTotal.value = 0
		} finally { datasetLoading.value = false }
	}

	function onDatasetPageChange(page, pageSize) {
		loadDataset(activeDataset.value, page, pageSize || 25)
	}
	function onBasicTabChange(dataset) { datasetPage.value = 1; loadDataset(dataset, 1, 25) }
	function onLeanTabChange(dataset) { datasetPage.value = 1; loadDataset(dataset, 1, 25) }
	function onEnergyTabChange(dataset) { datasetPage.value = 1; loadDataset(dataset, 1, 25) }
	function onTab1Change() {}
</script>

<style scoped>
	.simdata-container { padding: 16px; display: flex; flex-direction: column; gap: 12px; height: calc(100vh - 140px); overflow-y: auto; }
	.simdata-header { display: flex; align-items: center; gap: 12px; }
	.simdata-model-name { font-size: 14px; color: #666; }
	.overview-card { flex-shrink: 0; }
	.overview-card :deep(.ant-descriptions-item-label) { font-weight: 600; width: 140px; }
	.simdata-tabs { flex: 1; }
	.simdata-tabs :deep(.ant-card-body) { padding: 12px; }
	.tab-placeholder { padding: 40px 0; text-align: center; color: #999; font-size: 14px; }
	.simdata-empty { display: flex; justify-content: center; align-items: center; height: 300px; }
</style>
