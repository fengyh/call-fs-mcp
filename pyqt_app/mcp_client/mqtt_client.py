# -*- coding: utf-8 -*-
"""MQTT 订阅封装：订阅 FS 云仿真服务的实时消息（status / progress / console）

对应关系：

- 前端 `snowy-admin-web/src/utils/mqttClient.js`：浏览器走 `ws://<host>:8083/mqtt`（MQTT over WebSocket）
- Java 侧 `MqttClientService`：后端走 `tcp://localhost:1883`，订阅 `factory/sim/+/+/+`
- 本模块：Python 走 `tcp://<host>:1883`（paho-mqtt），订阅 `factory/sim/{userId}/{modelId}/+`

topic 与 payload（见 docs/MqttClientTest.log.md、Java MqttClientService.handleMessage）：

- `factory/sim/{userId}/{modelId}/status`   → `{"username","modelId","state":"running|paused|finished","timestamp"}`
- `factory/sim/{userId}/{modelId}/progress` → `{"username","modelId","progress":12.5,"simTimeStep":250,"totalStep":2000,"timestamp"}`
- `factory/sim/{userId}/{modelId}/console`  → `{"username","modelId","level":"INFO","content":"...","timestamp"}`

paho 的回调运行在其网络线程内，因此所有对外通知都走 `pyqtSignal`，由 Qt 排队投递到界面线程。
"""
import json
import time

from PyQt6.QtCore import QObject, pyqtSignal

try:                       # paho 缺失时仍允许应用启动，仅 MQTT 功能不可用
    import paho.mqtt.client as mqtt
    PAHO_AVAILABLE = True
except ImportError:         # pragma: no cover
    mqtt = None
    PAHO_AVAILABLE = False

DEFAULT_BROKER_HOST = "127.0.0.1"
DEFAULT_BROKER_PORT = 1883
DEFAULT_TOPIC_PREFIX = "factory/sim"


class MqttSubscriber(QObject):
    """订阅单个模型的三类实时消息

    :param broker_host: MQTT Broker 地址（TCP）
    :param broker_port: MQTT Broker 端口，默认 1883
    :param client_id_prefix: clientId 前缀（对应 Java MqttProperties.clientId）
    :param topic_prefix: topic 前缀，默认 factory/sim
    :param keepalive: keepalive 秒数（对应 Java MqttProperties.keepAliveInterval）
    :param log_hook: 可选日志回调 callable(str)
    """

    connected = pyqtSignal()             # MQTT 已连接并完成订阅
    disconnected = pyqtSignal()          # 连接关闭/断开
    failed = pyqtSignal(str)             # 连接或订阅失败
    status_received = pyqtSignal(dict)   # 仿真状态消息
    progress_received = pyqtSignal(dict) # 仿真进度消息
    console_received = pyqtSignal(dict)  # 仿真控制台消息

    def __init__(self, broker_host=DEFAULT_BROKER_HOST, broker_port=DEFAULT_BROKER_PORT,
                 client_id_prefix="pyqt-fs", topic_prefix=DEFAULT_TOPIC_PREFIX,
                 keepalive=30, log_hook=None, parent=None):
        super().__init__(parent)
        self.broker_host = broker_host
        self.broker_port = int(broker_port)
        self.client_id_prefix = client_id_prefix
        self.topic_prefix = topic_prefix
        self.keepalive = int(keepalive)
        self.log_hook = log_hook
        self.user_id = ""
        self.model_id = ""
        self.topic = ""
        self.running = False
        self._client = None

    # ==================== 日志 ====================

    def log(self, fmt, *args):
        if self.log_hook is None:
            return
        try:
            self.log_hook(fmt % args if args else fmt)
        except Exception:
            pass

    # ==================== 连接管理 ====================

    @property
    def broker_url(self):
        return "tcp://%s:%d" % (self.broker_host, self.broker_port)

    def start(self, user_id, model_id):
        """连接 Broker 并订阅 factory/sim/{userId}/{modelId}/+，返回是否已发起连接"""
        self.stop()
        if not user_id or not model_id:
            self.failed.emit("MQTT 订阅需要 userId 与 modelId")
            return False
        if not PAHO_AVAILABLE:
            self.failed.emit("未安装 paho-mqtt，无法订阅 MQTT（pip install paho-mqtt）")
            return False

        self.user_id = str(user_id)
        self.model_id = str(model_id)
        self.topic = "%s/%s/%s/+" % (self.topic_prefix, self.user_id, self.model_id)

        client_id = "%s-%s-%d" % (self.client_id_prefix, self.user_id,
                                  int(time.time() * 1000))
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                             client_id=client_id, clean_session=True)
        client.on_connect = self._on_connect
        client.on_disconnect = self._on_disconnect
        client.on_message = self._on_message
        client.reconnect_delay_set(min_delay=1, max_delay=10)   # 断线自动重连
        try:
            client.connect_async(self.broker_host, self.broker_port, self.keepalive)
        except Exception as exc:
            self.failed.emit("MQTT 连接失败: %s" % exc)
            return False
        client.loop_start()          # 网络循环独立线程，不阻塞界面
        self._client = client
        self.running = True
        self.log("MQTT 连接中: %s topic=%s clientId=%s" % (self.broker_url, self.topic, client_id))
        return True

    def stop(self):
        """断开连接并停止网络循环（可重复调用）"""
        client, self._client = self._client, None
        was_running = self.running
        self.running = False
        if client is not None:
            try:
                client.disconnect()
            except Exception:
                pass
            try:
                client.loop_stop()
            except Exception:
                pass
        if was_running:
            self.log("MQTT 已断开")
            self.disconnected.emit()
        self.user_id = ""
        self.model_id = ""
        self.topic = ""

    # ==================== paho 回调 ====================

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        if self._is_success(reason_code):
            client.subscribe(self.topic, qos=0)
            self.log("MQTT 已连接并订阅 %s" % self.topic)
            self.connected.emit()
        else:
            self.log("MQTT 连接被拒绝: %s" % reason_code)
            self.failed.emit("MQTT 连接被拒绝: %s" % reason_code)

    def _on_disconnect(self, client, userdata, flags, reason_code=None, properties=None):
        self.running = False
        self.log("MQTT 连接关闭: %s" % reason_code)
        self.disconnected.emit()

    def _on_message(self, client, userdata, message):
        topic = getattr(message, "topic", "") or ""
        msg_type = topic.rsplit("/", 1)[-1]
        try:
            payload = json.loads(message.payload.decode("utf-8", "replace"))
        except Exception as exc:
            self.log("MQTT 消息解析失败 (%s): %s" % (topic, exc))
            return
        if not isinstance(payload, dict):
            return
        payload["_topic"] = topic
        if msg_type == "status":
            self.status_received.emit(payload)
        elif msg_type == "progress":
            # print("MQTT 进度: %s" % payload)
            self.progress_received.emit(payload)
        elif msg_type == "console":
            self.console_received.emit(payload)
        # 其它（如 events、animation 子主题）本次不处理

    @staticmethod
    def _is_success(reason_code):
        """兼容 paho 2.x 的 ReasonCode 与旧版 int"""
        try:
            return not reason_code.is_failure
        except AttributeError:
            return int(reason_code) == 0
