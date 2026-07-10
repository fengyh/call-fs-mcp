### 测试代码运行的调试信息
```text
═══════════════════════════════════════════════════════
  MQTT 仿真测试客户端
  Broker : tcp://localhost:1883
  用户   : user001
  模型   : model01
  订阅   : factory/sim/user001/+/+
  ClientId: mqttClient_user001_1783654612094
═══════════════════════════════════════════════════════
  结束调试，处理退出信号

  [*] 正在连接... 已连接
  [*] 已订阅: factory/sim/user001/+/+

  [→ 发送] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654613749}
  [← 接收] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654613749}

  [→ 发送] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":12.5,"simTimeStep":250,"totalStep":2000,"timestamp":1783654614086}
  [← 接收] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":12.5,"simTimeStep":250,"totalStep":2000,"timestamp":1783654614086}

  [→ 发送] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"INFO","content":"Simulation initialized","timestamp":1783654614150}
  [← 接收] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"INFO","content":"Simulation initialized","timestamp":1783654614150}

  [→ 发送] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654615743}
  [← 接收] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654615743}

  [→ 发送] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":25.0,"simTimeStep":500,"totalStep":2000,"timestamp":1783654615795}
  [← 接收] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":25.0,"simTimeStep":500,"totalStep":2000,"timestamp":1783654615795}

  [→ 发送] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"INFO","content":"AGV001 start from warehouse","timestamp":1783654615846}
  [← 接收] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"INFO","content":"AGV001 start from warehouse","timestamp":1783654615846}

  [→ 发送] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654617743}
  [← 接收] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654617743}

  [→ 发送] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":37.5,"simTimeStep":750,"totalStep":2000,"timestamp":1783654617796}
  [← 接收] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":37.5,"simTimeStep":750,"totalStep":2000,"timestamp":1783654617796}

  [→ 发送] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"WARN","content":"AGV002 battery level below 20%, heading to charging station","timestamp":1783654617846}
  [← 接收] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"WARN","content":"AGV002 battery level below 20%, heading to charging station","timestamp":1783654617846}

  [→ 发送] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654619744}
  [← 接收] factory/sim/user001/model01/status
    {"username":"user001","modelId":"model01","state":"running","timestamp":1783654619744}

  [→ 发送] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":50.0,"simTimeStep":1000,"totalStep":2000,"timestamp":1783654619797}
  [← 接收] factory/sim/user001/model01/progress
    {"username":"user001","modelId":"model01","progress":50.0,"simTimeStep":1000,"totalStep":2000,"timestamp":1783654619797}

  [→ 发送] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"INFO","content":"AGV003 arrived at processing station W01","timestamp":1783654619848}
  [← 接收] factory/sim/user001/model01/console
    {"username":"user001","modelId":"model01","level":"INFO","content":"AGV003 arrived at processing station W01","timestamp":1783654619848}


[STOP] 收到退出信号，正在停止...
  [*] 正在断开连接...

Process finished with exit code 130
```

### 测试代码运行的MQTTX订阅主题消息
```text
Topic: factory/sim/user001/model01/statusQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "state": "running",
  "timestamp": 1783654613749
}
2026-07-10 11:36:54:036

Topic: factory/sim/user001/model01/progressQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "progress": 12.5,
  "simTimeStep": 250,
  "totalStep": 2000,
  "timestamp": 1783654614086
}
2026-07-10 11:36:54:101

Topic: factory/sim/user001/model01/consoleQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "level": "INFO",
  "content": "Simulation initialized",
  "timestamp": 1783654614150
}
2026-07-10 11:36:54:152

Topic: factory/sim/user001/model01/statusQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "state": "running",
  "timestamp": 1783654615743
}
2026-07-10 11:36:55:745

Topic: factory/sim/user001/model01/progressQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "progress": 25,
  "simTimeStep": 500,
  "totalStep": 2000,
  "timestamp": 1783654615795
}
2026-07-10 11:36:55:797

Topic: factory/sim/user001/model01/consoleQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "level": "INFO",
  "content": "AGV001 start from warehouse",
  "timestamp": 1783654615846
}
2026-07-10 11:36:55:848

Topic: factory/sim/user001/model01/statusQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "state": "running",
  "timestamp": 1783654617743
}
2026-07-10 11:36:57:745

Topic: factory/sim/user001/model01/progressQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "progress": 37.5,
  "simTimeStep": 750,
  "totalStep": 2000,
  "timestamp": 1783654617796
}
2026-07-10 11:36:57:798

Topic: factory/sim/user001/model01/consoleQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "level": "WARN",
  "content": "AGV002 battery level below 20%, heading to charging station",
  "timestamp": 1783654617846
}
2026-07-10 11:36:57:849

Topic: factory/sim/user001/model01/statusQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "state": "running",
  "timestamp": 1783654619744
}
2026-07-10 11:36:59:746

Topic: factory/sim/user001/model01/progressQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "progress": 50,
  "simTimeStep": 1000,
  "totalStep": 2000,
  "timestamp": 1783654619797
}
2026-07-10 11:36:59:799

Topic: factory/sim/user001/model01/consoleQoS: 0
{
  "username": "user001",
  "modelId": "model01",
  "level": "INFO",
  "content": "AGV003 arrived at processing station W01",
  "timestamp": 1783654619848
}
2026-07-10 11:36:59:849
```
