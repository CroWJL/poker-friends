import { sendNotification } from "@tauri-apps/api/notification";
import type { AppPlatform, StoredPokerSession } from "./index";

const PLAYER_NAME_KEY = "poker_friends_player_name";
const SESSION_KEY = "poker_friends_session";

export function createTauriPlatform(): AppPlatform {
  return {
    name: "desktop",
    async notify(title: string, body: string) {
      await sendNotification({ title, body });
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
