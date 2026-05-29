import { jsx as _jsx } from "react/jsx-runtime";
import React from "react";
import ReactDOM from "react-dom/client";
import { PokerApp } from "@poker-friends/ui";
import { createWebPlatform } from "@poker-friends/platform/web";
import { resolveApiBaseUrl } from "./resolveApiBaseUrl";
ReactDOM.createRoot(document.getElementById("root")).render(_jsx(React.StrictMode, { children: _jsx(PokerApp, { platform: createWebPlatform(), config: { apiBaseUrl: resolveApiBaseUrl() } }) }));
