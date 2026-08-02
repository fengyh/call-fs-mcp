<template>
	<div class="ea-container">
		<div v-if="summary">
			<a-descriptions :column="3" size="small" bordered class="ea-summary">
				<a-descriptions-item label="介质数量">{{ summary.mediumCount ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="设备数量">{{ summary.deviceCount ?? '-' }}</a-descriptions-item>
				<a-descriptions-item label="总能耗({{ mediumUnit }})">{{ fmtNum(summary.totalEnergy, 2) }}</a-descriptions-item>
			</a-descriptions>

			<h4 class="ea-title">按设备能耗</h4>
			<a-table
				:dataSource="deviceRows"
				:columns="deviceColumns"
				:rowKey="(_, idx) => idx"
				size="small"
				bordered
				:pagination="false"
			/>
		</div>
		<div v-else class="ea-empty">暂无能耗分析数据</div>
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

	const summary = computed(() => row.value?.summary || null)

	const deviceColumns = [
		{ title: '设备名', dataIndex: 'deviceName', key: 'deviceName', ellipsis: true },
		{ title: '能耗(kWh)', dataIndex: 'energy', key: 'energy', ellipsis: true },
	]

	const deviceRows = computed(() => {
		const s = summary.value
		if (!s || !s.totalByDevice) return []
		return Object.entries(s.totalByDevice).map(([deviceName, energy]) => ({
			deviceName,
			energy: Number(energy).toFixed(2)
		}))
	})

	const mediumUnit = computed(() => 'kWh')

	function fmtNum(val, d) {
		if (val === null || val === undefined) return '-'
		return Number(val).toFixed(d)
	}
</script>

<style scoped>
	.ea-container { }
	.ea-summary { margin-bottom: 16px; }
	.ea-title { font-weight: 600; font-size: 14px; margin-bottom: 8px; }
	.ea-empty { padding: 40px 0; text-align: center; color: #999; }
</style>
