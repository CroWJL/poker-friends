import { useEffect, useMemo, useRef, useState } from "react";
import {
  deriveLegalActions,
  PokerWsClient,
  usePokerStore,
  type ActionCommand,
  type WsServerMessage
} from "@poker-friends/game-client";
import type { AppPlatform, StoredPokerSession } from "@poker-friends/platform";
import { ActionPanel } from "./components/ActionPanel";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { EventFeed } from "./components/EventFeed";
import { PlayerSeats } from "./components/PlayerSeats";
import { SettlementOverlay } from "./components/SettlementOverlay";
import { TableHeader } from "./components/TableHeader";
import "./poker-theme.css";

interface PokerAppProps {
  platform: AppPlatform;
  config: {
    apiBaseUrl: string;
  };
}

interface RoomResponse {
  roomId: string;
  tableId: string;
  playerId: string;
  token: string;
}

export function PokerApp({ platform, config }: PokerAppProps) {
  const wsClient = useMemo(() => new PokerWsClient(), []);
  const logoAsset = new URL("./assets/poker-friends-logo.svg", import.meta.url).href;
  const chipSetAsset = new URL("./assets/poker-chip-set.svg", import.meta.url).href;
  const [roomId, setRoomId] = useState("");
  const [playerName, setPlayerName] = useState("Player");
  const [token, setToken] = useState("");
  const [tableId, setTableId] = useState("");
  const [selfPlayerId, setSelfPlayerId] = useState("");
  const [showDebug, setShowDebug] = useState(false);
  const [eventFeed, setEventFeed] = useState<string[]>([]);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [settlementAwards, setSettlementAwards] = useState<Array<{ playerId: string; amount: number }>>([]);
  const [settlementCountdown, setSettlementCountdown] = useState(0);
  const [actionCountInStage, setActionCountInStage] = useState(0);
  const previousStageRef = useRef<string | undefined>(undefined);
  const previousAwardKeyRef = useRef<string>("");
  const pendingActionRef = useRef<ActionCommand | null>(null);
  const snapshot = usePokerStore((state) => state.snapshot);
  const lastError = usePokerStore((state) => state.lastError);
  const connectionStatus = usePokerStore((state) => state.connectionStatus);
  const actionPending = usePokerStore((state) => state.actionPending);
  const setSnapshot = usePokerStore((state) => state.setSnapshot);
  const clearSnapshot = usePokerStore((state) => state.clearSnapshot);
  const setError = usePokerStore((state) => state.setError);
  const setConnectionStatus = usePokerStore((state) => state.setConnectionStatus);
  const setActionPending = usePokerStore((state) => state.setActionPending);
  const pushFeed = (message: string) => {
    const timestamp = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    setEventFeed((current) => [`${timestamp} ${message}`, ...current].slice(0, 12));
  };

  const showToast = (message: string) => {
    setToastMessage(message);
    window.setTimeout(() => setToastMessage(null), 1800);
  };

  const formatAction = (action: ActionCommand | null, selfName: string) => {
    if (!action) {
      return `${selfName} 执行动作`;
    }
    if (action.type === "RAISE") {
      return `${selfName} 加注到 ${action.amount ?? 0}`;
    }
    if (action.type === "ALL_IN") {
      return `${selfName} 全下`;
    }
    if (action.type === "CALL") {
      return `${selfName} 跟注`;
    }
    if (action.type === "CHECK") {
      return `${selfName} 过牌`;
    }
    return `${selfName} 弃牌`;
  };

  useEffect(() => {
    Promise.all([platform.getStoredPlayerName(), platform.getStoredSession()])
      .then(([storedName, storedSession]) => {
        if (storedName) {
          setPlayerName(storedName);
        }
        if (storedSession) {
          applySession(storedSession);
          connectWs(storedSession.tableId, storedSession.playerId, storedSession.token);
        }
      })
      .catch(() => setError("恢复本地会话失败"));
  }, [platform]);

  useEffect(() => {
    const onWsMessage = (message: WsServerMessage) => {
      if (message.event === "TABLE_SNAPSHOT") {
        const nextSnapshot = message.payload as {
          stage?: string;
          potAwards?: Array<{ playerId: string; amount: number }>;
        };
        setSnapshot(message.payload as never);
        setActionPending(false);
        setError(undefined);
        if (nextSnapshot.stage && previousStageRef.current !== nextSnapshot.stage) {
          pushFeed(`阶段进入 ${nextSnapshot.stage}`);
          setActionCountInStage(0);
          if (previousStageRef.current && previousStageRef.current !== nextSnapshot.stage) {
            showToast(`阶段：${nextSnapshot.stage}`);
          }
          previousStageRef.current = nextSnapshot.stage;
        }
        if (nextSnapshot.potAwards && nextSnapshot.potAwards.length > 0) {
          const summary = nextSnapshot.potAwards.map((award) => `${award.playerId} +${award.amount}`).join(" / ");
          const awardKey = summary;
          if (awardKey !== previousAwardKeyRef.current) {
            previousAwardKeyRef.current = awardKey;
            setSettlementAwards(nextSnapshot.potAwards);
            setSettlementCountdown(6);
            pushFeed(`结算派奖 ${summary}`);
            showToast("本手已结算");
          }
        } else if (nextSnapshot.stage === "PREFLOP") {
          previousAwardKeyRef.current = "";
          setSettlementAwards([]);
          setSettlementCountdown(0);
        }
      } else if (message.event === "ACTION_RESULT") {
        setActionPending(false);
        setActionCountInStage((value) => value + 1);
        pushFeed(`动作确认：${formatAction(pendingActionRef.current, playerName)}`);
        pendingActionRef.current = null;
      } else if (message.event === "ERROR") {
        setActionPending(false);
        const errorMessage = String((message.payload as { message?: string })?.message ?? "未知错误");
        setError(errorMessage);
        pushFeed(`动作失败：${formatAction(pendingActionRef.current, playerName)}（${errorMessage}）`);
        pendingActionRef.current = null;
        showToast("动作失败");
      }
    };
    wsClient.onMessage(onWsMessage);
    wsClient.onStatus(setConnectionStatus);
    return () => wsClient.close();
  }, [setActionPending, setConnectionStatus, setError, setSnapshot, wsClient]);

  const connectWs = (table: string, player: string, authToken: string) => {
    const wsBase = config.apiBaseUrl.replace(/^http/, "ws");
    wsClient.connect(`${wsBase}/ws/table/${table}?playerId=${player}&token=${authToken}`);
  };

  const applySession = (session: StoredPokerSession) => {
    setRoomId(session.roomId);
    setTableId(session.tableId);
    setSelfPlayerId(session.playerId);
    setToken(session.token);
  };

  const saveSession = async (session: StoredPokerSession) => {
    applySession(session);
    await platform.setStoredSession(session);
  };

  const createRoom = async () => {
    try {
      setError(undefined);
      await platform.setStoredPlayerName(playerName);
      const resp = await fetch(`${config.apiBaseUrl}/api/rooms`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ hostName: playerName, smallBlind: 10, bigBlind: 20, maxPlayers: 6 })
      });
      if (!resp.ok) {
        throw new Error(`创建房间失败(${resp.status})`);
      }
      const data = (await resp.json()) as RoomResponse;
      await saveSession({
        roomId: data.roomId,
        tableId: data.tableId,
        playerId: data.playerId,
        token: data.token
      });
      connectWs(data.tableId, data.playerId, data.token);
    } catch (error) {
      setError(error instanceof Error ? error.message : "创建房间失败");
    }
  };

  const joinRoom = async () => {
    try {
      setError(undefined);
      await platform.setStoredPlayerName(playerName);
      const resp = await fetch(`${config.apiBaseUrl}/api/rooms/${roomId}/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerName })
      });
      if (!resp.ok) {
        throw new Error(`加入房间失败(${resp.status})`);
      }
      const data = (await resp.json()) as RoomResponse;
      await saveSession({
        roomId: data.roomId,
        tableId: data.tableId,
        playerId: data.playerId,
        token: data.token
      });
      connectWs(data.tableId, data.playerId, data.token);
    } catch (error) {
      setError(error instanceof Error ? error.message : "加入房间失败");
    }
  };

  const leaveRoom = async () => {
    wsClient.close();
    clearSnapshot();
    setActionPending(false);
    setRoomId("");
    setTableId("");
    setSelfPlayerId("");
    setToken("");
    setEventFeed([]);
    setActionCountInStage(0);
    setSettlementAwards([]);
    setSettlementCountdown(0);
    pendingActionRef.current = null;
    previousAwardKeyRef.current = "";
    previousStageRef.current = undefined;
    await platform.clearStoredSession();
  };

  const sendAction = (action: ActionCommand) => {
    if (!snapshot || !selfPlayerId || snapshot.actionPlayerId !== selfPlayerId) {
      setError("当前不是你的行动回合");
      return;
    }
    setActionPending(true);
    pendingActionRef.current = action;
    pushFeed(`提交动作：${formatAction(action, playerName)}`);
    const sent = wsClient.send({ event: "ACTION", payload: action });
    if (!sent) {
      setActionPending(false);
      setError("连接未就绪，动作发送失败");
      pushFeed(`发送失败：${formatAction(action, playerName)}（连接未就绪）`);
      pendingActionRef.current = null;
    }
  };

  useEffect(() => {
    if (settlementAwards.length === 0) {
      return;
    }
    if (settlementCountdown <= 0) {
      setSettlementAwards([]);
      return;
    }
    const timer = window.setTimeout(() => setSettlementCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [settlementAwards, settlementCountdown]);

  const currentActionPlayer =
    snapshot?.players.find((player) => player.playerId === snapshot.actionPlayerId)?.playerName ?? "-";
  const isMyTurn = Boolean(snapshot && selfPlayerId && snapshot.actionPlayerId === selfPlayerId);
  const legalActions = deriveLegalActions(snapshot, selfPlayerId);
  const selfPlayer = snapshot?.players.find((player) => player.playerId === selfPlayerId);
  const inHandCount = snapshot?.players.filter((player) => player.inHand).length ?? 0;
  const canActCount = snapshot?.players.filter((player) => player.inHand && player.stack > 0).length ?? 0;
  const resolvePlayerName = (playerId: string) =>
    snapshot?.players.find((player) => player.playerId === playerId)?.playerName ?? playerId;

  return (
    <main className="pf-app">
      {settlementAwards.length > 0 ? (
        <SettlementOverlay
          awards={settlementAwards}
          resolvePlayerName={resolvePlayerName}
          countdownSeconds={settlementCountdown}
          onClose={() => {
            setSettlementAwards([]);
            setSettlementCountdown(0);
          }}
        />
      ) : null}
      <header className="pf-app-header">
        <div className="pf-brand-block">
          <img className="pf-brand-logo" src={logoAsset} alt="Poker Friends" />
          <p className="pf-subtitle">{platform.name} 客户端</p>
        </div>
        <ConnectionBanner status={connectionStatus} />
      </header>

      <section className="pf-lobby-card pf-lobby-toolbar">
        <div className="pf-field pf-toolbar-field">
          <label htmlFor="pf-player-name">玩家</label>
          <input id="pf-player-name" value={playerName} onChange={(event) => setPlayerName(event.target.value)} />
        </div>
        <div className="pf-field pf-toolbar-field">
          <label htmlFor="pf-room-id">房间号</label>
          <input
            id="pf-room-id"
            value={roomId}
            onChange={(event) => setRoomId(event.target.value)}
            placeholder="输入房间号"
          />
        </div>
        <div className="pf-lobby-actions">
          <button onClick={createRoom}>创建房间</button>
          <button onClick={joinRoom}>加入房间</button>
          <button onClick={leaveRoom} disabled={!token}>
            离开房间
          </button>
        </div>
      </section>
      <img className="pf-chip-strip" src={chipSetAsset} alt="筹码素材" />

      <section className="pf-table-layout">
        {toastMessage ? <div className="pf-toast">{toastMessage}</div> : null}
        <div className="pf-main-column">
          <TableHeader
            roomId={roomId}
            tableId={tableId}
            tokenReady={Boolean(token)}
            currentActionPlayer={currentActionPlayer}
            isMyTurn={isMyTurn}
            stage={snapshot?.stage}
            pot={snapshot?.pot}
            currentBet={snapshot?.currentBet}
            communityCards={snapshot?.communityCards ?? []}
            potAwards={snapshot?.potAwards ?? []}
            sidePots={snapshot?.sidePots ?? []}
            inHandCount={inHandCount}
            canActCount={canActCount}
            actionCountInStage={actionCountInStage}
          />
          <PlayerSeats
            players={snapshot?.players ?? []}
            actionPlayerId={snapshot?.actionPlayerId}
            selfPlayerId={selfPlayerId}
            tableFinished={snapshot?.stage === "FINISHED"}
            stage={snapshot?.stage}
            pot={snapshot?.pot}
          />
        </div>
        <aside className="pf-side-column">
          <ActionPanel
            legalActions={legalActions}
            actionPending={actionPending}
            stack={selfPlayer?.stack ?? 0}
            betThisRound={selfPlayer?.betThisRound ?? 0}
            onSendAction={sendAction}
          />
          <EventFeed items={eventFeed} />
          {lastError ? <p className="pf-error-box">错误：{lastError}</p> : null}
          <button className="pf-debug-toggle" onClick={() => setShowDebug((value) => !value)}>
            {showDebug ? "隐藏调试快照" : "显示调试快照"}
          </button>
          {showDebug ? <pre className="pf-debug-panel">{JSON.stringify(snapshot, null, 2)}</pre> : null}
        </aside>
      </section>
    </main>
  );
}
