# Poker Friends

朋友局德州扑克全栈项目（Web + Tauri Windows + Java 云后端）。

## Monorepo 结构

- `apps/web`：Web 客户端（React + Vite）
- `apps/desktop`：PC 客户端（Tauri v2 + React）
- `packages/game-client`：前端共享协议、WS 客户端、状态管理
- `packages/platform`：Web/Tauri 平台适配层
- `packages/ui`：共享 UI 组件
- `server`：Spring Boot 云后端（HTTP + WebSocket + 规则引擎）
- `deploy`：Docker Compose、Nginx 反代、备份脚本

## 关键设计

- 服务端权威裁判：发牌流程、下注合法性、回合推进、结算在服务端执行。
- 每桌串行处理：后端为每个桌子维护单线程执行器，避免并发状态错乱。
- 前端复用：Web 与 Desktop 共用 UI/状态/协议，仅平台能力走适配层。

## 本地开发

### 1) 前端
```bash
pnpm install
pnpm dev:web
```

Desktop（需要 Rust 与 Tauri 依赖）：
```bash
pnpm --filter @poker-friends/desktop tauri:dev
```

### 2) 后端与中间件
建议直接使用 Docker Compose：
```bash
docker compose -f deploy/docker-compose.yml up -d
```

服务默认端口：
- Backend: `8080`
- PostgreSQL: `5432`
- Redis: `6379`

## 协议与接口

- 房间创建：`POST /api/rooms`
- 加入房间：`POST /api/rooms/{roomId}/join`
- 实时通道：`/ws/table/{tableId}?playerId={playerId}&token={token}`

## 部署与备份

- 启动：`docker compose -f deploy/docker-compose.yml up -d`
- 停止：`docker compose -f deploy/docker-compose.yml down`
- 备份：`bash deploy/backup-postgres.sh`
