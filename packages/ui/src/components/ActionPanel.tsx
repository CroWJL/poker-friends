import type { ActionCommand, LegalActionState } from "@poker-friends/game-client";
import { useMemo, useState } from "react";

interface ActionPanelProps {
  legalActions: LegalActionState;
  actionPending: boolean;
  stack: number;
  betThisRound: number;
  onSendAction: (action: ActionCommand) => void;
}

export function ActionPanel({ legalActions, actionPending, stack, betThisRound, onSendAction }: ActionPanelProps) {
  const [raiseTo, setRaiseTo] = useState(20);
  const minRaiseTo = legalActions.minRaiseTo || 20;
  const maxRaiseTo = Math.max(minRaiseTo, betThisRound + stack);
  const defaultRaiseTo = useMemo(
    () => Math.min(maxRaiseTo, Math.max(raiseTo, minRaiseTo)),
    [raiseTo, minRaiseTo, maxRaiseTo]
  );
  const quickRaiseValues = useMemo(() => {
    const base = minRaiseTo;
    return [base, Math.min(maxRaiseTo, base * 2), Math.min(maxRaiseTo, base * 3)];
  }, [minRaiseTo, maxRaiseTo]);
  const disabledAll = actionPending;

  return (
    <section className={`pf-action-panel ${actionPending ? "pf-action-panel-pending" : ""}`}>
      <h3 className="pf-panel-title">行动面板</h3>
      <div className="pf-action-grid">
        <button className="pf-action-btn pf-action-btn-check" onClick={() => onSendAction({ type: "CHECK" })} disabled={disabledAll || !legalActions.canCheck}>
          Check
        </button>
        <button className="pf-action-btn pf-action-btn-call" onClick={() => onSendAction({ type: "CALL" })} disabled={disabledAll || !legalActions.canCall}>
          Call {legalActions.callAmount > 0 ? legalActions.callAmount : ""}
        </button>
        <button className="pf-action-btn pf-action-btn-danger" onClick={() => onSendAction({ type: "FOLD" })} disabled={disabledAll || !legalActions.canFold}>
          Fold
        </button>
        <button className="pf-action-btn pf-action-btn-allin" onClick={() => onSendAction({ type: "ALL_IN" })} disabled={disabledAll || !legalActions.canAllIn}>
          All-in
        </button>
      </div>
      <div className="pf-raise-row">
        <input
          className="pf-raise-input"
          type="number"
          min={minRaiseTo}
          max={maxRaiseTo}
          value={defaultRaiseTo}
          onChange={(event) => setRaiseTo(Number(event.target.value || 0))}
          disabled={disabledAll || !legalActions.canRaise}
        />
        <button
          className="pf-action-btn pf-action-btn-raise"
          onClick={() => onSendAction({ type: "RAISE", amount: defaultRaiseTo })}
          disabled={disabledAll || !legalActions.canRaise}
        >
          Raise
        </button>
      </div>
      <input
        className="pf-raise-slider"
        type="range"
        min={minRaiseTo}
        max={maxRaiseTo}
        step={1}
        value={defaultRaiseTo}
        onChange={(event) => setRaiseTo(Number(event.target.value || minRaiseTo))}
        disabled={disabledAll || !legalActions.canRaise}
      />
      <div className="pf-quick-raise-row">
        {quickRaiseValues.map((value) => (
          <button
            key={value}
            className="pf-quick-raise-btn"
            onClick={() => setRaiseTo(value)}
            disabled={disabledAll || !legalActions.canRaise}
          >
            {value}
          </button>
        ))}
      </div>
      <p className="pf-action-hint">
        最小加注到：{minRaiseTo} · 最大加注到：{maxRaiseTo}
        {actionPending ? " · 动作提交中..." : ""}
      </p>
    </section>
  );
}
