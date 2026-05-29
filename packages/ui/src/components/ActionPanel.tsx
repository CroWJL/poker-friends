import type { ActionCommand, LegalActionState } from "@poker-friends/game-client";
import { useMemo, useState } from "react";

interface ActionPanelProps {
  legalActions: LegalActionState;
  actionPending: boolean;
  stack: number;
  betThisRound: number;
  disabled?: boolean;
  disabledReason?: string;
  variant?: "default" | "compact";
  onSendAction: (action: ActionCommand) => void;
}

export function ActionPanel({
  legalActions,
  actionPending,
  stack,
  betThisRound,
  disabled = false,
  disabledReason,
  variant = "default",
  onSendAction
}: ActionPanelProps) {
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
  const disabledAll = actionPending || disabled;
  const isCompact = variant === "compact";

  return (
    <section
      className={`pf-action-panel ${isCompact ? "pf-action-panel-compact" : ""} ${
        actionPending ? "pf-action-panel-pending" : ""
      }`}
    >
      {!isCompact ? <h3 className="pf-panel-title">行动面板</h3> : null}
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
      {isCompact ? (
        <div className="pf-quick-raise-row pf-quick-raise-row-compact">
          {quickRaiseValues.map((value) => (
            <button
              key={value}
              className="pf-quick-raise-btn"
              onClick={() => onSendAction({ type: "RAISE", amount: value })}
              disabled={disabledAll || !legalActions.canRaise}
            >
              {value}
            </button>
          ))}
        </div>
      ) : (
        <>
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
        </>
      )}
      {!isCompact || actionPending || (disabled && disabledReason) ? (
        <p className="pf-action-hint">
          {!isCompact ? (
            <>
              最小加注到：{minRaiseTo} · 最大加注到：{maxRaiseTo}
            </>
          ) : null}
          {actionPending ? " · 动作提交中..." : ""}
          {!actionPending && disabled && disabledReason ? ` · ${disabledReason}` : ""}
        </p>
      ) : null}
    </section>
  );
}
