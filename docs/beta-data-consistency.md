# Beta 数据一致性与重启策略

## 一致性目标

- `rooms` 表反映房间配置与当前房间状态（`OPEN/FULL/CLOSED`）。
- `table_state_meta` 表反映牌桌最新摘要（`stage/pot/currentBet/actionPlayerId`）。
- 实时引擎仍以内存 `TableEngineService` 为权威执行器，数据库作为 Beta 阶段可观测与恢复辅助。

## 当前实现

- 房间状态一致性：
  - 在创建房间和加入房间后调用 `refreshRoomStatus`，根据当前人数自动同步 `OPEN/FULL`。
  - 文件：`server/src/main/java/com/pokerfriends/server/service/RoomService.java`
- 牌桌摘要一致性：
  - 创建、加入、WS 建连、ACTION 后都会 `upsert` 到 `table_state_meta`。
  - 文件：
    - `server/src/main/java/com/pokerfriends/server/service/TableStateMetaService.java`
    - `server/src/main/java/com/pokerfriends/server/ws/TableWebSocketHandler.java`

## 重启策略（Beta）

- 进程重启后，`rooms` 与 `table_state_meta` 仍在数据库中保留。
- 首次 `joinRoom` 时，如果内存中没有该 `tableId`，执行“按房间元数据重建空牌桌”：
  - `smallBlind/bigBlind/maxPlayers` 从 `rooms` 加载
  - 实时玩家状态、本手过程状态不会恢复（这是当前 Beta 已知限制）
- 文件：`server/src/main/java/com/pokerfriends/server/service/RoomService.java` 中 `ensureTableLoaded`

## 已知限制

- 玩家列表、手牌、边池明细不落库，重启后无法恢复到“正在进行的那一手”。
- `table_state_meta` 是摘要，不是可回放事件流。

## 后续演进建议

1. 增加 `table_state` JSON 快照表（按 handId + version 存储）。
2. 在 `ACTION` 写入事件日志（append-only），可用于回放与审计。
3. 引入 Redis 热状态（可选）+ PostgreSQL 冷存储，支持更平滑恢复。
