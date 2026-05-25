const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const waitFor = async (probe, timeoutMs = 6000, stepMs = 50) => {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const value = probe();
    if (value) return value;
    await sleep(stepMs);
  }
  throw new Error("wait timeout");
};

const checks = [];
const pass = (name, detail) => checks.push({ name, pass: true, detail });
const fail = (name, detail) => checks.push({ name, pass: false, detail });

const connect = (info, label) =>
  new Promise((resolve, reject) => {
    const ws = new WebSocket(
      `ws://localhost:8080/ws/table/${info.tableId}?playerId=${info.playerId}&token=${info.token}`
    );
    const state = { snapshots: [], errors: [], actionResults: 0 };
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.event === "TABLE_SNAPSHOT") state.snapshots.push(msg.payload);
      else if (msg.event === "ERROR") state.errors.push(msg.payload?.message ?? "UNKNOWN");
      else if (msg.event === "ACTION_RESULT") state.actionResults += 1;
    };
    ws.onopen = () => resolve({ ws, state, label });
    ws.onerror = () => reject(new Error(`${label} ws connect failed`));
  });

const run = async () => {
  const base = "http://localhost:8080";
  const hostName = process.env.BETA_HOST_NAME ?? "Alice";
  const guestName = process.env.BETA_GUEST_NAME ?? "Bob";
  const health = await fetch(`${base}/actuator/health`);
  if (!health.ok) throw new Error(`health check failed ${health.status}`);
  pass("后端健康检查", "UP");

  const createResp = await fetch(`${base}/api/rooms`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ hostName, smallBlind: 10, bigBlind: 20, maxPlayers: 6 })
  });
  if (!createResp.ok) throw new Error(`create room failed ${createResp.status}. 请先在 users 表写入用户名 ${hostName}`);
  const host = await createResp.json();
  pass("创建房间", host.roomId);

  const joinResp = await fetch(`${base}/api/rooms/${host.roomId}/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ playerName: guestName })
  });
  if (!joinResp.ok) throw new Error(`join room failed ${joinResp.status}. 请先在 users 表写入用户名 ${guestName}`);
  const guest = await joinResp.json();
  pass("加入房间", guest.playerId);

  const roomDetail = await fetch(`${base}/api/rooms/${host.roomId}`);
  if (roomDetail.ok) pass("房间详情接口可用", String(roomDetail.status));
  else fail("房间详情接口可用", String(roomDetail.status));

  const listResp = await fetch(`${base}/api/rooms?limit=5&status=OPEN`);
  if (listResp.ok) pass("房间列表筛选接口可用", String(listResp.status));
  else fail("房间列表筛选接口可用", String(listResp.status));

  const c1 = await connect(host, "host");
  const c2 = await connect(guest, "guest");
  await waitFor(() => c1.state.snapshots.length > 0 && c2.state.snapshots.length > 0);
  pass("双端WS连通", `host=${c1.state.snapshots.length},guest=${c2.state.snapshots.length}`);

  // 重连恢复
  c1.ws.close();
  await sleep(100);
  const c1r = await connect(host, "host-reconnect");
  await waitFor(() => c1r.state.snapshots.length > 0);
  pass("重连后收到快照", `snap=${c1r.state.snapshots.length}`);

  // 基础行动链路 + sidePots字段
  c1r.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CALL" } }));
  await waitFor(() => c1r.state.actionResults >= 1, 4000);
  await waitFor(() => c1r.state.snapshots.length >= 2 && c2.state.snapshots.length >= 2, 4000);
  const afterCall = c1r.state.snapshots.at(-1);
  const nextActorId = afterCall?.actionPlayerId;
  const nextActorClient = nextActorId === guest.playerId ? c2 : c1r;
  const nextActorActionResultsBefore = nextActorClient.state.actionResults;
  nextActorClient.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CHECK" } }));
  await waitFor(
    () =>
      nextActorClient.state.actionResults > nextActorActionResultsBefore ||
      nextActorClient.state.errors.length > 0,
    4000
  );
  if (nextActorClient.state.errors.length > 0) {
    throw new Error(`second action rejected: ${nextActorClient.state.errors.at(-1)}`);
  }
  await waitFor(() => c1r.state.snapshots.length >= 2 && c2.state.snapshots.length >= 2, 4000);
  const preflop = c1r.state.snapshots.find((snapshot) => snapshot?.stage === "PREFLOP");
  const latest = c1r.state.snapshots.at(-1);
  if (preflop?.players?.every((player) => Array.isArray(player.holeCards) && player.holeCards.length === 2)) {
    pass("玩家拿到两张手牌", "ok");
  } else {
    fail("玩家拿到两张手牌", JSON.stringify(preflop?.players ?? []));
  }
  if (Array.isArray(latest?.sidePots)) pass("快照包含sidePots", `count=${latest.sidePots.length}`);
  else fail("快照包含sidePots", JSON.stringify(latest));

  if (latest?.stage === "FLOP") pass("阶段推进到FLOP", "ok");
  else fail("阶段推进到FLOP", latest?.stage ?? "unknown");
  if (latest?.communityCards?.length === 3 && latest.communityCards.every((card) => card !== "??")) {
    pass("FLOP为真实牌面", latest.communityCards.join(" "));
  } else {
    fail("FLOP为真实牌面", JSON.stringify(latest?.communityCards ?? []));
  }

  c1r.ws.close();
  c2.ws.close();

  const summary = {
    pass: checks.filter((item) => item.pass).length,
    fail: checks.filter((item) => !item.pass).length
  };
  console.log(JSON.stringify({ checks, summary }, null, 2));
  if (summary.fail > 0) process.exit(2);
};

run().catch((err) => {
  console.error(err.stack || String(err));
  process.exit(1);
});
