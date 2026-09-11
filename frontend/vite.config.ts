/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Alvo do proxy de /api no dev server. Aponte para onde o backend responde.
// Assim o front via `npm run dev` fala com a API na mesma origem — inclusive
// quando exposto por um tunel (ngrok), sem depender de CORS. Ajuste se o backend
// nao estiver em localhost:8081.
const API_PROXY_TARGET = 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    // aceita qualquer Host header (ngrok, *.loca.lt, dominios proprios, etc.)
    allowedHosts: true,
    proxy: {
      '/api': {
        target: API_PROXY_TARGET,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
