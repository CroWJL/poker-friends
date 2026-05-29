import { useEffect, useState } from "react";

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
    if (!active || typeof screen === "undefined") {
      return;
    }
    const orientation = screen.orientation;
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
