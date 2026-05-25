const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const waitFor = async (probe, timeoutMs = 5000, stepMs = 50) => {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const value = probe();
    if (value) {
      return value;
    }
    await sleep(stepMs);
  }
  throw new Error("wait timeout");
};

const checks = [];
const markPass = (name, detail) => checks.push({ name, pass: true, detail });
const markFail = (name, detail) => checks.push({ name, pass: false, detail });

const connect = (info, label) =>
  new Promise((resolve, reject) => {
    const ws = new WebSocket(
      `ws://localhost:8080/ws/table/${info.tableId}?playerId=${info.playerId}&token=${info.token}`
    );
    const state = { snapshots: [], errors: [], actionResults: 0, pongs: 0 };
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.event === "TABLE_SNAPSHOT") state.snapshots.push(message.payload);
      else if (message.event === "ERROR") state.errors.push(message.payload?.message ?? "UNKNOWN");
      else if (message.event === "ACTION_RESULT") state.actionResults += 1;
      else if (message.event === "PONG") state.pongs += 1;
    };
    ws.onopen = () => resolve({ ws, state, label });
    ws.onerror = () => reject(new Error(`${label} websocket connection failed`));
  });

const run = async () => {
  const baseUrl = "http://localhost:8080";
  const hostName = process.env.MVP_HOST_NAME ?? "Alice";
  const guestName = process.env.MVP_GUEST_NAME ?? "Bob";

  const createResp = await fetch(`${baseUrl}/api/rooms`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ hostName, smallBlind: 10, bigBlind: 20, maxPlayers: 6 })
  });
  if (!createResp.ok) {
    throw new Error(`createRoom failed: ${createResp.status}. 请先在 users 表写入用户名 ${hostName}`);
  }
  const host = await createResp.json();
  markPass("创建房间", `roomId=${host.roomId}`);

  const joinResp = await fetch(`${baseUrl}/api/rooms/${host.roomId}/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ playerName: guestName })
  });
  if (!joinResp.ok) {
    throw new Error(`joinRoom failed: ${joinResp.status}. 请先在 users 表写入用户名 ${guestName}`);
  }
  const guest = await joinResp.json();
  markPass("加入房间", `playerId=${guest.playerId}`);

  const hostClient = await connect(host, "host");
  const guestClient = await connect(guest, "guest");

  await waitFor(() => hostClient.state.snapshots.length > 0 && guestClient.state.snapshots.length > 0, 6000);
  markPass(
    "WS连接并收到快照",
    `hostSnap=${hostClient.state.snapshots.length}, guestSnap=${guestClient.state.snapshots.length}`
  );

  hostClient.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CHECK" } }));
  await sleep(250);
  if (hostClient.state.errors.some((error) => String(error).includes("check"))) {
    markPass("非法CHECK被拒绝", hostClient.state.errors.at(-1));
  } else {
    markFail("非法CHECK被拒绝", JSON.stringify(hostClient.state.errors));
  }

  const snapshotBeforeCall = hostClient.state.snapshots.at(-1);
  const callActorClient = snapshotBeforeCall?.actionPlayerId === guest.playerId ? guestClient : hostClient;
  const callActionResultsBefore = callActorClient.state.actionResults;
  callActorClient.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CALL" } }));
  await waitFor(() => callActorClient.state.actionResults > callActionResultsBefore, 4000);
  await waitFor(() => hostClient.state.snapshots.length >= 2 && guestClient.state.snapshots.length >= 2, 4000);
  markPass("CALL成功并广播", `${callActorClient.label}ActionResults=${callActorClient.state.actionResults}`);

  const snapshotBeforeCheck = hostClient.state.snapshots.at(-1);
  const checkActorClient = snapshotBeforeCheck?.actionPlayerId === guest.playerId ? guestClient : hostClient;
  const checkActionResultsBefore = checkActorClient.state.actionResults;
  checkActorClient.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CHECK" } }));
  await waitFor(() => checkActorClient.state.actionResults > checkActionResultsBefore, 4000);
  await waitFor(() => hostClient.state.snapshots.length >= 3 && guestClient.state.snapshots.length >= 3, 4000);

  const lastSnapshot = hostClient.state.snapshots.at(-1);
  if (
    lastSnapshot?.stage === "FLOP" &&
    Array.isArray(lastSnapshot.communityCards) &&
    lastSnapshot.communityCards.length === 3
  ) {
    markPass("回合推进到FLOP", `communityCards=${lastSnapshot.communityCards.length}`);
  } else {
    markFail(
      "回合推进到FLOP",
      JSON.stringify({ stage: lastSnapshot?.stage, cards: lastSnapshot?.communityCards?.length })
    );
  }

  if (lastSnapshot?.actionPlayerId) {
    markPass("快照含actionPlayerId", String(lastSnapshot.actionPlayerId));
  } else {
    markFail("快照含actionPlayerId", JSON.stringify(lastSnapshot));
  }

  hostClient.ws.close();
  guestClient.ws.close();

  const summary = {
    pass: checks.filter((item) => item.pass).length,
    fail: checks.filter((item) => !item.pass).length
  };
  console.log(JSON.stringify({ checks, summary }, null, 2));
  if (summary.fail > 0) {
    process.exit(2);
  }
};

run().catch((error) => {
  console.error(error.stack || String(error));
  process.exit(1);
});
