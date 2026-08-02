# MQTT 浏览器动画接口说明

## 1. MVP 方案

当前版本采用低帧率截图流：FactorySimulation 在服务器内存中离屏绘制模型，将画面压缩为 JPEG，通过 MQTT 发布。浏览器不需要远程桌面，也不要求服务器打开模型 UI。

- 默认帧率：5 FPS
- 可选范围：1～10 FPS
- 最大画面：1280×720，保持原模型比例
- JPEG 质量：0.72
- 帧 QoS：0
- 帧 Retain：false
- 每个模型同一时间只周期推送一个活动层

## 2. 多层模型

根模型的 `layerId` 固定为 `root`。每个 `ObjectGroupComponent` 对应一个子层，层目录由以下 MCP 工具返回：

```text
listRemoteSimulationAnimationLayers(userId, modelId)
```

返回的每层包括 `layerId`、`parentLayerId`、`name`、`displayName`、`simpleNo`、`objectName`、`wholeNo`、`depth`、`breadcrumb`、画面尺寸和帧 topic。Web 端可以使用这些字段生成模型树或面包屑。

子层名称按照模型当前标签模式选择编号或业务名称；如果旧模型、导入模型的编号为空，会自动回退到业务名称，再回退到带对象 ID 的稳定名称。因此前端应优先显示 `displayName`，兼容旧版本时也可以继续显示 `name`，两者当前值相同。

子层示例：

```json
{
  "layerId": "object-group-id-2",
  "parentLayerId": "object-group-id-1",
  "name": "装配车间",
  "displayName": "装配车间",
  "simpleNo": "",
  "objectName": "装配车间",
  "wholeNo": ".工厂.装配车间",
  "depth": 2,
  "breadcrumb": [
    {"layerId": "root", "name": "factory.fs"},
    {"layerId": "object-group-id-1", "name": "工厂"},
    {"layerId": "object-group-id-2", "name": "装配车间"}
  ],
  "frameTopic": "factory/sim/u001/model-001/animation/object-group-id-2/frame"
}
```

切换显示层：

```text
selectRemoteSimulationAnimationLayer(userId, modelId, layerId, frameRate)
```

服务端直接对该 `ObjectGroupComponent` 离屏绘制，不需要在 FactorySimulation Swing UI 中打开子模型。切换成功后会立即生成一帧；运行期间继续周期推送。

需要按调用时刻获取一张 SVG，而不是订阅连续 JPEG 帧时，调用：

```text
getSvgPicture(userId, modelId, layerId)
```

响应的 `data.svg` 是原始 SVG XML 文本。该查询不切换活动层、不启动动画推流，也不发布 MQTT 消息。

## 3. MQTT Topic

```text
factory/sim/{username}/{modelId}/animation/layers
factory/sim/{username}/{modelId}/animation/state
factory/sim/{username}/{modelId}/animation/frameMeta
factory/sim/{username}/{modelId}/animation/{layerId}/frame
```

### layers

JSON，QoS 1，Retain。包含完整层目录和每层对应的帧 topic。

### state

JSON，QoS 1，Retain。包含：

```json
{
  "username": "u001",
  "recordId": "record-001",
  "modelId": "model-001",
  "runId": "run-001",
  "event": "animationStarted",
  "status": "RUNNING",
  "animationEnabled": true,
  "animationStreaming": true,
  "activeLayerId": "root",
  "frameRate": 5,
  "frameSequence": 12,
  "lastFrameAt": 1781620000000,
  "lastError": null,
  "contentType": "image/jpeg",
  "frameTopic": "factory/sim/u001/model-001/animation/root/frame"
}
```

### frameMeta

JSON，QoS 0，非 Retain。每帧对应一条元数据：

```json
{
  "layerId": "root",
  "sequence": 13,
  "simTime": 3600000,
  "width": 1280,
  "height": 720,
  "contentType": "image/jpeg",
  "byteLength": 86421,
  "capturedAt": 1781620000200,
  "frameTopic": "factory/sim/u001/model-001/animation/root/frame"
}
```

### frame

Payload 是原始 JPEG 字节，不是 JSON，也不是 Base64。Web MQTT 客户端必须以二进制接收，再创建 Blob URL。

## 4. 推荐调用顺序

```text
loadRemoteSimulationModel
listRemoteSimulationAnimationLayers
selectRemoteSimulationAnimationLayer(root, 5)
浏览器订阅 animation/layers、animation/state、animation/frameMeta 和 root/frame
startRemoteSimulation(showAnimation=true)
需要时调用 setRemoteSimulationAnimationEnabled 独立开关动画
需要时调用 setRemoteSimulationAccelerateFactor 调整动画运行节奏
收到 JPEG 字节后替换页面中的当前画面
切换子模型时先订阅新 frame topic，再调用 selectRemoteSimulationAnimationLayer
pause/stop/自然结束时接收最后一帧
```

`loadInUI` 与浏览器动画无关。云端无桌面部署可以使用：

```json
{
  "loadInUI": false,
  "showAnimation": true
}
```

## 5. Vue/MQTT.js 接收示例

```javascript
import mqtt from 'mqtt'

const client = mqtt.connect('ws://FS服务器地址:8083/mqtt', {
  clientId: `web-animation-${crypto.randomUUID()}`,
})

let currentFrameUrl = null

client.on('connect', () => {
  client.subscribe('factory/sim/u001/model-001/animation/root/frame', { qos: 0 })
})

client.on('message', (topic, payload) => {
  if (!topic.endsWith('/frame')) return

  const blob = new Blob([payload], { type: 'image/jpeg' })
  const nextUrl = URL.createObjectURL(blob)
  animationImageUrl.value = nextUrl

  if (currentFrameUrl) URL.revokeObjectURL(currentFrameUrl)
  currentFrameUrl = nextUrl
})
```

页面可以先用普通 `<img :src="animationImageUrl">`。为了避免画面尺寸变化导致布局抖动，应使用固定比例容器和 `object-fit: contain`。

## 6. 注意事项

- MQTT Broker 的 WebSocket 默认端口为 8083；TCP 客户端仍使用 1883。
- 当前 Broker 允许匿名连接，仅适合内网联调。正式环境需要鉴权和 topic ACL。
- MQTTX 如果使用 Plaintext/JSON 查看二进制帧，会显示乱码，这是正常现象；应主要观察 `frameMeta`，或由 Web 页面显示 JPEG。
- 10 FPS 是上限，不保证所有大模型都能达到。调度采用固定延迟，单帧绘制较慢时会自动降低实际帧率，不会无限堆积截图任务。
- 当前是截图流，不提供浏览器内对象点击、缩放编辑或原生矢量动画。
