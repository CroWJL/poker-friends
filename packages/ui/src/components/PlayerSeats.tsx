import type { PlayerView } from "@poker-friends/game-client";
import { PlayingCard } from "./PlayingCard";

interface PlayerSeatsProps {
  players: PlayerView[];
  roomId?: string;
  waitingForPlayers?: boolean;
  waitingMessage?: string;
  actionPlayerId?: string;
  selfPlayerId?: string;
  tableFinished?: boolean;
  stage?: string;
  pot?: number;
  communityCards?: string[];
  dealerPlayerId?: string;
  smallBlindPlayerId?: string;
  bigBlindPlayerId?: string;
  currentActionPlayer?: string;
}

interface OpponentPosition {
  top: string;
  left: string;
  variant?: "side";
}

function getOpponentLayout(count: number) {
  if (count <= 0) {
    return [];
  }
  const slots = Math.min(count, 7);
  if (slots === 7) {
    return [
      { top: "85%", left: "20%" },
      { top: "46%", left: "6%", variant: "side" },
      { top: "8%", left: "20%" },
      { top: "5%", left: "50%" },
      { top: "8%", left: "80%" },
      { top: "46%", left: "94%", variant: "side" },
      { top: "85%", left: "80%" }
    ] as OpponentPosition[];
  }
  const startAngleDeg = 200;
  const endAngleDeg = 340;
  const centerLeft = 50;
  const centerTop = 46;
  const radiusX = 38;
  const radiusY = 34;
  const step = slots === 1 ? 0 : (endAngleDeg - startAngleDeg) / (slots - 1);
  return Array.from({ length: slots }, (_, index): OpponentPosition => {
    const angle = ((startAngleDeg + step * index) * Math.PI) / 180;
    const left = centerLeft + radiusX * Math.cos(angle);
    const top = centerTop + radiusY * Math.sin(angle);
    return {
      top: `${top.toFixed(1)}%`,
      left: `${left.toFixed(1)}%`
    };
  });
}

function renderCards(
  player: PlayerView,
  tableFinished: boolean | undefined,
  selfPlayerId: string | undefined,
  waitingForPlayers: boolean | undefined
) {
  if (waitingForPlayers) {
    return null;
  }
  if (player.waitingForNextHand) {
    return null;
  }
  if (player.holeCards?.length === 2 && (tableFinished || player.playerId === selfPlayerId)) {
    return player.holeCards.map((card, cardIndex) => (
      <span className="pf-hole-stack-card" key={`${player.playerId}-${card}-${cardIndex}`}>
        <PlayingCard card={card} />
      </span>
    ));
  }
  return [
    <span className="pf-hole-stack-card" key="hidden-1">
      <PlayingCard card="??" />
    </span>,
    <span className="pf-hole-stack-card" key="hidden-2">
      <PlayingCard card="??" />
    </span>
  ];
}

export function PlayerSeats({
  players,
  roomId,
  waitingForPlayers,
  waitingMessage,
  actionPlayerId,
  selfPlayerId,
  tableFinished,
  stage,
  pot,
  communityCards = [],
  dealerPlayerId,
  smallBlindPlayerId,
  bigBlindPlayerId,
  currentActionPlayer
}: PlayerSeatsProps) {
  const orderedPlayers = [...players].sort((left, right) => left.seat - right.seat);
  const selfPlayer = orderedPlayers.find((player) => player.playerId === selfPlayerId) ?? orderedPlayers.at(-1);
  const selfSeat = selfPlayer?.seat;
  const maxSeat = orderedPlayers.at(-1)?.seat ?? 0;
  const seatDistance = (seat: number) => {
    if (selfSeat == null || maxSeat <= 0) {
      return 0;
    }
    return seat > selfSeat ? seat - selfSeat : seat + maxSeat - selfSeat;
  };
  const opponents =
    selfSeat == null
      ? orderedPlayers.filter((player) => player.playerId !== selfPlayer?.playerId)
      : orderedPlayers.filter((player) => player.playerId !== selfPlayer.playerId).sort((left, right) => seatDistance(left.seat) - seatDistance(right.seat));
  const opponentPositions = getOpponentLayout(opponents.length);
  const centerCards = Array.from({ length: 5 }, (_, index) => communityCards[index] ?? "??");
  const getRoleTags = (playerId: string) => {
    const tags: Array<{ key: string; label: string; tone: "dealer" | "small-blind" | "big-blind" }> = [];
    if (playerId === dealerPlayerId) {
      tags.push({ key: "D", label: "庄", tone: "dealer" });
    }
    if (playerId === smallBlindPlayerId) {
      tags.push({ key: "SB", label: "小盲", tone: "small-blind" });
    }
    if (playerId === bigBlindPlayerId) {
      tags.push({ key: "BB", label: "大盲", tone: "big-blind" });
    }
    return tags;
  };

  return (
    <section className="pf-seat-section">
      <h3 className="pf-panel-title">房间号：{roomId || "-"}</h3>
      <div className="pf-round-table">
        <div className="pf-table-oval-ring" />
        <div className="pf-table-oval-felt" />

        <div className="pf-community-center">
          <div className="pf-community-center-cards">
            {centerCards.map((card, index) => (
              <PlayingCard card={card} key={`${card}-${index}`} />
            ))}
          </div>
          <div className="pf-table-info-panel">
            <p className="pf-table-center-label">当前阶段</p>
            <p className="pf-table-center-stage">{waitingForPlayers ? "等待中" : stage ?? "-"}</p>
            <p className="pf-table-center-label">底池</p>
            <p className="pf-table-center-pot">¥ {pot ?? 0}</p>
            <p className="pf-table-center-label">行动</p>
            <p className="pf-table-center-action">{waitingForPlayers ? waitingMessage ?? "等待中" : currentActionPlayer ?? "-"}</p>
          </div>
        </div>

        {opponents.map((player, index) => {
          const position = opponentPositions[index] ?? { top: "30%", left: "50%" };
          const isSideSeat = position.variant === "side";
          return (
            <div
              key={player.playerId}
              className={`pf-seat-anchor pf-seat-anchor-opponent ${isSideSeat ? "pf-seat-anchor-side" : ""}`}
              style={{ top: position.top, left: position.left }}
            >
              <div
                className={`pf-seat-card pf-seat-card-opponent ${isSideSeat ? "pf-seat-card-side" : ""} ${
                  player.playerId === actionPlayerId ? "pf-seat-active" : ""
                }`}
              >
                <div className="pf-seat-content">
                  <div className="pf-seat-main">
                    <div className="pf-seat-head">
                      <strong>{player.playerName}</strong>
                      <div className="pf-seat-role-tags">
                        {getRoleTags(player.playerId).map((tag) => (
                          <span className={`pf-seat-role-tag pf-seat-role-tag-${tag.tone}`} key={`${player.playerId}-${tag.key}`}>
                            {tag.label}
                          </span>
                        ))}
                      </div>
                    </div>
                    <div className="pf-seat-line">筹码：{player.stack}</div>
                    <div className="pf-seat-line">本轮下注：{player.betThisRound}</div>
                    <div className="pf-seat-line">本手投入：{player.totalCommitted}</div>
                  </div>
                  {!waitingForPlayers && !player.waitingForNextHand ? (
                    <div className="pf-seat-cards pf-hole-stack">
                      <div className="pf-hole-stack-pile">{renderCards(player, tableFinished, selfPlayerId, waitingForPlayers)}</div>
                    </div>
                  ) : null}
                </div>
              </div>
            </div>
          );
        })}

        {selfPlayer ? (
          <div className="pf-seat-anchor pf-seat-anchor-self" style={{ top: "91%", left: "50%" }}>
            <div
              className={`pf-seat-card pf-seat-card-self ${selfPlayer.playerId === actionPlayerId ? "pf-seat-active" : ""} pf-seat-self`}
            >
              <div className="pf-seat-content">
                <div className="pf-seat-main">
                  <div className="pf-seat-head">
                    <strong>{selfPlayer.playerName}（你）</strong>
                    <div className="pf-seat-role-tags">
                      {getRoleTags(selfPlayer.playerId).map((tag) => (
                        <span className={`pf-seat-role-tag pf-seat-role-tag-${tag.tone}`} key={`${selfPlayer.playerId}-${tag.key}`}>
                          {tag.label}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="pf-seat-line">筹码：{selfPlayer.stack}</div>
                  <div className="pf-seat-line">本轮下注：{selfPlayer.betThisRound}</div>
                  <div className="pf-seat-line">本手投入：{selfPlayer.totalCommitted}</div>
                </div>
                {!waitingForPlayers && !selfPlayer.waitingForNextHand ? (
                  <div className="pf-seat-cards pf-hole-stack">
                    <div className="pf-hole-stack-pile">{renderCards(selfPlayer, tableFinished, selfPlayerId, waitingForPlayers)}</div>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </section>
  );
}
