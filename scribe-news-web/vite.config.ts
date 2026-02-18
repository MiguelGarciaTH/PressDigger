import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/articles': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
      '/images': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      }
    }
  }
})
