export function resolveApiBaseUrl() {
    if (import.meta.env.DEV && typeof window !== "undefined") {
        const hostname = window.location.hostname;
        if (hostname !== "localhost" && hostname !== "127.0.0.1") {
            return `http://${hostname}:8080`;
        }
    }
    return import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
}
