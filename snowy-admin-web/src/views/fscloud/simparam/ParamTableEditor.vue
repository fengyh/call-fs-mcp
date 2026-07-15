<template>
	<div class="pte-container">
		<div class="pte-toolbar">
			<span class="pte-table-name">{{ tableName }}</span>
			<a-space>
				<a-button size="small" :type="editMode ? 'primary' : 'default'" @click="toggleEdit">
					{{ editMode ? '🔓 查看' : '🔒 编辑' }}
				</a-button>
				<a-button size="small" type="primary" :disabled="!dirty" @click="saveChanges">💾 保存</a-button>
				<a-button size="small" :disabled="!dirty" @click="cancelChanges">↩ 取消</a-button>
			</a-space>
		</div>

		<div class="pte-table-wrap">
			<table class="pte-table" v-if="localColumns.length > 0">
				<colgroup>
					<col :style="{ width: rowNumColWidth + 'px' }" />
					<col v-for="(w, ci) in columnWidths" :key="'col-' + ci" />
					<col v-if="editMode" style="width: 40px" />
				</colgroup>
				<thead>
					<tr>
						<th class="pte-row-num">#</th>
						<th v-for="(col, ci) in localColumns"
							:key="'h-' + ci + '-' + ((colDirtyMap[ci] || headerDirtyFlags[ci]) ? '1' : '0')"
							class="pte-header-cell"
							:class="{ 'pte-col-dirty': colDirtyMap[ci] || headerDirtyFlags[ci] }"
							:style="{ width: columnWidths[ci] + 'px' }">
							<button v-if="editMode" class="pte-ins-btn pte-ins-col-left" @click.stop="addColumnBefore(ci)" title="左侧插入列">➕</button>
							<div class="pte-header-content">
								<div class="pte-header-main">
									<input v-if="editMode" class="pte-cell-input" :value="col.name" @input="e => commitColumnName(ci, e.target.value)" :placeholder="'列' + ci" />
									<span v-else>{{ col.name }}</span>
									<span class="pte-type-tag">{{ typeAbbr(col.type) }}</span>
								</div>
								<button v-if="editMode" class="pte-col-del" @click="deleteColumn(ci)" title="删除列">❌</button>
							</div>
							<button v-if="editMode" class="pte-ins-btn pte-ins-col-right" @click.stop="addColumnAfter(ci)" title="右侧插入列">➕</button>
						</th>
						<th v-if="editMode" class="pte-row-num">
							<button class="pte-ins-btn pte-ins-col-right" @click="addColumn" title="末尾追加列">➕</button>
						</th>
					</tr>
				</thead>
				<tbody>
					<tr v-for="(row, ri) in localRows" :key="'r-' + ri" class="pte-data-row">
						<td class="pte-row-num pte-index-cell">
							<button v-if="editMode" class="pte-ins-btn pte-ins-row-top" @click.stop="addRowBefore(ri)" title="上方插入行">➕</button>
							<span class="pte-row-num-text">{{ ri }}</span>
							<button v-if="editMode" class="pte-ins-btn pte-ins-row-bottom" @click.stop="addRowAfter(ri)" title="下方插入行">➕</button>
						</td>
						<td v-for="(cell, ci) in row" :key="'c-' + ri + '-' + ci"
							class="pte-cell"
							:class="{ 'pte-cell-dirty': cellDirtyFlags[ri + '-' + ci] }">
							<input v-if="editMode"
								class="pte-cell-input"
								:value="cell"
								@input="e => onCellInput(ri, ci, e.target.value)" />
							<span v-else>{{ cell }}</span>
						</td>
						<td v-if="editMode" class="pte-cell pte-cell-empty">
							<button class="pte-col-del" @click="deleteRow(ri)" title="删除行">❌</button>
						</td>
					</tr>
				</tbody>
			</table>
			<div v-else class="pte-empty">暂无数据</div>
		</div>

		<div class="pte-actions" v-if="editMode">
			<button class="pte-action-btn" @click="addRow">➕ 末尾新增行</button>
			<button class="pte-action-btn" @click="addColumn" style="margin-left: 8px;">➕ 末尾新增列</button>
		</div>

		<div class="pte-dirty-bar" v-if="dirty">数据已修改</div>
	</div>
</template>

<script setup>
	import { ref, computed, watch } from 'vue'
	import { Modal } from 'ant-design-vue'

	const props = defineProps({
		tableName: { type: String, default: '' },
		tableData: { type: Object, default: () => ({ columns: [], rows: [] }) }
	})

	const emit = defineEmits(['save', 'dirty-change'])

	const editMode = ref(false)
	const editingCell = ref(null)
	const localColumns = ref([])
	const localRows = ref([])
	const cellDirtyFlags = ref({})     // 单元格脏标记 { "ri-ci": true }
	const headerDirtyFlags = ref({})   // 表头主动标记 { "ci": true }

	const colDirtyMap = computed(() => {
		const map = {}
		for (const key of Object.keys(cellDirtyFlags.value)) {
			if (!cellDirtyFlags.value[key]) continue
			const ci = key.split('-')[1]
			if (ci !== undefined) map[ci] = true
		}
		for (const ci of Object.keys(headerDirtyFlags.value)) {
			if (headerDirtyFlags.value[ci]) map[ci] = true
		}
		return map
	})

	const dirty = computed(() => {
		const origRows = props.tableData.rows || []
		const currRows = localRows.value
		if (origRows.length !== currRows.length) return true
		const origCols = props.tableData.columns || []
		if (origCols.length !== localColumns.value.length) return true
		for (let i = 0; i < origCols.length; i++) {
			if ((origCols[i]?.name ?? '') !== (localColumns.value[i]?.name ?? '')) return true
		}
		for (let i = 0; i < origRows.length; i++) {
			const a = origRows[i] || []
			const b = currRows[i] || []
			if (a.length !== b.length) return true
			for (let j = 0; j < a.length; j++) {
				if (String(a[j] ?? '') !== String(b[j] ?? '')) return true
			}
		}
		return false
	})

	const rowNumColWidth = computed(() => {
		const maxRow = Math.max(localRows.value.length, 1)
		const digits = String(maxRow - 1).length
		return Math.max(36, Math.min(digits * 8 + 28, 80))
	})

	const columnWidths = computed(() => {
		return localColumns.value.map((col, ci) => {
			const headerLen = (col.name || '').length
			let maxDataLen = 0
			for (const row of localRows.value) {
				const cell = String(row[ci] ?? '')
				if (cell.length > maxDataLen) maxDataLen = cell.length
			}
			const maxLen = Math.max(headerLen, maxDataLen, 4)
			const extra = editMode.value ? 36 : 16
			return Math.max(64, Math.min(maxLen * 8 + 32 + extra, 380))
		})
	})

	watch(dirty, (val) => {
		emit('dirty-change', val)
	}, { immediate: true })

	function deepCopy(data) {
		return {
			columns: (data.columns || []).map(col => ({ ...col })),
			rows: (data.rows || []).map(row => [...(row || [])])
		}
	}

	function syncLocalData() {
		const copy = deepCopy(props.tableData)
		localColumns.value = copy.columns
		localRows.value = copy.rows
		cellDirtyFlags.value = {}
		headerDirtyFlags.value = {}
		editingCell.value = null
	}

	watch(() => props.tableData, () => {
		syncLocalData()
	}, { deep: true, immediate: true })

	function confirmDiscard() {
		return new Promise((resolve) => {
			Modal.confirm({
				title: '确认不保存数据吗？',
				content: '当前修改尚未保存，切换后将丢失所有未保存的修改。',
				okText: '确认丢弃',
				cancelText: '取消',
				onOk: () => resolve(true),
				onCancel: () => resolve(false)
			})
		})
	}

	async function toggleEdit() {
		if (editMode.value && dirty.value) {
			const ok = await confirmDiscard()
			if (!ok) return
		}
		editMode.value = !editMode.value
		if (!editMode.value) {
			editingCell.value = null
			if (dirty.value) syncLocalData()
		}
	}

	function saveChanges() {
		emit('save', {
			columns: localColumns.value.map(col => ({ ...col })),
			rows: localRows.value.map(row => [...row])
		})
		cellDirtyFlags.value = {}
		headerDirtyFlags.value = {}
	}

	function cancelChanges() {
		syncLocalData()
	}

	// ====== 单元格编辑 ======

	function onCellInput(ri, ci, val) {
		localRows.value[ri][ci] = val
		const orig = props.tableData.rows?.[ri]?.[ci]
		const changed = String(val ?? '') !== String(orig ?? '')
		if (changed) {
			cellDirtyFlags.value = { ...cellDirtyFlags.value, [ri + '-' + ci]: true }
		} else {
			const f = { ...cellDirtyFlags.value }
			delete f[ri + '-' + ci]
			cellDirtyFlags.value = f
		}
	}

	// ====== 表头编辑 ======

	function commitColumnName(index, value) {
		if (index < 0 || index >= localColumns.value.length) return
		const orig = props.tableData.columns?.[index]?.name ?? ''
		localColumns.value[index] = { ...localColumns.value[index], name: value }
		if (value !== orig) {
			headerDirtyFlags.value = { ...headerDirtyFlags.value, [index]: true }
		} else {
			const f = { ...headerDirtyFlags.value }
			delete f[index]
			headerDirtyFlags.value = f
		}
	}

	// ====== flag 管理 ======

	function getFlags() { return cellDirtyFlags.value }

	function flagRow(ri) {
		const f = { ...getFlags() }
		for (let j = 0; j < localColumns.value.length; j++) f[ri + '-' + j] = true
		cellDirtyFlags.value = f
	}

	function flagColumn(ci) {
		const f = { ...getFlags() }
		for (let i = 0; i < localRows.value.length; i++) f[i + '-' + ci] = true
		cellDirtyFlags.value = f
	}

	function removeRowFlags(ri) {
		const f = {}
		for (const key of Object.keys(getFlags())) {
			const parts = key.split('-')
			const r = parseInt(parts[0], 10)
			const c = parts[1]
			if (r === ri) continue
			const nr = r > ri ? r - 1 : r
			f[nr + '-' + c] = getFlags()[key]
		}
		cellDirtyFlags.value = f
	}

	function removeColumnFlags(ci) {
		const f = {}
		for (const key of Object.keys(getFlags())) {
			const parts = key.split('-')
			const r = parts[0]
			const c = parseInt(parts[1], 10)
			if (c === ci) continue
			const nc = c > ci ? c - 1 : c
			f[r + '-' + nc] = getFlags()[key]
		}
		const hf = {}
		for (const key of Object.keys(headerDirtyFlags.value)) {
			const k = parseInt(key, 10)
			if (k === ci) continue
			const nk = k > ci ? k - 1 : k
			hf[nk] = headerDirtyFlags.value[key]
		}
		cellDirtyFlags.value = f
		headerDirtyFlags.value = hf
	}

	// ====== 行操作 ======

	function addRow() {
		localRows.value.push(new Array(localColumns.value.length).fill(''))
		flagRow(localRows.value.length - 1)
	}

	function addRowBefore(index) {
		localRows.value.splice(index, 0, new Array(localColumns.value.length).fill(''))
		const f = {}
		for (const key of Object.keys(getFlags())) {
			const parts = key.split('-')
			const r = parseInt(parts[0], 10)
			const c = parts[1]
			const nr = r >= index ? r + 1 : r
			f[nr + '-' + c] = getFlags()[key]
		}
		for (let j = 0; j < localColumns.value.length; j++) f[index + '-' + j] = true
		cellDirtyFlags.value = f
	}

	function addRowAfter(index) {
		addRowBefore(index + 1)
	}

	function deleteRow(index) {
		if (localRows.value.length <= 1) return
		localRows.value.splice(index, 1)
		removeRowFlags(index)
	}

	// ====== 列操作 ======

	function addColumn() {
		const newCi = localColumns.value.length
		localColumns.value.push({ name: '', index: newCi })
		for (const row of localRows.value) row.push('')
		flagColumn(newCi)
	}

	function addColumnBefore(index) {
		localColumns.value.splice(index, 0, { name: '', index })
		for (const row of localRows.value) row.splice(index, 0, '')
		const f = {}
		for (const key of Object.keys(getFlags())) {
			const parts = key.split('-')
			const r = parts[0]
			const c = parseInt(parts[1], 10)
			const nc = c >= index ? c + 1 : c
			f[r + '-' + nc] = getFlags()[key]
		}
		for (let i = 0; i < localRows.value.length; i++) f[i + '-' + index] = true
		cellDirtyFlags.value = f
	}

	function addColumnAfter(index) {
		addColumnBefore(index + 1)
	}

	function deleteColumn(index) {
		if (localColumns.value.length <= 1) return
		localColumns.value.splice(index, 1)
		for (const row of localRows.value) row.splice(index, 1)
		removeColumnFlags(index)
	}

	function typeAbbr(type) {
		if (!type) return ''
		return type.charAt(0).toLowerCase()
	}
</script>

<style scoped>
	.pte-container { border: 1px solid #e8e8e8; border-radius: 4px; overflow: hidden; }
	.pte-toolbar { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: #fafafa; border-bottom: 1px solid #e8e8e8; }
	.pte-table-name { font-weight: 600; font-size: 14px; }
	.pte-table-wrap { overflow: auto; max-height: 50vh; }
	.pte-table { table-layout: fixed; border-collapse: collapse; font-size: 13px; }
	.pte-table th, .pte-table td { border: 1px solid #e8e8e8; padding: 4px 8px; text-align: left; overflow: visible; line-height: 1.3; }
	.pte-header-cell { background: #f5f5f5; font-weight: 600; position: relative; padding: 4px 2px !important; vertical-align: middle; }
	.pte-header-content { display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 0; text-align: center; }
	.pte-header-main { display: inline-flex; align-items: center; gap: 2px; white-space: nowrap; }
	.pte-col-del { border: none; background: none; cursor: pointer; color: #ff2d2f; font-size: 12px; padding: 0 2px; opacity: 0.6; line-height: 1; }
	.pte-col-del:hover { opacity: 1; }
	.pte-type-tag { color: #1890ff; font-size: 10px; font-weight: 400; margin-left: 0; opacity: 0.85; }
	.pte-row-num { text-align: center; background: #f5f5f5; padding: 4px 2px; font-weight: 600; color: #888; font-size: 12px; position: relative; vertical-align: middle; }
	.pte-index-cell { user-select: none; }
	.pte-row-num-text { display: inline-block; padding: 1px 0; }
	.pte-cell { position: relative; }
	.pte-cell-active { outline: 2px solid #1890ff; outline-offset: -2px; }
	.pte-cell-empty { background: #fafafa; text-align: center; }
	.pte-cell-dirty { background: #ffe58f !important; box-shadow: inset 0 0 0 1px #faad14; }
	.pte-col-dirty .pte-header-main { background: #ffe58f; border-radius: 2px; padding: 0 2px; }
	.pte-cell-input { border: 1px solid #d9d9d9; outline: none; width: 100%; background: transparent; font-size: 12px; line-height: 1.2; height: 22px; padding: 0 4px; margin: 0; box-sizing: border-box; border-radius: 2px; }
	.pte-cell-input:focus { border-color: #1890ff; }
	.pte-ins-btn { position: absolute; z-index: 2; border: none; background: #1890ff; color: #fff; font-size: 14px; line-height: 1; padding: 0; margin: 0; cursor: pointer; border-radius: 50%; width: 18px; height: 18px; display: none; align-items: center; justify-content: center; opacity: 0.85; transition: opacity 0.15s; }
	.pte-ins-btn:hover { opacity: 1; background: #40a9ff; }
	.pte-header-cell:hover .pte-ins-btn, .pte-index-cell:hover .pte-ins-btn { display: flex; }
	.pte-ins-col-left { left: -9px; top: 50%; transform: translateY(-50%); }
	.pte-ins-col-right { right: -9px; top: 50%; transform: translateY(-50%); }
	.pte-ins-row-top { top: -9px; left: 50%; transform: translateX(-50%); }
	.pte-ins-row-bottom { bottom: -9px; left: 50%; transform: translateX(-50%); }
	.pte-actions { padding: 8px 12px; border-top: 1px solid #e8e8e8; background: #fafafa; }
	.pte-action-btn { border: 1px dashed #d9d9d9; background: #fff; cursor: pointer; padding: 4px 12px; font-size: 13px; border-radius: 4px; color: #666; }
	.pte-action-btn:hover { border-color: #1890ff; color: #1890ff; }
	.pte-empty { padding: 40px; text-align: center; color: #999; }
	.pte-dirty-bar { padding: 4px 12px; background: #fff7e6; border-top: 1px solid #ffd591; color: #d46b08; font-size: 12px; }
</style>
