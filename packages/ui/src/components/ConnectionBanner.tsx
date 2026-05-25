interface ConnectionBannerProps {
  status: "idle" | "connecting" | "connected" | "reconnecting" | "disconnected";
}

const STATUS_LABEL: Record<ConnectionBannerProps["status"], string> = {
  idle: "未连接",
  connecting: "连接中",
  connected: "已连接",
  reconnecting: "重连中",
  disconnected: "已断开"
};

export function ConnectionBanner({ status }: ConnectionBannerProps) {
  const toneClass =
    status === "connected"
      ? "pf-banner-ok"
      : status === "reconnecting" || status === "connecting"
        ? "pf-banner-warn"
        : "pf-banner-neutral";
  return (
    <div className={`pf-connection-banner ${toneClass}`}>
      <span className="pf-banner-dot" />
      <span>连接状态：{STATUS_LABEL[status]}</span>
    </div>
  );
}
