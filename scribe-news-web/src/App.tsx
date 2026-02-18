import React, { Suspense, lazy, useEffect, useState, useRef } from "react"
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom"
import GoogleLoginButton from "./components/GoogleLoginButton"
import { useAuth } from './components/useAuth'

// replace direct imports with lazy imports
const SearchPage = lazy(() => import("./pages/SearchPage"))
const ResultsPage = lazy(() => import("./pages/ResultsPage"))
const EditorSearchPage = lazy(() => import("./pages/EditorSearchPage"))

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

function UserProfile() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  // close menu on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    if (menuOpen) document.addEventListener("mousedown", handleClick)
    return () => document.removeEventListener("mousedown", handleClick)
  }, [menuOpen])

  if (!user) return null

  return (
    <div
      ref={menuRef}
      style={{
        position: "fixed",
        top: 16,
        right: 20,
        zIndex: 9998,
      }}
    >
      <img
        src={user.picture}
        alt={user.name}
        referrerPolicy="no-referrer"
        onClick={() => setMenuOpen((v) => !v)}
        style={{
          width: 36,
          height: 36,
          borderRadius: "50%",
          cursor: "pointer",
          border: "2px solid #555",
          objectFit: "cover",
          display: "block",
        }}
      />
      {menuOpen && (
        <div
          style={{
            position: "absolute",
            top: 44,
            right: 0,
            background: "#1e1e1e",
            border: "1px solid #444",
            borderRadius: 10,
            padding: "12px 16px",
            minWidth: 180,
            boxShadow: "0 4px 16px rgba(0,0,0,0.5)",
          }}
        >
          <p style={{ margin: "0 0 4px", color: "#eee", fontSize: 14, fontWeight: 600 }}>
            {user.name}
          </p>
          <p style={{ margin: "0 0 10px", color: "#999", fontSize: 12 }}>
            {user.email}
          </p>
          <button
            onClick={logout}
            style={{
              width: "100%",
              padding: "6px 0",
              background: "transparent",
              border: "1px solid #666",
              borderRadius: 6,
              color: "#e3e3e3",
              cursor: "pointer",
              fontSize: 13,
            }}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}

export default function App() {
  const { user, loading } = useAuth()

  useEffect(() => {
    // add a class for touch devices so you can target touch-specific styles
    if (typeof window !== "undefined" && ('ontouchstart' in window || navigator.maxTouchPoints > 0)) {
      document.documentElement.classList.add("touch")
    }
  }, [])

  if (loading) {
    return (
      <div style={{ 
        display: "flex", 
        justifyContent: "center", 
        alignItems: "center", 
        minHeight: "100vh" 
      }}>
        Loading...
      </div>
    )
  }

  return (
    <BrowserRouter>
      <ScrollToTop />
      
      {/* Show login button if not logged in, profile if logged in */}
      {user ? <UserProfile /> : <GoogleLoginButton />}
      
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
          </Routes>
        </Suspense>
      </main>
    </BrowserRouter>
  )
}