interface PotAward {
  playerId: string;
  amount: number;
}

interface SettlementOverlayProps {
  awards: PotAward[];
  resolvePlayerName: (playerId: string) => string;
  countdownSeconds: number;
  onClose: () => void;
}

export function SettlementOverlay({ awards, resolvePlayerName, countdownSeconds, onClose }: SettlementOverlayProps) {
  return (
    <div className="pf-settlement-mask" role="dialog" aria-modal="true">
      <div className="pf-settlement-card">
        <h3>本手结算</h3>
        <p className="pf-settlement-subtitle">派奖已完成，将在 {countdownSeconds} 秒后自动关闭</p>
        <ul className="pf-settlement-list">
          {awards.map((award) => (
            <li key={`${award.playerId}-${award.amount}`}>
              <span>{resolvePlayerName(award.playerId)}</span>
              <strong>+{award.amount}</strong>
            </li>
          ))}
        </ul>
        <button className="pf-settlement-close" onClick={onClose}>
          继续下一手
        </button>
      </div>
    </div>
  );
}
