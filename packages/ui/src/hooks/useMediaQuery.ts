import { useEffect, useState } from "react";

/** Screen Orientation API 的 lock/unlock 在部分 TS lib 中未声明，运行时仍可能可用。 */
type LockableScreenOrientation = ScreenOrientation & {
  lock?: (orientation: "landscape" | "portrait") => Promise<void>;
  unlock?: () => void;
};

function getLockableOrientation(): LockableScreenOrientation | undefined {
  if (typeof screen === "undefined") {
    return undefined;
  }
  return screen.orientation as LockableScreenOrientation;
}

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => {
    if (typeof window === "undefined") {
      return false;
    }
    return window.matchMedia(query).matches;
  });

  useEffect(() => {
    const mediaQuery = window.matchMedia(query);
    const onChange = () => setMatches(mediaQuery.matches);
    onChange();
    mediaQuery.addEventListener("change", onChange);
    return () => mediaQuery.removeEventListener("change", onChange);
  }, [query]);

  return matches;
}

export function useIsMobileLayout(): boolean {
  const narrowScreen = useMediaQuery("(max-width: 1024px)");
  const touchLike = useMediaQuery("(hover: none), (pointer: coarse)");
  const veryNarrow = useMediaQuery("(max-width: 768px)");
  return (narrowScreen && touchLike) || veryNarrow;
}

export function useIsPortrait(): boolean {
  return useMediaQuery("(orientation: portrait)");
}

export function useLandscapeLock(active: boolean): void {
  useEffect(() => {
    if (!active) {
      return;
    }
    const orientation = getLockableOrientation();
    if (!orientation?.lock) {
      return;
    }
    orientation.lock("landscape").catch(() => {
      // iOS / some browsers require fullscreen or deny lock silently.
    });
    return () => {
      orientation.unlock?.();
    };
  }, [active]);
}

/** 微信等内置浏览器工具栏会改变可视高度，用 visualViewport 同步真实可用高度。 */
export function useVisualViewportHeight(active: boolean): void {
  useEffect(() => {
    if (!active || typeof window === "undefined") {
      document.documentElement.style.removeProperty("--pf-vvh");
      return;
    }
    const update = () => {
      const height = window.visualViewport?.height ?? window.innerHeight;
      document.documentElement.style.setProperty("--pf-vvh", `${Math.round(height)}px`);
    };
    update();
    window.visualViewport?.addEventListener("resize", update);
    window.visualViewport?.addEventListener("scroll", update);
    window.addEventListener("resize", update);
    window.addEventListener("orientationchange", update);
    return () => {
      window.visualViewport?.removeEventListener("resize", update);
      window.visualViewport?.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
      window.removeEventListener("orientationchange", update);
      document.documentElement.style.removeProperty("--pf-vvh");
    };
  }, [active]);
}
