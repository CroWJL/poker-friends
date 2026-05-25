import type { PlayerView } from "@poker-friends/game-client";
import { PlayingCard } from "./PlayingCard";

interface PlayerSeatsProps {
  players: PlayerView[];
  actionPlayerId?: string;
  selfPlayerId?: string;
  tableFinished?: boolean;
  stage?: string;
  pot?: number;
}

const opponentLayouts: Record<number, Array<{ top: string; left: string }>> = {
  1: [{ top: "18%", left: "50%" }],
  2: [
    { top: "18%", left: "32%" },
    { top: "18%", left: "68%" }
  ],
  3: [
    { top: "14%", left: "50%" },
    { top: "28%", left: "20%" },
    { top: "28%", left: "80%" }
  ],
  4: [
    { top: "14%", left: "32%" },
    { top: "14%", left: "68%" },
    { top: "34%", left: "18%" },
    { top: "34%", left: "82%" }
  ],
  5: [
    { top: "12%", left: "22%" },
    { top: "12%", left: "50%" },
    { top: "12%", left: "78%" },
    { top: "34%", left: "16%" },
    { top: "34%", left: "84%" }
  ]
};

function getOpponentLayout(count: number) {
  if (count <= 0) {
    return [];
  }
  if (count >= 5) {
    return opponentLayouts[5];
  }
  return opponentLayouts[count];
}

function renderCards(player: PlayerView, tableFinished: boolean | undefined, selfPlayerId: string | undefined) {
  if (player.holeCards?.length === 2 && (tableFinished || player.playerId === selfPlayerId)) {
    return player.holeCards.map((card, cardIndex) => (
      <PlayingCard card={card} key={`${player.playerId}-${card}-${cardIndex}`} />
    ));
  }
  return [<PlayingCard card="??" key="hidden-1" />, <PlayingCard card="??" key="hidden-2" />];
}

export function PlayerSeats({ players, actionPlayerId, selfPlayerId, tableFinished, stage, pot }: PlayerSeatsProps) {
  const selfPlayer = players.find((player) => player.playerId === selfPlayerId) ?? players.at(-1);
  const opponents = players.filter((player) => player.playerId !== selfPlayer?.playerId);
  const opponentPositions = getOpponentLayout(opponents.length);

  return (
    <section className="pf-seat-section">
      <h3 className="pf-panel-title">玩家座位</h3>
      <div className="pf-round-table">
        <div className="pf-table-center">
          <p className="pf-table-center-label">当前阶段</p>
          <p className="pf-table-center-stage">{stage ?? "-"}</p>
          <p className="pf-table-center-label">底池</p>
          <p className="pf-table-center-pot">¥ {pot ?? 0}</p>
        </div>

        {opponents.map((player, index) => {
          const position = opponentPositions[index] ?? { top: "30%", left: "50%" };
          return (
            <div
              key={player.playerId}
              className="pf-seat-anchor pf-seat-anchor-opponent"
              style={{ top: position.top, left: position.left }}
            >
              <div
                className={`pf-seat-card pf-seat-card-opponent ${player.playerId === actionPlayerId ? "pf-seat-active" : ""}`}
              >
                <div className="pf-seat-head">
                  <strong>{player.playerName}</strong>
                  <span className="pf-seat-pos">座位 {player.seat}</span>
                </div>
                <div className="pf-seat-line">筹码：{player.stack}</div>
                <div className="pf-seat-line">本轮下注：{player.betThisRound}</div>
                <div className="pf-seat-line">本手投入：{player.totalCommitted}</div>
                <div className="pf-seat-line">
                  状态：{player.waitingForNextHand ? "等待下一局" : player.inHand ? "在手" : "弃牌"}
                </div>
                <div className="pf-seat-cards">
                  <span className="pf-muted-label">手牌</span>
                  {renderCards(player, tableFinished, selfPlayerId)}
                </div>
              </div>
            </div>
          );
        })}

        {selfPlayer ? (
          <div className="pf-seat-anchor pf-seat-anchor-self" style={{ top: "82%", left: "50%" }}>
            <div
              className={`pf-seat-card pf-seat-card-self ${selfPlayer.playerId === actionPlayerId ? "pf-seat-active" : ""} pf-seat-self`}
            >
              <div className="pf-seat-head">
                <strong>{selfPlayer.playerName}（你）</strong>
                <span className="pf-seat-pos">座位 {selfPlayer.seat}</span>
              </div>
              <div className="pf-seat-line">筹码：{selfPlayer.stack}</div>
              <div className="pf-seat-line">本轮下注：{selfPlayer.betThisRound}</div>
              <div className="pf-seat-line">本手投入：{selfPlayer.totalCommitted}</div>
              <div className="pf-seat-line">
                状态：{selfPlayer.waitingForNextHand ? "等待下一局" : selfPlayer.inHand ? "在手" : "弃牌"}
              </div>
              <div className="pf-seat-cards">
                <span className="pf-muted-label">手牌</span>
                {renderCards(selfPlayer, tableFinished, selfPlayerId)}
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </section>
  );
}
