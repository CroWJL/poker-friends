interface EventFeedProps {
  items: string[];
}

const EVENT_BADGE_LABEL: Array<{ prefix: string; label: string; className: string }> = [
  { prefix: "结算：", label: "结算", className: "pf-event-badge-settle" },
  { prefix: "动作：", label: "动作", className: "pf-event-badge-action" },
  { prefix: "动作失败：", label: "失败", className: "pf-event-badge-fail" },
  { prefix: "阶段进入：", label: "阶段", className: "pf-event-badge-stage" },
  { prefix: "发送失败：", label: "发送失败", className: "pf-event-badge-fail" }
];

function splitFeedItem(item: string) {
  const firstSpaceIndex = item.indexOf(" ");
  if (firstSpaceIndex <= 0) {
    return { time: "", content: item };
  }
  return {
    time: item.slice(0, firstSpaceIndex),
    content: item.slice(firstSpaceIndex + 1)
  };
}

function resolveBadge(content: string) {
  return EVENT_BADGE_LABEL.find((rule) => content.startsWith(rule.prefix));
}

export function EventFeed({ items }: EventFeedProps) {
  return (
    <section className="pf-event-feed">
      <h3 className="pf-panel-title">牌桌动态</h3>
      {items.length === 0 ? (
        <p className="pf-event-empty">暂无动态</p>
      ) : (
        <ul className="pf-event-list">
          {items.map((item, index) => {
            const { time, content } = splitFeedItem(item);
            const badge = resolveBadge(content);
            const renderedContent = badge ? content.slice(badge.prefix.length) : content;
            return (
              <li key={`${item}-${index}`} className={badge ? "pf-event-item pf-event-item-badged" : "pf-event-item"}>
                <span className="pf-event-time">{time}</span>
                {badge ? <span className={`pf-event-badge ${badge.className}`}>{badge.label}</span> : null}
                <span className="pf-event-content">{renderedContent}</span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
