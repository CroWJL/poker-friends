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
  layout?: "desktop" | "mobile";
}

interface OpponentPosition {
  top: string;
  left: string;
  variant?: "side";
}

const STAGE_LABELS: Record<string, string> = {
  WAITING: "等待中",
  PREFLOP: "翻牌前",
  FLOP: "翻牌",
  TURN: "转牌",
  RIVER: "河牌",
  SHOWDOWN: "摊牌",
  FINISHED: "结算"
};

const EIGHT_MAX_SEAT_LAYOUT: Record<number, OpponentPosition> = {
  2: { top: "85%", left: "20%" },
  3: { top: "46%", left: "6%", variant: "side" },
  4: { top: "8%", left: "20%" },
  5: { top: "5%", left: "50%" },
  6: { top: "8%", left: "80%" },
  7: { top: "46%", left: "94%", variant: "side" },
  8: { top: "85%", left: "80%" }
};

function getPositionForSeat(seat: number): OpponentPosition {
  return EIGHT_MAX_SEAT_LAYOUT[seat] ?? { top: "30%", left: "50%" };
}

function formatStage(stage?: string, waitingForPlayers?: boolean) {
  if (waitingForPlayers) {
    return "等待中";
  }
  if (!stage) {
    return "-";
  }
  return STAGE_LABELS[stage] ?? stage;
}

function renderCards(
  player: PlayerView,
  tableFinished: boolean | undefined,
  selfPlayerId: string | undefined,
  waitingForPlayers: boolean | undefined
) {
  if (waitingForPlayers || player.waitingForNextHand) {
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

function getRoleTags(
  playerId: string,
  dealerPlayerId?: string,
  smallBlindPlayerId?: string,
  bigBlindPlayerId?: string
) {
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
}

function TableInfoPanel({
  waitingForPlayers,
  stage,
  pot,
  waitingMessage,
  currentActionPlayer
}: {
  waitingForPlayers?: boolean;
  stage?: string;
  pot?: number;
  waitingMessage?: string;
  currentActionPlayer?: string;
}) {
  return (
    <div className="pf-table-info-panel">
      <p className="pf-table-center-label">当前阶段</p>
      <p className="pf-table-center-stage">{waitingForPlayers ? "等待中" : (stage ?? "-")}</p>
      <p className="pf-table-center-label">底池</p>
      <p className="pf-table-center-pot">¥ {pot ?? 0}</p>
      <p className="pf-table-center-label">行动</p>
      <p className="pf-table-center-action">{waitingForPlayers ? (waitingMessage ?? "等待中") : (currentActionPlayer ?? "-")}</p>
    </div>
  );
}

function MobilePlayerSeats({
  opponents,
  selfPlayer,
  centerCards,
  roomId,
  waitingForPlayers,
  waitingMessage,
  actionPlayerId,
  selfPlayerId,
  tableFinished,
  stage,
  pot,
  dealerPlayerId,
  smallBlindPlayerId,
  bigBlindPlayerId,
  currentActionPlayer
}: {
  opponents: PlayerView[];
  selfPlayer?: PlayerView;
  centerCards: string[];
  roomId?: string;
  waitingForPlayers?: boolean;
  waitingMessage?: string;
  actionPlayerId?: string;
  selfPlayerId?: string;
  tableFinished?: boolean;
  stage?: string;
  pot?: number;
  dealerPlayerId?: string;
  smallBlindPlayerId?: string;
  bigBlindPlayerId?: string;
  currentActionPlayer?: string;
}) {
  const stageLabel = formatStage(stage, waitingForPlayers);
  const actionLabel = waitingForPlayers ? (waitingMessage ?? "等待中") : (currentActionPlayer ?? "-");

  return (
    <section className="pf-seat-section pf-seat-section-mobile">
      <div className="pf-mobile-table-board">
        <div className="pf-mobile-table-meta">
          <span className="pf-mobile-room-id">#{roomId || "-"}</span>
          <span className="pf-mobile-meta-divider">·</span>
          <span>{stageLabel}</span>
          <span className="pf-mobile-meta-divider">·</span>
          <span className="pf-mobile-meta-pot">底池 ¥{pot ?? 0}</span>
          <span className="pf-mobile-meta-divider">·</span>
          <span className="pf-mobile-meta-action">行动 {actionLabel}</span>
        </div>

        {opponents.length > 0 ? (
          <div className="pf-mobile-opponents-grid" aria-label="对手列表">
            {opponents.map((player) => {
              const roleTags = getRoleTags(player.playerId, dealerPlayerId, smallBlindPlayerId, bigBlindPlayerId);
              const isActive = player.playerId === actionPlayerId;
              return (
                <div
                  key={player.playerId}
                  className={`pf-mobile-opponent-chip ${isActive ? "pf-mobile-opponent-chip-active" : ""} ${
                    player.waitingForNextHand ? "pf-mobile-opponent-chip-waiting" : ""
                  }`}
                >
                  <div className="pf-mobile-opponent-head">
                    <strong>{player.playerName}</strong>
                    {roleTags.map((tag) => (
                      <span className={`pf-seat-role-tag pf-seat-role-tag-${tag.tone}`} key={`${player.playerId}-${tag.key}`}>
                        {tag.label}
                      </span>
                    ))}
                  </div>
                  <div className="pf-mobile-opponent-stats">
                    <span>{player.stack}</span>
                    {player.betThisRound > 0 ? <span className="pf-mobile-opponent-bet">+{player.betThisRound}</span> : null}
                  </div>
                </div>
              );
            })}
          </div>
        ) : null}

        <div className="pf-mobile-table-center">
          <div className="pf-mobile-community-cards">
            {centerCards.map((card, index) => (
              <PlayingCard card={card} key={`${card}-${index}`} />
            ))}
          </div>
        </div>

        {selfPlayer ? (
          <div
            className={`pf-mobile-self-bar ${selfPlayer.playerId === actionPlayerId ? "pf-mobile-self-bar-active" : ""}`}
          >
            <div className="pf-mobile-self-info">
              <div className="pf-mobile-self-head">
                <strong>{selfPlayer.playerName}（你）</strong>
                {getRoleTags(selfPlayer.playerId, dealerPlayerId, smallBlindPlayerId, bigBlindPlayerId).map((tag) => (
                  <span className={`pf-seat-role-tag pf-seat-role-tag-${tag.tone}`} key={`${selfPlayer.playerId}-${tag.key}`}>
                    {tag.label}
                  </span>
                ))}
              </div>
              <div className="pf-mobile-self-stats">
                <span>筹码 {selfPlayer.stack}</span>
                <span>本轮 {selfPlayer.betThisRound}</span>
              </div>
            </div>
            {!waitingForPlayers && !selfPlayer.waitingForNextHand ? (
              <div className="pf-mobile-self-cards pf-hole-stack">
                <div className="pf-hole-stack-pile">{renderCards(selfPlayer, tableFinished, selfPlayerId, waitingForPlayers)}</div>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
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
  currentActionPlayer,
  layout = "desktop"
}: PlayerSeatsProps) {
  const orderedPlayers = [...players].sort((left, right) => left.seat - right.seat);
  const selfPlayer = orderedPlayers.find((player) => player.playerId === selfPlayerId) ?? orderedPlayers.at(-1);
  const selfSeat = selfPlayer?.seat;
  const opponents = orderedPlayers
    .filter((player) => player.playerId !== selfPlayer?.playerId && player.seat !== selfSeat)
    .sort((left, right) => left.seat - right.seat);
  const centerCards = Array.from({ length: 5 }, (_, index) => communityCards[index] ?? "??");

  if (layout === "mobile") {
    return (
      <MobilePlayerSeats
        opponents={opponents}
        selfPlayer={selfPlayer}
        centerCards={centerCards}
        roomId={roomId}
        waitingForPlayers={waitingForPlayers}
        waitingMessage={waitingMessage}
        actionPlayerId={actionPlayerId}
        selfPlayerId={selfPlayerId}
        tableFinished={tableFinished}
        stage={stage}
        pot={pot}
        dealerPlayerId={dealerPlayerId}
        smallBlindPlayerId={smallBlindPlayerId}
        bigBlindPlayerId={bigBlindPlayerId}
        currentActionPlayer={currentActionPlayer}
      />
    );
  }

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
          <TableInfoPanel
            waitingForPlayers={waitingForPlayers}
            stage={stage}
            pot={pot}
            waitingMessage={waitingMessage}
            currentActionPlayer={currentActionPlayer}
          />
        </div>

        {opponents.map((player) => {
          const position = getPositionForSeat(player.seat);
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
                        {getRoleTags(player.playerId, dealerPlayerId, smallBlindPlayerId, bigBlindPlayerId).map((tag) => (
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
                      {getRoleTags(selfPlayer.playerId, dealerPlayerId, smallBlindPlayerId, bigBlindPlayerId).map((tag) => (
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
