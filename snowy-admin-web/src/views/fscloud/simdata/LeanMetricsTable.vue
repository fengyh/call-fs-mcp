<template>
	<div class="lm-container">
		<div v-if="summary">
			<a-descriptions :column="2" size="small" bordered>
				<a-descriptions-item label="产线" :span="2">{{ lineName || '-' }}</a-descriptions-item>
				<a-descriptions-item label="客户需求">{{ summary.customerDemand ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="合格产出">{{ summary.qualifiedOutput ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="总投入">{{ summary.totalInput ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="直通率(FPY)">{{ fmtPct(summary.factoryFPY) }}</a-descriptions-item>
				<a-descriptions-item label="报废率">{{ fmtPct(summary.factoryScrapRate) }}</a-descriptions-item>
				<a-descriptions-item label="平均提前期(LT)">
					<span v-if="isNa(summary.avgLTMs)" class="lm-cell-na">n/a</span>
					<span v-else>{{ fmtMs(summary.avgLTMs) }}</span>
				</a-descriptions-item>
				<a-descriptions-item label="最大LT">
					<span v-if="isNa(summary.maxLTMs)" class="lm-cell-na">n/a</span>
					<span v-else>{{ fmtMs(summary.maxLTMs) }}</span>
				</a-descriptions-item>
				<a-descriptions-item label="最小LT">
					<span v-if="isNa(summary.minLTMs)" class="lm-cell-na">n/a</span>
					<span v-else>{{ fmtMs(summary.minLTMs) }}</span>
				</a-descriptions-item>
				<a-descriptions-item label="OEE">{{ fmtPct(summary.oee) }}</a-descriptions-item>
				<a-descriptions-item label="可用率">{{ fmtPct(summary.availabilityRate) }}</a-descriptions-item>
				<a-descriptions-item label="性能率">{{ fmtPct(summary.performanceRate) }}</a-descriptions-item>
				<a-descriptions-item label="质量率">{{ fmtPct(summary.qualityRate) }}</a-descriptions-item>
				<a-descriptions-item label="当前WIP">{{ summary.currentWIP ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="峰值WIP">{{ summary.peakWIP ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="实际UPH">{{ fmtNum(summary.actualUPH, 2) }}</a-descriptions-item>
				<a-descriptions-item label="理论UPH">{{ fmtNum(summary.theoreticalUPH, 2) }}</a-descriptions-item>
				<a-descriptions-item label="平均工序CT">
					<span v-if="isNa(summary.avgProcessPTMs)" class="lm-cell-na">n/a</span>
					<span v-else>{{ fmtMs(summary.avgProcessPTMs) }}</span>
				</a-descriptions-item>
				<a-descriptions-item label="平衡率">{{ fmtPct(summary.avgBalanceRate) }}</a-descriptions-item>
				<a-descriptions-item label="增值比">{{ fmtPct(summary.avgValueAddedRatio) }}</a-descriptions-item>
				<a-descriptions-item label="平均等待">
					<span v-if="isNa(summary.avgWaitingMs)" class="lm-cell-na">n/a</span>
					<span v-else>{{ fmtMs(summary.avgWaitingMs) }}</span>
				</a-descriptions-item>
				<a-descriptions-item label="平均换型时间">
					<span v-if="isNa(summary.avgChangeoverMs)" class="lm-cell-na">n/a</span>
					<span v-else>{{ fmtMs(summary.avgChangeoverMs) }}</span>
				</a-descriptions-item>
				<a-descriptions-item label="工人数">{{ summary.workerCount ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="PPLH">{{ fmtNum(summary.pplh, 2) }}</a-descriptions-item>
			</a-descriptions>
		</div>
		<div v-else class="lm-empty">暂无精益指标数据</div>
	</div>
</template>

<script setup>
	import { computed } from 'vue'

	const props = defineProps({
		summaryResult: { type: Object, default: null }
	})

	const row = computed(() => {
		if (!props.summaryResult) return null
		const rows = props.summaryResult.rows
		if (!rows || rows.length === 0) return null
		return rows[0]
	})

	const lineName = computed(() => {
		const r = row.value
		if (!r || !r.productLines || r.productLines.length === 0) return ''
		return r.productLines[0]?.lineName || ''
	})

	const summary = computed(() => row.value?.summary || null)

	function isNa(val) {
		return val !== null && val !== undefined && Number(val) === -1
	}

	function fmtPct(val) {
		if (val === null || val === undefined) return '-'
		return Number(val).toFixed(2) + '%'
	}
	function fmtMs(ms) {
		if (ms === null || ms === undefined) return '-'
		return Number(ms).toLocaleString() + ' ms'
	}
	function fmtNum(val, d) {
		if (val === null || val === undefined) return '-'
		return Number(val).toFixed(d)
	}
</script>

<style scoped>
	.lm-container { }
	.lm-empty { padding: 40px 0; text-align: center; color: #999; }
	.lm-cell-na { color: #cf1322; font-weight: 600; }
</style>
