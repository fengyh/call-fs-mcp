<template>
	<div class="rc-container">
		<div v-if="hint" class="rc-hint">{{ hint }}</div>
		<div v-show="hasData" ref="chartRef" class="rc-chart"></div>
		<div v-if="!hasData" class="rc-empty">暂无图表数据</div>
	</div>
</template>

<script setup>
	/**
	 * 结果图表（ECharts）：柱状 / 阶梯折线（方波）
	 *
	 * 由 TableTitleKeyMap.js 中每个 dataset 的 `chart` 配置驱动：
	 *   { type: "bar"|"line", x: 列名, y: [列名…], stacked: bool, groupBy: 列名, title: str, step: bool|"end"|"start"|"middle", labelBy: 列名 }
	 * 语义与 pyqt_app/ui/sim_tabs/result_chart.py 保持一致：
	 * - bar：x 作分类轴（t 类型标签按 fmtTimestamp 格式化），y 多列分簇柱，stacked=true 时堆叠
	 * - line：x 作数值轴（t 类型按秒绘制、刻度按 fmtTimestamp 格式化），按 groupBy 分组成多条线、按 x 排序
	 *         默认以阶梯（方波，step='end'）绘制：数据保持到下一个点才直上直下跳变，
	 *         同一 x 的多个点（功率曲线垂直跳变）因此呈现为纯粹的竖线；
	 *         如需普通斜线折线，在 chart 配置里设 step:false
	 */
	import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
	import * as echarts from 'echarts/core'
	import { BarChart, LineChart } from 'echarts/charts'
	import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
	import { CanvasRenderer } from 'echarts/renderers'
	import { fmtTimestamp, toNumber } from './utils/format.js'

	echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

	/** 柱状图类别过多时自动开启的缩放窗口大小 */
	const BAR_ZOOM_THRESHOLD = 50

	const props = defineProps({
		/** chart 配置（TableTitleKeyMap.js 的 dataset.chart） */
		chart: { type: Object, default: null },
		/** 图表数据行 */
		rows: { type: Array, default: () => [] },
		/** 该 dataset 的列定义（title/unit/type），用于轴名与格式化 */
		columns: { type: Object, default: () => ({}) },
		/** 源数据总点数（降采样提示用） */
		total: { type: Number, default: 0 },
		/** 是否发生了降采样 */
		sampled: { type: Boolean, default: false }
	})

	const chartRef = ref(null)
	let inst = null
	let observer = null

	const hasData = computed(() =>
		!!props.chart && Array.isArray(props.rows) && props.rows.length > 0
	)
	const hint = computed(() => {
		if (!props.sampled || !props.rows.length) return ''
		return `共 ${props.total} 点，已抽样显示 ${props.rows.length} 点（跳变与平台已保留）`
	})

	// ==================== 取值辅助 ====================

	function metaOf(key) {
		return (props.columns && props.columns[key]) || {}
	}

	/** 轴名 / 图例名："标题(单位)" */
	function axisName(key) {
		const meta = metaOf(key)
		const title = meta.title || key || ''
		return meta.unit ? `${title}(${meta.unit})` : title
	}

	/** 行里可用于标识设备/节点的字段（按优先级），用于补全 tooltip 的"哪个对象的 WIP/功率" */
	const LABEL_KEYS = ['objectName', 'componentNo', 'deviceName', 'nodeName', 'stationName', 'lineName', 'objectId', 'name']
	/** 从数据中探测标识字段；chart.labelBy 可显式指定，groupBy 优先，再按常见字段匹配，最后扫描行内的字符串字段兜底 */
	function probeLabelKey(rows, chart) {
		if (chart.labelBy) return chart.labelBy
		if (chart.groupBy) return chart.groupBy
		const sample = rows.find(r => r && typeof r === 'object') || {}
		for (const k of LABEL_KEYS) {
			if (rows.some(r => r && r[k] !== undefined && r[k] !== null && r[k] !== '')) return k
		}
		// 兜底：取首个非空字符串值且与 x/y 列不同的字段（避免把数值列当标签）
		for (const k of Object.keys(sample)) {
			if (k === chart.x || (chart.y || []).includes(k)) continue
			if (typeof sample[k] === 'string' && sample[k] !== '') return k
		}
		return ''
	}

	/** t 类型且值为 -1 表示 n/a（与 ResultTable.vue 的表格口径一致），图表中按空值处理 */
	function cellNumber(row, key) {
		const v = toNumber(row ? row[key] : null)
		if (v === null) return null
		if (metaOf(key).type === 't' && v < 0) return null
		return v
	}

	/** 分类轴标签 */
	function cellLabel(row, key) {
		const raw = row ? row[key] : null
		if (raw === null || raw === undefined || raw === '') return '-'
		if (metaOf(key).type === 't') return fmtTimestamp(raw)
		return String(raw)
	}

	// ==================== 渲染 ====================

	function buildOption() {
		const chart = props.chart || {}
		const xKey = chart.x
		const yKeys = (chart.y || []).filter(k => !!k)
		const isLine = chart.type === 'line'
		const xMeta = metaOf(xKey)
		const xIsTime = xMeta.type === 't'
		// line 图补全 tooltip：时间 + 设备/节点名 + 值（同一时刻多个对象的点也能区分）
		const lineLabelKey = isLine ? probeLabelKey(props.rows, chart) : ''
		const option = {
			title: chart.title ? { text: chart.title, left: 'center', textStyle: { fontSize: 13, fontWeight: 600 } } : undefined,
			tooltip: isLine
				? {
					trigger: 'axis',
					formatter: params => {
						const list = Array.isArray(params) ? params : [params]
						if (!list.length) return ''
						const first = list[0]
						const rawX = Array.isArray(first.value) ? first.value[0] : first.axisValue
						const timeText = xIsTime ? fmtTimestamp(rawX * 1000) : rawX
						const head = `${first.marker || ''}${timeText == null ? '' : timeText}<br/>`
						const yName = axisName(yKeys[0])
						const body = list.map(p => {
							const label = Array.isArray(p.value) ? p.value[3] : ''
							const v = Array.isArray(p.value) ? p.value[1] : p.value
							return `${p.marker || ''}${label ? label + '　' : ''}${yName}：${v}`
						}).join('<br/>')
						return head + body
					}
				}
				: { trigger: 'axis', axisPointer: { type: 'shadow' } },
			legend: { show: yKeys.length > 1 || !!chart.groupBy, top: chart.title ? 26 : 8, type: 'scroll' },
			grid: {
				left: 60,
				right: 30,
				bottom: isLine ? 56 : 80,
				top: (chart.title ? 60 : 40) + (yKeys.length > 1 || chart.groupBy ? 12 : 0),
				containLabel: true
			},
			color: ['#3B82F6', '#22C55E', '#F59E0B', '#EF4444', '#A855F7', '#06B6D4', '#84CC16', '#EC4899'],
			series: []
		}

		if (isLine) {
			option.xAxis = {
				type: 'value',
				name: axisName(xKey),
				nameLocation: 'middle',
				nameGap: 28,
				axisLabel: { formatter: v => (xIsTime ? fmtTimestamp(v * 1000) : v) }
			}
			option.yAxis = {
				type: 'value',
				name: axisName(yKeys[0]),
				scale: true,
				axisLabel: { formatter: v => (metaOf(yKeys[0]).type === 't' ? fmtTimestamp(v) : v) }
			}
			option.dataZoom = [{ type: 'inside' }]
			const groupKey = chart.groupBy
			const groups = new Map()
			for (const row of props.rows) {
				const g = groupKey ? (row[groupKey] ?? '') : ''
				if (!groups.has(g)) groups.set(g, [])
				groups.get(g).push(row)
			}
			let idx = 0
			for (const [group, groupRows] of groups) {
				const points = []
				let seq = 0
				for (const row of groupRows) {
					const xv = cellNumber(row, xKey)
					const yv = cellNumber(row, yKeys[0])
					if (xv === null || yv === null) continue
					// 第三个元素是原始序号：同一个 x 上的多个点（功率曲线的垂直跳变）必须保持
					// 原始先后顺序，否则先降后升会被连成斜线，跳变方向翻转；第四个是设备/节点标签，供 tooltip 用
					const label = lineLabelKey ? String(row[lineLabelKey] ?? '') : ''
					points.push([xIsTime ? xv / 1000 : xv, yv, seq++, label])
				}
				if (!points.length) continue
				points.sort((a, b) => (a[0] - b[0]) || (a[2] - b[2]))
				option.series.push({
					name: groupKey ? String(group) : axisName(yKeys[0]),
					type: 'line',
					// 阶梯（方波）：水平走到下一点的 x 再垂直跳变，形成直上直下的台阶；
					// 值为 false 可退回普通斜线折线；字符串则按 ECharts step 模式绘制
					step: chart.step === false ? false : (chart.step || 'end'),
					showSymbol: false,
					smooth: false,
					lineStyle: { width: 1.4 },
					data: points.map(p => [p[0], p[1], p[2], p[3]]),
					color: option.color[idx++ % option.color.length]
				})
			}
		} else {
			const labels = props.rows.map(row => cellLabel(row, xKey))
			const zoomed = labels.length > BAR_ZOOM_THRESHOLD
			option.xAxis = {
				type: 'category',
				name: axisName(xKey),
				nameLocation: 'middle',
				nameGap: zoomed ? 62 : 46,
				data: labels,
				axisLabel: { rotate: 30, hideOverlap: true }
			}
			option.yAxis = {
				type: 'value',
				name: yKeys.length === 1 ? axisName(yKeys[0]) : '',
				scale: false,
				axisLabel: { formatter: v => (metaOf(yKeys[0]).type === 't' ? fmtTimestamp(v) : v) }
			}
			if (zoomed) {
				option.dataZoom = [
					{ type: 'inside' },
					{ type: 'slider', bottom: 10, height: 16, end: Math.min(100, (BAR_ZOOM_THRESHOLD / labels.length) * 100) }
				]
			}
			const stacked = !!chart.stacked
			const single = yKeys.length === 1
			for (const key of yKeys) {
				option.series.push({
					name: axisName(key),
					type: 'bar',
					stack: stacked ? 'total' : undefined,
					barMaxWidth: single ? 46 : 28,
					data: props.rows.map(row => cellNumber(row, key)),
					emphasis: { focus: 'series' }
				})
			}
		}
		return option
	}

	function render() {
		if (!chartRef.value) return
		if (!inst) inst = echarts.init(chartRef.value)
		if (!hasData.value) {
			inst.clear()
			return
		}
		inst.setOption(buildOption(), true)
		inst.resize()
	}

	onMounted(() => {
		render()
		observer = new ResizeObserver(() => { if (inst) inst.resize() })
		if (chartRef.value) observer.observe(chartRef.value)
	})

	onBeforeUnmount(() => {
		if (observer) { observer.disconnect(); observer = null }
		if (inst) { inst.dispose(); inst = null }
	})

	watch(() => [props.chart, props.rows, props.columns], () => nextTick(render), { deep: true })
</script>

<style scoped>
	.rc-container { display: flex; flex-direction: column; height: 100%; min-height: 380px; }
	.rc-hint { font-size: 12px; color: #d46b08; margin-bottom: 4px; }
	.rc-chart { flex: 1; width: 100%; min-height: 360px; }
	.rc-empty { padding: 60px 0; text-align: center; color: #999; }
</style>
