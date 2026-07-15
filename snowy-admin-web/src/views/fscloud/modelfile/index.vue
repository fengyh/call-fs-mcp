<template>
	<xn-panel>
		<div class="model-file-container">
			<!-- 左侧：列表区域（带过滤条件） -->
			<div class="left-panel">
				<!-- 过滤条件 -->
				<div class="filter-bar">
					<a-space :size="12" wrap>
						<a-select
							v-model:value="searchForm.modelUsage"
							placeholder="模型用途"
							style="width: 140px"
							allowClear
							size="small"
						>
							<a-select-option v-for="item in usageOptions" :key="item.value" :value="item.value">
								{{ item.label }}
							</a-select-option>
						</a-select>
						<a-select
							v-model:value="searchForm.modelCompany"
							placeholder="公司"
							style="width: 120px"
							allowClear
							size="small"
						>
							<a-select-option v-for="item in companyOptions" :key="item.value" :value="item.value">
								{{ item.label }}
							</a-select-option>
						</a-select>
						<a-select
							v-model:value="searchForm.modelFactory"
							placeholder="工厂"
							style="width: 120px"
							allowClear
							size="small"
							:disabled="!searchForm.modelCompany"
						>
							<a-select-option v-for="item in factoryOptions" :key="item.value" :value="item.value">
								{{ item.label }}
							</a-select-option>
						</a-select>
						<a-input v-if="false"
							v-model:value="searchForm.modelWorkshop"
							placeholder="车间"
							style="width: 120px"
							allowClear
							size="small"
						/>
						<a-input v-if="false"
							v-model:value="searchForm.modelVersion"
							placeholder="版本"
							style="width: 80px"
							allowClear
							size="small"
						/>
						<a-button type="primary" size="small" @click="tableRef.refresh(true)">查询</a-button>
						<a-button size="small" @click="reset">重置</a-button>
						<a-button type="dashed" danger size="small" style="margin-left: 16px" @click="jumpToSimControl" v-if="myModel.isLoaded"><PlayCircleOutlined />仿真控制</a-button>
					</a-space>
				</div>

				<!-- 数据表格 -->
				<s-table
					ref="tableRef"
					:columns="columns"
					:data="loadData"
					compSize="small"
					:bordered="true"
					:row-key="(record) => record.id"
					:tool-config="toolConfig"
					:scroll="{ x: 'max-content', y: 'calc(100vh - 320px)' }"
					:customRow="(record) => ({
						class: selectedRecord?.id === record.id ? 'model-row-selected' : '',
						style: { background: selectedRecord?.id === record.id ? '#bae0ff' : '' },
						onClick: () => selectedRecord = record
					})"
				>
					<template #bodyCell="{ column, record }">
						<template v-if="column.dataIndex === 'action'">
							<a-space>
								<a-button type="link" size="small" :disabled="myModel.isLoaded" @click="simulateModel(record)">仿真</a-button>
								<a-popconfirm title="确定要删除吗？" @confirm="deleteFsModelFile(record)">
									<a-button type="link" danger size="small" v-if="hasPerm('fsModelFileDelete')">删除</a-button>
								</a-popconfirm>
							</a-space>
						</template>
						<template v-if="column.dataIndex === 'model'">
							<a v-bind:href="`/static/uploaded/${record.userId}/${record.model}`" v-bind:download="record.model" target="_blank">{{ record.model }}</a>
						</template>
						<template v-if="column.dataIndex === 'modelUsage'">
							{{ $TOOL.dictTypeData('FS_USAGE', record.modelUsage) }}
						</template>
						<template v-if="column.dataIndex === 'shareFlag'">
							{{ $TOOL.dictTypeData('MODEL_SHARE', record.shareFlag) }}
						</template>
						<template v-if="column.dataIndex === 'simStatus'">
							{{ $TOOL.dictTypeData('SIM_STATUS', statusUpdates[record.id]?.simStatus ?? record.simStatus) }}
						</template>
						<template v-if="column.dataIndex === 'simTimes'">
							{{ statusUpdates[record.id]?.simTimes ?? record.simTimes }}
						</template>
					</template>
				</s-table>
			</div>

			<!-- 右侧：详情区域 -->
			<div class="right-panel" v-if="selectedRecord">
				<a-tabs v-model:activeKey="detailTabKey" size="small">
					<a-tab-pane key="info" tab="模型信息">
						<div style="display: flex; gap: 8px; margin-bottom: 8px;">
							<a-button type="primary" size="small" :disabled="!detailChanged" @click="saveDetail">保存</a-button>
							<a-button size="small" :disabled="!detailChanged" @click="cancelDetail">取消</a-button>
						</div>
						<a-row :gutter="4">
						<a-col :span="12">
							<a-descriptions :column="1" bordered size="small" :labelStyle="{ width: '120px' }" class="detail-desc">
								<a-descriptions-item label="仿真模型名称">{{ selectedRecord.name }}</a-descriptions-item>
								<a-descriptions-item label="仿真文件">{{ selectedRecord.model }}</a-descriptions-item>
								<a-descriptions-item label="模型描述">{{ selectedRecord.description }}</a-descriptions-item>
								<a-descriptions-item label="引擎版本">{{ selectedRecord.engineVersion }}</a-descriptions-item>
								<a-descriptions-item label="创建人">{{ selectedRecord.userName }}</a-descriptions-item>
								<a-descriptions-item label="仿真状态">{{ $TOOL.dictTypeData('SIM_STATUS', selectedRecord.simStatus) }}</a-descriptions-item>
								<a-descriptions-item label="仿真次数">{{ selectedRecord.simTimes }}</a-descriptions-item>
							</a-descriptions>
						</a-col>
						<a-col :span="12">
							<a-descriptions :column="1" bordered size="small" :labelStyle="{ width: '120px' }" class="detail-desc">
									<a-descriptions-item label="创建时间">{{ selectedRecord.createdAt }}</a-descriptions-item>
									<a-descriptions-item label="所属公司">
										<a-select v-model:value="editData.modelCompany" size="small" style="width: 180px"
											:class="fieldCls('modelCompany')"
											@change="onDetailChange">
											<a-select-option v-for="item in companyOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
										</a-select>
									</a-descriptions-item>
									<a-descriptions-item label="所属工厂">
										<a-select v-model:value="editData.modelFactory" size="small" style="width: 180px"
											:class="fieldCls('modelFactory')"
											:disabled="!editData.modelCompany" @change="onDetailChange">
											<a-select-option v-for="item in detailFactoryOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
										</a-select>
									</a-descriptions-item>
									<a-descriptions-item label="所属车间">
										<a-input v-model:value="editData.modelWorkshop" size="small" style="width: 180px"
											:class="fieldCls('modelWorkshop')" @change="onDetailChange" />
									</a-descriptions-item>
									<a-descriptions-item label="模型用途">
										<a-select v-model:value="editData.modelUsage" size="small" style="width: 180px"
												  :class="fieldCls('modelUsage')"
												  @change="onDetailChange">
											<a-select-option v-for="item in usageOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
										</a-select>
									</a-descriptions-item>
									<a-descriptions-item label="模型版本">
										<a-input v-model:value="editData.modelVersion" size="small" style="width: 180px"
											:class="fieldCls('modelVersion')" @change="onDetailChange" />
									</a-descriptions-item>
									<a-descriptions-item label="分享范围">
										<a-select v-model:value="editData.shareFlag" size="small" style="width: 180px"
											:class="fieldCls('shareFlag')"
											@change="onDetailChange">
											<a-select-option v-for="item in shareFlagOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option>
										</a-select>
									</a-descriptions-item>
								</a-descriptions>
							</a-col>
						</a-row>
						<img :src="`/static/uploaded/${selectedRecord.userId}/${coverImage(selectedRecord.image)}`" alt="模型预览" v-if="selectedRecord.image" class="model-preview-img" @click="previewVisible = true" />
						<a-modal v-model:open="previewVisible" :footer="null" :closable="true" :width="'100vw'" wrapClassName="fullscreen-preview-modal" @keydown.esc="previewVisible = false">
							<img :src="`/static/uploaded/${selectedRecord.userId}/${coverImage(selectedRecord.image)}`" alt="模型预览"
								:style="previewImgStyle"
								@wheel.prevent="onPreviewWheel"
								@mousedown="onPreviewMouseDown"
								@mousemove="onPreviewMouseMove"
								@mouseup="onPreviewMouseUp"
								@mouseleave="onPreviewMouseUp"
							/>
						</a-modal>
					</a-tab-pane>
<!--					<a-tab-pane key="sim" tab="仿真信息">-->
<!--						<div style="margin-bottom: 8px;">-->
<!--							<a-button size="small" style="margin-right: 8px;"-->
<!--								:disabled="btn1Disabled"-->
<!--								@click="btn1Click">{{ btn1Text }}</a-button>-->
<!--							<a-button size="small" style="margin-right: 8px;"-->
<!--								:disabled="btn2Disabled"-->
<!--								@click="btn2Click">{{ btn2Text }}</a-button>-->
<!--							<a-button size="small" style="margin-right: 8px;"-->
<!--								:disabled="btn3Disabled"-->
<!--								@click="btn3Click">{{ btn3Text }}</a-button>-->
<!--							<a-button size="small" style="margin-right: 8px;"-->
<!--								:disabled="btn4Disabled"-->
<!--								@click="btn4Click">{{ btn4Text }}</a-button>-->
<!--						</div>-->
<!--					</a-tab-pane>-->
				</a-tabs>
			</div>
			<!-- 未选中时右侧显示提示 -->
			<div class="right-panel empty-hint" v-else>
				<a-empty description="请选择左侧列表查看详情" />
			</div>
		</div>
	</xn-panel>
	<Form ref="formRef" @successful="onFormSuccess" />
</template>

<script setup name="modelfile">
	import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
	import { cloneDeep } from 'lodash-es'
	import { useRouter } from 'vue-router'
	import Form from './form.vue'
	import fsModelFileApi from '@/api/fscloud/fsModelFileApi'
	import fsSimApi from '@/api/fscloud/fsSimApi'
	import { useMyModelStore } from '@/store/myModel'
	import { createMqttClient, disconnectMqtt } from '@/utils/mqttClient'
	import tool from '@/utils/tool'
	import {message} from "ant-design-vue";

	const tableRef = ref()
	const formRef = ref()
	const toolConfig = { refresh: true, height: false, columnSetting: true, striped: false }
	const detailTabKey = ref('info')

	// 选中的记录（用于右侧详情展示）
	const selectedRecord = ref(null)
	const previewVisible = ref(false)
	const simResult = ref('')

	// 定时轮询：最新状态覆盖，keyed by record id，用于表格行实时更新
	const statusUpdates = reactive({})
	// 当前页记录副本（loadData 时更新），供轮询收集 id
	const pageRecords = ref([])
	let pollTimer = null

	/** 收集当前页记录的 id，调用 listByIds 更新 statusUpdates */
	const pollStatus = () => {
		const ids = pageRecords.value.map(r => r.id)
		if (ids.length === 0) return
		fsModelFileApi.fsModelFileListByIds(ids).then(freshList => {
			if (!freshList || freshList.length === 0) return
			// 更新 statusUpdates Map
			freshList.forEach(r => {
				statusUpdates[r.id] = { simStatus: r.simStatus, simTimes: r.simTimes, modelId: r.modelId }
			})
			// 同步更新 selectedRecord（右侧详情面板）
			if (selectedRecord.value) {
				const match = freshList.find(r => r.id === selectedRecord.value.id)
				if (match) {
					selectedRecord.value.simStatus = match.simStatus
					selectedRecord.value.simTimes = match.simTimes
					selectedRecord.value.modelId = match.modelId
				}
			}
		}).catch(err => {
			console.error('pollStatus error', err)
		})
	}

	const startPolling = () => {
		stopPolling()
		pollTimer = setInterval(pollStatus, 3000)
	}

	const stopPolling = () => {
		if (pollTimer) {
			clearInterval(pollTimer)
			pollTimer = null
		}
	}

	onMounted(() => {
		startPolling()
		// 查询是否已有加载的模型，如有则订阅 MQTT
		initMqttSubscription()
	})

	onUnmounted(() => {
		stopPolling()
		disconnectMqtt()
	})

	// ---- MQTT 实时订阅 ----
	const myModel = useMyModelStore()
	const router = useRouter()

	/** 初始化：查询当前用户已加载的模型，如有则订阅 MQTT */
	const initMqttSubscription = () => {
		fsSimApi.fsSimGetLoadedModel().then(data => {
			if (data && data.modelId) {
				myModel.setModel(data)
				subscribeMqtt(data.modelId)
			}
		}).catch(err => {
			console.error('getLoadedModel error', err)
		})
	}

	/** 订阅 MQTT（取消旧的，订阅新的） */
	const subscribeMqtt = (modelId) => {
		return
		disconnectMqtt()
		const userInfo = tool.data.get('USER_INFO') || {}
		const userId = userInfo.id || 'admin'
		createMqttClient(userId, modelId, {
			onConnect: () => {
				myModel.setMqttConnected(true)
			},
			onDisconnect: () => {
				myModel.setMqttConnected(false)
			},
			onStatus: (data) => {
				console.log('MQTT status update:', data)
				const status = data.status
				if (!status) return
				// 显示状态反馈
				console.log(`MQTT status update: ${status}, progress: ${data.progress}, simTimeStep: ${data.simTimeStep}`)
				const mcpStatus = { running: 'RUNNING', paused: 'PAUSED', finished: 'FINISHED' }[status]
				if (mcpStatus) {
					myModel.updateStatus(mcpStatus)
				}
			},
			onProgress: (data) => {
				myModel.updateProgress(data.progress, data.simTimeStep)
			},
			onConsole: (data) => {
				myModel.addConsoleLog(data.level, data.content, data.timestamp)
			}
		})
	}

	// 可编辑字段（右侧详情区域）
	const editData = reactive({
		modelUsage: undefined,
		modelCompany: undefined,
		modelFactory: undefined,
		modelWorkshop: undefined,
		modelVersion: undefined,
		shareFlag: undefined
	})
	const originalData = ref(null)
	const detailChanged = ref(false)
	// 选中记录时初始化编辑数据和原始值
	watch(selectedRecord, (val) => {
		if (val) {
			editData.modelUsage = val.modelUsage
			editData.modelCompany = val.modelCompany
			editData.modelFactory = val.modelFactory
			editData.modelWorkshop = val.modelWorkshop
			editData.modelVersion = val.modelVersion
			editData.shareFlag = val.shareFlag
			originalData.value = cloneDeep({
				modelUsage: val.modelUsage,
				modelCompany: val.modelCompany,
				modelFactory: val.modelFactory,
				modelWorkshop: val.modelWorkshop,
				modelVersion: val.modelVersion,
				shareFlag: val.shareFlag
			})
			detailChanged.value = false
		}
	})
	const onDetailChange = () => {
		detailChanged.value = Object.keys(editData).some(k => editData[k] !== originalData.value[k])
	}
	const saveDetail = () => {
		// 克隆完整记录再覆盖编辑字段，避免遗漏后端必填字段
		const params = { ...cloneDeep(selectedRecord.value), ...editData }
		// 清理 undefined 值
		Object.keys(params).forEach(k => params[k] === undefined && delete params[k])
		fsModelFileApi.fsModelFileSubmitForm(params, true).then(() => {
			Object.assign(selectedRecord.value, editData)
			originalData.value = cloneDeep(editData)
			detailChanged.value = false
		})
	}
	const cancelDetail = () => {
		Object.assign(editData, originalData.value)
		detailChanged.value = false
	}

	// 编辑字段 class：新旧一致=field-match，不一致=field-changed
	const fieldCls = (field) => {
		const changed = originalData.value && editData[field] !== originalData.value[field]
		return changed ? 'field-changed' : 'field-match'
	}

	// 预览图片缩放拖动状态
	const previewZoom = ref(1)
	const previewPos = reactive({ x: 0, y: 0 })
	const isDragging = ref(false)
	const dragStart = reactive({ x: 0, y: 0 })
	const previewImgStyle = computed(() => ({
		display: 'block',
		margin: '0 auto',
		cursor: isDragging.value ? 'grabbing' : 'grab',
		transform: `translate(${previewPos.x}px, ${previewPos.y}px) scale(${previewZoom.value})`,
		transition: isDragging.value ? 'none' : 'transform 0.15s ease'
	}))
	const onPreviewWheel = (e) => {
		const oldScale = previewZoom.value
		const d = e.deltaY > 0 ? -0.1 : 0.1
		const newScale = Math.max(0.2, Math.min(5, oldScale + d))
		if (newScale === oldScale) return
		// 以鼠标为中心缩放：保持鼠标所在图像像素点屏幕位置不变
		const rect = e.currentTarget.getBoundingClientRect()
		const cx = (rect.left + rect.right) / 2   // 缩放后图片中心X
		const cy = (rect.top + rect.bottom) / 2    // 缩放后图片中心Y
		const ratio = (oldScale - newScale) / oldScale
		previewZoom.value = newScale
		previewPos.x += (e.clientX - cx) * ratio
		previewPos.y += (e.clientY - cy) * ratio
	}
	const onPreviewMouseDown = (e) => {
		if (e.button !== 0) return
		isDragging.value = true
		dragStart.x = e.clientX - previewPos.x
		dragStart.y = e.clientY - previewPos.y
	}
	const onPreviewMouseMove = (e) => {
		if (!isDragging.value) return
		previewPos.x = e.clientX - dragStart.x
		previewPos.y = e.clientY - dragStart.y
	}
	const onPreviewMouseUp = () => {
		isDragging.value = false
	}

	// 打开预览时重置缩放和平移
	watch(previewVisible, (val) => {
		if (val) {
			previewZoom.value = 1
			previewPos.x = 0
			previewPos.y = 0
		}
	})

	// 过滤表单
	const searchForm = reactive({
		modelUsage: undefined,
		modelCompany: undefined,
		modelFactory: undefined,
		modelWorkshop: undefined,
		modelVersion: undefined
	})

	// 模型用途选项（从字典获取）
	const usageOptions = ref((tool.dictTypeList('FS_USAGE') || []).map(item => ({
		value: item.dictValue,
		label: item.dictLabel
	})))
	// 分享范围选项（从字典获取）
	const shareFlagOptions = ref((tool.dictTypeList('MODEL_SHARE') || []).map(item => ({
		value: item.dictValue,
		label: item.dictLabel
	})))
	// 公司选项（从字典获取）
	const companyOptions = ref((tool.dictTypeList('FS_COMPANY') || []).map(item => ({
		value: item.dictValue,
		label: item.dictLabel
	})))
	// 工厂选项：根据选择的公司动态获取（从公司的children中获取）
	const factoryOptions = computed(() => {
		if (!searchForm.modelCompany) return []
		return getFactoryOptionsByCompany(searchForm.modelCompany)
	})
	// 详情区域工厂选项：根据编辑数据的公司动态获取
	const detailFactoryOptions = computed(() => {
		if (!editData.modelCompany) return []
		return getFactoryOptionsByCompany(editData.modelCompany)
	})
	// 根据公司编码获取工厂选项列表
	const getFactoryOptionsByCompany = (companyValue) => {
		const companies = tool.dictTypeList('FS_COMPANY') || []
		const selectedCompany = companies.find(item => item.dictValue === companyValue)
		return (selectedCompany?.children || []).map(item => ({
			value: item.dictValue,
			label: item.dictLabel || item.name
		}))
	}

	// 选择公司变化时重置工厂
	watch(() => searchForm.modelCompany, () => {
		searchForm.modelFactory = undefined
	})

	// 表格列定义
	const columns = [
		{
			title: '仿真文件(点击下载)',
			dataIndex: 'model',
			width: 120,
			ellipsis: true,
			fixed: 'left'
		},
		{
			title: '模型用途',
			dataIndex: 'modelUsage',
			width: 70,
			align: 'center'
		},
		{
			title: '创建人',
			dataIndex: 'userName',
			width: 60,
			align: 'center'
		},
		{
			title: '分享范围',
			dataIndex: 'shareFlag',
			width: 90,
			align: 'center'
		},
		{
			title: '仿真状态',
			dataIndex: 'simStatus',
			width: 90,
			align: 'center'
		},
		{
			title: '仿真次数',
			dataIndex: 'simTimes',
			width: 80,
			align: 'center'
		},
	]
	// 操作栏通过权限判断是否显示
	if (hasPerm(['fsModelFileEdit', 'fsModelFileDelete'])) {
		columns.push({
			title: '操作',
			dataIndex: 'action',
			align: 'center',
			width: 120,
			fixed: 'right'
		})
	}

	// 加载数据，携带过滤参数
	const loadData = (parameter) => {
		const params = {
			...parameter,
			...searchForm
		}
		return fsModelFileApi.fsModelFilePage(params).then((data) => {
			// 保存当前页记录副本，供定时轮询使用
			pageRecords.value = data.records || []
			// 自动选中第一条记录
			if (data.records && data.records.length > 0 && !selectedRecord.value) {
				selectedRecord.value = data.records[0]
			}
			return data
		})
	}

	// 重置过滤条件
	const reset = () => {
		searchForm.modelUsage = undefined
		searchForm.modelCompany = undefined
		searchForm.modelFactory = undefined
		searchForm.modelWorkshop = undefined
		searchForm.modelVersion = undefined
		tableRef.value.refresh(true)
	}

	// 删除单条
	const deleteFsModelFile = (record) => {
		let params = [{ id: record.id }]
		fsModelFileApi.fsModelFileDelete(params).then(() => {
			selectedRecord.value = null
			tableRef.value.refresh(true)
		})
	}

	// 编辑成功回调
	const onFormSuccess = () => {
		tableRef.value.refresh(true)
	}

	// 将图片文件名后缀 .jpg 替换为 .svg
	const coverImage = (image) => {
		return image && image.replace(/\.jpg$/i, '.svg')
	}

	// 跳转至仿真控制页面
	const jumpToSimControl = () => {
		router.push('/fscloud/simcontrol')
	}


	// ---------------------------------- 以下为仿真相关代码 ----------------------------------
	// 当前选中记录的 simStatus 快捷引用
	const currentStatus = computed(() => selectedRecord.value?.simStatus)

	// ----- 按钮 1：加载/卸载 -----
	const btn1Text = computed(() => {
		const s = currentStatus.value
		if (s === 'SIM_STATUS_NOTYET' || s === 'SIM_STATUS_ALREADY') return '加载'
		return '卸载'
	})
	const btn1Disabled = computed(() => {
		const s = currentStatus.value
		return s === 'SIM_STATUS_GOING' || s === 'SIM_STATUS_PAUSED'
	})
	const btn1Click = () => {
		// if (btn1Text.value === '加载') doLoadModel()
		// else doUnloadModel()
	}

	// ----- 按钮 2：启动/暂停/继续 -----
	const btn2Text = computed(() => {
		const s = currentStatus.value
		if (s === 'SIM_STATUS_GOING') return '暂停'
		if (s === 'SIM_STATUS_PAUSED') return '继续'
		return '启动'
	})
	const btn2Disabled = computed(() => {
		const s = currentStatus.value
		return s === 'SIM_STATUS_NOTYET' || s === 'SIM_STATUS_ALREADY'
			|| !selectedRecord.value?.modelId
	})
	const btn2Click = () => {
		const t = btn2Text.value
		if (t === '启动') doRunModel()
		else if (t === '暂停') doPauseModel()
		else if (t === '继续') doResumeModel()
	}

	// ----- 按钮 3：停止 -----
	const btn3Text = computed(() => '停止')
	const btn3Disabled = computed(() => {
		const s = currentStatus.value
		return s !== 'SIM_STATUS_GOING' && s !== 'SIM_STATUS_PAUSED'
	})
	const btn3Click = () => doStopModel()

	// ----- 按钮 4：重置 -----
	const btn4Text = computed(() => '重置')
	const btn4Disabled = computed(() => {
		const s = currentStatus.value
		return s !== 'SIM_STATUS_WAITING' && s !== 'SIM_STATUS_FINISHED' && s !== 'SIM_STATUS_STOPPED' && s !== 'SIM_STATUS_FAILED'
	})
	const btn4Click = () => doResetModel()

	// ----- guard：检查选中和 modelId -----
	const checkSelected = () => {
		if (!selectedRecord.value || !selectedRecord.value.id) {
			simResult.value = '请先选择一个模型'
			return false
		}
		return true
	}
	const checkModelId = () => {
		if (!selectedRecord.value?.modelId) {
			simResult.value = '请先加载模型'
			return false
		}
		return true
	}

	// ===== 模型库页面的「仿真」按钮 =====
	const simulateModel = (record) => {
		if (myModel.isLoaded) return
		// 设置当前选中的记录
		selectedRecord.value = record
		simResult.value = '正在加载模型...'
		myModel.setFeedbackMsg('正在加载模型...')
		// 加载模型
		doLoadModel(record.id).then(res => {
			if (res && res.modelId) {
				myModel.setFeedbackMsg('加载模型成功')
				router.push('/fscloud/simcontrol')
			}
		})
	}

	// ----- 实际操作函数 -----
	const doLoadModel = (recordId) => {
		if (!checkSelected()) return Promise.resolve()
		simResult.value = '正在加载模型...'
		return fsSimApi.fsSimLoadModel({ id: recordId }).then(res => {
			console.log('loadModel res', res)
			if (res && res.modelId) {
				selectedRecord.value.modelId = res.modelId
				// 更新 myModel store 并订阅 MQTT
				myModel.setModel(res)
				subscribeMqtt(res.modelId)
				simResult.value = '模型加载成功：' + (res.modelName || res.modelFileName)
				let msg = '模型加载成功：' + (res.modelName || res.modelFileName)
				message.success(msg)
			} else {
				simResult.value = '模型加载失败：' + (res.message || '未知错误')
				let msg = '模型加载失败：' + (res.message || '未知错误')
				message.error(msg)
			}
			return res
		}).catch(err => {
			console.error('loadModel error', err)
			let msg = err?.msg || err?.message || '请求异常'
			message.error(msg)
			simResult.value = '模型加载失败：' + (err?.msg || err?.message || '请求异常')
			return null
		})
	}

	const doUnloadModel = () => {
		if (!checkSelected() || !checkModelId()) return
		simResult.value = '正在卸载模型...'
		fsSimApi.fsSimUnloadModel({
			id: selectedRecord.value.id,
			modelId: selectedRecord.value.modelId
		}).then(res => {
			console.log('unloadModel res', res)
			disconnectMqtt()
			myModel.clearModel()
			simResult.value = '模型已卸载'
		}).catch(err => {
			console.error('unloadModel error', err)
			simResult.value = '卸载失败：' + (err?.msg || err?.message || '请求异常')
		})
	}

	const doRunModel = () => {
		if (!checkSelected() || !checkModelId()) return
		simResult.value = '正在启动仿真...'
		fsSimApi.fsSimRunModel({
			id: selectedRecord.value.id,
			modelId: selectedRecord.value.modelId,
			showAnimation: false
		}).then(res => {
			console.log('runModel res', res)
			if (res && res.status && res.status !== 'FAILED') {
				simResult.value = '仿真启动成功'
			} else {
				simResult.value = '仿真启动失败：' + (res.message || '未知错误')
			}
		}).catch(err => {
			console.error('runModel error', err)
			simResult.value = '仿真启动失败：' + (err?.msg || err?.message || '请求异常')
		})
	}

	const doPauseModel = () => {
		if (!checkSelected() || !checkModelId()) return
		simResult.value = '正在暂停仿真...'
		fsSimApi.fsSimPauseModel({
			id: selectedRecord.value.id,
			modelId: selectedRecord.value.modelId
		}).then(res => {
			console.log('pauseModel res', res)
			simResult.value = '仿真已暂停'
		}).catch(err => {
			console.error('pauseModel error', err)
			simResult.value = '暂停仿真失败：' + (err?.msg || err?.message || '请求异常')
		})
	}

	const doResumeModel = () => {
		if (!checkSelected() || !checkModelId()) return
		simResult.value = '正在恢复仿真...'
		fsSimApi.fsSimResumeModel({
			id: selectedRecord.value.id,
			modelId: selectedRecord.value.modelId
		}).then(res => {
			console.log('resumeModel res', res)
			simResult.value = '仿真已恢复'
		}).catch(err => {
			console.error('resumeModel error', err)
			simResult.value = '恢复仿真失败：' + (err?.msg || err?.message || '请求异常')
		})
	}

	const doStopModel = () => {
		if (!checkSelected() || !checkModelId()) return
		simResult.value = '正在停止仿真...'
		fsSimApi.fsSimStopModel({
			id: selectedRecord.value.id,
			modelId: selectedRecord.value.modelId
		}).then(res => {
			console.log('stopModel res', res)
			simResult.value = '仿真已停止'
		}).catch(err => {
			console.error('stopModel error', err)
			simResult.value = '停止仿真失败：' + (err?.msg || err?.message || '请求异常')
		})
	}

	const doResetModel = () => {
		if (!checkSelected() || !checkModelId()) return
		simResult.value = '正在重置仿真...'
		fsSimApi.fsSimResetModel({
			id: selectedRecord.value.id,
			modelId: selectedRecord.value.modelId
		}).then(res => {
			console.log('resetModel res', res)
			simResult.value = '仿真已重置'
		}).catch(err => {
			console.error('resetModel error', err)
			simResult.value = '重置仿真失败：' + (err?.msg || err?.message || '请求异常')
		})
	}
</script>

<style scoped>
	.model-file-container {
		display: flex;
		gap: 16px;
		height: calc(100vh - 200px);
		min-height: 500px;
	}
	.left-panel {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.right-panel {
		width: 880px;
		flex-shrink: 0;
		border: 1px solid #f0f0f0;
		border-radius: 6px;
		padding: 0px 12px;
		background: #fff;
		overflow-y: auto;
	}
	.right-panel.empty-hint {
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.filter-bar {
		margin-bottom: 12px;
		padding: 10px 8px;
		background: #fafafa;
		border-radius: 6px;
		border: 1px solid #f0f0f0;
	}
	/* 表格边框加深 */
	:deep(.ant-table) {
		border-color: #bfbfbf;
	}
	:deep(.ant-table-cell) {
		cursor: default;
		padding-top: 4px !important;
		padding-bottom: 4px !important;
		border-top-color: #bfbfbf !important;
		border-bottom-color: #bfbfbf !important;
	}
		/* 模型信息描述行高缩小、边框加深 */
	:deep(.ant-descriptions-view) {
		border-color: #bfbfbf !important;
	}
	:deep(.ant-descriptions-item) {
		padding-top: 6px !important;
		padding-bottom: 6px !important;
	}
	:deep(.ant-descriptions-row > td) {
		border-color: #bfbfbf !important;
	}
	:deep(.ant-descriptions-item-label) {
		padding-top: 6px !important;
		padding-bottom: 6px !important;
	}
	:deep(.ant-descriptions-item-content) {
		padding-top: 6px !important;
		padding-bottom: 6px !important;
	}
	/* 右侧详情两列等高校 */
	:deep(.detail-desc .ant-descriptions-item-content) {
		height: 36px;
		display: flex;
		align-items: center;
	}
	/* 模型预览缩略图样式 */
	.model-preview-img {
		max-width: 100%;
		max-height: 400px;
		width: auto;
		height: auto;
		display: block;
		margin-top: 12px;
		cursor: pointer;
	}
	/* 左侧表格选中行高亮：直接作用于所有 td，避免个别单元格丢失底色 */
	:deep(.model-row-selected > td) {
		background-color: #bae0ff !important;
	}
</style>
<style>
	/* 全屏预览弹框（非 scoped，portal 渲染需要） */
	.fullscreen-preview-modal .ant-modal {
		max-width: 100vw;
		top: 0;
		padding-bottom: 0;
	}
	.fullscreen-preview-modal .ant-modal-content {
		min-height: 100vh;
		border-radius: 0;
		padding: 16px;
		display: flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
	}
	.fullscreen-preview-modal .ant-modal-body {
		overflow: hidden;
		width: 100vw;
		height: 100vh;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.fullscreen-preview-modal .ant-modal-close {
		z-index: 1000;
	}
	/* 可编辑字段底色：一致浅蓝，不一致浅黄 */
	.field-match .ant-select-selector,
	.field-match {
		background-color: #dbeafe !important;
	}
	.field-changed .ant-select-selector,
	.field-changed {
		background-color: #fff3bf !important;
	}
</style>
