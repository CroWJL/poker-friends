import { create } from "zustand";
import type { TableSnapshot } from "./protocol";
import type { ConnectionStatus } from "./wsClient";

interface PokerState {
  snapshot?: TableSnapshot;
  lastError?: string;
  connectionStatus: ConnectionStatus;
  actionPending: boolean;
  setSnapshot: (snapshot: TableSnapshot) => void;
  clearSnapshot: () => void;
  setError: (error?: string) => void;
  setConnectionStatus: (status: ConnectionStatus) => void;
  setActionPending: (pending: boolean) => void;
}

export const usePokerStore = create<PokerState>((set) => ({
  snapshot: undefined,
  lastError: undefined,
  connectionStatus: "idle",
  actionPending: false,
  setSnapshot: (snapshot) => set({ snapshot }),
  clearSnapshot: () => set({ snapshot: undefined }),
  setError: (lastError) => set({ lastError }),
  setConnectionStatus: (connectionStatus) => set({ connectionStatus }),
  setActionPending: (actionPending) => set({ actionPending })
}));
