import React, { Suspense, lazy, useEffect } from "react"
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom"
// replace direct imports with lazy imports
const SearchPage = lazy(() => import("./pages/SearchPage"))
const ResultsPage = lazy(() => import("./pages/ResultsPage"))

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

export default function App() {
  useEffect(() => {
    // add a class for touch devices so you can target touch-specific styles
    if (typeof window !== "undefined" && ('ontouchstart' in window || navigator.maxTouchPoints > 0)) {
      document.documentElement.classList.add("touch")
    }
  }, [])

  return (
    <BrowserRouter>
      <ScrollToTop />
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
          </Routes>
        </Suspense>
      </main>
    </BrowserRouter>
  )
}