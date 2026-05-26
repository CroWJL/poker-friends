import { PlayingCard } from "./PlayingCard";

interface TableHeaderProps {
  tableId: string;
  tokenReady: boolean;
  currentActionPlayer: string;
  isMyTurn: boolean;
  stage?: string;
  pot?: number;
  currentBet?: number;
  communityCards: string[];
  potAwards: Array<{ playerId: string; amount: number }>;
  sidePots: Array<{ amount: number; eligiblePlayerIds: string[] }>;
  inHandCount: number;
  canActCount: number;
  actionCountInStage: number;
}

export function TableHeader({
  tableId,
  tokenReady,
  currentActionPlayer,
  isMyTurn,
  stage,
  pot,
  currentBet,
  communityCards,
  potAwards,
  sidePots,
  inHandCount,
  canActCount,
  actionCountInStage
}: TableHeaderProps) {
  const stageLabel = stage ?? "-";

  return (
    <section className="pf-board-card">
      <div className="pf-board-meta">
        <div className="pf-chip">
          <span>牌桌</span>
          <strong>{tableId || "-"}</strong>
        </div>
        <div className="pf-chip">
          <span>会话</span>
          <strong>{tokenReady ? "已获取" : "未获取"}</strong>
        </div>
        <div className="pf-chip">
          <span>阶段</span>
          <strong className="pf-stage-pill">{stageLabel}</strong>
        </div>
      </div>

      <div className="pf-board-topline">
        <div>
          <p className="pf-muted-label">底池</p>
          <p className="pf-money">¥ {pot ?? 0}</p>
        </div>
        <div>
          <p className="pf-muted-label">当前注</p>
          <p className="pf-money-sub">¥ {currentBet ?? 0}</p>
        </div>
        <div>
          <p className="pf-muted-label">行动玩家</p>
          <p className="pf-inline-value">{currentActionPlayer}</p>
        </div>
        <div>
          <p className="pf-muted-label">我的回合</p>
          <p className={`pf-inline-value ${isMyTurn ? "pf-turn-yes" : "pf-turn-no"}`}>{isMyTurn ? "是" : "否"}</p>
        </div>
      </div>
      <div className="pf-status-strip">
        <span>在手人数：{inHandCount}</span>
        <span>可行动人数：{canActCount}</span>
        <span>本阶段动作：{actionCountInStage}</span>
      </div>

      <div className="pf-community-area">
        <p className="pf-section-title">公共牌</p>
        <div className="pf-community-cards">
          {communityCards.length > 0 ? (
            communityCards.map((card, index) => (
              <span className="pf-deal-in" style={{ animationDelay: `${index * 120}ms` }} key={`${card}-${index}`}>
                <PlayingCard card={card} />
              </span>
            ))
          ) : (
            <span className="pf-empty-text">等待发牌</span>
          )}
        </div>
      </div>

      <div className="pf-split-grid">
        <div>
          <p className="pf-section-title">最近派奖</p>
          <p className="pf-award-line">
            {potAwards.length > 0 ? potAwards.map((award) => `${award.playerId} +${award.amount}`).join(" / ") : "无"}
          </p>
        </div>
        <div>
          <p className="pf-section-title">边池</p>
          <p className="pf-award-line">
            {sidePots.length > 0
              ? sidePots.map((sidePot, index) => `#${index + 1} ${sidePot.amount}`).join(" / ")
              : "无"}
          </p>
        </div>
      </div>
    </section>
  );
}
