import { jsx as _jsx } from "react/jsx-runtime";
import React from "react";
import ReactDOM from "react-dom/client";
import { PokerApp } from "@poker-friends/ui";
import { createWebPlatform } from "@poker-friends/platform/web";
ReactDOM.createRoot(document.getElementById("root")).render(_jsx(React.StrictMode, { children: _jsx(PokerApp, { platform: createWebPlatform(), config: { apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080" } }) }));
