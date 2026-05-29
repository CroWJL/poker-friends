export type PlayerActionType = "FOLD" | "CHECK" | "CALL" | "RAISE" | "ALL_IN";

export interface CreateRoomRequest {
  hostName: string;
  smallBlind: number;
  bigBlind: number;
  maxPlayers: number;
}

export interface JoinRoomRequest {
  roomId: string;
  playerName: string;
}

export interface RoomResponse {
  roomId: string;
  tableId: string;
  playerId: string;
  token: string;
  walletBalance: number;
}

export interface WalletResponse {
  displayName: string;
  walletBalance: number;
}

export interface UserProfileResponse {
  userId: string;
  displayName: string;
  walletBalance: number;
}

export type WalletTransactionType = "TABLE_BUY_IN" | "AUTO_REBUY" | "TABLE_CASH_OUT";

export interface WalletTransactionResponse {
  id: number;
  type: WalletTransactionType;
  amount: number;
  balanceAfter: number;
  roomId?: string | null;
  tableId?: string | null;
  playerId?: string | null;
  createdAt: string;
}

export interface LeaveRoomRequest {
  playerName: string;
}

export interface LeaveRoomResponse {
  walletBalance: number;
}

export interface ActionCommand {
  type: PlayerActionType;
  amount?: number;
}

export interface WsClientMessage {
  event: "ACTION" | "PING" | "START_GAME" | "PRACTICE_ACK";
  payload?: ActionCommand;
}

export interface PlayerView {
  playerId: string;
  playerName: string;
  seat: number;
  stack: number;
  inHand: boolean;
  waitingForNextHand?: boolean;
  betThisRound: number;
  totalCommitted: number;
  holeCards: string[];
}

export interface SidePotView {
  amount: number;
  eligiblePlayerIds: string[];
}

export type PracticeOutcome = "WIN" | "LOSE";

export interface TableSnapshot {
  tableId: string;
  handId: string;
  stage: "WAITING" | "PREFLOP" | "FLOP" | "TURN" | "RIVER" | "SHOWDOWN" | "FINISHED";
  started: boolean;
  practiceMode?: boolean;
  practiceOutcome?: PracticeOutcome | null;
  pot: number;
  currentBet: number;
  actionPlayerId?: string;
  dealerPlayerId?: string;
  smallBlindPlayerId?: string;
  bigBlindPlayerId?: string;
  communityCards: string[];
  sidePots?: SidePotView[];
  potAwards?: Array<{ playerId: string; amount: number; bestFiveCards?: string[]; handType?: string }>;
  players: PlayerView[];
}

export interface WsServerMessage {
  event: "TABLE_SNAPSHOT" | "ACTION_RESULT" | "ACTION_EVENT" | "ERROR" | "PONG";
  payload: unknown;
}
