<template>
	<div class="ep-container">
		<!-- 模型概要信息 -->
		<a-card size="small" class="info-card">
			<a-descriptions :column="3" size="small" bordered>
				<a-descriptions-item label="模型名称">
					<a-tag color="blue">{{ myModel.modelName || '-' }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="模型文件">
					<a-tag color="blue">{{ myModel.modelFileName || '-' }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="总仿真时长">
					<a-tag color="green">{{ formatDuration(myModel.simTime || 0) }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="modelId">
					<a-tag color="purple">{{ myModel.modelId || '-' }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="runId">
					<a-tag color="purple">{{ myModel.runId || '-' }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="实时事件帧">
					<a-tag color="orange">{{ myModel.events.length }} / 100</a-tag>
				</a-descriptions-item>
			</a-descriptions>
		</a-card>

		<!-- 1) 时间轴 slider：0 ~ 总仿真时长，timeWindowCursor 保存当前位置对应的时间点 -->
		<a-card size="small" class="slider-card">
			<div class="cursor-label">
				时间轴光标 timeWindowCursor：<b>{{ formatDuration(timeWindowCursor) }}</b> <span class="ml-8">(</span><b class="text-red-600">注意：FS引擎只保留5分钟左右的对象位置快照</b><span>)</span>
			</div>
			<a-slider
				:min="0"
				:max="sliderMax"
				:step="1000"
				:tip-formatter="formatDuration"
				v-model:value="timeWindowCursor" />
		</a-card>

		<!-- 操作按钮 -->
		<a-card size="small" class="op-card">
			<a-space wrap>
				<a-button type="primary" :disabled="!myModel.isLoaded" @click="doGetSnapshot">取位置快照</a-button>
				<a-button type="primary" ghost :disabled="!myModel.isLoaded" @click="doStepForward">步进播放(+10s)</a-button>
				<a-button :disabled="!myModel.isLoaded" @click="doStepBack">步进回退(-10s)</a-button>
				<a-button :type="subscribing ? 'danger' : 'default'" :disabled="!myModel.isLoaded" @click="toggleSubscribe">
					{{ subscribing ? '停止实时订阅' : '实时事件订阅' }}
				</a-button>
			</a-space>
		</a-card>

		<!-- 2) 3) 4) 结果显示区（pre 标签） -->
		<a-card size="small" class="result-card" :title="lastOp">
			<a-row>
				<a-col :span="12">
					<pre class="result-pre">{{ displayText || '暂无数据，请点击上方按钮获取。' }}</pre>
				</a-col>
				<a-col :span="12">
					<pre class="result-pre">{{ myModel.events.length > 0 ? JSON.stringify(myModel.events[0], null, 2) : '暂无实时事件数据。' }}</pre>
				</a-col>
			</a-row>
		</a-card>

		<!-- 页面尾部：保留原始 todo 说明 -->
		<pre class="todo-pre">
todo —— 数据分析:
1) 一个slider组件（0~此时仿真进度/或总仿真时长）, 滑动时， 将对应的时间点显示在slider上方，变用变量 timeWindowCursor 保存当前slider位置对应的时间点。
2) 一个取位置快照按钮，点击后， 按slider位置调用MCP接口(查询时间轴对象位置快照 getRemoteSimulationObjectPositionSnapshot)获取当前仿真模型对应时间的对象位置快照；
3）一个步进播放按钮，点击后调用MCP接口（时间窗口内的仿真事件 getRemoteSimulationEvents）获取后序10秒(时间窗口)内的仿真事件，并在页面上用pre标签显示这次获取的事件。
  再次点，获取下一个10秒的事件；timeWindowCursor += 10000;
4）一个步进回退按钮，点击后调用MCP接口（时间窗口内的仿真事件 getRemoteSimulationEvents）获取前10秒(时间窗口)内的仿真事件，并在页面上用pre标签显示这次获取的事件。
再次点，获取下一个10秒的事件；timeWindowCursor -= 10000;
5) 一个实时事件订阅按钮，点击后，接收MQTT主题“factory/sim/{username}/{modelId}/events”，并将接收到的消息保存在store(myModel的events)中，
   最多保存100个消息用于分析，之后不再保存。
		</pre>
	</div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useMyModelStore } from '@/store/myModel'
import fsSimApi from '@/api/fscloud/fsSimApi'
import tool from '@/utils/tool'

const myModel = useMyModelStore()

// 时间轴光标（仿真毫秒），保存当前 slider 位置对应的时间点
const timeWindowCursor = ref(0)

// 显示区内容与标题
const displayText = ref('')
const lastOp = ref('')

// 实时订阅状态
const subscribing = ref(false)

// slider 最大值：仿真总时长（或当前进度），至少 1000ms 避免 slider 异常
const sliderMax = computed(() => {
	const t = myModel.simTime || 0
	return t > 0 ? t : 1000
})

// 格式化仿真时长（毫秒 -> HH:MM:SS.mmm）
const formatDuration = (ms) => {
	if (ms === null || ms === undefined) ms = 0
	const total = Math.max(0, Math.floor(ms))
	const h = Math.floor(total / 3600000)
	const m = Math.floor((total % 3600000) / 60000)
	const s = Math.floor((total % 60000) / 1000)
	const milli = total % 1000
	return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(milli).padStart(3, '0')}`
}

// 获取当前登录用户名（作为 MQTT 主题中的 username）
const getUserId = () => {
	const userInfo = tool.data.get('USER_INFO') || {}
	return userInfo.id || 'admin'
}

// 2) 取位置快照：按 slider 位置调用 MCP 接口获取对象位置快照
const doGetSnapshot = async () => {
	if (!myModel.isLoaded) return
	lastOp.value = '取位置快照'
	displayText.value = '请求中...'
	try {
		const res = await fsSimApi.fsSimGetObjectPositionSnapshot({
			modelId: myModel.modelId,
			runId: myModel.runId || '',
			layerId: myModel.animationActiveLayer || 'root',
			simTime: timeWindowCursor.value
		})
		displayText.value = JSON.stringify(res, null, 2)
	} catch (err) {
		displayText.value = '取位置快照失败: ' + (err?.message || err)
	}
}

// 3) 步进播放：获取 timeWindowCursor 起后 10 秒窗口内的仿真事件，然后光标后移
const doStepForward = async () => {
	if (!myModel.isLoaded) return
	lastOp.value = '步进播放（后10秒）'
	displayText.value = '请求中...'
	try {
		const res = await fsSimApi.fsSimGetSimulationEvents({
			modelId: myModel.modelId,
			runId: myModel.runId || '',
			startSimTime: timeWindowCursor.value,
			duration: 10000,
			offset: 0,
			limit: 500
		})
		displayText.value = JSON.stringify(res, null, 2)
		timeWindowCursor.value += 10000
		if (timeWindowCursor.value > sliderMax.value) {
			timeWindowCursor.value = sliderMax.value
		}
	} catch (err) {
		displayText.value = '获取仿真事件失败: ' + (err?.message || err)
	}
}

// 4) 步进回退：光标前移 10 秒，获取前 10 秒窗口内的仿真事件
const doStepBack = async () => {
	if (!myModel.isLoaded) return
	lastOp.value = '步进回退（前10秒）'
	displayText.value = '请求中...'
	try {
		timeWindowCursor.value -= 10000
		if (timeWindowCursor.value < 0) {
			timeWindowCursor.value = 0
		}
		const res = await fsSimApi.fsSimGetSimulationEvents({
			modelId: myModel.modelId,
			runId: myModel.runId || '',
			startSimTime: timeWindowCursor.value,
			duration: 10000,
			offset: 0,
			limit: 500
		})
		displayText.value = JSON.stringify(res, null, 2)
	} catch (err) {
		displayText.value = '获取仿真事件失败: ' + (err?.message || err)
	}
}

// 5) 实时事件订阅：先让 FS 开始发布 events，再订阅 MQTT events -> store.events（最多 100 条）
// 注意：本页面自建独立 MQTT 连接（不复用 utils/mqttClient 的模块级单例），
// 避免与仿真控制页等页面的实时连接互相掐断。
let mqttClient = null

const subscribeEvents = async () => {
	const userId = getUserId()
	const modelId = myModel.modelId
	const runId = myModel.runId
	if (!userId || !modelId || !runId) {
		myModel.setFeedbackMsg('请先在仿真控制页启动仿真后再订阅事件')
		subscribing.value = false
		return
	}
	// 1) 先让 FS 开始发布事件消息（MCP 工具 setRemoteSimulationEventPublishing）
	try {
		await fsSimApi.fsSimSetEventPublishing({ modelId, enabled: true, publishIntervalMs: 500 })
		console.log('setEventPublishing enabled ok')
	} catch (err) {
		myModel.setFeedbackMsg('开启事件发布失败：' + (err?.message || err))
		subscribing.value = false
		return
	}
	// 2) 建立 MQTT 连接订阅 events 主题
	if (typeof window.mqtt === 'undefined') {
		subscribing.value = false
		return
	}
	const brokerUrl = import.meta.env.VITE_MQTT_BROKER_URL || 'ws://127.0.0.1:8083/mqtt'
	const client = window.mqtt.connect(brokerUrl, { protocolVersion: 5 })
	mqttClient = client
	subscribing.value = true   // 订阅中（连接建立前也视为订阅中，防止连点重复创建）
	client.on('connect', () => {
		console.log('MQTT----eventplayback connected: ', `factory/sim/${userId}/${modelId}/events`)
		client.subscribe(`factory/sim/${userId}/${modelId}/events`, { qos: 0 }, err => {
			if (err) console.error('MQTT subscribe error:', err)
		})
	})
	client.on('message', (topic, payload) => {
		console.log('MQTT----eventplayback onEvents', topic)
		if (!topic.endsWith('/events')) return
		let data
		try {
			data = JSON.parse(payload.toString())
		} catch (e) {
			console.warn('MQTT events parse error:', e)
			return
		}
		// 若事件消息携带 runId，则只接收当前运行的事件
		if (data.runId && runId && data.runId !== runId) return
		myModel.addEvent(topic, data)
	})
	client.on('close', () => {
		console.log('MQTT----eventplayback closed')
		subscribing.value = false
	})
	client.on('offline', () => {
		console.log('MQTT----eventplayback offline')
		subscribing.value = false
	})
	client.on('error', err => {
		console.error('MQTT error:', err)
	})
}

const stopSubscribe = async () => {
	// 1) 先让 FS 停止发布事件消息（MCP 工具 setRemoteSimulationEventPublishing）
	if (myModel.isLoaded) {
		try {
			await fsSimApi.fsSimSetEventPublishing({ modelId: myModel.modelId, enabled: false })
			console.log('setEventPublishing disabled ok')
		} catch (err) {
			console.warn('关闭事件发布失败：', err?.message || err)
		}
	}
	// 2) 断开 MQTT 连接
	if (mqttClient) {
		try {
			mqttClient.end(true)
		} catch (e) {
			console.warn('MQTT disconnect error:', e)
		}
		mqttClient = null
	}
	subscribing.value = false
}

const toggleSubscribe = () => {
	if (!myModel.isLoaded) return
	if (subscribing.value) {
		stopSubscribe()
		return
	}
	myModel.clearEvents()
	subscribeEvents()
}

// 页面初始化：恢复当前用户已加载的模型状态
const initPage = async () => {
	if (!myModel.isLoaded) {
		const data = await fsSimApi.fsSimGetLoadedModel().catch(() => null)
		console.log('eventplayback getLoadedModel res', data)
		if (data && data.modelId) {
			myModel.setModel(data)
		}
	}
}

onMounted(() => {
	initPage()
})

onUnmounted(() => {
	// 页面离开时无条件停止实时订阅，避免残留连接
	stopSubscribe()
})
</script>

<style scoped lang="less">
.ep-container {
	padding: 12px;
}

.info-card {
	margin-bottom: 12px;
}

.slider-card {
	margin-bottom: 12px;
}

.cursor-label {
	margin-bottom: 8px;
	font-size: 13px;
}

.op-card {
	margin-bottom: 12px;
}

.result-card {
	margin-bottom: 12px;
}

.result-pre {
	max-height: 400px;
	overflow: auto;
	background: #f6f6f6;
	padding: 8px;
	font-size: 12px;
	white-space: pre-wrap;
	word-break: break-all;
	margin-bottom: 0;
}

.todo-pre {
	background: #fffbe6;
	border: 1px dashed #faad14;
	padding: 8px;
	font-size: 12px;
	white-space: pre-wrap;
	word-break: break-all;
	color: #ad6800;
}
</style>
