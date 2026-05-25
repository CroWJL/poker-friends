import type { TableSnapshot } from "./protocol";

export interface LegalActionState {
  canFold: boolean;
  canCheck: boolean;
  canCall: boolean;
  callAmount: number;
  canRaise: boolean;
  minRaiseTo: number;
  canAllIn: boolean;
}

export function deriveLegalActions(snapshot: TableSnapshot | undefined, selfPlayerId: string): LegalActionState {
  if (!snapshot || !selfPlayerId || snapshot.actionPlayerId !== selfPlayerId) {
    return {
      canFold: false,
      canCheck: false,
      canCall: false,
      callAmount: 0,
      canRaise: false,
      minRaiseTo: 0,
      canAllIn: false
    };
  }
  const self = snapshot.players.find((player) => player.playerId === selfPlayerId);
  if (!self || !self.inHand) {
    return {
      canFold: false,
      canCheck: false,
      canCall: false,
      callAmount: 0,
      canRaise: false,
      minRaiseTo: 0,
      canAllIn: false
    };
  }
  const callAmount = Math.max(0, snapshot.currentBet - self.betThisRound);
  const canCheck = callAmount === 0;
  const canCall = callAmount > 0 && self.stack >= callAmount;
  const minRaiseDelta = Math.max(1, snapshot.currentBet || 1);
  const minRaiseTo = snapshot.currentBet + minRaiseDelta;
  const canRaise = self.stack + self.betThisRound > snapshot.currentBet && self.stack > callAmount;
  const canAllIn = self.stack > 0;
  return {
    canFold: true,
    canCheck,
    canCall,
    callAmount,
    canRaise,
    minRaiseTo,
    canAllIn
  };
}
