<template>
	<div class="sim-control-container" v-if="loaded">
		<!-- 模型概要信息 -->
		<a-card size="small" class="info-card">
			<a-descriptions :column="3" size="small" bordered>
				<a-descriptions-item label="模型名称">
					<a-tag :color="statusColor">{{ myModel.modelName }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="模型文件">
					<a-tag :color="statusColor">{{ myModel.modelFileName }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="仿真状态">
					<a-tag :color="statusColor">{{ $TOOL.dictTypeData('SIM_STATUS', myModel.simStatus) }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="仿真次数">
					<a-tag :color="statusColor">{{ myModel.record?.simTimes || 0 }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="仿真进度">
					<a-tag :color="statusColor">{{ formatDuration((myModel.simTime || 0)) }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="上个消息到达时间">
					<a-tag :color="statusColor">{{ tsToTime(myModel.simProgress) }}</a-tag>
				</a-descriptions-item>
			</a-descriptions>
		</a-card>

		<!-- 控制按钮区 -->
		<a-card size="small" class="control-card">
			<a-space>
				<a-checkbox v-model:checked="showAnimationEnabled">开动画</a-checkbox>
				<a-button type="primary" size="small" :disabled="btn1Disabled" @click="btn1Click">{{ btn1Text }}</a-button>
				<a-button size="small" :disabled="btn2Disabled" @click="btn2Click">{{ btn2Text }}</a-button>
				<a-button size="small" :disabled="btn3Disabled" @click="btn3Click">{{ btn3Text }}</a-button>
				<a-button danger size="small" @click="doClose">关闭</a-button>
				<a-slider style="width:120px;display:inline-block;margin:0 8px;"
						  :min="1" :max="1000" :step="1"
						  v-model:value="accelerateFactor"
						  @afterChange="onAccelerateChange" />
				<span style="font-size:12px;white-space:nowrap;">{{ accelerateFactor }}x</span>
			</a-space>
			<div class="feedback" v-if="myModel.feedbackMsg" :class="feedbackType">{{ myModel.feedbackMsg }}</div>
		</a-card>

		<!-- 控制台日志 -->
		<a-row class="console-row">
			<a-col :span="12" class="console-col">
				<a-card size="small" class="console-card">
					<a-button danger size="small" class="mt-0 ml-0 mb-2" @click="doClearLog">清除</a-button>
					<div class="console-log" ref="consoleRef">
						<div v-for="log in myModel.consoleLog" :key="log.id" :data-id="log.id" class="log-line" :class="'log-' + (log.level || 'INFO').toLowerCase()">
							<span class="log-time">{{ formatTime(log.timestamp) }}</span>
							<span class="log-level">[{{ log.level || 'INFO' }}]</span>
							<span class="log-msg">{{ formatLogContent(log.content) }}</span>
						</div>
						<div v-if="myModel.consoleLog.length === 0" class="log-empty">暂无日志</div>
					</div>
				</a-card>
			</a-col>
			<a-col :span="12" class="console-col">
				<a-card size="small" class="animation-card" :class="{ 'anim-fullscreen': animFullscreen }">
					<template #title>
						<div class="ml-2 mb-2">
							<span>动画播放</span>
							<a-tag v-if="myModel.animationStreaming" color="green" style="margin-left:4px;">直播 {{ myModel.animationFrameRate }}fps</a-tag>
							<a-checkbox v-if="myModel.animationStreaming" v-model:checked="showHdImage" style="margin-left:8px;" @change="onHdImageChange">显示高清静态图</a-checkbox>
							<a style="float:right; cursor:pointer; font-size:16px;" @click="animFullscreen = !animFullscreen" :title="animFullscreen ? '恢复' : '全屏'">
								{{ animFullscreen ? '🗕' : '🗖' }}
							</a>
						</div>
						<div class="layer-bar" v-if="sortedLayers.length>0">
							<a-tag v-for="layer in sortedLayers" :key="layer.layerId"
								   :color="layer.layerId === myModel.animationActiveLayer ? 'blue' : ''"
								   style="cursor:pointer; margin-bottom:1px;"
								   @click="switchAnimLayer(layer.layerId)">
								{{ layer.layerId === 'root' ? '主模型' : (layer.name || layer.layerId) }}
							</a-tag>
						</div>
					</template>
					<div class="animation-viewport" :class="{ 'svg-viewport': !!svgDataUrl || !!coverImgUrl }">
						<img v-if="myModel.animationFrameUrl && (!showHdImage || !svgDataUrl)" :src="myModel.animationFrameUrl" class="animation-frame" alt="仿真动画"
							 :style="previewImgStyle"
							 @click="onClickImage"
							 @wheel="onPreviewWheel"
							 @mousedown="onPreviewMouseDown"
							 @mousemove="onPreviewMouseMove"
							 @mouseup="onPreviewMouseUp"
							 @mouseleave="onPreviewMouseUp" />
						<img v-else-if="svgDataUrl" :src="svgDataUrl" class="animation-frame" alt="高清静态图"
							 :style="previewImgStyle"
							 @click="onClickImage"
							 @wheel="onPreviewWheel"
							 @mousedown="onPreviewMouseDown"
							 @mousemove="onPreviewMouseMove"
							 @mouseup="onPreviewMouseUp"
							 @mouseleave="onPreviewMouseUp" />
						<img v-else-if="coverImgUrl" :src="coverImgUrl" class="animation-cover" alt="模型封面"
							 :style="previewImgStyle"
							 @click="onClickImage"
							 @wheel="onPreviewWheel"
							 @mousedown="onPreviewMouseDown"
							 @mousemove="onPreviewMouseMove"
							 @mouseup="onPreviewMouseUp"
							 @mouseleave="onPreviewMouseUp" />
						<div v-else class="animation-empty">加载模型后显示仿真动画</div>
					</div>
				</a-card>
			</a-col>
		</a-row>
	</div>

	<!-- 未加载模型时 -->
	<div class="sim-control-empty" v-else>
		<a-empty description="当前没有加载的仿真模型">
			<a-button type="link" @click="goToModelfile">前往模型库, 启动仿真</a-button>
		</a-empty>
	</div>
</template>
<!--
todo: 1 控制台清除按钮，点击后清除所有日志
todo: 2 fix 控制操作的提示信息又是不中确，根据api返回的信息动态更新
-->
<script setup>
import { ref, reactive, computed, onMounted, onActivated, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useMyModelStore } from '@/store/myModel'
import { createMqttClient, disconnectMqtt } from '@/utils/mqttClient'
import fsSimApi from '@/api/fscloud/fsSimApi'
import fsModelFileApi from '@/api/fscloud/fsModelFileApi'
import tool from '@/utils/tool'

const router = useRouter()
const myModel = useMyModelStore()
const consoleRef = ref(null)
const showAnimationEnabled = ref(true)  // 是否开启动画
const animFullscreen = ref(false)        // 动画全屏切换
const accelerateFactor = ref(50)         // 仿真加速因子 1-1000
const showHdImage = ref(false)         // 是否显示高清 SVG 静态图
const svgDataUrl = ref('')               // SVG data URL

// ===== 本地状态持久化 =====
const getStorageKey = () => {
	if (!myModel.modelId) return null
	const userInfo = tool.data.get('USER_INFO') || {}
	return `FS_SIM_STATE_${userInfo.id || 'admin'}_${myModel.modelId}`
}
const saveSimState = () => {
	const key = getStorageKey()
	if (!key) return
	tool.data.set(key, {
		animationEnabled: showAnimationEnabled.value,
		accelerateFactor: accelerateFactor.value,
		activeLayerId: myModel.animationActiveLayer
	})
}
const loadSimState = () => {
	const key = getStorageKey()
	return key ? tool.data.get(key) : null
}

const onAccelerateChange = (val) => {
	if (!myModel.isLoaded || !myModel.modelId) return
	fsSimApi.fsSimSetAccelerateFactor({ modelId: myModel.modelId, accelerateFactor: val })
		.then(() => {
			console.log('setAccelerateFactor:', val)
			saveSimState()
		})
		.catch(err => console.warn('setAccelerateFactor error:', err))
}
const onHdImageChange = async (checked) => {
	if (!myModel.isLoaded || !myModel.modelId) return
	if (checked) {
		svgDataUrl.value = ''  // 清旧 SVG → JPEG 继续显示
		try {
			const res = await fsSimApi.fsSimGetSvgPicture({ modelId: myModel.modelId, layerId: myModel.animationActiveLayer || 'root' })
			const svg = res?.data?.svg
			if (svg) {
				svgDataUrl.value = 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(svg)
			}
		} catch (err) {
			console.error('[getSvgPicture] error:', err)
		}
	} else {
		svgDataUrl.value = ''
	}
}
// 全屏时按 ESC 退出
watch(animFullscreen, (val) => {
	if (val) {
		previewZoom.value = 1
		previewPos.x = 0
		previewPos.y = 0
		document.addEventListener('keydown', onEscKey)
	} else {
		previewZoom.value = 1
		previewPos.x = 0
		previewPos.y = 0
		document.removeEventListener('keydown', onEscKey)
	}
})
const onEscKey = (e) => {
	if (e.key === 'Escape') animFullscreen.value = false
}

// 预览图片缩放/拖动状态（仿 modelfile 的全屏预览交互）
const previewZoom = ref(1)
const previewPos = reactive({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = reactive({ x: 0, y: 0 })
const previewImgStyle = computed(() => ({
	display: 'block',
	margin: '0 auto',
	cursor: animFullscreen.value ? (isDragging.value ? 'grabbing' : 'grab') : 'pointer',
	transform: `translate(${previewPos.x}px, ${previewPos.y}px) scale(${previewZoom.value})`,
	transition: isDragging.value ? 'none' : 'transform 0.15s ease'
}))
// 点击图片进入全屏并重置缩放/平移
const enterFullscreen = () => {
	previewZoom.value = 1
	previewPos.x = 0
	previewPos.y = 0
	animFullscreen.value = true
}
// 仅在恢复态（非全屏）点击图片才进入全屏；全屏态点击不重置视图
const onClickImage = () => {
	if (!animFullscreen.value) enterFullscreen()
}
const onPreviewWheel = (e) => {
	if (!animFullscreen.value) return
	e.preventDefault()
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
	if (!animFullscreen.value) return
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

// ===== 状态计算 =====
const loaded = computed(() => myModel.isLoaded)

const statusColor = computed(() => {
	const map = { SIM_STATUS_WAITING: 'orange', SIM_STATUS_GOING: 'blue', SIM_STATUS_PAUSED: 'gold', SIM_STATUS_FINISHED: 'green' }
	return map[myModel.simStatus] || 'default'
})
const statusLabel = computed(() => {
	const map = { SIM_STATUS_WAITING: '待仿真', SIM_STATUS_GOING: '仿真中', SIM_STATUS_PAUSED: '已暂停', SIM_STATUS_FINISHED: '仿真结束' }
	return map[myModel.simStatus] || myModel.simStatus
})
const progressColor = computed(() => {
	if (myModel.simProgress >= 100) return '#52c41a'
	if (myModel.simProgress > 50) return '#1890ff'
	return '#faad14'
})

// ===== 按钮状态（参照模型库仿真 tab 的 btn2/btn3/btn4） =====
const s = computed(() => myModel.simStatus)

// 按钮 1：启动/暂停/继续
const btn1Text = computed(() => {
	const v = s.value
	if (v === 'SIM_STATUS_GOING') return '暂停'
	if (v === 'SIM_STATUS_PAUSED') return '继续'
	return '启动'
})
const btn1Disabled = computed(() => {
	const v = s.value
	return v === 'SIM_STATUS_NOTYET' || v === 'SIM_STATUS_ALREADY' || !myModel.modelId
})
const btn1Click = () => {
	const t = btn1Text.value
	if (t === '启动') doRun()
	else if (t === '暂停') doPause()
	else if (t === '继续') doResume()
}

// 按钮 2：停止
const btn2Text = computed(() => '停止')
const btn2Disabled = computed(() => {
	const v = s.value
	return v !== 'SIM_STATUS_GOING' && v !== 'SIM_STATUS_PAUSED'
})
const btn2Click = () => doStop()

// 按钮 3：重置
const btn3Text = computed(() => '重置')
const btn3Disabled = computed(() => {
	const v = s.value
	return v !== 'SIM_STATUS_WAITING' && v !== 'SIM_STATUS_FINISHED'
})
const btn3Click = () => doReset()

// ===== 操作 =====
const feedbackType = computed(() => {
	if (!myModel.feedbackMsg) return ''
	return myModel.feedbackMsg.includes('失败') || myModel.feedbackMsg.includes('错误') ? 'feedback-error' : 'feedback-success'
})

const doRun = () => {
	myModel.setFeedbackMsg('正在启动仿真...')
	fsSimApi.fsSimRunModel({ id: myModel.recordId, modelId: myModel.modelId, showAnimation: showAnimationEnabled.value }).then(res => {
		if (res && res.status && res.status !== 'FAILED') {
			if (res.status) myModel.updateStatus(res.status)
			if (res.message) myModel.message = res.message
			if (res.runId) myModel.runId = res.runId
			myModel.setFeedbackMsg('仿真启动成功')
		} else {
			myModel.setFeedbackMsg('仿真启动失败：' + (res.message || '未知错误'))
		}
	}).catch(err => {
		myModel.setFeedbackMsg('仿真启动失败：' + (err?.msg || err?.message || '请求异常'))
	})
}
const doPause = () => {
	myModel.setFeedbackMsg('正在暂停仿真...')
	fsSimApi.fsSimPauseModel({ id: myModel.recordId, modelId: myModel.modelId }).then(res => {
		if (res && res.status && res.status !== 'FAILED') {
			if (res.status) myModel.updateStatus(res.status)
			if (res.message) myModel.message = res.message
			myModel.setFeedbackMsg('仿真已暂停')
		} else {
			myModel.setFeedbackMsg('暂停仿真失败：' + (res.message || '未知错误'))
		}
	}).catch(err => {
		myModel.setFeedbackMsg('暂停仿真失败：' + (err?.msg || err?.message || '请求异常'))
	})
}
const doResume = () => {
	myModel.setFeedbackMsg('正在恢复仿真...')
	fsSimApi.fsSimResumeModel({ id: myModel.recordId, modelId: myModel.modelId }).then(res => {
		if (res && res.status && res.status !== 'FAILED') {
			if (res.status) myModel.updateStatus(res.status)
			if (res.message) myModel.message = res.message
			myModel.setFeedbackMsg('仿真已恢复')
		} else {
			myModel.setFeedbackMsg('恢复仿真失败：' + (res.message || '未知错误'))
		}
	}).catch(err => {
		myModel.setFeedbackMsg('恢复仿真失败：' + (err?.msg || err?.message || '请求异常'))
	})
}
const doStop = () => {
	myModel.setFeedbackMsg('正在停止仿真...')
	fsSimApi.fsSimStopModel({ id: myModel.recordId, modelId: myModel.modelId }).then(res => {
		if (res && res.status && res.status !== 'FAILED') {
			if (res.status) myModel.updateStatus(res.status)
			if (res.message) myModel.message = res.message
			myModel.setFeedbackMsg('仿真已停止')
		} else {
			myModel.setFeedbackMsg('停止仿真失败：' + (res.message || '未知错误'))
		}
	}).catch(err => {
		myModel.setFeedbackMsg('停止仿真失败：' + (err?.msg || err?.message || '请求异常'))
	})
}
const doReset = () => {
	myModel.setFeedbackMsg('正在重置仿真...')
	fsSimApi.fsSimResetModel({ id: myModel.recordId, modelId: myModel.modelId }).then(res => {
		if (res && res.status && res.status !== 'FAILED') {
			if (res.status) myModel.updateStatus(res.status)
			if (res.message) myModel.message = res.message
			myModel.setFeedbackMsg('仿真已重置')
		} else {
			myModel.setFeedbackMsg('重置仿真失败：' + (res.message || '未知错误'))
		}
	}).catch(err => {
		myModel.setFeedbackMsg('重置仿真失败：' + (err?.msg || err?.message || '请求异常'))
	})
}
const doClose = () => {
	myModel.setFeedbackMsg('正在关闭...')
	fsSimApi.fsSimUnloadModel({ id: myModel.recordId, modelId: myModel.modelId }).then(() => {
		disconnectMqtt()
		myModel.clearModel()
		router.push('/fscloud/modelfile')
	}).catch(err => {
		myModel.setFeedbackMsg('关闭失败：' + (err?.msg || err?.message || '请求异常'))
	})
}

// 清除控制台日志
const doClearLog = () => {
	myModel.clearConsoleLog()
	userAtBottom = true
	scrollConsole()
}

const goToModelfile = () => router.push('/fscloud/modelfile')

// ===== 日志自动滚到底部 =====
let scrollTimer = null
let userAtBottom = true  // 用户是否在底部，默认在底部

const isNearBottom = () => {
	const el = consoleRef.value
	if (!el) return true
	return el.scrollHeight - el.scrollTop - el.clientHeight < 20
}

const onConsoleScroll = () => {
	userAtBottom = isNearBottom()
}

const scrollConsole = () => {
	if (!userAtBottom) return
	nextTick(() => {
		if (consoleRef.value) consoleRef.value.scrollTop = consoleRef.value.scrollHeight
	})
}

// 订阅 MQTT（内部已处理重复调用安全）
const subscribeMqtt = () => {
	const userInfo = tool.data.get('USER_INFO') || {}
	const userId = userInfo.id || 'admin'
	createMqttClient(userId, myModel.modelId, {
		onConnect: () => {
			console.log('MQTT----onConnect')
			myModel.setMqttConnected(true)
		},
		onDisconnect: () => {
			console.log('MQTT----onDisconnect')
			myModel.setMqttConnected(false)
		},
		onStatus: (data) => {
			console.log('MQTT----onStatus')
			const key = data.status?.toLowerCase()
			const mcpStatus = { running: 'RUNNING', paused: 'PAUSED', finished: 'FINISHED', loaded: 'LOADED', stopped: 'STOPPED', reset: 'RESET', failed: 'FAILED' }[key]
			console.log('onStatus, data.status:', data.status, 'mapped mcpStatus:', mcpStatus)
			if (mcpStatus) { myModel.updateStatus(mcpStatus) }
		},
		onProgress: (data) => {
			//console.log('MQTT----onProgress: ', data)
			myModel.updateProgress(data.timestamp, data.simTime)
		},
		onConsole: (data) => {
			console.log('MQTT----onConsole')
			const el = consoleRef.value
			const wasAtBottom = userAtBottom
			// 非底部时，记录当前视口内第一条可见日志行作为锚点
			let anchorId = null
			let anchorOffset = 0  // 锚点元素顶部相对 scrollTop 的偏移
			if (el && !wasAtBottom) {
				const lines = el.querySelectorAll('.log-line')
				for (const line of lines) {
					const top = line.offsetTop
					const bottom = top + line.offsetHeight
					if (bottom > el.scrollTop) {  // 第一条可见行
						anchorId = line.getAttribute('data-id')
						anchorOffset = top - el.scrollTop
						break
					}
				}
			}

			myModel.addConsoleLog(data.level, data.content, data.timestamp)

			nextTick(() => {
				if (!el) return
				if (wasAtBottom) {
					el.scrollTop = el.scrollHeight  // 在底部 → 自动跟底
				} else if (anchorId != null) {
					// 保持锚点行在视口中的相对位置不变，用户看到的内容静止
					const anchorEl = el.querySelector(`.log-line[data-id="${anchorId}"]`)
					if (anchorEl) {
						el.scrollTop = anchorEl.offsetTop - anchorOffset
					}
				}
			})
		},
		// 动画帧
		onAnimationFrame: (blob) => {
			if (frameBlobUrl) URL.revokeObjectURL(frameBlobUrl)
			frameBlobUrl = URL.createObjectURL(blob)
			myModel.animationFrameUrl = frameBlobUrl
		},
		onAnimationState: (data) => {
			myModel.animationStreaming = !!data.animationStreaming
			if (data.frameRate) myModel.animationFrameRate = data.frameRate
		},
		onAnimationLayers: (data) => {
			const layers = data.layers || data || []
			if (Array.isArray(layers)) myModel.animationLayers = layers
		},
		onAnimationFrameMeta: (data) => {
			if (data.sequence) myModel.animationFrameSeq = data.sequence
		}
	})
}

// 页面切回前台时，若模型已加载但 MQTT 断连则重连
const onVisibilityChange = () => {
	console.log('-------> visibilitychange:', document.visibilityState)
	if (document.visibilityState === 'visible' && myModel.isLoaded && !myModel.mqttConnected) {
		console.log('Page visible, reconnecting MQTT...')
		subscribeMqtt()
	}
}

// 初始化：恢复模型状态 + 订阅 MQTT
const initPage = async () => {
	if (!myModel.isLoaded) {
		const data = await fsSimApi.fsSimGetLoadedModel().catch(() => null)
		console.log('getLoadedModel res', data)
		if (data && data.modelId) {
			myModel.setModel(data)
		} else {
			return
		}
	}
	subscribeMqtt()
	loadAnimationLayers()
	// 从 localStorage 恢复本地状态
	const saved = loadSimState()
	if (saved) {
		showAnimationEnabled.value = !!saved.animationEnabled
		accelerateFactor.value = saved.accelerateFactor ?? 50
		if (saved.activeLayerId && saved.activeLayerId !== 'root') {
			switchAnimLayer(saved.activeLayerId)
		}
	}
	// 日志滚动轮询（先清除旧定时器，避免泄漏）
	if (scrollTimer) clearInterval(scrollTimer)
	scrollTimer = setInterval(scrollConsole, 2000)
	// 监听控制台日志滚动事件
	if (consoleRef.value) {
		consoleRef.value.removeEventListener('scroll', onConsoleScroll)
		consoleRef.value.addEventListener('scroll', onConsoleScroll, { passive: true })
	}
	userAtBottom = true
	// 监听 Tab 切换回前台（先移除旧监听器，避免重复绑定）
	document.removeEventListener('visibilitychange', onVisibilityChange)
	document.addEventListener('visibilitychange', onVisibilityChange)
}

onMounted(() => { initPage() })
onActivated(() => { initPage() })

onUnmounted(() => {
	if (scrollTimer) clearInterval(scrollTimer)
	if (consoleRef.value) consoleRef.value.removeEventListener('scroll', onConsoleScroll)
	document.removeEventListener('visibilitychange', onVisibilityChange)
})

// 监听"开动画"复选框变化，实时开关 MCP 引擎动画
watch(showAnimationEnabled, (val) => {
	if (!myModel.isLoaded || !myModel.modelId) return
	fsSimApi.fsSimSetAnimationEnabled({ modelId: myModel.modelId, enabled: val })
		.then(() => {
			console.log('setAnimationEnabled:', val)
			saveSimState()
		})
		.catch(err => console.warn('setAnimationEnabled error:', err))
})

const formatTime = (ts) => {
	if (!ts) return ''
	const d = new Date(ts)
	const hh = String(d.getHours()).padStart(2, '0')
	const mm = String(d.getMinutes()).padStart(2, '0')
	const ss = String(d.getSeconds()).padStart(2, '0')
	const ms = String(d.getMilliseconds()).padStart(3, '0')
	return `${hh}:${mm}:${ss}.${ms}`
}

// 输入为毫秒（ms），输出 “x天 HH:MM:SS”，不足 1 天不显示天
const formatDuration = (ms) => {
	//console.log('------------ formatDuration ms: ', ms)
	if (ms == null) return '00:00:00'
	ms = Math.floor(ms)
	let sec = Math.floor(ms / 1000)
	const days = Math.floor(sec / 86400)
	let rem = sec % 86400
	const hh = String(Math.floor(rem / 3600)).padStart(2, '0')
	rem = rem % 3600
	const mm = String(Math.floor(rem / 60)).padStart(2, '0')
	const ss = String(rem % 60).padStart(2, '0')
	const hms = `${hh}:${mm}:${ss}`
	return days > 0 ? `${days}天 ${hms}` : hms
}

function tsToTime(ts) {
	// 兼容秒/毫秒
	const t = ts.toString().length === 10 ? ts * 1000 : ts;
	const d = new Date(t);
	const pad = n => String(n).padStart(2, '0');
	const ms = String(d.getMilliseconds()).padStart(3, '0');
	return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${ms}`;
}


// 格式化控制台日志：\r\n / \n → 换行 + 8 空格，\t → 8 空格
const formatLogContent = (content) => {
	if (!content) return ''
	return content.replace(/\r\n/g, '\n').replace(/\n/g, '\n        ').replace(/\t/g, '        ')
}

// ===== 动画播放 =====
const coverImgUrl = computed(() => {
	if (!myModel.record?.image || !myModel.record?.userId) return ''
	const img = myModel.record.image.replace(/\.jpg$/i, '.svg')
	return `/static/uploaded/${myModel.record.userId}/${img}`
})

const sortedLayers = computed(() => {
	const layers = myModel.animationLayers || []
	const root = layers.find(l => l.layerId === 'root')
	const others = layers.filter(l => l.layerId !== 'root')
	return root ? [root, ...others] : others
})

const switchAnimLayer = async (layerId) => {
	if (!myModel.modelId || !layerId) return
	try {
		await fsSimApi.fsSimSelectAnimationLayer({ modelId: myModel.modelId, layerId, frameRate: 5 })
		myModel.animationActiveLayer = layerId
		saveSimState()
		// 高清模式下重新取新层的 SVG
		if (showHdImage.value) await onHdImageChange(true)
	} catch (err) {
		console.error('switchAnimLayer error', err)
	}
}

const loadAnimationLayers = async () => {
	if (!myModel.modelId) return
	try {
		const res = await fsSimApi.fsSimListAnimationLayers({ modelId: myModel.modelId })
		if (res?.data?.layers) myModel.animationLayers = res.data.layers
	} catch (err) {
		console.warn('loadAnimationLayers error', err)
	}
}

let frameBlobUrl = null
</script>

<style scoped>
.sim-control-container { padding: 16px; display: flex; flex-direction: column; gap: 12px; height: calc(100vh - 110px); }
.info-card { flex-shrink: 0; }
.control-card { flex-shrink: 0; }
.console-row { flex: 1 1 auto; min-height: 0; flex-wrap: nowrap; align-items: stretch; }
.console-col { display: flex; min-height: 0; overflow: hidden; }
.console-card { flex: 1 1 auto; min-height: 0; display: flex; flex-direction: column; }
.console-card :deep(.ant-card-body) { flex: 1 1 auto; min-height: 0; overflow: hidden; padding: 12px; display: flex; flex-direction: column; }
.console-card :deep(.ant-card-head) { padding: 0 12px !important; }
.console-card :deep(.ant-card-head-title) { padding: 0 !important; }
.console-log { flex: 1 1 auto; min-height: 0; overflow-y: auto; font-family: 'Courier New', monospace; font-size: 12px; line-height: 1.5; background: #1e1e1e; color: #d4d4d4; padding: 8px; border-radius: 4px; }
.log-line { white-space: nowrap; }
.log-time { color: #888; margin-right: 8px; }
.log-level { margin-right: 8px; font-weight: bold; }
.log-info .log-level { color: #6bc; }
.log-warn .log-level { color: #e8c547; }
.log-error .log-level { color: #f44747; }
.log-msg { color: #d4d4d4; white-space: pre-wrap; word-break: break-all; }
.log-empty { color: #666; text-align: center; padding: 40px 0; }
.sim-control-empty { display: flex; justify-content: center; align-items: center; height: calc(100vh - 200px); }
.feedback { margin-top: 8px; font-size: 13px; padding: 4px 8px; border-radius: 4px; }
.feedback-success { color: #389e0d; background: #f6ffed; border: 1px solid #b7eb8f; }
.feedback-error { color: #cf1322; background: #fff2f0; border: 1px solid #ffccc7; }
/* animation card */
.animation-card { display: flex; flex-direction: column; }
.animation-card :deep(.ant-card-head-title) { overflow: visible; }
.animation-card :deep(.ant-card-body) { flex: 1; min-height: 0; overflow: hidden; padding: 12px; display: flex; align-items: center; justify-content: center; }
.layer-bar { display: inline-flex; flex-wrap: wrap; gap: 2px; max-height: 48px; overflow-y: auto; margin-left: 8px; vertical-align: middle; }
.animation-viewport { width: 100%; min-height: 200px; background: #000; display: flex; align-items: center; justify-content: center; border-radius: 4px; overflow: hidden; }
.animation-frame, .animation-cover { max-width: 100%; max-height: 400px; object-fit: contain; }
.animation-empty { color: #888; font-size: 14px; padding: 60px 0; }
.svg-viewport { background: #e8e8e8 !important; }
/* 全屏模式 */
.anim-fullscreen { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; z-index: 9999; background: #000; }
.anim-fullscreen :deep(.ant-card-body) { height: calc(100vh - 80px); display: flex; align-items: stretch; justify-content: stretch; padding: 0; }
.anim-fullscreen .animation-viewport { width: 100%; height: 100%; min-height: 0; border-radius: 0; }
.anim-fullscreen .animation-frame, .anim-fullscreen .animation-cover { width: 100%; height: 100%; max-width: none; max-height: none; object-fit: contain; }
.anim-fullscreen .layer-bar .ant-tag { color: #fff !important; background: rgba(255,255,255,0.15) !important; border-color: rgba(255,255,255,0.3) !important; }
.anim-fullscreen .layer-bar .ant-tag-blue { color: #fff !important; background: #1890ff !important; border-color: #1890ff !important; }
.anim-fullscreen :deep(.ant-card-head) { color: #fff !important; border-color: rgba(255,255,255,0.2) !important; }
.anim-fullscreen :deep(.ant-checkbox-wrapper) { color: #fff !important; }
</style>
