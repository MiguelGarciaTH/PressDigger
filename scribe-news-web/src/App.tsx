import { Suspense, lazy, useEffect } from "react"
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom"
import Sidebar from "./components/Sidebar"
import { LanguageProvider } from "./contexts/LanguageContext"

// replace direct imports with lazy imports
const SearchPage = lazy(() => import("./pages/SearchPage"))
const ResultsPage = lazy(() => import("./pages/ResultsPage"))
const EditorSearchPage = lazy(() => import("./pages/EditorSearchPage"))
const PublicCollectionsPage = lazy(() => import("./pages/PublicCollectionsPage"))
const PrivateCollectionsPage = lazy(() => import("./pages/PrivateCollectionsPage"))

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

export default function App() {
  useEffect(() => {
    if (typeof window !== "undefined" && ('ontouchstart' in window || navigator.maxTouchPoints > 0)) {
      document.documentElement.classList.add("touch")
    }
  }, [])

  return (
    <LanguageProvider>
    <BrowserRouter>
      <ScrollToTop />
      <Sidebar />
      <main
        style={{
          margin: "0 auto",
          maxWidth: 1024,
          padding: "env(safe-area-inset-top, 16px) 16px 16px",
          minHeight: "100vh",
          boxSizing: "border-box",
        }}
      >
        <Suspense fallback={<div aria-busy="true" aria-live="polite">Loading…</div>}>
          <Routes>
            <Route path="/" element={<SearchPage />} />
            <Route path="/results" element={<ResultsPage />} />
            <Route path="/editor-search" element={<EditorSearchPage />} />
            <Route path="/collections/public" element={<PublicCollectionsPage />} />
            <Route path="/collections/private" element={<PrivateCollectionsPage />} />
          </Routes>
        </Suspense>
      </main>
    </BrowserRouter>
    </LanguageProvider>
  )
}