import type { WsClientMessage, WsServerMessage } from "./protocol";

export type MessageListener = (message: WsServerMessage) => void;
export type ConnectionStatus = "idle" | "connecting" | "connected" | "reconnecting" | "disconnected";
export type ConnectionStatusListener = (status: ConnectionStatus) => void;

export class PokerWsClient {
  private socket?: WebSocket;
  private listeners: MessageListener[] = [];
  private statusListeners: ConnectionStatusListener[] = [];
  private reconnectAttempts = 0;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private reconnectEnabled = false;
  private manualClose = false;
  private lastUrl?: string;
  private status: ConnectionStatus = "idle";

  connect(url: string): void {
    this.lastUrl = url;
    this.reconnectEnabled = true;
    this.manualClose = false;
    this.clearReconnectTimer();
    this.setStatus(this.status === "connected" ? "reconnecting" : "connecting");
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
      this.socket.close();
    }
    this.socket = new WebSocket(url);
    this.socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.setStatus("connected");
    };
    this.socket.onclose = () => {
      if (this.manualClose) {
        this.setStatus("disconnected");
        return;
      }
      if (!this.reconnectEnabled || !this.lastUrl) {
        this.setStatus("disconnected");
        return;
      }
      this.setStatus("reconnecting");
      this.scheduleReconnect();
    };
    this.socket.onerror = () => {
      if (this.status !== "reconnecting") {
        this.setStatus("disconnected");
      }
    };
    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data as string) as WsServerMessage;
      this.listeners.forEach((listener) => listener(message));
    };
  }

  send(message: WsClientMessage): boolean {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return false;
    }
    this.socket.send(JSON.stringify(message));
    return true;
  }

  onMessage(listener: MessageListener): void {
    this.listeners.push(listener);
  }

  onStatus(listener: ConnectionStatusListener): void {
    this.statusListeners.push(listener);
    listener(this.status);
  }

  getStatus(): ConnectionStatus {
    return this.status;
  }

  close(): void {
    this.reconnectEnabled = false;
    this.manualClose = true;
    this.clearReconnectTimer();
    this.socket?.close();
    this.socket = undefined;
    this.setStatus("disconnected");
  }

  private scheduleReconnect(): void {
    if (!this.lastUrl) {
      return;
    }
    const delayMs = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 15000);
    this.reconnectAttempts += 1;
    this.clearReconnectTimer();
    this.reconnectTimer = setTimeout(() => {
      if (this.lastUrl && this.reconnectEnabled && !this.manualClose) {
        this.connect(this.lastUrl);
      }
    }, delayMs);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
  }

  private setStatus(status: ConnectionStatus): void {
    this.status = status;
    this.statusListeners.forEach((listener) => listener(status));
  }
}
