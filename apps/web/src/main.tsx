import React from "react";
import ReactDOM from "react-dom/client";
import { PokerApp } from "@poker-friends/ui";
import { createWebPlatform } from "@poker-friends/platform/web";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <PokerApp
      platform={createWebPlatform()}
      config={{ apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080" }}
    />
  </React.StrictMode>
);
