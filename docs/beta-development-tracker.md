# Poker Friends Beta 开发跟踪清单（可直接执行）

> 文档用途：给后续新 Agent 作为唯一执行清单。  
> 状态标记：`[x] 已完成` / `[~] 部分完成` / `[ ] 未完成`  
> 最后更新：2026-05-25

---

## 0. 总体目标（Beta）

- 可多人联机完成一手德州扑克（含边池）
- 刷新/断线后可恢复会话并继续对局
- 后端元数据可观测，自动化验收脚本可稳定通过

---

## 1. 后端核心规则（M1）

### 1.1 下注轮与行动顺序

- [x] 轮到谁才能行动（非当前玩家拒绝）
- [x] `CHECK/CALL/RAISE/ALL_IN/FOLD` 基本合法性校验
- [x] 加注后其余玩家必须重新响应，不能提前推进阶段
- [x] 仅可行动玩家（`inHand && stack > 0`）参与轮转

代码落点：
- `server/src/main/java/com/pokerfriends/server/service/TableEngineService.java`

### 1.2 边池与投入跟踪

- [x] 玩家本手累计投入（`totalCommitted`）
- [x] 主池/边池结构计算（`sidePots`）
- [x] 快照中返回 `sidePots`

代码落点：
- `server/src/main/java/com/pokerfriends/server/model/PlayerState.java`
- `server/src/main/java/com/pokerfriends/server/model/SidePot.java`
- `server/src/main/java/com/pokerfriends/server/model/TableState.java`
- `server/src/main/java/com/pokerfriends/server/service/TableEngineService.java`

### 1.3 结算与收尾

- [x] 单人存活（其余 fold）直接赢池
- [x] 无可行动玩家时自动 runout 到收尾
- [x] `SHOWDOWN` 结算占位流程（非真实牌力比较）
- [x] 结算后清池（`pot/currentBet/sidePots` 重置）

代码落点：
- `server/src/main/java/com/pokerfriends/server/service/TableEngineService.java`

### 1.4 **硬缺口（必须补齐）**

- [x] 发真实玩家手牌（2 张 hole cards）
- [x] 发真实公共牌（flop/turn/river，不再是 `??`）
- [x] 真实牌力比较（7 选 5，标准德州牌型）
- [x] 真实边池派彩（按比牌结果分配，不是占位赢家）

说明：这 4 条未完成前，M1 只能算“流程可跑”，不能算“规则完成”。

---

## 2. 后端数据层与查询（M4 数据侧）

### 2.1 房间/牌局元数据落库

- [x] 房间元数据持久化（`rooms`）
- [x] 牌局摘要持久化（`table_state_meta`）
- [x] create/join/ws/action 触发摘要 upsert

代码落点：
- `server/src/main/java/com/pokerfriends/server/persistence/RoomEntity.java`
- `server/src/main/java/com/pokerfriends/server/persistence/TableStateMetaEntity.java`
- `server/src/main/java/com/pokerfriends/server/service/RoomService.java`
- `server/src/main/java/com/pokerfriends/server/service/TableStateMetaService.java`
- `server/src/main/java/com/pokerfriends/server/ws/TableWebSocketHandler.java`

### 2.2 元数据查询接口

- [x] `GET /api/rooms/{roomId}` 房间详情
- [x] `GET /api/rooms?limit=...` 最近房间列表
- [x] `status=OPEN|FULL|CLOSED` 列表筛选

代码落点：
- `server/src/main/java/com/pokerfriends/server/controller/RoomController.java`
- `server/src/main/java/com/pokerfriends/server/service/RoomQueryService.java`
- `server/src/main/java/com/pokerfriends/server/persistence/RoomRepository.java`

### 2.3 一致性策略

- [x] 房间状态自动刷新（OPEN/FULL）
- [x] 重启策略文档（元数据保留 + 空桌重建）

文档：
- `docs/beta-data-consistency.md`

### 2.4 **硬缺口（后续）**

- [x] 完整牌桌状态持久化（非仅摘要）
- [x] 动作事件日志（可回放/审计）
- [x] 重启后恢复“进行中的一手”能力

---

## 3. 前端实时能力（M2）

### 3.1 WS 连接层

- [x] 连接状态：`idle/connecting/connected/reconnecting/disconnected`
- [x] 自动重连（指数退避）
- [x] 发送失败返回显式结果（不再静默）
- [x] 消费 `ACTION_RESULT` 控制 pending

代码落点：
- `packages/game-client/src/wsClient.ts`
- `packages/game-client/src/store.ts`

### 3.2 会话恢复

- [x] 本地持久化 `roomId/tableId/playerId/token`
- [x] 页面刷新自动恢复会话并重连
- [x] 支持离开房间并清理会话

代码落点：
- `packages/platform/src/index.ts`
- `packages/platform/src/web.ts`
- `packages/platform/src/tauri.ts`
- `packages/ui/src/PokerApp.tsx`

---

## 4. 前端可玩化（M3）

### 4.1 组件拆分

- [x] `ConnectionBanner`
- [x] `TableHeader`
- [x] `PlayerSeats`
- [x] `ActionPanel`

代码落点：
- `packages/ui/src/components/ConnectionBanner.tsx`
- `packages/ui/src/components/TableHeader.tsx`
- `packages/ui/src/components/PlayerSeats.tsx`
- `packages/ui/src/components/ActionPanel.tsx`

### 4.2 动作合法性前置

- [x] 前端推导可行动作（check/call/raise/all-in/fold）
- [x] Raise 输入（不再固定 Raise 20）
- [x] 动作 pending 防重复提交

代码落点：
- `packages/game-client/src/actions.ts`
- `packages/ui/src/PokerApp.tsx`

### 4.3 **硬缺口（后续）**

- [x] 手牌可视化（当前后端无真实手牌）
- [x] 公共牌真实牌面渲染（当前后端仍 `??`）
- [x] 结算结果/赢家展示（当前仅基础快照）

---

## 5. 验收与质量门禁（M4）

### 5.1 自动化脚本

- [x] MVP 验收脚本：`pnpm acceptance:mvp`
- [x] Beta 验收脚本：`pnpm acceptance:beta`
- [x] 边池 smoke：`node sidepot-smoke.mjs`

脚本文件：
- `mvp-acceptance.mjs`
- `beta-acceptance.mjs`
- `sidepot-smoke.mjs`

### 5.2 文档

- [x] MVP 验收文档
- [x] 协议文档含 `sidePots`
- [x] Beta 一致性文档

文档文件：
- `docs/mvp-acceptance.md`
- `docs/protocol.md`
- `docs/beta-data-consistency.md`

---

## 6. 下一阶段（新 Agent 必做顺序）

> 下面是按业务价值排序的“必须继续开发”清单。

### P0（先做，不做就不算规则完成）

- [x] 引入 Deck（52 张牌）与洗牌
- [x] 玩家发 2 张手牌（服务端权威）
- [x] 发真实公共牌（flop/turn/river）
- [x] 实现牌力比较（7 选 5）
- [x] 用真实比牌结果执行主池/边池派彩

建议新增文件：
- `server/src/main/java/com/pokerfriends/server/service/DeckService.java`
- `server/src/main/java/com/pokerfriends/server/service/HandEvaluatorService.java`
- `server/src/main/java/com/pokerfriends/server/model/Card.java`（或以字符串规范表示）

### P1（紧随其后）

- [x] 扩展 `beta-acceptance.mjs`：增加真实牌面与真实结算断言
- [x] 前端显示 hole cards / community cards（不再 JSON 调试依赖）
- [x] 前端显示 winners / 派彩结果

---

## 7. 新 Agent 执行规则（建议直接照做）

1. 只要 P0 未完成，不要声明“规则完成”。  
2. 每完成一个功能必须同步：  
   - 单元测试（`TableEngineServiceTest` 或新增 evaluator 测试）  
   - 脚本验收（至少 `acceptance:mvp` + `acceptance:beta`）  
3. 每次改协议字段，必须同步更新：  
   - `packages/game-client/src/protocol.ts`  
   - `docs/protocol.md`  
4. 每完成一项就在本文件更新状态（`[ ] -> [x]`）。

---

## 8. 当前结论（一句话）

当前版本已达到“Beta 功能闭环（联机、重连、真实发牌/比牌/派彩、全量快照、动作日志、重启恢复）”，  
后续可转向“多桌并发性能与安全加固”。
