import type { AppPlatform, StoredPokerSession } from "./index";

const PLAYER_NAME_KEY = "poker_friends_player_name";
const SESSION_KEY = "poker_friends_session";

function readSession(): StoredPokerSession | undefined {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) {
    return undefined;
  }
  try {
    return JSON.parse(raw) as StoredPokerSession;
  } catch {
    return undefined;
  }
}

function clearLegacySession() {
  localStorage.removeItem(SESSION_KEY);
}

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
      clearLegacySession();
      return readSession();
    },
    async setStoredSession(session: StoredPokerSession) {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    },
    async clearStoredSession() {
      sessionStorage.removeItem(SESSION_KEY);
    }
  };
}
