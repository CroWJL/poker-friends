import type { AppPlatform, StoredPokerSession } from "./index";

const PLAYER_NAME_KEY = "poker_friends_player_name";
const SESSION_KEY = "poker_friends_session";

export function createWebPlatform(): AppPlatform {
  return {
    name: "web",
    async notify(title: string, body: string) {
      if ("Notification" in window && Notification.permission === "granted") {
        new Notification(title, { body });
      }
    },
    async getStoredPlayerName() {
      return localStorage.getItem(PLAYER_NAME_KEY) ?? undefined;
    },
    async setStoredPlayerName(name: string) {
      localStorage.setItem(PLAYER_NAME_KEY, name);
    },
    async getStoredSession() {
      const raw = localStorage.getItem(SESSION_KEY);
      if (!raw) {
        return undefined;
      }
      try {
        return JSON.parse(raw) as StoredPokerSession;
      } catch {
        return undefined;
      }
    },
    async setStoredSession(session: StoredPokerSession) {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    },
    async clearStoredSession() {
      localStorage.removeItem(SESSION_KEY);
    }
  };
}
