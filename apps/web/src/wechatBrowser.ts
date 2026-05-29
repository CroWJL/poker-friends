type WeixinBridge = {
  call?: (method: string) => void;
  invoke?: (method: string, args?: unknown, callback?: () => void) => void;
};

declare global {
  interface Window {
    WeixinJSBridge?: WeixinBridge;
  }
}

function isWeChatBrowser(): boolean {
  return /MicroMessenger/i.test(navigator.userAgent);
}

/** 首次进入时补上 _wv=1，尽量隐藏微信底部前进/后退栏（Android X5 有效）。 */
export function ensureWeChatViewportParam(): boolean {
  if (!isWeChatBrowser()) {
    return false;
  }
  const url = new URL(window.location.href);
  if (url.searchParams.has("_wv")) {
    return false;
  }
  url.searchParams.set("_wv", "1");
  window.location.replace(url.toString());
  return true;
}

function callBridgeHideToolbar(): void {
  const bridge = window.WeixinJSBridge;
  if (!bridge) {
    return;
  }
  try {
    bridge.call?.("hideToolbar");
  } catch {
    // 普通订阅号 H5 可能无效，忽略
  }
  try {
    bridge.invoke?.("hideToolbar", {}, () => undefined);
  } catch {
    // ignore
  }
}

/** 进入 SPA 后再次尝试隐藏工具栏，并压缩 history 降低 iOS 出底栏概率。 */
export function setupWeChatBrowserChrome(): void {
  if (!isWeChatBrowser()) {
    return;
  }
  const url = new URL(window.location.href);
  if (!url.searchParams.has("_wv")) {
    url.searchParams.set("_wv", "1");
    window.history.replaceState(window.history.state, "", url.toString());
  } else {
    window.history.replaceState(window.history.state, "", window.location.href);
  }
  callBridgeHideToolbar();
  document.addEventListener("WeixinJSBridgeReady", callBridgeHideToolbar, { once: true });
}
