import { PlayingCard } from "./PlayingCard";

interface PotAward {
  playerId: string;
  amount: number;
  bestFiveCards?: string[];
  handType?: string;
}

interface SettlementOverlayProps {
  awards: PotAward[];
  resolvePlayerName: (playerId: string) => string;
  countdownSeconds: number;
}

export function SettlementOverlay({ awards, resolvePlayerName, countdownSeconds }: SettlementOverlayProps) {
  return (
    <div className="pf-settlement-mask" role="dialog" aria-modal="true">
      <div className="pf-settlement-card">
        <h3>本局结算</h3>
        <p className="pf-settlement-subtitle">{countdownSeconds} 秒后开始下一局</p>
        <ul className="pf-settlement-list">
          {awards.map((award) => (
            <li key={`${award.playerId}-${award.amount}`}>
              <div className="pf-settlement-winner-row">
                <span className="pf-settlement-winner-name">{resolvePlayerName(award.playerId)}</span>
                <span className="pf-settlement-hand-type">{award.handType || "未摊牌获胜"}</span>
                <strong className="pf-settlement-amount">+{award.amount}</strong>
              </div>
              {award.bestFiveCards && award.bestFiveCards.length === 5 ? (
                <div className="pf-settlement-cards">
                  {award.bestFiveCards.map((card, index) => (
                    <PlayingCard key={`${award.playerId}-${card}-${index}`} card={card} />
                  ))}
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
