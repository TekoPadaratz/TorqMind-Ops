/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// PWA/service worker removido durante a homologação para eliminar cache preso no iOS.
// BUILD_ID visível na UI permite confirmar qual versão o aparelho está executando.
export default defineConfig({
  plugins: [react()],
  define: {
    __BUILD_ID__: JSON.stringify(new Date().toISOString().replace('T', ' ').slice(0, 19))
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts']
  }
});
