/**
 * MQTT over WebSocket 客户端
 * 用于前端直接订阅 FS 云仿真服务的实时消息。
 *
 * MQTT Broker 地址: ws://127.0.0.1:8083/mqtt
 * 订阅主题: factory/sim/{userId}/{modelId}/+
 *
 * 依赖: mqtt.js (CDN: https://unpkg.com/mqtt/dist/mqtt.min.js)
 */
let mqttClient = null
let mqttConnected = false

/**
 * 创建并连接 MQTT 客户端
 * @param {string} userId  当前登录用户名
 * @param {string} modelId MCP 模型 ID
 * @param {object} callbacks { onStatus, onProgress, onConsole, onConnect, onDisconnect }
 * @returns {object} { client, disconnect }
 */
export function createMqttClient(userId, modelId, callbacks = {}) {
	// 如果已有连接，先断开
	disconnectMqtt()

	if (!userId || !modelId) return null
	if (typeof window.mqtt === 'undefined') {
		console.warn('mqtt.js not loaded, skipping MQTT connection')
		return null
	}

	const topic = `factory/sim/${userId}/${modelId}/+`
	const brokerUrl = import.meta.env.VITE_MQTT_BROKER_URL || 'ws://127.0.0.1:8083/mqtt'
	const client = window.mqtt.connect(brokerUrl, {
		protocolVersion: 5   // MQTT v5.0
	})

	client.on('connect', () => {
		mqttConnected = true
		console.log('MQTT connected, subscribing:', topic)
		client.subscribe(topic, { qos: 0 }, err => {
			if (err) console.error('MQTT subscribe error:', err)
		})
		callbacks.onConnect?.()
	})

	client.on('message', (topic, payload) => {
		const type = topic.split('/').pop()  // status / progress / console
		let data
		try {
			data = JSON.parse(payload.toString())
		} catch (e) {
			console.warn('MQTT parse error:', e)
			return
		}
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
