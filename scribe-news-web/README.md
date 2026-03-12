<p align="center">
  <img src="public/pressdigger-logo.svg" alt="PressDigger" width="500"/>
</p>

# PressDigger — Web Frontend

React web interface for [PressDigger](../README.md), a tool for journalists, researchers, and the general public to explore and analyze Portuguese news articles from [Arquivo.pt](https://arquivo.pt).

---

## Stack

| Layer | Technology |
|---|---|
| Framework | React 18 |
| Language | TypeScript |
| Build tool | Vite |
| Styling | Tailwind CSS + inline CSS-in-JS |
| Routing | React Router v6 |
| Auth | Google OAuth (via backend session) |
| i18n | Custom context (EN / PT) |
| Backend API | Spring Boot REST at `localhost:8085` (configurable via `VITE_API_URL`) |

---

## Features

### Search
- **Keyword search** — full-text search across all indexed articles
- **Editor / paragraph search** — paste or write a text; the system returns the most relevant articles per paragraph
- Filter results by **date range** and **news site**
- Infinite scroll on results

### Article Viewer
- Microfilm-style image viewer with zoom, pan, and fit controls
- Thumbnail strip for quick navigation between articles in the same result set
- **OCR text extraction** — extract the raw text from an article image
- **Bookmark** articles to private collections

### Collections
- **Public collections** — curated keyword-based collections browsable without login
- **Private collections** — personal article lists; requires Google login
  - Add/remove articles
  - **Annotate articles** with personal notes (private collections only)

### Journalists
- Browse all article authors in a paginated grid (infinite scroll)
- View article count per author
- Click an author to see all their articles
- Scroll position is restored when navigating back

### Internationalization
- Full UI in **English** and **Portuguese**, switchable at runtime

### Authentication
- Google Login button
- Protected routes and features (private collections, annotations, bookmarks)

---

## Development

```bash
npm install
npm run dev
```

App runs at `http://localhost:5173` by default.

To point at a different backend:

```bash
VITE_API_URL=http://my-server:8085 npm run dev
```


Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) (or [oxc](https://oxc.rs) when used in [rolldown-vite](https://vite.dev/guide/rolldown)) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```

You can also install [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])
```
