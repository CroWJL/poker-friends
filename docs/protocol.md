# Poker Friends 协议定义（MVP）

## 1. HTTP API

### 1.1 创建房间
- `POST /api/rooms`
- 请求体：
```json
{
  "hostName": "alice",
  "smallBlind": 10,
  "bigBlind": 20,
  "maxPlayers": 6
}
```
- 响应体：
```json
{
  "roomId": "ABCD12",
  "tableId": "table-ABCD12",
  "playerId": "p-001",
  "token": "jwt-token"
}
```

### 1.2 加入房间
- `POST /api/rooms/{roomId}/join`
- 请求体：
```json
{
  "playerName": "bob"
}
```
- 响应体同创建房间。

> 用户身份说明：房间创建/加入接口均直接使用用户名。服务端只允许 `users` 表已存在的用户名入桌，不会自动创建用户。

### 1.3 查询房间概览（列表）
- `GET /api/rooms?limit=20`
- 说明：
  - `limit` 可选，默认 `20`，最大 `100`
  - `status` 可选：`OPEN` / `FULL` / `CLOSED`
  - 按创建时间倒序返回最近房间
- 响应体（示例）：
```json
[
  {
    "roomId": "ABCD12",
    "tableId": "table-ABCD12",
    "smallBlind": 10,
    "bigBlind": 20,
    "maxPlayers": 6,
    "status": "OPEN",
    "roomCreatedAt": "2026-05-25T06:55:21.639471Z",
    "roomUpdatedAt": "2026-05-25T06:55:21.639471Z",
    "tableMeta": {
      "handId": "h-001",
      "stage": "PREFLOP",
      "pot": 30,
      "currentBet": 20,
      "actionPlayerId": "p-001"
    }
  }
]
```

### 1.4 查询单个房间概览
- `GET /api/rooms/{roomId}`
- 响应体结构同列表中的单项对象。
- 房间不存在时返回 `404`。

### 1.5 查询动作事件日志
- `GET /api/rooms/{roomId}/actions?limit=50`
- 说明：
  - 返回该房间对应牌桌最近动作日志（倒序）
  - `limit` 可选，默认 `50`，最大 `200`
- 响应体（示例）：
```json
[
  {
    "tableId": "table-ABCD12",
    "handId": "h-001",
    "playerId": "p-001",
    "actionType": "CALL",
    "amount": null,
    "accepted": true,
    "errorMessage": null,
    "stage": "PREFLOP",
    "createdAt": "2026-05-25T07:38:12.120217Z"
  }
]
```

## 2. WebSocket

### 2.1 握手地址
- `GET /ws/table/{tableId}?playerId={playerId}&token={token}`

### 2.2 客户端 -> 服务端
```json
{
  "event": "ACTION",
  "payload": {
    "type": "CALL"
  }
}
```

动作类型：
- `FOLD`
- `CHECK`
- `CALL`
- `RAISE`（需要 `amount`）
- `ALL_IN`

心跳：
```json
{
  "event": "PING"
}
```

### 2.3 服务端 -> 客户端
- `TABLE_SNAPSHOT`：广播当前牌桌快照
- `ACTION_RESULT`：动作执行确认
- `ERROR`：非法动作或权限错误
- `PONG`：心跳响应

`TABLE_SNAPSHOT` 结构（简化）：
```json
{
  "event": "TABLE_SNAPSHOT",
  "payload": {
    "tableId": "table-ABCD12",
    "handId": "h-001",
    "stage": "PREFLOP",
    "pot": 30,
    "currentBet": 20,
    "actionPlayerId": "p-002",
    "potAwards": [
      {
        "playerId": "p-001",
        "amount": 120
      }
    ],
    "sidePots": [
      {
        "amount": 300,
        "eligiblePlayerIds": ["p-001", "p-002", "p-003"]
      }
    ],
    "communityCards": ["AH", "KD", "TC"],
    "players": [
      {
        "playerId": "p-001",
        "playerName": "alice",
        "seat": 1,
        "stack": 950,
        "inHand": true,
        "waitingForNextHand": false,
        "betThisRound": 20,
        "totalCommitted": 50,
        "holeCards": ["AS", "AD"]
      }
    ]
  }
}
```

字段说明补充：
- `players[].holeCards`：当前手牌（2 张），在结算后可用于展示赢家牌面。
- `players[].totalCommitted`：当前手累计投入（用于前端展示/调试边池）。
- `potAwards`：最近一次结算派奖结果（按玩家聚合后的金额）。

## 3. 断线重连约定
- 客户端重连时复用 `tableId + playerId + token`。
- 服务端校验通过后立即下发最近一份 `TABLE_SNAPSHOT`。
- 若会话失效，返回 `ERROR` 并提示重新加入房间。
