<template>
	<div class="rt-container">
		<a-table
			v-if="rows.length > 0 || loading"
			:dataSource="formattedRows"
			:columns="antColumns"
			:rowKey="(_, idx) => idx"
			:loading="loading"
			:pagination="paginationProps"
			@change="onPageChange"
			size="small"
			bordered
			:scroll="{ x: 'max-content' }"
		/>
		<div v-else class="rt-empty">暂无数据</div>
	</div>
</template>

<script setup>
	import { computed, h } from 'vue'
	import { tableTitleKeyMap } from './TableTitleKeyMap.js'

	const props = defineProps({
		rows: { type: Array, default: () => [] },
		columns: { type: Array, default: () => [] },
		loading: { type: Boolean, default: false },
		total: { type: Number, default: 0 },
		pageSize: { type: Number, default: 25 },
		current: { type: Number, default: 1 },
		groupKey: { type: String, default: '' },
		datasetKey: { type: String, default: '' }
	})

	const emit = defineEmits(['pageChange'])

	function metaForKey(key) {
		const gk = props.groupKey
		const dk = props.datasetKey
		if (!gk || !dk) return null
		return tableTitleKeyMap[gk]?.[dk]?.columns?.[key] || null
	}

	/** 判断某字段是否为数值类型（t/i/f）应右对齐 */
	function isNumType(key) {
		const meta = metaForKey(key)
		return meta && (meta.type === 't' || meta.type === 'i' || meta.type === 'f')
	}

	/** 判断某字段是否为 t 类型（时间戳），用于 -1 标记 n/a */
	function isTType(key) {
		const meta = metaForKey(key)
		return !!(meta && meta.type === 't')
	}

	function formatValue(key, rawValue) {
		const meta = metaForKey(key)
		if (!meta) return rawValue
		const { type, decimals } = meta
		if (rawValue === null || rawValue === undefined) return '-'
		switch (type) {
			case 'f': return Number(rawValue).toFixed(decimals)
			case 'i': return Math.round(Number(rawValue))
			case 't': return fmtTimestamp(rawValue)
			case 's': return String(rawValue)
			default:  return rawValue
		}
	}

	function fmtTimestamp(ms) {
		if (!ms && ms !== 0) return '-'
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

	function columnTitle(key) {
		const meta = metaForKey(key)
		if (!meta) return key
		let t = meta.title || key
		if (meta.unit) t += `(${meta.unit})`
		return t
	}

	const antColumns = computed(() => {
		if (props.columns.length > 0) {
			return props.columns.map(col => ({
				title: col.title,
				dataIndex: col.key || col.dataIndex,
				key: col.key || col.dataIndex,
				ellipsis: true,
				width: col.width || 120
			}))
		}
		if (props.rows.length > 0) {
			const first = props.rows[0]
			if (typeof first === 'object' && first !== null) {
				return Object.keys(first).filter(k => k !== 'steps').map(key => ({
					title: columnTitle(key),
					dataIndex: key,
					key: key,
					ellipsis: true,
					width: 120,
					align: isNumType(key) ? 'right' : 'left',
					customRender: ({ text, record }) => {
						if (record._naMap && record._naMap[key]) {
							return h('span', { style: 'color:#cf1322;font-weight:600' }, 'n/a')
						}
						return text
					}
				}))
			}
		}
		return []
	})

	const formattedRows = computed(() => {
		return props.rows.map(row => {
			const newRow = { ...row }
			// 处理 steps 字段
			if (row.steps && Array.isArray(row.steps)) {
				newRow._stepsDetail = row.steps.map(s =>
					`${s.processName}: ${fmtTimestamp(s.avgPTMs)}`
				).join('; ')
			}
			// 第一步：检查是否有 t 类型字段值为 -1
			const foundInvalidT = Object.keys(row).some(k => {
				if (k.startsWith('_') || k === 'steps') return false
				return isTType(k) && row[k] !== null && row[k] !== undefined && Number(row[k]) === -1
			})
			// 第二步：如果有，该行所有 t 类型字段标记为 n/a；否则正常格式化
			if (foundInvalidT) {
				const naMap = {}
				for (const key of Object.keys(newRow)) {
					if (key.startsWith('_') || key === 'steps') continue
					if (isTType(key)) {
						naMap[key] = true
					} else {
						newRow[key] = formatValue(key, newRow[key])
					}
				}
				newRow._naMap = naMap
			} else {
				for (const key of Object.keys(newRow)) {
					if (key === '_stepsDetail') continue
					if (key.startsWith('_')) continue
					newRow[key] = formatValue(key, newRow[key])
				}
			}
			return newRow
		})
	})

	const paginationProps = computed(() => ({
		total: props.total,
		pageSize: props.pageSize,
		current: props.current,
		showSizeChanger: false,
		showTotal: (total) => `共 ${total} 条`,
	}))

	function onPageChange(pagination) {
		emit('pageChange', pagination.current, pagination.pageSize)
	}
</script>

<style scoped>
	.rt-container { }
	.rt-empty { padding: 40px 0; text-align: center; color: #999; }
</style>