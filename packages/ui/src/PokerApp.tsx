import { useEffect, useMemo, useRef, useState } from "react";
import {
  deriveLegalActions,
  PokerWsClient,
  usePokerStore,
  type ActionCommand,
  type PracticeOutcome,
  type TableSnapshot,
  type WsServerMessage
} from "@poker-friends/game-client";
import type { AppPlatform, StoredPokerSession } from "@poker-friends/platform";
import { ActionPanel } from "./components/ActionPanel";
import { ConnectionBanner } from "./components/ConnectionBanner";
import { EventFeed } from "./components/EventFeed";
import { PlayerSeats } from "./components/PlayerSeats";
import { PracticeResultOverlay } from "./components/PracticeResultOverlay";
import { LandscapeRotatePrompt } from "./components/LandscapeRotatePrompt";
import { SettlementOverlay } from "./components/SettlementOverlay";
import { UserProfilePanel, type WalletTransactionView } from "./components/UserProfilePanel";
import { useIsMobileLayout, useIsPortrait, useLandscapeLock } from "./hooks/useMediaQuery";
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
  walletBalance: number;
}

const STAGE_LABELS: Record<string, string> = {
  WAITING: "等待中",
  PREFLOP: "翻牌前",
  FLOP: "翻牌",
  TURN: "转牌",
  RIVER: "河牌",
  SHOWDOWN: "摊牌",
  FINISHED: "结算完成"
};

export function PokerApp({ platform, config }: PokerAppProps) {
  const wsClient = useMemo(() => new PokerWsClient(), []);
  const [roomId, setRoomId] = useState("");
  const [playerName, setPlayerName] = useState("Player");
  const [token, setToken] = useState("");
  const [tableId, setTableId] = useState("");
  const [selfPlayerId, setSelfPlayerId] = useState("");
  const [showDebug, setShowDebug] = useState(false);
  const [eventFeed, setEventFeed] = useState<string[]>([]);
  const [walletBalance, setWalletBalance] = useState<number | null>(null);
  const [userId, setUserId] = useState<string | null>(null);
  const [showProfile, setShowProfile] = useState(false);
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [walletTransactions, setWalletTransactions] = useState<WalletTransactionView[]>([]);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [startPending, setStartPending] = useState(false);
  const [settlementAwards, setSettlementAwards] = useState<
    Array<{ playerId: string; amount: number; bestFiveCards?: string[]; handType?: string }>
  >([]);
  const [settlementCountdown, setSettlementCountdown] = useState(0);
  const previousStageRef = useRef<string | undefined>(undefined);
  const lastSettlementFeedKeyRef = useRef<string | null>(null);
  const lastSettlementDisplayKeyRef = useRef<string | null>(null);
  const processedActionEventIdsRef = useRef<Set<string>>(new Set());
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
  const snapshotRef = useRef(snapshot);
  const selfPlayerIdRef = useRef(selfPlayerId);
  const playerNameRef = useRef(playerName);
  const pushFeed = (message: string) => {
    const timestamp = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    setEventFeed((current) => [`${timestamp} ${message}`, ...current].slice(0, 30));
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

  const formatStageLabel = (stage?: string) => {
    if (!stage) {
      return "-";
    }
    return STAGE_LABELS[stage] ?? stage;
  };

  const formatCardWithSuit = (card: string) => {
    if (!card || card.length < 2) {
      return card;
    }
    const rank = card[0] === "T" ? "10" : card[0];
    const suit = card[1];
    const suitSymbol = suit === "S" ? "♠" : suit === "H" ? "♥" : suit === "D" ? "♦" : suit === "C" ? "♣" : suit;
    return `${rank}${suitSymbol}`;
  };

  useEffect(() => {
    snapshotRef.current = snapshot;
  }, [snapshot]);

  useEffect(() => {
    selfPlayerIdRef.current = selfPlayerId;
  }, [selfPlayerId]);

  useEffect(() => {
    playerNameRef.current = playerName;
  }, [playerName]);

  useEffect(() => {
    if (!token || !tableId || !selfPlayerId) {
      return;
    }
    void refreshWalletBalance(playerName);
  }, [playerName, token, tableId, selfPlayerId]);

  useEffect(() => {
    Promise.all([platform.getStoredPlayerName(), platform.getStoredSession()])
      .then(([storedName, storedSession]) => {
        if (storedName) {
          setPlayerName(storedName);
        }
        if (storedSession) {
          applySession(storedSession);
          if (storedName) {
            void refreshWalletBalance(storedName);
          }
          connectWs(storedSession.tableId, storedSession.playerId, storedSession.token);
        }
      })
      .catch(() => setError("恢复本地会话失败"));
  }, [platform]);

  useEffect(() => {
    const onWsMessage = (message: WsServerMessage) => {
      if (message.event === "TABLE_SNAPSHOT") {
        const nextSnapshot = message.payload as {
          handId?: string;
          stage?: string;
          players?: Array<{
            playerId: string;
            playerName: string;
          }>;
          potAwards?: Array<{ playerId: string; amount: number; bestFiveCards?: string[]; handType?: string }>;
        };
        const fullSnapshot = message.payload as TableSnapshot;
        setSnapshot(fullSnapshot as never);
        setActionPending(false);
        setStartPending(false);
        setError(undefined);
        if (fullSnapshot.practiceOutcome) {
          setSettlementAwards([]);
          setSettlementCountdown(0);
        }
        if (nextSnapshot.stage && previousStageRef.current !== nextSnapshot.stage) {
          pushFeed(`阶段进入：${formatStageLabel(nextSnapshot.stage)}`);
          if (previousStageRef.current && previousStageRef.current !== nextSnapshot.stage) {
            showToast(`阶段：${formatStageLabel(nextSnapshot.stage)}`);
          }
          previousStageRef.current = nextSnapshot.stage;
          if (nextSnapshot.stage === "FINISHED" && !fullSnapshot.practiceMode) {
            void refreshWalletBalance(playerNameRef.current);
          }
        }
        if (nextSnapshot.potAwards && nextSnapshot.potAwards.length > 0) {
          const settlementFeedKey = `${nextSnapshot.handId ?? "unknown"}:${nextSnapshot.potAwards
            .map((award) => `${award.playerId}-${award.amount}`)
            .join("|")}`;
          const resolveName = (playerId: string) =>
            nextSnapshot.players?.find((player) => player.playerId === playerId)?.playerName ?? playerId;
          const summary = nextSnapshot.potAwards.map((award) => {
            const handType = award.handType ? `（${award.handType}）` : "";
            const cards =
              award.bestFiveCards && award.bestFiveCards.length === 5
                ? `（牌面：${award.bestFiveCards.map(formatCardWithSuit).join(" ")}）`
                : "";
            return `${resolveName(award.playerId)} 赢了 ${award.amount}${handType}${cards}`;
          });
          if (lastSettlementDisplayKeyRef.current !== settlementFeedKey) {
            setSettlementAwards(nextSnapshot.potAwards);
            if (!fullSnapshot.practiceMode) {
              setSettlementCountdown(3);
            }
            lastSettlementDisplayKeyRef.current = settlementFeedKey;
          }
          if (lastSettlementFeedKeyRef.current !== settlementFeedKey) {
            pushFeed(`结算：${summary.join(" / ")}`);
            lastSettlementFeedKeyRef.current = settlementFeedKey;
            showToast(summary.join("，"));
          }
        } else if (nextSnapshot.stage === "PREFLOP") {
          setSettlementAwards([]);
          setSettlementCountdown(0);
          lastSettlementFeedKeyRef.current = null;
          lastSettlementDisplayKeyRef.current = null;
        }
      } else if (message.event === "ACTION_EVENT") {
        const actionEvent = message.payload as {
          eventId?: string;
          playerName?: string;
          playerId?: string;
          actionType?: "FOLD" | "CHECK" | "CALL" | "RAISE" | "ALL_IN";
          amount?: number;
        };
        if (actionEvent.eventId) {
          if (processedActionEventIdsRef.current.has(actionEvent.eventId)) {
            return;
          }
          processedActionEventIdsRef.current.add(actionEvent.eventId);
          if (processedActionEventIdsRef.current.size > 200) {
            const oldest = processedActionEventIdsRef.current.values().next().value as string | undefined;
            if (oldest) {
              processedActionEventIdsRef.current.delete(oldest);
            }
          }
        }
        const actorName = actionEvent.playerName || actionEvent.playerId || "玩家";
        const actionCommand: ActionCommand | null = actionEvent.actionType
          ? {
              type: actionEvent.actionType,
              amount: actionEvent.amount
            }
          : null;
        pushFeed(`动作：${formatAction(actionCommand, actorName)}`);
      } else if (message.event === "ACTION_RESULT") {
        setActionPending(false);
        pendingActionRef.current = null;
      } else if (message.event === "ERROR") {
        setActionPending(false);
        setStartPending(false);
        const errorMessage = String((message.payload as { message?: string })?.message ?? "未知错误");
        setError(errorMessage);
        const latestSnapshot = snapshotRef.current;
        const latestSelfPlayerId = selfPlayerIdRef.current;
        const selfName =
          latestSnapshot?.players.find((player) => player.playerId === latestSelfPlayerId)?.playerName ??
          playerNameRef.current;
        const actionText = pendingActionRef.current ? formatAction(pendingActionRef.current, selfName) : "动作失败";
        pushFeed(`动作失败：${actionText}（${errorMessage}）`);
        pendingActionRef.current = null;
        showToast("动作失败");
      }
    };
    const disposeMessage = wsClient.onMessage(onWsMessage);
    const disposeStatus = wsClient.onStatus(setConnectionStatus);
    return () => {
      disposeMessage();
      disposeStatus();
      wsClient.close();
    };
  }, [setActionPending, setConnectionStatus, setError, setSnapshot, wsClient]);

  const fetchUserProfile = async (displayName: string, options?: { silent?: boolean }) => {
    const normalized = displayName.trim();
    if (!normalized) {
      if (!options?.silent) {
        setProfileError("请先输入玩家昵称");
      }
      return;
    }
    if (!options?.silent) {
      setProfileLoading(true);
      setProfileError(null);
    }
    try {
      const profileUrl = `${config.apiBaseUrl}/api/users/profile?displayName=${encodeURIComponent(normalized)}`;
      const transactionsUrl = `${config.apiBaseUrl}/api/users/wallet/transactions?displayName=${encodeURIComponent(normalized)}&limit=30`;
      if (options?.silent) {
        const profileResp = await fetch(profileUrl);
        if (!profileResp.ok) {
          return;
        }
        const data = (await profileResp.json()) as { userId: string; displayName: string; walletBalance: number };
        setUserId(data.userId);
        setWalletBalance(data.walletBalance);
        return;
      }
      const [profileResp, txResp] = await Promise.all([fetch(profileUrl), fetch(transactionsUrl)]);
      if (!profileResp.ok) {
        const message = (await profileResp.text()) || `加载失败(${profileResp.status})`;
        throw new Error(message);
      }
      const data = (await profileResp.json()) as { userId: string; displayName: string; walletBalance: number };
      setUserId(data.userId);
      setWalletBalance(data.walletBalance);
      if (txResp.ok) {
        const transactions = (await txResp.json()) as WalletTransactionView[];
        setWalletTransactions(transactions);
      } else {
        setWalletTransactions([]);
      }
      setProfileError(null);
    } catch (error) {
      if (!options?.silent) {
        setProfileError(error instanceof Error ? error.message : "加载个人信息失败");
      }
    } finally {
      if (!options?.silent) {
        setProfileLoading(false);
      }
    }
  };

  const refreshWalletBalance = (displayName: string) => fetchUserProfile(displayName, { silent: true });

  const openProfile = () => {
    if (!token || !tableId || !selfPlayerId) {
      return;
    }
    setShowProfile(true);
    void fetchUserProfile(playerName);
  };

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

  const startPracticeGame = async () => {
    const hostName = playerName.trim();
    if (!hostName) {
      setError("请先输入玩家名");
      return;
    }
    try {
      setError(undefined);
      await platform.setStoredPlayerName(hostName);
      const resp = await fetch(`${config.apiBaseUrl}/api/rooms/practice`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ hostName })
      });
      if (!resp.ok) {
        const message = await resp.text();
        throw new Error(message || `人机对战创建失败(${resp.status})`);
      }
      const data = (await resp.json()) as RoomResponse;
      setWalletBalance(data.walletBalance);
      await saveSession({
        roomId: data.roomId,
        tableId: data.tableId,
        playerId: data.playerId,
        token: data.token
      });
      connectWs(data.tableId, data.playerId, data.token);
    } catch (error) {
      setError(error instanceof Error ? error.message : "人机对战创建失败");
    }
  };

  const createRoom = async () => {
    const hostName = playerName.trim();
    if (!hostName) {
      setError("请先输入玩家名");
      return;
    }
    try {
      setError(undefined);
      await platform.setStoredPlayerName(hostName);
      const resp = await fetch(`${config.apiBaseUrl}/api/rooms`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ hostName, smallBlind: 10, bigBlind: 20, maxPlayers: 6 })
      });
      if (!resp.ok) {
        throw new Error(`创建房间失败(${resp.status})`);
      }
      const data = (await resp.json()) as RoomResponse;
      setWalletBalance(data.walletBalance);
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
    const joinPlayerName = playerName.trim();
    const targetRoomId = roomId.trim();
    if (!joinPlayerName) {
      setError("请先输入玩家名");
      return;
    }
    if (!targetRoomId) {
      setError("请输入房间号后再加入");
      return;
    }
    try {
      setError(undefined);
      await platform.setStoredPlayerName(joinPlayerName);
      const resp = await fetch(`${config.apiBaseUrl}/api/rooms/${targetRoomId}/join`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerName: joinPlayerName })
      });
      if (!resp.ok) {
        throw new Error(`加入房间失败(${resp.status})`);
      }
      const data = (await resp.json()) as RoomResponse;
      setWalletBalance(data.walletBalance);
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

  const clearLocalSession = () => {
    setShowProfile(false);
    setProfileError(null);
    wsClient.close();
    clearSnapshot();
    setActionPending(false);
    setError(undefined);
    setRoomId("");
    setTableId("");
    setSelfPlayerId("");
    setToken("");
    setEventFeed([]);
    setStartPending(false);
    setSettlementAwards([]);
    setSettlementCountdown(0);
    processedActionEventIdsRef.current.clear();
    lastSettlementFeedKeyRef.current = null;
    lastSettlementDisplayKeyRef.current = null;
    pendingActionRef.current = null;
    previousStageRef.current = undefined;
  };

  const leaveRoom = async () => {
    const activeRoomId = roomId;
    const activePlayerName = playerName.trim();
    try {
      if (activeRoomId && activePlayerName) {
        const resp = await fetch(`${config.apiBaseUrl}/api/rooms/${activeRoomId}/leave`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ playerName: activePlayerName })
        });
        if (resp.ok) {
          const data = (await resp.json()) as { walletBalance: number };
          setWalletBalance(data.walletBalance);
        }
      }
    } catch {
      // 本地仍清理会话，避免卡在无效房间
    } finally {
      clearLocalSession();
      await platform.clearStoredSession();
    }
  };

  const sendAction = (action: ActionCommand) => {
    if (!snapshot || snapshot.stage === "WAITING" || snapshot.stage === "FINISHED" || snapshot.stage === "SHOWDOWN") {
      setError("当前牌局不可行动，请等待下一手开始");
      return;
    }
    if (!selfPlayerId || snapshot.actionPlayerId !== selfPlayerId) {
      setError("当前不是你的行动回合");
      return;
    }
    setActionPending(true);
    pendingActionRef.current = action;
    const selfName = snapshot.players.find((player) => player.playerId === selfPlayerId)?.playerName ?? playerName;
    const sent = wsClient.send({ event: "ACTION", payload: action });
    if (!sent) {
      setActionPending(false);
      setError("连接未就绪，动作发送失败");
      pushFeed(`发送失败：${formatAction(action, selfName)}（连接未就绪）`);
      pendingActionRef.current = null;
    }
  };

  const acknowledgePracticeOutcome = () => {
    const sent = wsClient.send({ event: "PRACTICE_ACK" });
    if (!sent) {
      setError("连接未就绪，确认失败");
    }
  };

  const startGame = () => {
    if (!snapshot) {
      setError("牌桌状态未就绪");
      return;
    }
    if (snapshot.stage !== "WAITING" && snapshot.stage !== "FINISHED") {
      setError("牌局进行中，请稍候");
      return;
    }
    if (snapshot.practiceOutcome) {
      setError("请先确认练习结果");
      return;
    }
    const self = snapshot.players.find((player) => player.playerId === selfPlayerId);
    if (!self || self.seat !== 1) {
      setError("仅房主可开始牌局");
      return;
    }
    if (snapshot.players.filter((player) => player.stack > 0).length < 2) {
      setError("人数不足 2 人，无法开始");
      return;
    }
    setStartPending(true);
    pendingActionRef.current = null;
    const sent = wsClient.send({ event: "START_GAME" });
    if (!sent) {
      setStartPending(false);
      setError("连接未就绪，开始失败");
    }
  };

  const previewEightPlayers = () => {
    wsClient.close();
    const mockPlayers = [
      { playerId: "p1", playerName: "你", seat: 1, stack: 1880, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["AS", "KH"] },
      { playerId: "p2", playerName: "Alice", seat: 2, stack: 1560, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] },
      { playerId: "p3", playerName: "Bob", seat: 3, stack: 1420, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] },
      { playerId: "p4", playerName: "Cindy", seat: 4, stack: 2010, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] },
      { playerId: "p5", playerName: "Dylan", seat: 5, stack: 980, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] },
      { playerId: "p6", playerName: "Eva", seat: 6, stack: 1760, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] },
      { playerId: "p7", playerName: "Frank", seat: 7, stack: 1320, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] },
      { playerId: "p8", playerName: "Gina", seat: 8, stack: 1670, inHand: true, betThisRound: 40, totalCommitted: 120, holeCards: ["??", "??"] }
    ];
    const ordered = [...mockPlayers].sort((left, right) => left.seat - right.seat);
    const dealer = ordered[0];
    const nextSeatPlayer = (fromSeat: number) =>
      ordered.find((player) => player.seat > fromSeat) ?? ordered[0];
    const smallBlind = nextSeatPlayer(dealer.seat);
    const bigBlind = nextSeatPlayer(smallBlind.seat);
    const mockSnapshot: TableSnapshot = {
      tableId: tableId || "preview-8-max",
      handId: "preview-hand-8",
      stage: "TURN",
      started: true,
      pot: 320,
      currentBet: 40,
      actionPlayerId: "p3",
      dealerPlayerId: dealer.playerId,
      smallBlindPlayerId: smallBlind.playerId,
      bigBlindPlayerId: bigBlind.playerId,
      communityCards: ["AH", "KD", "TC", "4S"],
      players: mockPlayers
    };
    setSelfPlayerId("p1");
    if (!roomId) {
      setRoomId("PREVIEW8");
    }
    if (!tableId) {
      setTableId(mockSnapshot.tableId);
    }
    if (!token) {
      setToken("preview-token");
    }
    setSnapshot(mockSnapshot);
    setActionPending(false);
    setError(undefined);
    setStartPending(false);
    setEventFeed([]);
    setSettlementAwards([]);
    setSettlementCountdown(0);
    pendingActionRef.current = null;
    previousStageRef.current = mockSnapshot.stage;
    showToast("已加载 8 人样式预览");
  };

  const practiceMode = Boolean(snapshot?.practiceMode);

  useEffect(() => {
    if (settlementAwards.length === 0 || practiceMode) {
      return;
    }
    if (settlementCountdown <= 0) {
      setSettlementAwards([]);
      return;
    }
    const timer = window.setTimeout(() => setSettlementCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [settlementAwards, settlementCountdown, practiceMode]);

  const confirmSettlementAndContinue = () => {
    setSettlementAwards([]);
    setSettlementCountdown(0);
    lastSettlementDisplayKeyRef.current = null;
    if (snapshot?.practiceOutcome) {
      return;
    }
    startGame();
  };

  const currentActionPlayer =
    snapshot?.players.find((player) => player.playerId === snapshot.actionPlayerId)?.playerName ?? "-";
  const practiceOutcome = snapshot?.practiceOutcome as PracticeOutcome | null | undefined;
  const waitingForPlayers = snapshot?.stage === "WAITING";
  const handFinished = snapshot?.stage === "FINISHED";
  const handLocked =
    snapshot?.stage === "SHOWDOWN" || Boolean(practiceOutcome) || handFinished;
  const legalActions = deriveLegalActions(snapshot, selfPlayerId);
  const selfPlayer = snapshot?.players.find((player) => player.playerId === selfPlayerId);
  const isHost = selfPlayer?.seat === 1;
  const activePlayerCount = snapshot?.players.filter((player) => player.stack > 0).length ?? 0;
  const canStartGame = Boolean(
    waitingForPlayers && isHost && activePlayerCount >= 2 && !startPending && !practiceOutcome
  );
  const waitingMessage = activePlayerCount < 2 ? "等待玩家加入" : "等待房主开始";
  const actionDisabledReason = practiceOutcome
    ? "请先确认练习结果"
    : handLocked
      ? "结算中，请等待下一手"
      : waitingForPlayers
        ? waitingMessage
        : undefined;
  const resolvePlayerName = (playerId: string) =>
    snapshot?.players.find((player) => player.playerId === playerId)?.playerName ?? playerId;
  const hasActiveSession = Boolean(token && tableId && selfPlayerId);
  const canCreateRoom = playerName.trim().length > 0;
  const canJoinRoom = playerName.trim().length > 0 && roomId.trim().length > 0;
  const canStartPractice = playerName.trim().length > 0;
  const isMobileLayout = useIsMobileLayout();
  const isPortrait = useIsPortrait();
  useLandscapeLock(hasActiveSession && isMobileLayout);

  return (
    <main className={`pf-app ${isMobileLayout ? "pf-app-mobile" : ""}`}>
      {isMobileLayout && hasActiveSession && isPortrait ? <LandscapeRotatePrompt /> : null}
      {practiceOutcome ? (
        <PracticeResultOverlay outcome={practiceOutcome} onConfirm={acknowledgePracticeOutcome} />
      ) : null}
      {settlementAwards.length > 0 && !practiceOutcome ? (
        <SettlementOverlay
          awards={settlementAwards}
          resolvePlayerName={resolvePlayerName}
          countdownSeconds={settlementCountdown}
          requireConfirm={practiceMode}
          onConfirm={confirmSettlementAndContinue}
        />
      ) : null}
      {showProfile && hasActiveSession ? (
        <UserProfilePanel
          displayName={playerName}
          userId={userId ?? undefined}
          walletBalance={walletBalance}
          tableStack={hasActiveSession ? (selfPlayer?.stack ?? null) : null}
          roomId={hasActiveSession ? roomId : undefined}
          transactions={walletTransactions}
          loading={profileLoading}
          error={profileError ?? undefined}
          onClose={() => setShowProfile(false)}
          onRefresh={() => void fetchUserProfile(playerName)}
        />
      ) : null}
      <header className="pf-app-header">
        <div className="pf-brand-block">
          <p className="pf-subtitle">{platform.name} 客户端</p>
        </div>
        <div className="pf-header-actions">
          {hasActiveSession ? (
            <>
              <button
                type="button"
                className="pf-profile-entry"
                onClick={openProfile}
                title="查看个人信息与钱包"
              >
                我的信息
              </button>
              <button className="pf-header-leave" onClick={leaveRoom}>
                离开房间
              </button>
            </>
          ) : null}
          <ConnectionBanner status={connectionStatus} />
        </div>
      </header>

      {!hasActiveSession ? (
        <section className="pf-home-shell">
          <div className="pf-lobby-card pf-lobby-toolbar pf-home-card">
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
                placeholder="输入房间号（加入房间时使用）"
              />
            </div>
            <div className="pf-lobby-actions">
              <button className="pf-practice-btn" onClick={startPracticeGame} disabled={!canStartPractice}>
                人机对战
              </button>
              <button onClick={createRoom} disabled={!canCreateRoom}>
                创建房间
              </button>
              <button onClick={joinRoom} disabled={!canJoinRoom}>
                加入房间
              </button>
            </div>
          </div>
          {lastError ? <p className="pf-error-box">错误：{lastError}</p> : null}
        </section>
      ) : (
        <section className={`pf-table-layout ${isMobileLayout ? "pf-table-layout-mobile" : ""}`}>
          {toastMessage ? <div className="pf-toast">{toastMessage}</div> : null}
          <div className="pf-main-column">
            <PlayerSeats
              players={snapshot?.players ?? []}
              roomId={roomId}
              waitingForPlayers={waitingForPlayers}
              waitingMessage={waitingMessage}
              actionPlayerId={snapshot?.actionPlayerId}
              selfPlayerId={selfPlayerId}
              tableFinished={snapshot?.stage === "FINISHED"}
              stage={snapshot?.stage}
              pot={snapshot?.pot}
              communityCards={snapshot?.communityCards ?? []}
              dealerPlayerId={snapshot?.dealerPlayerId}
              smallBlindPlayerId={snapshot?.smallBlindPlayerId}
              bigBlindPlayerId={snapshot?.bigBlindPlayerId}
              currentActionPlayer={currentActionPlayer}
              layout={isMobileLayout ? "mobile" : "desktop"}
            />
          </div>
          <aside className="pf-side-column">
            {waitingForPlayers && isHost ? (
              <button className="pf-action-btn pf-start-hand-btn" onClick={startGame} disabled={!canStartGame}>
                {startPending ? "开始中..." : "开始牌局"}
              </button>
            ) : null}
            <ActionPanel
              legalActions={legalActions}
              actionPending={actionPending}
              stack={selfPlayer?.stack ?? 0}
              betThisRound={selfPlayer?.betThisRound ?? 0}
              disabled={waitingForPlayers || handLocked}
              disabledReason={actionDisabledReason}
              variant={isMobileLayout ? "compact" : "default"}
              onSendAction={sendAction}
            />
            <EventFeed items={eventFeed} variant={isMobileLayout ? "drawer" : "default"} />
            {lastError ? <p className="pf-error-box">错误：{lastError}</p> : null}
            {!isMobileLayout ? (
              <>
                <button className="pf-debug-toggle" onClick={previewEightPlayers}>
                  模拟8人牌桌
                </button>
                <button className="pf-debug-toggle" onClick={() => setShowDebug((value) => !value)}>
                  {showDebug ? "隐藏调试快照" : "显示调试快照"}
                </button>
                {showDebug ? <pre className="pf-debug-panel">{JSON.stringify(snapshot, null, 2)}</pre> : null}
              </>
            ) : null}
          </aside>
        </section>
      )}
    </main>
  );
}
