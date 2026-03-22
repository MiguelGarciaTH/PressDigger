import { useState, useEffect, useRef } from "react"
import { useNavigate, useLocation } from "react-router-dom"
import { PUBLIC_COLLECTIONS_URL, PRIVATE_COLLECTIONS_URL, JOURNALIST_COLLECTIONS_URL, GOOGLE_AUTH_URL } from "../config"
import { useAuth } from "./useAuth"
import { useLang } from "../contexts/LanguageContext"

interface Collection {
  id: number
  name: string
  articleCount: number
}

type PanelType = "public" | "private" | "journalists" | null

const COLLAPSED_W = 52
const EXPANDED_W = 210

/* ── Icons (20×20, stroke-based) ────────────────────────────────── */

function HamburgerIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="3" y1="6" x2="21" y2="6" />
      <line x1="3" y1="12" x2="21" y2="12" />
      <line x1="3" y1="18" x2="21" y2="18" />
    </svg>
  )
}

function CloseIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="8" />
      <path d="m21 21-4.35-4.35" />
    </svg>
  )
}

function EditorIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
    </svg>
  )
}

function PublicCollectionIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  )
}

function PrivateCollectionIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
      <rect x="10" y="12" width="4" height="4" rx="0.5" />
      <path d="M11 12v-1a1 1 0 0 1 2 0v1" />
    </svg>
  )
}

function JournalistCollectionIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
      <path d="M14 11l-2 6-1-1-2-2 5-3z" />
    </svg>
  )
}

function AboutIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="16" x2="12" y2="12" />
      <line x1="12" y1="8" x2="12.01" y2="8" />
    </svg>
  )
}

/* Google "G" — monochrome grey SVG */
function GoogleLogoMono() {
  return (
    <svg width="22" height="22" viewBox="0 0 48 48" style={{ flexShrink: 0 }}>
      <path
        fill="#bbb"
        d="M44.5 20H24v8.5h11.8C34.7 33.9 30.1 37 24 37c-7.2 0-13-5.8-13-13s5.8-13 13-13c3.1 0 5.9 1.1 8.1 2.9l6.4-6.4C34.6 4.1 29.6 2 24 2 11.8 2 2 11.8 2 24s9.8 22 22 22c11 0 21-8 21-22 0-1.3-.2-2.7-.5-4z"
      />
    </svg>
  )
}

/* ── Sidebar ────────────────────────────────────────────────────── */

export default function Sidebar() {
  const { user, logout } = useAuth()
  const { t, lang, setLang } = useLang()
  const navigate = useNavigate()
  const location = useLocation()

  const [expanded, setExpanded] = useState(false)
  const [activePanel, setActivePanel] = useState<PanelType>(null)
  const [collections, setCollections] = useState<Collection[]>([])
  const [loadingCollections, setLoadingCollections] = useState(false)
  const [collectionError, setCollectionError] = useState<string | null>(null)
  const [profileMenuOpen, setProfileMenuOpen] = useState(false)

  const sidebarRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const profileRef = useRef<HTMLDivElement>(null)

  const currentWidth = expanded ? EXPANDED_W : COLLAPSED_W

  // Close panels/menus on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      const target = e.target as Node
      const inSidebar = sidebarRef.current?.contains(target)
      const inPanel = panelRef.current?.contains(target)
      const inProfile = profileRef.current?.contains(target)
      if (!inSidebar && !inPanel && !inProfile) {
        setActivePanel(null)
        setProfileMenuOpen(false)
      }
    }
    document.addEventListener("mousedown", handleClick)
    return () => document.removeEventListener("mousedown", handleClick)
  }, [])

  // Fetch collections when panel opens
  useEffect(() => {
    if (!activePanel) {
      setCollections([])
      setCollectionError(null)
      return
    }

    const urlMap: Record<string, string> = {
      public: PUBLIC_COLLECTIONS_URL,
      private: PRIVATE_COLLECTIONS_URL,
      journalists: JOURNALIST_COLLECTIONS_URL,
    }

    setLoadingCollections(true)
    setCollectionError(null)
    setCollections([])

    fetch(urlMap[activePanel], { credentials: "include" })
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status}`)
        return r.json()
      })
      .then((data) => setCollections(data))
      .catch((err) => setCollectionError(err.message ?? "Failed to load"))
      .finally(() => setLoadingCollections(false))
  }, [activePanel])

  function handleNav(path: string) {
    navigate(path)
    setActivePanel(null)
  }

  function handleCollectionClick(panel: PanelType) {
    if (panel === "private" && !user) {
      window.location.href = GOOGLE_AUTH_URL
      return
    }
    setActivePanel((prev) => (prev === panel ? null : panel))
  }

  function handleLogin() {
    window.location.href = GOOGLE_AUTH_URL
  }

  const panelTitles: Record<string, string> = {
    public: t.panelPublic,
    private: t.panelPrivate,
    journalists: t.panelJournalists,
  }

  const isActivePath = (path: string) => location.pathname === path

  /* Row style — icon + optional label */
  const rowStyle = (active: boolean): React.CSSProperties => ({
    display: "flex",
    alignItems: "center",
    gap: 12,
    width: "100%",
    height: 40,
    padding: expanded ? "0 14px" : "0",
    justifyContent: expanded ? "flex-start" : "center",
    background: active ? "rgba(255,255,255,0.1)" : "transparent",
    border: "none",
    borderRadius: 8,
    cursor: "pointer",
    color: active ? "#fff" : "#aaa",
    fontSize: 13,
    fontWeight: active ? 600 : 400,
    transition: "all 150ms",
    whiteSpace: "nowrap",
    overflow: "hidden",
  })

  const iconWrap: React.CSSProperties = {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    width: 20,
    flexShrink: 0,
  }

  return (
    <>
      {/* ── Sidebar rail — RIGHT side ── */}
      <div
        ref={sidebarRef}
        style={{
          position: "fixed",
          right: 0,
          top: 0,
          bottom: 0,
          width: currentWidth,
          zIndex: 9990,
          display: "flex",
          flexDirection: "column",
          background: "rgba(14,14,14,0.92)",
          backdropFilter: "blur(16px)",
          borderLeft: "1px solid rgba(255,255,255,0.06)",
          transition: "width 200ms ease",
          overflow: "hidden",
        }}
      >
        {/* ─ Hamburger / X toggle ─ */}
        <div style={{ padding: "14px 0 4px", display: "flex", justifyContent: "flex-end", paddingRight: (COLLAPSED_W - 36) / 2 }}>
          <button
            onClick={() => { setExpanded((v) => !v); setActivePanel(null); setProfileMenuOpen(false) }}
            aria-label={expanded ? t.closeMenu : t.openMenu}
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: 36,
              height: 36,
              background: "none",
              border: "none",
              cursor: "pointer",
              color: "#aaa",
              borderRadius: 8,
              transition: "color 150ms",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = "#fff" }}
            onMouseLeave={(e) => { e.currentTarget.style.color = "#aaa" }}
          >
            {expanded ? <CloseIcon /> : <HamburgerIcon />}
          </button>
        </div>

        {/* ─ Login / Profile ─ */}
        <div style={{ padding: "8px 0 4px", display: "flex", flexDirection: "column", alignItems: "center" }}>
          {user ? (
            <div ref={profileRef} style={{ position: "relative", width: "100%", display: "flex", justifyContent: expanded ? "flex-start" : "center", padding: expanded ? "0 10px" : "0" }}>
              <button
                onClick={() => setProfileMenuOpen((v) => !v)}
                title={!expanded ? user.name : undefined}

                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  padding: "4px",
                  borderRadius: 8,
                  overflow: "hidden",
                  width: expanded ? "100%" : "auto",
                }}
              >
                <img
                  src={user.picture}
                  alt={user.name}
                  referrerPolicy="no-referrer"
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: "50%",
                    border: "1.5px solid #555",
                    objectFit: "cover",
                    flexShrink: 0,
                  }}
                />
                {expanded && (
                  <span style={{ color: "#ddd", fontSize: 13, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {user.name}
                  </span>
                )}
              </button>

              {/* Profile dropdown — opens to the LEFT */}
              {profileMenuOpen && (
                <div style={{
                  position: "absolute",
                  top: 40,
                  right: expanded ? undefined : COLLAPSED_W + 4,
                  left: expanded ? 10 : undefined,
                  background: "#1e1e1e",
                  border: "1px solid #444",
                  borderRadius: 10,
                  padding: "12px 16px",
                  minWidth: 180,
                  boxShadow: "0 4px 16px rgba(0,0,0,0.5)",
                  zIndex: 10000,
                }}>
                  <p style={{ margin: "0 0 4px", color: "#eee", fontSize: 14, fontWeight: 600 }}>{user.name}</p>
                  <p style={{ margin: "0 0 10px", color: "#999", fontSize: 12 }}>{user.email}</p>
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
                  {t.signOut}
                  </button>
                </div>
              )}
            </div>
          ) : (
            <button
              onClick={handleLogin}
              title={t.signIn}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                background: "none",
                border: "none",
                cursor: "pointer",
                padding: expanded ? "4px 14px" : "4px",
                width: expanded ? "100%" : "auto",
                borderRadius: 8,
              }}
            >
              <span style={iconWrap}><GoogleLogoMono /></span>
              {expanded && (
                <span style={{ color: "#ddd", fontSize: 13, fontWeight: 500, whiteSpace: "nowrap" }}>
                  {t.signIn}
                </span>
              )}
            </button>
          )}
        </div>

        {/* ─ Separator ─ */}
        <div style={{ height: 1, background: "rgba(255,255,255,0.08)", margin: "10px 10px 12px" }} />

        {/* ─ Nav items ─ */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 2, padding: "0 6px" }}>
          <button
            style={rowStyle(isActivePath("/"))}
            onClick={() => handleNav("/")}
            title={!expanded ? t.navSearch : undefined}
            onMouseEnter={(e) => { if (!isActivePath("/")) e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { if (!isActivePath("/")) e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}><SearchIcon /></span>
            {expanded && <span>{t.navSearch}</span>}
          </button>

          <button
            style={rowStyle(isActivePath("/editor-search"))}
            onClick={() => handleNav("/editor-search")}
            title={!expanded ? t.navTextEditor : undefined}
            onMouseEnter={(e) => { if (!isActivePath("/editor-search")) e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { if (!isActivePath("/editor-search")) e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}><EditorIcon /></span>
            {expanded && <span>{t.navTextEditor}</span>}
          </button>

          <div style={{ height: 1, background: "rgba(255,255,255,0.06)", margin: "10px 4px" }} />

          <button
            style={rowStyle(isActivePath("/collections/public"))}
            onClick={() => handleNav("/collections/public")}
            title={!expanded ? t.panelPublic : undefined}
            onMouseEnter={(e) => { if (!isActivePath("/collections/public")) e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { if (!isActivePath("/collections/public")) e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}><PublicCollectionIcon /></span>
            {expanded && <span>{t.navPublic}</span>}
          </button>

          <button
            style={rowStyle(isActivePath("/collections/private"))}
            onClick={() => {
              if (!user) {
                window.location.href = GOOGLE_AUTH_URL
                return
              }
              handleNav("/collections/private")
            }}
            title={!expanded ? (!user ? t.signInToView : t.panelPrivate) : undefined}
            onMouseEnter={(e) => { if (!isActivePath("/collections/private")) e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { if (!isActivePath("/collections/private")) e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}><PrivateCollectionIcon /></span>
            {expanded && <span>{t.navPrivate}</span>}
          </button>

          <button
            style={rowStyle(isActivePath("/journalists"))}
            onClick={() => handleNav("/journalists")}
            title={!expanded ? t.navJournalists : undefined}
            onMouseEnter={(e) => { if (!isActivePath("/journalists")) e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { if (!isActivePath("/journalists")) e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}><JournalistCollectionIcon /></span>
            {expanded && <span>{t.navJournalists}</span>}
          </button>

          <div style={{ height: 1, background: "rgba(255,255,255,0.06)", margin: "10px 4px" }} />

          <button
            style={rowStyle(isActivePath("/about"))}
            onClick={() => handleNav("/about")}
            title={!expanded ? t.navAbout : undefined}
            onMouseEnter={(e) => { if (!isActivePath("/about")) e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { if (!isActivePath("/about")) e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}><AboutIcon /></span>
            {expanded && <span>{t.navAbout}</span>}
          </button>
        </div>

        {/* ─ Bottom links ─ */}
        <div style={{ display: "flex", flexDirection: "column", gap: 2, padding: "0 6px 12px" }}>

          {/* Language toggle */}
          {expanded ? (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 4, padding: "4px 8px", marginBottom: 2 }}>
              {(["en", "pt"] as const).map((l, i) => (
                <>
                  {i > 0 && <span key={`sep-${l}`} style={{ color: "#333", fontSize: 11, userSelect: "none" }}>|</span>}
                  <button
                    key={l}
                    onClick={() => setLang(l)}
                    style={{
                      background: lang === l ? "rgba(255,255,255,0.12)" : "none",
                      border: "none",
                      borderRadius: 5,
                      padding: "3px 8px",
                      color: lang === l ? "#fff" : "#555",
                      fontSize: 11,
                      fontWeight: lang === l ? 700 : 500,
                      cursor: "pointer",
                      letterSpacing: 0.5,
                      transition: "all 150ms",
                    }}
                    onMouseEnter={(e) => { if (lang !== l) e.currentTarget.style.color = "#999" }}
                    onMouseLeave={(e) => { if (lang !== l) e.currentTarget.style.color = "#555" }}
                  >
                    {l.toUpperCase()}
                  </button>
                </>
              ))}
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, padding: "4px 0", marginBottom: 2 }}>
              {(["en", "pt"] as const).map((l) => (
                <button
                  key={l}
                  onClick={() => setLang(l)}
                  style={{
                    background: lang === l ? "rgba(255,255,255,0.12)" : "none",
                    border: "none",
                    borderRadius: 4,
                    padding: "2px 6px",
                    color: lang === l ? "#fff" : "#555",
                    fontSize: 10,
                    fontWeight: lang === l ? 700 : 500,
                    cursor: "pointer",
                    letterSpacing: 0.5,
                    transition: "all 150ms",
                    lineHeight: 1.4,
                  }}
                  onMouseEnter={(e) => { if (lang !== l) e.currentTarget.style.color = "#999" }}
                  onMouseLeave={(e) => { if (lang !== l) e.currentTarget.style.color = "#555" }}
                >
                  {l.toUpperCase()}
                </button>
              ))}
            </div>
          )}

          <div style={{ height: 1, background: "rgba(255,255,255,0.06)", margin: "8px 4px 10px" }} />
          <a
            href="https://github.com/MiguelGarciaTH/ScribeRef"
            target="_blank"
            rel="noopener noreferrer"
            title={!expanded ? "GitHub" : undefined}

            style={{
              ...rowStyle(false),
              textDecoration: "none",
              color: "#aaa",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2C6.48 2 2 6.58 2 12.26c0 4.52 2.87 8.35 6.84 9.7.5.1.68-.22.68-.49 0-.24-.01-.86-.01-1.69-2.78.62-3.37-1.38-3.37-1.38-.45-1.18-1.11-1.49-1.11-1.49-.9-.64.07-.63.07-.63 1 .07 1.53 1.06 1.53 1.06.89 1.57 2.34 1.12 2.91.86.09-.66.35-1.12.64-1.38-2.22-.26-4.56-1.14-4.56-5.08 0-1.12.39-2.03 1.03-2.75-.1-.26-.45-1.31.1-2.74 0 0 .84-.28 2.75 1.05A9.3 9.3 0 0 1 12 6.8c.83 0 1.67.11 2.45.33 1.91-1.33 2.75-1.05 2.75-1.05.55 1.43.2 2.48.1 2.74.64.72 1.03 1.63 1.03 2.75 0 3.95-2.34 4.82-4.57 5.08.36.32.68.95.68 1.92 0 1.38-.01 2.5-.01 2.84 0 .27.18.6.69.49A10.03 10.03 0 0 0 22 12.26C22 6.58 17.52 2 12 2z" />
              </svg>
            </span>
            {expanded && <span>GitHub</span>}

          </a>
          <a
            href="https://www.linkedin.com/in/miguelgarciath/"
            target="_blank"
            rel="noopener noreferrer"
            title={!expanded ? "Miguel Garcia" : undefined}
            style={{
              ...rowStyle(false),
              textDecoration: "none",
              color: "#aaa",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M20.45 20.45H17.2v-5.4c0-1.29-.02-2.95-1.8-2.95-1.8 0-2.07 1.4-2.07 2.85v5.5H10.1V9h3.12v1.56h.04c.43-.82 1.5-1.69 3.08-1.69 3.3 0 3.9 2.17 3.9 5v6.58zM5.34 7.53a1.88 1.88 0 1 1 0-3.76 1.88 1.88 0 0 1 0 3.76zM6.98 20.45H3.7V9h3.28v11.45zM22 2H2a2 2 0 0 0-2 2v18a2 2 0 0 0 2 2h20a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z" />
              </svg>
            </span>
            {expanded && <span>Miguel Garcia</span>}
          </a>
          <a
            href="https://arquivo.pt/"
            target="_blank"
            rel="noopener noreferrer"
            title={!expanded ? "Arquivo.pt" : undefined}
            style={{
              ...rowStyle(false),
              textDecoration: "none",
              color: "#aaa",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
            onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
          >
            <span style={iconWrap}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 8v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8" />
                <path d="M7 8V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v3" />
                <path d="M3 8h18" />
              </svg>
            </span>
            {expanded && <span>Arquivo.pt</span>}
          </a>
        </div>
      </div>

      {/* ── Collection panel — slides out to the LEFT ── */}
      {activePanel && (
        <div
          ref={panelRef}
          style={{
            position: "fixed",
            right: currentWidth + 4,
            top: 120,
            zIndex: 9989,
            width: 280,
            maxHeight: "70vh",
            background: "rgba(20,20,20,0.95)",
            backdropFilter: "blur(16px)",
            border: "1px solid rgba(255,255,255,0.1)",
            borderRadius: 12,
            boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
          }}
        >
          <div style={{
            padding: "14px 16px 10px",
            borderBottom: "1px solid rgba(255,255,255,0.08)",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}>
            <span style={{ color: "#eee", fontSize: 15, fontWeight: 600 }}>
              {panelTitles[activePanel] ?? ""}
            </span>
            <button
              onClick={() => setActivePanel(null)}
              style={{ background: "none", border: "none", color: "#888", cursor: "pointer", fontSize: 18, lineHeight: 1, padding: "0 2px" }}
            >
              ✕
            </button>
          </div>

          <div style={{ flex: 1, overflowY: "auto", padding: "8px 0" }}>
            {loadingCollections && (
              <p style={{ color: "#888", fontSize: 13, textAlign: "center", padding: 20 }}>{t.loading}</p>
            )}

            {collectionError && (
              <p style={{ color: "#e55", fontSize: 13, textAlign: "center", padding: 20 }}>
                {activePanel === "journalists" ? t.comingSoon : `Error: ${collectionError}`}
              </p>
            )}

            {!loadingCollections && !collectionError && collections.length === 0 && (
              <p style={{ color: "#777", fontSize: 13, textAlign: "center", padding: 20 }}>
                {activePanel === "journalists" ? t.comingSoon : t.noCollectionsFoundPanel}
              </p>
            )}

            {collections.map((col) => (
              <button
                key={col.id}
                onClick={() => {
                  navigate(`/results?collection=${col.id}`)
                  setActivePanel(null)
                }}
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  width: "100%",
                  padding: "10px 16px",
                  background: "transparent",
                  border: "none",
                  cursor: "pointer",
                  transition: "background 120ms",
                  textAlign: "left",
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
                onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
              >
                <span style={{ color: "#ddd", fontSize: 14 }}>{col.name}</span>
                <span style={{
                  color: "#888",
                  fontSize: 12,
                  background: "rgba(255,255,255,0.06)",
                  padding: "2px 8px",
                  borderRadius: 10,
                  flexShrink: 0,
                  marginLeft: 8,
                }}>
                  {col.articleCount}
                </span>
              </button>
            ))}
          </div>
        </div>
      )}
    </>
  )
}
