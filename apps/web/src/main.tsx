import React from "react";
import ReactDOM from "react-dom/client";
import { PokerApp } from "@poker-friends/ui";
import { createWebPlatform } from "@poker-friends/platform/web";
import { resolveApiBaseUrl } from "./resolveApiBaseUrl";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <PokerApp
      platform={createWebPlatform()}
      config={{ apiBaseUrl: resolveApiBaseUrl() }}
    />
  </React.StrictMode>
);
