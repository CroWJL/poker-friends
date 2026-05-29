export function LandscapeRotatePrompt() {
  return (
    <div className="pf-rotate-overlay" role="dialog" aria-modal="true" aria-label="请旋转设备">
      <div className="pf-rotate-overlay-card">
        <div className="pf-rotate-overlay-icon" aria-hidden="true">
          ↻
        </div>
        <h2 className="pf-rotate-overlay-title">请旋转手机至横屏</h2>
        <p className="pf-rotate-overlay-text">扑克牌桌为横屏布局，旋转设备后可获得最佳体验。</p>
      </div>
    </div>
  );
}
