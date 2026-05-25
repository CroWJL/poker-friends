interface EventFeedProps {
  items: string[];
}

export function EventFeed({ items }: EventFeedProps) {
  return (
    <section className="pf-event-feed">
      <h3 className="pf-panel-title">牌桌动态</h3>
      {items.length === 0 ? (
        <p className="pf-event-empty">暂无动态</p>
      ) : (
        <ul className="pf-event-list">
          {items.map((item, index) => (
            <li key={`${item}-${index}`}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  );
}
