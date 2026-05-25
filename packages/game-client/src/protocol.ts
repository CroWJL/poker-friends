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
}

export interface ActionCommand {
  type: PlayerActionType;
  amount?: number;
}

export interface WsClientMessage {
  event: "ACTION" | "PING";
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

export interface TableSnapshot {
  tableId: string;
  handId: string;
  stage: "PREFLOP" | "FLOP" | "TURN" | "RIVER" | "SHOWDOWN" | "FINISHED";
  pot: number;
  currentBet: number;
  actionPlayerId?: string;
  communityCards: string[];
  sidePots?: SidePotView[];
  potAwards?: Array<{ playerId: string; amount: number }>;
  players: PlayerView[];
}

export interface WsServerMessage {
  event: "TABLE_SNAPSHOT" | "ACTION_RESULT" | "ERROR" | "PONG";
  payload: unknown;
}
