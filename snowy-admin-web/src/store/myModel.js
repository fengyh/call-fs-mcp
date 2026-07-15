/**
 *  Copyright [2022] [https://www.xiaonuo.vip]
 *	Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *	1.请不要删除和修改根目录下的LICENSE文件。
 *	2.请不要删除和修改Snowy源码头部的版权声明。
 *	3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 *	4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 *	5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 *	6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
import { defineStore } from 'pinia'
import fsModelFileApi from "@/api/fscloud/fsModelFileApi";

const MAX_CONSOLE_LOG = 1000

// 仿真模型实时状态 store（由前端 MQTT 推送更新）
export const useMyModelStore = defineStore('myModel', () => {
	// ===== state =====
	const modelId = ref('')
	const recordId = ref(null)
	const modelName = ref('')
	const modelFileName = ref('')
	const simStatus = ref('')        // 本地仿真状态
	const simTime = ref(0)
	const runId = ref('')
	const simProgress = ref(0)
	const message = ref('')
	const mqttConnected = ref(false)
	const consoleLog = ref([])       // { level, content, timestamp }[]
	const feedbackMsg = ref('')      // 操作反馈消息
	const record = ref(null)	// 模型库记录

	// 参数表数据
	const parameterTables = ref([])        // 参数表列表（from listParameterTables）
	const snapshotRaw = ref(null)          // 参数快照原始内容（不可变）
	const snapshotData = ref({})           // 参数快照按表名解析 { tableName: rows[][] }（可修改）
	const snapshotBackup = ref({})         // 参数快照备份（用于取消恢复）
	const selectedTableName = ref('')      // 当前选中的表名

	// ===== getters =====
	const isLoaded = computed(() => !!modelId.value)

	// ===== actions =====
	// 设置模型（从 getLoadedModel 或 loadModel 返回的数据填充）
	async function setModel(data) {
		if (!data) return
		modelId.value = data.modelId || ''
		recordId.value = data.recordId || null
		modelName.value = data.modelName || '';
		modelFileName.value = data.modelFileName || ''
		simStatus.value = data.status || ''
		simTime.value = data.simTime ?? 0
		runId.value = data.runId || ''
		simProgress.value = data.simProgress ?? 0
		message.value = data.message || ''

		if (data.recordId) {
			const recordDetail = await fsModelFileApi.fsModelFileDetail({id: data.recordId}).catch(() => null)
			if (recordDetail) setRecord(recordDetail)
		}
	}

	// 设置模块库记录
	function setRecord(recordDetail) {
		console.log("************** setRecord", recordDetail)
		record.value = recordDetail
		if (modelName.value===''){ modelName.value = recordDetail.name || '' }
		if (modelFileName.value===''){ modelFileName.value = recordDetail.model || '' }
	}

	// 清空模型
	function clearModel() {
		modelId.value = ''
		recordId.value = null
		modelName.value = ''
		modelFileName.value = ''
		simStatus.value = ''
		simTime.value = 0
		runId.value = ''
		simProgress.value = 0
		message.value = ''
		consoleLog.value = []
		feedbackMsg.value = ''
		mqttConnected.value = false
		clearSnapshot()
	}

	// 清除控制台日志
	function clearConsoleLog() {
		consoleLog.value = []
	}

	// 更新 MQTT 连接状态
	function setMqttConnected(connected) {
		mqttConnected.value = connected
	}

	// 处理 status 消息
	function updateStatus(mcpStatus) {
		console.log('updateStatus [store]', mcpStatus)
		simStatus.value = mcpStatus
		// 映射本地状态
		const map = {
			LOADED: 'SIM_STATUS_WAITING',
			RUNNING: 'SIM_STATUS_GOING',
			PAUSED: 'SIM_STATUS_PAUSED',
			FINISHED: 'SIM_STATUS_FINISHED',
			STOPPED: 'SIM_STATUS_FINISHED',
			FAILED: 'SIM_STATUS_FINISHED',
		}
		simStatus.value = map[mcpStatus] || ''
	}

	// 处理 progress 消息
	function updateProgress(timestamp, st) {
		if (timestamp !== undefined && timestamp !== null) {
			simProgress.value = timestamp
		}
		if (st !== undefined && st !== null) {
			simTime.value = st
		}
	}

	// 追加控制台日志
	let consoleLogSeq = 0   // 自增序号，作为稳定 id 用于滚动锚定
	function addConsoleLog(level, content, timestamp) {
		const MAX_LINES = 10
		let text = content || ''
		// 截断超长 content：最多保留 10 行
		const lines = text.split(/\r?\n/)
		if (lines.length > MAX_LINES) {
			text = lines.slice(0, MAX_LINES).join('\n') + '\n{...以下省略...}'
		}
		const entry = { id: ++consoleLogSeq, level: level || 'INFO', content: text, timestamp: timestamp || Date.now() }
		const list = consoleLog.value
		list.push(entry)
		if (list.length > MAX_CONSOLE_LOG) {
			list.splice(0, list.length - MAX_CONSOLE_LOG)
		}
	}

	// 设置操作反馈消息
	function setFeedbackMsg(msg) {
		feedbackMsg.value = msg
	}

	// 清除参数快照数据
	function clearSnapshot() {
		parameterTables.value = []
		snapshotRaw.value = null
		snapshotData.value = {}
		snapshotBackup.value = {}
		selectedTableName.value = ''
	}

	/** 解析快照数据并存入 store */
	function parseAndSetSnapshot(raw) {
		if (!raw) {
			clearSnapshot()
			return
		}
		snapshotRaw.value = JSON.parse(JSON.stringify(raw))
		const parsed = {}
		const tables = raw.tables || []
		for (const table of tables) {
			const name = table.tableName || table.name || ''
			if (!name) continue
			parsed[name] = {
				columns: (table.columns || []).map(col => ({ ...col })),
				rows: (table.rows || []).map(row => [...(row || [])])
			}
		}
		snapshotData.value = parsed
		snapshotBackup.value = JSON.parse(JSON.stringify(parsed))
		const keys = Object.keys(parsed)
		selectedTableName.value = keys.length > 0 ? keys[0] : ''
	}

	return {
		modelId, recordId, modelName, modelFileName, simStatus,
		runId, simTime, simProgress, message, mqttConnected, consoleLog, feedbackMsg,
		isLoaded, record,
		parameterTables, snapshotRaw, snapshotData, snapshotBackup, selectedTableName,
		setModel, clearModel, clearConsoleLog, setMqttConnected, updateStatus, updateProgress, addConsoleLog, setRecord, setFeedbackMsg,
		clearSnapshot, parseAndSetSnapshot
	}
})
