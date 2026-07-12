<template>
	<div class="sim-control-container" v-if="loaded">
		<!-- 模型概要信息 -->
		<a-card size="small" class="info-card">
			<a-descriptions :column="3" size="small" bordered>
				<a-descriptions-item label="模型名称">{{ myModel.modelName }}</a-descriptions-item>
				<a-descriptions-item label="模型文件">{{ myModel.modelFileName }}</a-descriptions-item>
				<a-descriptions-item label="仿真状态">
					<a-tag :color="statusColor">{{ $TOOL.dictTypeData('SIM_STATUS', myModel.simStatus) }}</a-tag>
				</a-descriptions-item>
				<a-descriptions-item label="仿真次数">{{ myModel.record?.simTimes || 0 }}</a-descriptions-item>
				<a-descriptions-item label="仿真进度">
					{{ formatDuration(myModel.simTime) }}
				</a-descriptions-item>
				<a-descriptions-item label="仿真时间">{{ myModel.simTime }} μs</a-descriptions-item>
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
			</a-space>
			<div class="feedback" v-if="myModel.feedbackMsg" :class="feedbackType">{{ myModel.feedbackMsg }}</div>
		</a-card>

		<!-- 控制台日志 -->
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
	import { ref, computed, onMounted, onActivated, onUnmounted, nextTick } from 'vue'
	import { useRouter } from 'vue-router'
	import { useMyModelStore } from '@/store/myModel'
	import { createMqttClient, disconnectMqtt } from '@/utils/mqttClient'
	import fsSimApi from '@/api/fscloud/fsSimApi'
	import fsModelFileApi from '@/api/fscloud/fsModelFileApi'
	import tool from '@/utils/tool'

	const router = useRouter()
	const myModel = useMyModelStore()
	const consoleRef = ref(null)
	const showAnimationEnabled = ref(false)  // 是否开启动画

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
				console.log('MQTT----onProgress: ', data)
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

	const formatTime = (ts) => {
		if (!ts) return ''
		const d = new Date(ts)
		const hh = String(d.getHours()).padStart(2, '0')
		const mm = String(d.getMinutes()).padStart(2, '0')
		const ss = String(d.getSeconds()).padStart(2, '0')
		const ms = String(d.getMilliseconds()).padStart(3, '0')
		return `${hh}:${mm}:${ss}.${ms}`
	}

	// 将仿真时间（微秒）格式化为 HH:MM:SS.mmm（精确到毫秒）
	const formatDuration = (us) => {
		if (us == null) return '00:00:00.000'
		let ms = Math.floor(us / 1000)
		const mmm = String(ms % 1000).padStart(3, '0')
		ms = Math.floor(ms / 1000)
		const ss = String(ms % 60).padStart(2, '0')
		ms = Math.floor(ms / 60)
		const mm = String(ms % 60).padStart(2, '0')
		const hh = String(Math.floor(ms / 60)).padStart(2, '0')
		return `${hh}:${mm}:${ss}.${mmm}`
	}


	// 格式化控制台日志：\r\n / \n → 换行 + 8 空格，\t → 8 空格
	const formatLogContent = (content) => {
		if (!content) return ''
		return content.replace(/\r\n/g, '\n').replace(/\n/g, '\n        ').replace(/\t/g, '        ')
	}
</script>

<style scoped>
	.sim-control-container { padding: 16px; display: flex; flex-direction: column; gap: 12px; height: calc(100vh - 140px); }
	.info-card { flex-shrink: 0; }
	.control-card { flex-shrink: 0; }
	.console-card { flex: 1; min-height: 0; display: flex; flex-direction: column; }
	.console-card :deep(.ant-card-body) { flex: 1; overflow: hidden; padding: 12px; }
	.console-card :deep(.ant-card-head) { padding: 0 12px !important; }
	.console-card :deep(.ant-card-head-title) { padding: 0 !important; }
	.console-log { height: 94%; overflow-y: auto; font-family: 'Courier New', monospace; font-size: 12px; line-height: 1.5; background: #1e1e1e; color: #d4d4d4; padding: 8px; border-radius: 4px; }
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
</style>
