<template>
	<div class="rt-container">
		<a-table
			v-if="rows.length > 0 || loading"
			:dataSource="displayRows"
			:columns="antColumns"
			:rowKey="(_, idx) => idx"
			:loading="loading"
			:pagination="paginationProps"
			@change="onPageChange"
			size="small"
			bordered
			:scroll="{ x: 'max-content' }"
			:rowClassName="rowClassName"
		/>
		<div v-else class="rt-empty">暂无数据</div>
	</div>
</template>

<script setup>
	import { ref, computed, watch, h } from 'vue'
	import { tableTitleKeyMap } from './TableTitleKeyMap.js'

	const props = defineProps({
		rows: { type: Array, default: () => [] },
		columns: { type: Array, default: () => [] },
		loading: { type: Boolean, default: false },
		total: { type: Number, default: 0 },
		pageSize: { type: Number, default: 25 },
		current: { type: Number, default: 1 },
		groupKey: { type: String, default: '' },
		datasetKey: { type: String, default: '' },
		// 对比模式：对照快照数据 + 对齐键（compareKey 为空串则不做对比）
		compareRows: { type: Array, default: () => [] },
		compareKey: { type: String, default: '' },
		// 本地分页：true 时组件内部切页（total=rows.length，不 emit pageChange），用于 overview 全量数据
		localPaging: { type: Boolean, default: false }
	})

	const emit = defineEmits(['pageChange'])

	/** 本地分页当前页 */
	const localCurrent = ref(1)

	// rows 变化时本地分页回到第一页
	watch(() => props.rows, () => {
		localCurrent.value = 1
	})

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
		if (ms === null || ms === undefined) return '-'
		if (typeof ms === 'string' && ms.trim() === '') return '-'
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

	/** 列顺序：优先取映射表 columns 顺序；数据有而映射表没有的列追加末尾；映射表有而数据没有的列跳过；无映射表时回退数据首行 key 顺序 */
	function orderedColumnKeys(firstRow) {
		const dataKeys = Object.keys(firstRow).filter(k => k !== 'steps')
		const metaCols = tableTitleKeyMap[props.groupKey]?.[props.datasetKey]?.columns
		if (metaCols && Object.keys(metaCols).length > 0) {
			const ordered = []
			const seen = new Set()
			for (const k of Object.keys(metaCols)) {
				if (dataKeys.includes(k)) {
					ordered.push(k)
					seen.add(k)
				}
			}
			for (const k of dataKeys) {
				if (!seen.has(k)) ordered.push(k)
			}
			return ordered
		}
		return dataKeys
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
				return orderedColumnKeys(first).map(key => ({
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
						const diff = record._compareDiff && record._compareDiff[key]
						if (diff) {
							const style = diff.cls === 'gt' ? 'color:#cf1322' : diff.cls === 'lt' ? 'color:#389e0d' : 'color:#d46b08'
							return h('span', {}, [
								h('span', {}, text),
								h('span', { style: style + ';font-weight:600;margin-left:4px' }, diff.text)
							])
						}
						return text
					}
				}))
			}
		}
		return []
	})

	/** 构造对照行索引 { compareKey值: row } */
	function buildCompareIndex() {
		const idx = {}
		if (!props.compareKey || !Array.isArray(props.compareRows)) return idx
		for (const row of props.compareRows) {
			if (row && typeof row === 'object') {
				const k = row[props.compareKey]
				if (k !== undefined && k !== null) idx[String(k)] = row
			}
		}
		return idx
	}

	/** 计算某单元格差异：返回 { cls: 'gt'|'lt'|'diff', text } 或 null（相等/无对比） */
	function diffForCell(key, v1, v2) {
		if (v1 === undefined || v2 === undefined) return null
		const meta = metaForKey(key)
		const n1 = Number(v1)
		const n2 = Number(v2)
		if (isNaN(n1) || isNaN(n2)) {
			// 非数值：字符串比较，不等时显示"主体[对照]"
			if (String(v1) === String(v2)) return null
			return { cls: 'diff', text: '[' + formatValue(key, v2) + ']' }
		}
		const d = n1 - n2
		if (d === 0) return null
		const cls = d > 0 ? 'gt' : 'lt'
		const arrow = d > 0 ? '↑' : '↓'
		let diffVal
		if (meta && meta.type === 't') {
			diffVal = fmtTimestamp(Math.abs(d))
		} else {
			diffVal = formatValue(key, Math.abs(d))
		}
		return { cls, text: ` ${arrow}${diffVal}` }
	}

	const formattedRows = computed(() => {
		const compareIdx = buildCompareIndex()
		return props.rows.map(row => {
			const newRow = { ...row }
			// 处理 steps 字段
			if (row.steps && Array.isArray(row.steps)) {
				newRow._stepsDetail = row.steps.map(s =>
					`${s.processName}: ${fmtTimestamp(s.avgPTMs)}`
				).join('; ')
			}
			// 对比模式：定位对照行
			let cmpRow = null
			if (props.compareKey && Object.keys(compareIdx).length > 0) {
				const ck = row[props.compareKey]
				if (ck !== undefined && ck !== null) {
					cmpRow = compareIdx[String(ck)] || null
				}
			}
			if (props.compareKey && !cmpRow) {
				// 主体有、对照无：整行浅蓝底
				newRow._compareMissing = true
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
			// 对比模式：计算差异标注（n/a 行不参与差值计算）
			if (cmpRow && !newRow._naMap) {
				const diffMap = {}
				for (const key of Object.keys(newRow)) {
					if (key.startsWith('_')) continue
					const diff = diffForCell(key, row[key], cmpRow[key])
					if (diff) diffMap[key] = diff
				}
				newRow._compareDiff = diffMap
			}
			return newRow
		})
	})

	/** 行样式：对比模式下主体有、对照无 → 浅蓝底 */
	function rowClassName(record) {
		return record && record._compareMissing ? 'rt-row-missing' : ''
	}

	const paginationProps = computed(() => ({
		total: props.localPaging ? formattedRows.value.length : props.total,
		pageSize: props.pageSize,
		current: props.localPaging ? localCurrent.value : props.current,
		showSizeChanger: false,
		showTotal: (total) => `共 ${total} 条`,
	}))

	/** 展示数据源：本地分页时按页切片，否则原样 */
	const displayRows = computed(() => {
		if (!props.localPaging) return formattedRows.value
		const start = (localCurrent.value - 1) * props.pageSize
		return formattedRows.value.slice(start, start + props.pageSize)
	})

	function onPageChange(pagination) {
		if (props.localPaging) {
			localCurrent.value = pagination.current
			return
		}
		emit('pageChange', pagination.current, pagination.pageSize)
	}
</script>

<style scoped>
	.rt-container { }
	.rt-empty { padding: 40px 0; text-align: center; color: #999; }
</style>

<style>
	/* 非 scoped：行级 class 作用于 a-table 内部 tr */
	.rt-row-missing td {
		background-color: #dbeafe !important;
	}
</style>
