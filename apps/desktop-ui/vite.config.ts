import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api/ytm': {
        target: 'https://music.youtube.com/youtubei/v1',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/ytm/, ''),
        headers: {
          Origin: 'https://music.youtube.com',
          Referer: 'https://music.youtube.com/',
        },
      },
      '/api/spotify-embed': {
        target: 'https://open.spotify.com/embed',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/spotify-embed/, ''),
      },
    },
  },
});
