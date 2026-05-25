const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const waitFor = async (probe, timeoutMs = 6000, stepMs = 50) => {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const value = probe();
    if (value) return value;
    await sleep(stepMs);
  }
  throw new Error("wait timeout");
};

const connect = (info, label) =>
  new Promise((resolve, reject) => {
    const ws = new WebSocket(
      `ws://localhost:8080/ws/table/${info.tableId}?playerId=${info.playerId}&token=${info.token}`
    );
    const state = { snapshots: [], errors: [] };
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.event === "TABLE_SNAPSHOT") state.snapshots.push(msg.payload);
      if (msg.event === "ERROR") state.errors.push(msg.payload?.message ?? "UNKNOWN");
    };
    ws.onopen = () => resolve({ ws, state, label });
    ws.onerror = () => reject(new Error(`${label} websocket connection failed`));
  });

const run = async () => {
  const baseUrl = "http://localhost:8080";
  const hostName = process.env.SIDEPOT_HOST_NAME ?? "Alice";
  const guestName = process.env.SIDEPOT_GUEST_NAME ?? "Bob";
  const createResp = await fetch(`${baseUrl}/api/rooms`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ hostName, smallBlind: 10, bigBlind: 20, maxPlayers: 6 })
  });
  if (!createResp.ok) {
    throw new Error(`create room failed ${createResp.status}. 请先在 users 表写入用户名 ${hostName}`);
  }
  const host = await createResp.json();
  const joinResp = await fetch(`${baseUrl}/api/rooms/${host.roomId}/join`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ playerName: guestName })
  });
  if (!joinResp.ok) {
    throw new Error(`join room failed ${joinResp.status}. 请先在 users 表写入用户名 ${guestName}`);
  }
  const guest = await joinResp.json();

  const c1 = await connect(host, "host");
  const c2 = await connect(guest, "guest");
  await waitFor(() => c1.state.snapshots.length > 0 && c2.state.snapshots.length > 0);

  c1.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CALL" } }));
  await waitFor(() => c1.state.snapshots.length >= 2);
  c2.ws.send(JSON.stringify({ event: "ACTION", payload: { type: "CHECK" } }));
  await waitFor(() => c1.state.snapshots.length >= 3);

  const latest = c1.state.snapshots.at(-1);
  const sidePots = latest?.sidePots ?? [];

  const result = {
    roomId: host.roomId,
    stage: latest?.stage,
    pot: latest?.pot,
    sidePots,
    checks: {
      hasSidePotsField: Array.isArray(sidePots),
      hasMainPotOnly: sidePots.length === 1,
      mainPotAmountMatchesPot: sidePots.length === 1 && sidePots[0].amount === latest?.pot
    }
  };
  console.log(JSON.stringify(result, null, 2));

  c1.ws.close();
  c2.ws.close();

  if (!result.checks.hasSidePotsField || !result.checks.hasMainPotOnly || !result.checks.mainPotAmountMatchesPot) {
    process.exit(2);
  }
};

run().catch((err) => {
  console.error(err.stack || String(err));
  process.exit(1);
});
