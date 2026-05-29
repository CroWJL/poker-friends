export interface WalletTransactionView {
  id: number;
  type: string;
  amount: number;
  balanceAfter: number;
  roomId?: string | null;
  createdAt: string;
}

interface UserProfilePanelProps {
  displayName: string;
  userId?: string;
  walletBalance: number | null;
  tableStack?: number | null;
  roomId?: string;
  transactions: WalletTransactionView[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onRefresh: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  TABLE_BUY_IN: "入座买入",
  AUTO_REBUY: "自动补码",
  TABLE_CASH_OUT: "离桌回款"
};

function formatTransactionTime(iso: string) {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

export function UserProfilePanel({
  displayName,
  userId,
  walletBalance,
  tableStack,
  roomId,
  transactions,
  loading,
  error,
  onClose,
  onRefresh
}: UserProfilePanelProps) {
  return (
    <div className="pf-profile-overlay" role="dialog" aria-modal="true" aria-labelledby="pf-profile-title">
      <div className="pf-profile-card">
        <div className="pf-profile-header">
          <h2 id="pf-profile-title">我的信息</h2>
          <button type="button" className="pf-profile-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </div>

        {loading ? <p className="pf-profile-status">加载中…</p> : null}
        {error ? <p className="pf-profile-error">{error}</p> : null}

        <dl className="pf-profile-list">
          <div className="pf-profile-row">
            <dt>玩家昵称</dt>
            <dd>{displayName || "-"}</dd>
          </div>
          {userId ? (
            <div className="pf-profile-row">
              <dt>用户 ID</dt>
              <dd className="pf-profile-mono">{userId}</dd>
            </div>
          ) : null}
          <div className="pf-profile-row pf-profile-row-highlight">
            <dt>钱包筹码</dt>
            <dd className="pf-profile-wallet">
              {walletBalance !== null ? `¥ ${walletBalance}` : "—"}
            </dd>
          </div>
          {tableStack !== null && tableStack !== undefined ? (
            <div className="pf-profile-row">
              <dt>当前牌桌筹码</dt>
              <dd>¥ {tableStack}</dd>
            </div>
          ) : null}
          {roomId ? (
            <div className="pf-profile-row">
              <dt>所在房间</dt>
              <dd className="pf-profile-mono">{roomId}</dd>
            </div>
          ) : null}
        </dl>

        <section className="pf-wallet-ledger">
          <h3 className="pf-wallet-ledger-title">钱包流水</h3>
          {transactions.length === 0 ? (
            <p className="pf-wallet-ledger-empty">暂无流水记录</p>
          ) : (
            <ul className="pf-wallet-ledger-list">
              {transactions.map((item) => (
                <li key={item.id} className="pf-wallet-ledger-item">
                  <div className="pf-wallet-ledger-item-main">
                    <span className="pf-wallet-ledger-type">{TYPE_LABELS[item.type] ?? item.type}</span>
                    <span className={`pf-wallet-ledger-amount ${item.amount >= 0 ? "is-credit" : "is-debit"}`}>
                      {item.amount >= 0 ? "+" : ""}
                      {item.amount}
                    </span>
                  </div>
                  <div className="pf-wallet-ledger-item-meta">
                    <span>{formatTransactionTime(item.createdAt)}</span>
                    <span>余额 ¥ {item.balanceAfter}</span>
                    {item.roomId ? <span>房间 {item.roomId}</span> : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <p className="pf-profile-note">
          钱包用于入座（每次 1000）与自动补码；桌上剩余筹码在离开房间时退回钱包。已投入底池的筹码无法带走。
        </p>

        <div className="pf-profile-actions">
          <button type="button" className="pf-profile-btn-secondary" onClick={onRefresh} disabled={loading}>
            刷新
          </button>
          <button type="button" className="pf-profile-btn-primary" onClick={onClose}>
            关闭
          </button>
        </div>
      </div>
    </div>
  );
}
