<template>
	<div class="sim-param-container" v-if="loaded">
		<!-- === 表格编辑器 === -->
		<a-card size="small" class="param-card">
			<template #title>
				<a-button class="px-4 mx-4" size="small" type="primary" @click="loadSnapshot" :loading="snapshotLoading">从模型重新读取重新</a-button>
				<span>[{{ myModel.modelFileName}}]参数表编辑</span>
			</template>
			<a-tabs :activeKey="myModel.selectedTableName" size="small" v-if="tableOptions.length > 0" @change="onTabChange">
				<a-tab-pane v-for="opt in tableOptions" :key="opt.value" :tab="opt.label" />
			</a-tabs>
			<div v-if="snapshotLoading" class="param-loading">正在加载参数快照...</div>
			<div v-else-if="snapshotErrorMsg" class="param-error">{{ snapshotErrorMsg }}</div>
			<div v-else-if="tableOptions.length === 0" class="param-empty">暂无参数表数据</div>
			<ParamTableEditor
				v-else
				:key="myModel.selectedTableName"
				:tableName="myModel.selectedTableName"
				:tableData="currentTableData"
				@save="onTableSave"
				@dirty-change="onDirtyChange"
			/>
		</a-card>

		<!-- === 原始数据（折叠保留，完成后移除）=== -->
		<a-collapse ghost class="raw-collapse">
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
	import { ref, computed, watch } from 'vue'
	import { useRouter } from 'vue-router'
	import { useMyModelStore } from '@/store/myModel'
	import fsSimApi from '@/api/fscloud/fsSimApi'
	import ParamTableEditor from './ParamTableEditor.vue'
	import { Modal } from 'ant-design-vue'

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

	const tableOptions = computed(() => {
		return Object.keys(myModel.snapshotData).map(name => ({
			label: name,
			value: name
		}))
	})

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

	const loadSnapshot = async () => {
		snapshotLoading.value = true
		snapshotErrorMsg.value = ''
		snapshotJson.value = ''

		try {
			const res = await fsSimApi.fsSimGetParameterSnapshot({ modelId: myModel.modelId })
			if (res) {
				snapshotJson.value = JSON.stringify(res, null, 2)
				myModel.parseAndSetSnapshot(res)
			} else {
				snapshotErrorMsg.value = '获取参数快照失败：返回数据为空'
			}
		} catch (err) {
			snapshotErrorMsg.value = '获取参数快照失败：' + (err?.msg || err?.message || '请求异常')
		} finally {
			snapshotLoading.value = false
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
					myModel.selectedTableName = key
				},
				onCancel: () => {
					// 不切换，保持当前 tab
				}
			})
		} else {
			myModel.selectedTableName = key
		}
	}

	function onTableSave(newData) {
		const name = myModel.selectedTableName
		if (!name) return
		myModel.snapshotData[name] = newData
		myModel.snapshotBackup = JSON.parse(JSON.stringify(myModel.snapshotData))
	}

	const goToModelfile = () => router.push('/fscloud/modelfile')

	watch(() => myModel.modelId, (newVal) => {
		if (newVal) {
			loadParams()
			loadSnapshot()
		} else {
			paramsJson.value = ''
			errorMsg.value = ''
			snapshotJson.value = ''
			snapshotErrorMsg.value = ''
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
	.sim-param-empty { display: flex; justify-content: center; align-items: center; height: calc(100vh - 200px); }
	.raw-collapse { flex-shrink: 0; }
	.raw-collapse :deep(.ant-collapse-content-box) { padding: 8px 0 !important; }
</style>
