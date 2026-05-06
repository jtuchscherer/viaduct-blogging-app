import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    watch: {
      // Prevent Vite from force-reloading browsers when Playwright writes report
      // artefacts into frontend/playwright-report/ or frontend/test-results/ mid-run.
      ignored: ['**/playwright-report/**', '**/test-results/**'],
    },
  },
})
