/**
 * MQTT over WebSocket 客户端
 * 用于前端直接订阅 FS 云仿真服务的实时消息。
 *
 * MQTT Broker 地址: ws://127.0.0.1:8083/mqtt
 * 订阅主题:
 *   - factory/sim/{userId}/{modelId}/+          (status/progress/console)
 *   - factory/sim/{userId}/{modelId}/animation/# (动画帧/状态/层)
 *
 * 依赖: mqtt.js
 */
let mqttClient = null
let mqttConnected = false

/**
 * 创建并连接 MQTT 客户端
 * @param {string} userId  当前登录用户名
 * @param {string} modelId MCP 模型 ID
 * @param {object} callbacks
 *   onStatus, onProgress, onConsole — JSON 消息回调
 *   onAnimationFrame(blob, topic)   — 二进制 JPEG 帧
 *   onAnimationState(data)          — animation/state JSON
 *   onAnimationLayers(data)         — animation/layers JSON
 *   onAnimationFrameMeta(data)      — animation/frameMeta JSON
 *   onConnect, onDisconnect
 * @returns {object} { client, disconnect }
 */
export function createMqttClient(userId, modelId, callbacks = {}) {
	disconnectMqtt()

	if (!userId || !modelId) return null
	if (typeof window.mqtt === 'undefined') {
		console.warn('mqtt.js not loaded, skipping MQTT connection')
		return null
	}

	const base = `factory/sim/${userId}/${modelId}`
	const topics = [`${base}/+`, `${base}/animation/#`]
	const brokerUrl = import.meta.env.VITE_MQTT_BROKER_URL || 'ws://127.0.0.1:8083/mqtt'
	const client = window.mqtt.connect(brokerUrl, {
		protocolVersion: 5
	})

	client.on('connect', () => {
		mqttConnected = true
		console.log('MQTT connected, subscribing:', topics)
		client.subscribe(topics, { qos: 0 }, err => {
			if (err) console.error('MQTT subscribe error:', err)
		})
		callbacks.onConnect?.()
	})

	client.on('message', (topic, payload) => {
		// 二进制帧：topic 包含 /animation/.../frame
		if (topic.includes('/animation/') && topic.endsWith('/frame')) {
			const blob = new Blob([payload], { type: 'image/jpeg' })
			callbacks.onAnimationFrame?.(blob, topic)
			return
		}

		// JSON 消息
		let data
		try {
			data = JSON.parse(payload.toString())
		} catch (e) {
			console.warn('MQTT parse error:', e)
			return
		}

		const parts = topic.split('/')
		const type = parts[parts.length - 1]

		// 动画相关 JSON 消息
		if (topic.includes('/animation/')) {
			switch (type) {
				case 'state':
					callbacks.onAnimationState?.(data)
					return
				case 'layers':
					callbacks.onAnimationLayers?.(data)
					return
				case 'frameMeta':
					callbacks.onAnimationFrameMeta?.(data)
					return
			}
			return
		}

		// 原有 JSON 消息
		switch (type) {
			case 'status':
				callbacks.onStatus?.(data)
				break
			case 'progress':
				callbacks.onProgress?.(data)
				break
			case 'console':
				callbacks.onConsole?.(data)
				break
		}
	})

	client.on('reconnect', () => {
		console.log('MQTT reconnecting...')
	})

	client.on('close', () => {
		mqttConnected = false
		console.log('MQTT connection closed')
		callbacks.onDisconnect?.()
	})

	client.on('offline', () => {
		mqttConnected = false
		console.log('MQTT offline')
		callbacks.onDisconnect?.()
	})

	client.on('error', err => {
		console.error('MQTT error:', err)
	})

	mqttClient = client
	return client
}

/** 断开 MQTT 连接 */
export function disconnectMqtt() {
	if (mqttClient) {
		try {
			mqttClient.end(true)
		} catch (e) {
			console.warn('MQTT disconnect error:', e)
		}
		mqttClient = null
		mqttConnected = false
	}
}

/** 获取连接状态 */
export function isMqttConnected() {
	return mqttConnected
}
