<template>
	<div class="sim-param-container" v-if="loaded">
		<!-- === 表格编辑器 === -->
		<a-card size="small" class="param-card">
			<template #title>
				<a-button class="px-4 " size="small" type="primary" @click="loadSnapshotFromMCP" :loading="snapshotLoading">取模型参数</a-button>
				<a-button class="px-4 mx-4" size="small" type="primary" @click="saveSnapshotToDb" :loading="snapshotLoading">快照写库</a-button>
				<span>[{{ myModel.modelFileName}}]</span><span class="ml-4 text-sm text-gray-500">参数表编辑({{ activeSnapshotItem?.title || '当前模型的参数' }})</span>
				<a-button class="px-4 mx-4" size="small" type="primary" danger ghost @click="saveSnapshotToModel" :loading="snapshotLoading">快照写模型</a-button>
			</template>
			<a-row :wrap="false">
				<a-col :flex="'0 0 160px'" class="px-0 mr-2">
				<a-list :data-source="listParamSnapshot" :bordered="true" :pagination="false" class="px-0 !important">
					<a-list-item v-for="item in listParamSnapshot" :key="item.key || item.title"
						:class="{ 'list-item-active': item.key === activeSnapshotKey }"
						@click="onSnapshotItemClick(item)"
						style="cursor: pointer;">
						<a-row :wrap="false" style="width: 100%; align-items: center;">
							<a-col :flex="'auto'">{{ item.title }}</a-col>
							<a-col :flex="'0 0 20px'" v-if="item.type === 'history'" @click.stop="deleteSnapshot(item.id)">
								<a-button type="link" size="small" danger style="padding: 0; font-size: 12px; line-height: 1;">✕</a-button>
							</a-col>
						</a-row>
					</a-list-item>
				</a-list>
				</a-col>
				<a-col :flex="'auto'">
					<a-tabs :activeKey="myModel.selectedTableName" size="small" v-if="tableOptions.length > 0" @change="onTabChange">
						<a-tab-pane v-for="opt in tableOptions" :key="opt.value" :tab="opt.label" />
					</a-tabs>
					<div v-if="snapshotLoading" class="param-loading">正在加载参数快照...</div>
					<div v-else-if="snapshotErrorMsg" class="param-error">{{ snapshotErrorMsg }}</div>
					<div v-else-if="tableOptions.length === 0" class="param-empty">暂无参数表数据——<b>请确认模型中是否有“MCP表”</b></div>
					<ParamTableEditor
						v-else
						:key="myModel.selectedTableName"
						:tableName="myModel.selectedTableName"
						:tableData="currentTableData"
						@save="onTableSave"
						@dirty-change="onDirtyChange"
					/>
				</a-col>
			</a-row>
		</a-card>

		<!-- === 原始数据（折叠保留，完成后移除）=== -->
		<a-collapse ghost class="raw-collapse">
			<a-collapse-panel key="params" header="参数快照列表（原始 JSON）">
				<pre class="param-pre">{{ listParamSnapshot }}</pre>
			</a-collapse-panel>
			<a-collapse-panel key="params" header="参数表列表（原始 JSON）">
				<pre class="param-pre">{{ paramsJson }}</pre>
			</a-collapse-panel>
			<a-collapse-panel key="snapshot" header="参数快照（原始 JSON）">
				<pre class="param-pre">{{ snapshotJson }}</pre>
			</a-collapse-panel>
		</a-collapse>
	</div>

	<div class="sim-param-empty" v-else>
		<a-empty description="当前没有加载的仿真模型">
			<a-button type="link" @click="goToModelfile">前往模型库, 加载模型</a-button>
		</a-empty>
	</div>
</template>

<script setup>
	import { ref, onMounted, onActivated, computed, watch, nextTick } from 'vue'
	import { useRouter } from 'vue-router'
	import { useMyModelStore } from '@/store/myModel'
	import fsSimApi from '@/api/fscloud/fsSimApi'
	import ParamTableEditor from './ParamTableEditor.vue'
	import { Modal, message } from 'ant-design-vue'

	const router = useRouter()
	const myModel = useMyModelStore()

	const paramsJson = ref('')
	const loading = ref(false)
	const errorMsg = ref('')

	const snapshotJson = ref('')
	const snapshotLoading = ref(false)
	const snapshotErrorMsg = ref('')

	const editorDirty = ref(false)

	const loaded = computed(() => myModel.isLoaded)

	/** 表名归一化：去前导点与 Param_ 前缀（MCP 清单与快照的表名格式可能不一致） */
	const normalizeTableName = (name) => {
		let n = String(name || '').trim().replace(/^\.+/, '')
		if (/^param_/i.test(n)) n = n.slice(6)
		return n
	}

	/** 当前展示快照的 MCP 清单（含 remark）：历史快照 keyed 格式也能据此过滤输出表 */
	const snapshotMcpRows = ref([])

	/** remark=Out 的输出表：参数页不显示（归到仿真结果页的「自定义结果表」） */
	const outTableNames = computed(() => {
		let rows = snapshotMcpRows.value
		if (!Array.isArray(rows) || rows.length === 0) {
			rows = myModel.snapshotRaw?.mcpRows
			if (typeof rows === 'string') {
				try { rows = JSON.parse(rows) } catch (e) { rows = [] }
			}
		}
		const names = new Set()
		for (const row of (Array.isArray(rows) ? rows : [])) {
			if (!row) continue
			if (String(row.remark ?? '').trim().toLowerCase() !== 'out') continue
			const key = normalizeTableName(row.tableName)
			if (key) names.add(key)
		}
		return names
	})

	const tableOptions = computed(() => {
		return Object.keys(myModel.snapshotData)
			.filter(name => !outTableNames.value.has(normalizeTableName(name)))
			.map(name => ({
				label: name.replace(/^Param_/i, ''),
				value: name
			}))
	})

	/** 过滤后当前选中表可能已被隐藏，自动切到第一张可见表 */
	watch(tableOptions, (opts) => {
		if (opts.length === 0) return
		if (!opts.some(opt => opt.value === myModel.selectedTableName)) {
			myModel.setSelectedTableName(opts[0].value)
		}
	}, { immediate: true })

	const currentTableData = computed(() => {
		const name = myModel.selectedTableName
		if (!name || !myModel.snapshotData[name]) return { columns: [], rows: [] }
		return myModel.snapshotData[name]
	})

	const loadParams = async () => {
		loading.value = true
		errorMsg.value = ''
		paramsJson.value = ''

		try {
			const res = await fsSimApi.fsSimListParameterTables({ modelId: myModel.modelId })
			if (res) {
				paramsJson.value = JSON.stringify(res, null, 2)
			} else {
				errorMsg.value = '获取参数列表失败：返回数据为空'
			}
		} catch (err) {
			errorMsg.value = '获取参数列表失败：' + (err?.msg || err?.message || '请求异常')
		} finally {
			loading.value = false
		}
	}

	const loadSnapshotFromMCP = async () => {
		activeSnapshotKey.value = '__current__'
		activeSnapshotItem.value = null
		loadSnapshot()
	}

	const loadSnapshot = async () => {
		snapshotLoading.value = true
		snapshotErrorMsg.value = ''
		snapshotJson.value = ''
		snapshotMcpRows.value = []

		try {
			const res = await fsSimApi.fsSimGetParameterSnapshot({ modelId: myModel.modelId })
			if (res) {
				snapshotJson.value = JSON.stringify(res, null, 2)
				snapshotMcpRows.value = Array.isArray(res.mcpRows) ? res.mcpRows : []
				myModel.parseAndSetSnapshot(res)
				message.info('已成功从模型中取到参数')
			} else {
				snapshotErrorMsg.value = '获取参数快照失败：返回数据为空'
			}
		} catch (err) {
			snapshotErrorMsg.value = '获取参数快照失败：' + (err?.msg || err?.message || '请求异常')
		} finally {
			snapshotLoading.value = false
		}
	}

	const saveSnapshotToDb = async () => {
		snapshotLoading.value = true
		snapshotErrorMsg.value = ''
		try {
			const payload = {
				modelFileId: String(myModel.recordId),
				parameterTables: myModel.snapshotRaw?.parameterTables ? JSON.stringify(myModel.snapshotRaw.parameterTables) : null,
				mcpRows: myModel.snapshotRaw?.mcpRows ? JSON.stringify(myModel.snapshotRaw.mcpRows) : null,
				tables: JSON.stringify(myModel.snapshotData),
				missingTables: myModel.snapshotRaw?.missingTables ? JSON.stringify(myModel.snapshotRaw.missingTables) : null
			}
			await fsSimApi.fsSimSaveSnapshot(payload)
			message.success('快照保存成功')
			loadSnapshotList()
		} catch (err) {
			snapshotErrorMsg.value = '保存参数快照失败：' + (err?.msg || err?.message || '请求异常')
		} finally {
			snapshotLoading.value = false
		}
	}

	/**
	* 快照写模型: 组织 myModel.snapshotData 中的参数表数据，调用 MCP 工具 batchUpdateRemoteSimulationParameterTable 写入模型
	 */
	const saveSnapshotToModel = async () => {
		const data = myModel.snapshotData
		const tableNames = Object.keys(data)
		if (tableNames.length === 0) {
			message.warning('没有可写入的参数表数据，请先从模型获取参数')
			return
		}

		// 确认对话框
		const confirmed = await new Promise((resolve) => {
			Modal.confirm({
				title: '确认将参数写入模型',
				content: `将 ${tableNames.length} 张参数表写入仿真模型。修改将保存到 .fs 模型文件，确定继续？`,
				okText: '确认写入',
				cancelText: '取消',
				onOk: () => resolve(true),
				onCancel: () => resolve(false)
			})
		})
		if (!confirmed) return

		// 组装 tablesJson
		const tables = tableNames.map(name => {
			const t = data[name]
			return {
				tableName: name,
				columns: t.columns || [],
				columnCount: (t.columns || []).length,
				rows: t.rows || []
			}
		})

		snapshotLoading.value = true
		snapshotErrorMsg.value = ''
		try {
			const res = await fsSimApi.fsSaveParamSnapshotToModel({
				modelId: myModel.modelId,
				tablesJson: JSON.stringify(tables),
				bSaveModel: true
			})
			if (res) {
				message.success(`写入成功：${res.updatedTableCount || 0} 张表，${res.updatedRowCount || 0} 行`)
				// 自动刷新从模型获取最新参数
				loadSnapshot()
			} else {
				message.error('写入失败：返回数据为空')
			}
		} catch (err) {
			const errMsg = err?.msg || err?.message || '请求异常'
			message.error('写入模型失败：' + errMsg)
		} finally {
			snapshotLoading.value = false
		}
	}

	// 快照列表数据
	const snapshotList = ref([])
	const snapshotListLoading = ref(false)

	const loadSnapshotList = async () => {
		if (!myModel.recordId) return
		snapshotListLoading.value = true
		try {
			const list = await fsSimApi.fsSimListSnapshots({ modelFileId: String(myModel.recordId) })
			snapshotList.value = list || []
		} catch (err) {
			console.error('loadSnapshotList error', err)
			snapshotList.value = []
		} finally {
			snapshotListLoading.value = false
		}
	}

	const activeSnapshotKey = ref('__current__')
	const activeSnapshotItem = ref(null)

	const onSnapshotItemClick = (item) => {
		activeSnapshotItem.value = item
		activeSnapshotKey.value = item.key
		if (item.type === 'current') {
			// 重新从 MCP 获取当前参数
			loadSnapshot()
		} else if (item.type === 'history' && item.id) {
			loadSnapshotDetail(item.id)
		}
	}

	const formatSnapshotTime = (ts) => {
		if (!ts) return ''
		const d = new Date(ts)
		const y = d.getFullYear()
		const mo = String(d.getMonth() + 1).padStart(2, '0')
		const dd = String(d.getDate()).padStart(2, '0')
		const hh = String(d.getHours()).padStart(2, '0')
		const mm = String(d.getMinutes()).padStart(2, '0')
		return `${y}.${mo}.${dd} ${hh}:${mm}`
	}

	const loadSnapshotDetail = async (id) => {
		try {
			const detail = await fsSimApi.fsSimGetSnapshot({ id })
			console.log('[simparam/index.vue] getSnapshot res', detail)
			if (detail) {
				let mcpRows = []
				if (detail.mcpRows) {
					try { mcpRows = JSON.parse(detail.mcpRows) } catch (e) { mcpRows = [] }
				}
				snapshotMcpRows.value = Array.isArray(mcpRows) ? mcpRows : []
			}
			if (detail && detail.tables) {
				const parsedTables = JSON.parse(detail.tables)
				if (Array.isArray(parsedTables)) {
					// MCP 原生格式：数组，每项有 tableName / columns / rows
					myModel.parseAndSetSnapshot({
						parameterTables: detail.parameterTables ? JSON.parse(detail.parameterTables) : [],
						mcpRows: detail.mcpRows ? JSON.parse(detail.mcpRows) : [],
						tables: parsedTables,
						missingTables: detail.missingTables ? JSON.parse(detail.missingTables) : []
					})
				} else {
					// 保存后的格式：对象 keyed by 表名，通过 action 设置
					myModel.setSnapshotFromKeyed(parsedTables)
				}
				message.info('已从数据库中加载历史快照')
			} else {
				message.warning('快照数据为空')
			}
		} catch (err) {
			console.error('loadSnapshotDetail error', err)
			message.error('从数据库中加载快照详情失败')
		}
	}

	const deleteSnapshot = async (id) => {
		// 记录被删项在列表中的索引
		const oldList = listParamSnapshot.value
		const deletedIdx = oldList.findIndex(item => item.id === id)

		Modal.confirm({
			title: '确认删除',
			content: '确定要删除此参数快照吗？',
			okText: '确认',
			cancelText: '取消',
			onOk: async () => {
				try {
					await fsSimApi.fsSimDeleteSnapshot({ id })
					message.success('快照已删除')
					await loadSnapshotList()

					// 删除后自动选中邻近项
					const newList = listParamSnapshot.value
					const targetIdx = Math.min(deletedIdx, newList.length - 1)
					const targetItem = newList[targetIdx]
					if (targetItem) {
						activeSnapshotKey.value = targetItem.key
						onSnapshotItemClick(targetItem)
					}
				} catch (err) {
					message.error('删除快照失败')
				}
			}
		})
	}

	const listParamSnapshot = computed(() => {
		const items = [
			{ key: '__current__', title: '当前模型参数', type: 'current' }
		]
		snapshotList.value.forEach(item => {
			items.push({
				key: item.id,
				title: formatSnapshotTime(item.snapshotAt),
				type: 'history',
				id: item.id
			})
		})
		return items
	})

	onMounted(() => {
		initPage()
	})

	onActivated(() => {
		initPage()
	})

	const initPage = async () => {
		if (!myModel.isLoaded) {
			const data = await fsSimApi.fsSimGetLoadedModel().catch(() => null)
			console.log('[simparam/index.vue] getLoadedModel res', data)
			if (data && data.modelId) {
				myModel.setModel(data)
				loadSnapshotList()
			} else {
				return
			}
		}
	}

	function onDirtyChange(val) {
		editorDirty.value = val
	}

	function onTabChange(key) {
		if (editorDirty.value) {
			Modal.confirm({
				title: '确认不保存数据吗？',
				content: '当前修改尚未保存，切换后将丢失所有未保存的修改。',
				okText: '确认丢弃',
				cancelText: '取消',
				onOk: () => {
					myModel.setSelectedTableName(key)
				},
				onCancel: () => {
					// 不切换，保持当前 tab
				}
			})
		} else {
			myModel.setSelectedTableName(key)
		}
	}

	function onTableSave(newData) {
		const name = myModel.selectedTableName
		if (!name) return
		myModel.updateSnapshotTable(name, newData)
	}

	const goToModelfile = () => router.push('/fscloud/modelfile')

	watch(() => myModel.modelId, (newVal) => {
		if (newVal) {
			loadParams()
			loadSnapshot()
			loadSnapshotList()
		} else {
			paramsJson.value = ''
			errorMsg.value = ''
			snapshotJson.value = ''
			snapshotErrorMsg.value = ''
			snapshotMcpRows.value = []
			snapshotList.value = []
		}
	}, { immediate: true })
</script>

<style scoped>
	.sim-param-container { padding: 16px; display: flex; flex-direction: column; gap: 12px; height: calc(100vh - 140px); overflow-y: auto; }
	.param-card { flex-shrink: 0; }
	.param-card :deep(.ant-card-body) { overflow: auto; padding: 16px; }
	.param-pre {
		background: #1e1e1e;
		color: #d4d4d4;
		padding: 16px;
		border-radius: 4px;
		font-family: 'Courier New', monospace;
		font-size: 13px;
		line-height: 1.5;
		overflow: auto;
		white-space: pre-wrap;
		word-break: break-all;
		margin: 0;
		max-height: 300px;
	}
	.param-loading { color: #666; text-align: center; padding: 40px 0; font-size: 14px; }
	.param-error { color: #cf1322; background: #fff2f0; border: 1px solid #ffccc7; padding: 8px 12px; border-radius: 4px; }
	.param-empty { color: #666; text-align: center; padding: 40px 0; }
	:deep(.ant-list-item) { padding: 6px 12px; }
	.list-item-active { background-color: #e6f7ff; color: #1890ff; font-weight: normal; }
	.sim-param-empty { display: flex; justify-content: center; align-items: center; height: calc(100vh - 200px); }
	.raw-collapse { flex-shrink: 0; }
	.raw-collapse :deep(.ant-collapse-content-box) { padding: 8px 0 !important; }
</style>
