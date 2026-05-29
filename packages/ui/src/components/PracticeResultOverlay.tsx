interface PracticeResultOverlayProps {
  outcome: "WIN" | "LOSE";
  onConfirm: () => void;
}

export function PracticeResultOverlay({ outcome, onConfirm }: PracticeResultOverlayProps) {
  const title = outcome === "WIN" ? "牛逼" : "菜逼";
  const subtitle = outcome === "WIN" ? "你击败了所有机器人" : "你的筹码已用尽";

  return (
    <div className="pf-practice-result-overlay" role="dialog" aria-modal="true">
      <div className="pf-practice-result-card">
        <h2 className={`pf-practice-result-title ${outcome === "WIN" ? "is-win" : "is-lose"}`}>{title}</h2>
        <p className="pf-practice-result-subtitle">{subtitle}</p>
        <button type="button" className="pf-practice-result-btn" onClick={onConfirm}>
          确认
        </button>
      </div>
    </div>
  );
}
