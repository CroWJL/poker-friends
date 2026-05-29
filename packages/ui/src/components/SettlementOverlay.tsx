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
  nextHandHint?: string;
  requireConfirm?: boolean;
  onConfirm?: () => void;
}

export function SettlementOverlay({
  awards,
  resolvePlayerName,
  countdownSeconds,
  nextHandHint,
  requireConfirm = false,
  onConfirm
}: SettlementOverlayProps) {
  const subtitle = requireConfirm
    ? "点击确认开始下一局"
    : (nextHandHint ?? `${countdownSeconds} 秒后开始下一局`);
  return (
    <div className="pf-settlement-mask" role="dialog" aria-modal="true">
      <div className="pf-settlement-card">
        <div className="pf-settlement-header">
          <h3>本局结算</h3>
          <p className="pf-settlement-subtitle">{subtitle}</p>
        </div>
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
        {requireConfirm ? (
          <div className="pf-settlement-footer">
            <button type="button" className="pf-settlement-confirm-btn" onClick={onConfirm}>
              确认
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
