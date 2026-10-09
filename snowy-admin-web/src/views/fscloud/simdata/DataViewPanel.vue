<template>
	<div class="dv-panel">
		<div v-if="chartable" class="dv-switch">
			<a-radio-group :value="chartMode" size="small" button-style="solid" @change="onSwitch">
				<a-radio-button value="table">表格</a-radio-button>
				<a-radio-button value="chart">图表</a-radio-button>
			</a-radio-group>
		</div>
		<a-spin :spinning="showChart && chartLoading">
			<ResultChart
				v-if="showChart"
				:chart="chartCfg"
				:rows="chartRows"
				:columns="columns"
				:total="chartTotal"
				:sampled="chartSampled"
			/>
			<ResultTable
				v-else
				:rows="rows"
				:loading="loading"
				:total="total"
				:pageSize="pageSize"
				:current="current"
				:groupKey="groupKey"
				:datasetKey="datasetKey"
				:compareRows="compareRows"
				:compareKey="compareKey"
				:localPaging="localPaging"
				@pageChange="(p, ps) => emit('pageChange', p, ps)"
			/>
		</a-spin>
	</div>
</template>

<script setup>
	/**
	 * 数据集面板：表格 / 图表 两种视图
	 *
	 * 与 pyqt_app/ui/sim_tabs/result_tab.py 的行为一致——只有配置了 chart 的 dataset
	 * 才出现「表格 / 图表」切换；未配置 chart 的 dataset 始终显示表格。
	 * 图表配置与列定义直接取自 TableTitleKeyMap.js（由 groupKey + datasetKey 定位）。
	 */
	import { computed } from 'vue'
	import ResultTable from './ResultTable.vue'
	import ResultChart from './ResultChart.vue'
	import { tableTitleKeyMap } from './TableTitleKeyMap.js'

	const props = defineProps({
		groupKey: { type: String, default: '' },
		datasetKey: { type: String, default: '' },
		/** 父级持有的视图模式：table | chart */
		chartMode: { type: String, default: 'table' },
		/** 图表是否可用（有 chart 配置且处于单快照模式） */
		chartable: { type: Boolean, default: false },
		rows: { type: Array, default: () => [] },
		loading: { type: Boolean, default: false },
		total: { type: Number, default: 0 },
		pageSize: { type: Number, default: 25 },
		current: { type: Number, default: 1 },
		compareRows: { type: Array, default: () => [] },
		compareKey: { type: String, default: '' },
		localPaging: { type: Boolean, default: false },
		// 图表数据（父级按 dataset 懒加载）
		chartRows: { type: Array, default: () => [] },
		chartTotal: { type: Number, default: 0 },
		chartSampled: { type: Boolean, default: false },
		chartLoading: { type: Boolean, default: false }
	})

	const emit = defineEmits(['pageChange', 'viewChange'])

	const chartCfg = computed(() => tableTitleKeyMap[props.groupKey]?.[props.datasetKey]?.chart || null)
	const columns = computed(() => tableTitleKeyMap[props.groupKey]?.[props.datasetKey]?.columns || {})
	const showChart = computed(() => props.chartable && props.chartMode === 'chart' && !!chartCfg.value)

	function onSwitch(e) {
		emit('viewChange', e.target.value)
	}
</script>

<style scoped>
	.dv-panel { display: flex; flex-direction: column; gap: 8px; }
	.dv-switch { display: flex; justify-content: flex-end; }
</style>
