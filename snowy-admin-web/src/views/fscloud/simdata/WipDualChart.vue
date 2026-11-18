<template>
	<div class="wdc">
		<div class="wdc-bar">
			<a-button size="small" class="wdc-obj-btn" @click="drawerOpen = true">
				对象（{{ selectedObjects.length }}/{{ maxObjects }}）
			</a-button>
			<a-checkbox v-model:checked="showDist" :disabled="!showStep">分布图</a-checkbox>
			<a-checkbox v-model:checked="showStep" :disabled="!showDist">阶梯图</a-checkbox>
			<span class="wdc-tip"
				>共 {{ objectOptions.length }} 个对象，已选 {{ selectedObjects.length }} 个（最多 {{ maxObjects }}）</span
			>
		</div>

		<div class="wdc-charts" :class="{ 'wdc-one': !showDist || !showStep }">
			<div v-show="showDist" class="wdc-cell">
				<ResultChart :chart="distChart" :rows="distRows" :columns="distColumns" :total="distRows.length" />
			</div>
			<div v-show="showStep" class="wdc-cell">
				<ResultChart :chart="stepChart" :rows="stepRows" :columns="stepColumns" :total="stepRows.length" />
			</div>
		</div>

		<a-drawer v-model:open="drawerOpen" title="选择对象" :width="440" :bodyStyle="{ paddingTop: '12px' }">
			<div class="wdc-drawer-head">
				<span>最多勾选 {{ maxObjects }} 个对象，至少保留 1 个</span>
				<a-button size="small" type="link" @click="resetSelection">重置</a-button>
			</div>
			<div class="wdc-obj-grid">
				<a-checkbox
					v-for="o in objectOptions"
					:key="o"
					:checked="selectedObjects.includes(o)"
					@change="(e) => onObjectCheck(o, e.target.checked)"
					>{{ o }}</a-checkbox
				>
			</div>
			<template #footer>
				<div class="wdc-drawer-foot">
					<span>已选 {{ selectedObjects.length }}/{{ maxObjects }}</span>
					<a-button size="small" type="primary" @click="drawerOpen = false">确定</a-button>
				</div>
			</template>
		</a-drawer>
	</div>
</template>

<script setup>
	/**
	 * WIP 双图（对象筛选 + 分布图 / 阶梯图）
	 *
	 * 由 TableTitleKeyMap.js 中 chart.dualWip=true 的 dataset（wip.distribution）启用。
	 * 数据源为该 dataset 的全量行：wip / duration / objectName / objectType / proportion。
	 * - 分布图：按 wip 值聚合各对象的 proportion（转百分比），分簇柱状
	 * - 阶梯图：按对象分组、按原始行顺序累加 duration 重建时间轴，wip 作阶梯（方波）
	 * 两图共用同一份对象勾选（最多 8 个）；顶部两个 checkbox 控制显示哪张图，至少保留一张。
	 */
	import { ref, computed, watch } from 'vue'
	import { message } from 'ant-design-vue'
	import ResultChart from './ResultChart.vue'
	import { toNumber } from './utils/format.js'

	const props = defineProps({
		/** wip.distribution 的全量图表行 */
		rows: { type: Array, default: () => [] }
	})

	const maxObjects = 8
	const drawerOpen = ref(false)
	const showDist = ref(true)
	const showStep = ref(true)
	const selectedObjects = ref([])

	/** 对象字段兜底探测（优先 objectName） */
	const OBJECT_KEYS = ['objectName', 'componentNo', 'deviceName', 'nodeName', 'stationName', 'objectId']
	function detectField(rows, keys) {
		for (const k of keys) {
			if (rows.some((r) => r && r[k] !== undefined && r[k] !== null && r[k] !== '')) return k
		}
		return keys[0]
	}
	const objField = computed(() => detectField(props.rows, OBJECT_KEYS))
	const durField = computed(() => detectField(props.rows, ['duration', 'dur']))
	const propField = computed(() => detectField(props.rows, ['proportion', 'ratio']))

	/** 设备列表：全量行里出现的对象去重，按中文自然序排 */
	const objectOptions = computed(() => {
		const set = new Set()
		for (const r of props.rows) {
			const v = r && r[objField.value]
			if (v !== undefined && v !== null && v !== '') set.add(String(v))
		}
		return [...set].sort((a, b) => a.localeCompare(b, 'zh'))
	})

	/** 数据源变化（切快照）时保留仍存在的勾选，否则重置为前 8 个 */
	watch(
		objectOptions,
		(opts) => {
			const kept = selectedObjects.value.filter((o) => opts.includes(o))
			selectedObjects.value = kept.length ? kept : opts.slice(0, maxObjects)
		},
		{ immediate: true }
	)

	function onObjectCheck(name, checked) {
		if (checked) {
			if (selectedObjects.value.length >= maxObjects) {
				message.warning(`最多选择 ${maxObjects} 个对象`)
				return
			}
			selectedObjects.value = [...selectedObjects.value, name]
		} else {
			if (selectedObjects.value.length <= 1) {
				message.warning('至少保留 1 个对象')
				return
			}
			selectedObjects.value = selectedObjects.value.filter((o) => o !== name)
		}
	}

	function resetSelection() {
		selectedObjects.value = objectOptions.value.slice(0, maxObjects)
	}

	// ==================== 分布图数据 ====================
	// 按 wip 值聚合各选中对象的 proportion（×100 转百分比）
	const distRows = computed(() => {
		const map = new Map()
		for (const r of props.rows) {
			const obj = String((r && r[objField.value]) ?? '')
			if (!selectedObjects.value.includes(obj)) continue
			const wipV = toNumber(r && r.wip)
			if (wipV === null) continue
			const pv = toNumber(r && r[propField.value]) || 0
			if (!map.has(wipV)) map.set(wipV, {})
			const slot = map.get(wipV)
			slot[obj] = (slot[obj] || 0) + pv * 100
		}
		return [...map.entries()].sort((a, b) => a[0] - b[0]).map(([wip, slot]) => ({ wip, ...slot }))
	})
	const distChart = computed(() => ({
		type: 'bar',
		x: 'wip',
		y: [...selectedObjects.value],
		stacked: false,
		legendGrid: 2,
		title: 'WIP 停留分布（占比 %）'
	}))
	const distColumns = computed(() => {
		const c = { wip: { title: 'WIP', unit: '件', type: 'i' } }
		for (const o of selectedObjects.value) c[o] = { title: o, unit: '%', type: 'f' }
		return c
	})

	// ==================== 阶梯图数据 ====================
	// 按对象分组，按原始行顺序累加 duration 作为时间轴（毫秒），wip 作阶梯值
	const stepRows = computed(() => {
		const acc = new Map()
		const out = []
		for (const r of props.rows) {
			const obj = String((r && r[objField.value]) ?? '')
			if (!selectedObjects.value.includes(obj)) continue
			const wipV = toNumber(r && r.wip)
			if (wipV === null) continue
			const dur = toNumber(r && r[durField.value]) || 0
			const cum = (acc.get(obj) || 0) + dur
			acc.set(obj, cum)
			out.push({ cumTime: cum, wip: wipV, objectName: obj })
		}
		return out
	})
	const stepChart = computed(() => ({
		type: 'line',
		x: 'cumTime',
		y: ['wip'],
		groupBy: 'objectName',
		step: 'end',
		legendGrid: 2,
		title: 'WIP 阶梯趋势'
	}))
	const stepColumns = {
		cumTime: { title: '仿真时长', unit: 'h:m:s', type: 't' },
		wip: { title: 'WIP', unit: '件', type: 'i' }
	}
</script>

<style scoped>
	.wdc {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.wdc-bar {
		display: flex;
		align-items: center;
		gap: 14px;
		flex-wrap: wrap;
	}
	.wdc-obj-btn {
		min-width: 108px;
	}
	.wdc-tip {
		font-size: 12px;
		color: #999;
	}
	.wdc-charts {
		display: flex;
		gap: 10px;
		align-items: stretch;
	}
	.wdc-cell {
		flex: 1;
		min-width: 0;
		min-height: 400px;
		border: 1px solid #f0f0f0;
		border-radius: 4px;
	}
	.wdc-one .wdc-cell {
		flex: 1 1 100%;
	}
	.wdc-drawer-head {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 10px;
		color: #666;
		font-size: 12px;
	}
	.wdc-obj-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px 12px;
		max-height: 62vh;
		overflow-y: auto;
		padding-right: 4px;
	}
	.wdc-drawer-foot {
		display: flex;
		justify-content: space-between;
		align-items: center;
		color: #666;
		font-size: 12px;
	}
</style>
