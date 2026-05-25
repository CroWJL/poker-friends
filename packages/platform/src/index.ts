export interface StoredPokerSession {
  roomId: string;
  tableId: string;
  playerId: string;
  token: string;
}

export interface AppPlatform {
  name: "web" | "desktop";
  notify: (title: string, body: string) => Promise<void>;
  getStoredPlayerName: () => Promise<string | undefined>;
  setStoredPlayerName: (name: string) => Promise<void>;
  getStoredSession: () => Promise<StoredPokerSession | undefined>;
  setStoredSession: (session: StoredPokerSession) => Promise<void>;
  clearStoredSession: () => Promise<void>;
}
