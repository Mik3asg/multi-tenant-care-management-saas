import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev server proxies /api to the Spring backend so session cookies stay
// same-origin during development.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      // Proxy Auth0 OAuth2 endpoints so /oauth2/authorization/auth0 works in local dev
      "/oauth2": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/login/oauth2": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
    },
  },
});
