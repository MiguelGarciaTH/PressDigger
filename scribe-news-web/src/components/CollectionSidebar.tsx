import { useState, useEffect, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { PUBLIC_COLLECTIONS_URL, PRIVATE_COLLECTIONS_URL, JOURNALIST_COLLECTIONS_URL } from "../config"

interface Collection {
  id: number
  name: string
  articleCount: number
}

type PanelType = "search" | "editor" | "public" | "private" | "journalists" | null

/* ── SVG Icons (all 20×20, stroke-based, minimal) ────────────────── */

function SearchIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "#999"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="11" cy="11" r="8" />
      <path d="m21 21-4.35-4.35" />
    </svg>
  )
}

function EditorIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "#999"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
    </svg>
  )
}

/* Public collection: open folder */
function PublicCollectionIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "#999"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
  )
}

/* Private collection: folder with lock */
function PrivateCollectionIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "#999"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
      <rect x="10" y="12" width="4" height="4" rx="0.5" />
      <path d="M11 12v-1a1 1 0 0 1 2 0v1" />
    </svg>
  )
}

/* Journalists collection: folder with pen nib */
function JournalistCollectionIcon({ active }: { active: boolean }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={active ? "#fff" : "#999"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
      <path d="M14 11l-2 6-1-1-2-2 5-3z" />
    </svg>
  )
}

export default function CollectionSidebar({ user }: { user: any }) {
  const navigate = useNavigate()
  const [activePanel, setActivePanel] = useState<PanelType>(null)
  const [collections, setCollections] = useState<Collection[]>([])
  const [loadingCollections, setLoadingCollections] = useState(false)
  const [collectionError, setCollectionError] = useState<string | null>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const sidebarRef = useRef<HTMLDivElement>(null)

  // Close panel on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (
        panelRef.current && !panelRef.current.contains(e.target as Node) &&
        sidebarRef.current && !sidebarRef.current.contains(e.target as Node)
      ) {
        setActivePanel(null)
      }
    }
    if (activePanel) document.addEventListener("mousedown", handleClick)
    return () => document.removeEventListener("mousedown", handleClick)
  }, [activePanel])

  // Fetch collections when a collection panel opens
  useEffect(() => {
    if (activePanel !== "public" && activePanel !== "private" && activePanel !== "journalists") {
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

  function handleIconClick(panel: PanelType) {
    // Search & Editor navigate directly
    if (panel === "search") {
      navigate("/")
      setActivePanel(null)
      return
    }
    if (panel === "editor") {
      navigate("/editor-search")
      setActivePanel(null)
      return
    }
    // Private collections require login
    if (panel === "private" && !user) {
      // Trigger Google login
      window.location.href = "http://localhost:8085/oauth2/authorization/google"
      return
    }
    // Toggle panel
    setActivePanel((prev) => (prev === panel ? null : panel))
  }

  const panelTitles: Record<string, string> = {
    public: "Public Collections",
    private: "My Collections",
    journalists: "Journalist Collections",
  }

  const iconBtnStyle = (isActive: boolean): React.CSSProperties => ({
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    width: 40,
    height: 40,
    borderRadius: 10,
    border: "none",
    background: isActive ? "rgba(255,255,255,0.12)" : "transparent",
    cursor: "pointer",
    transition: "background 150ms",
  })

  return (
    <>
      {/* Icon rail */}
      <div
        ref={sidebarRef}
        style={{
          position: "fixed",
          right: 0,
          top: "50%",
          transform: "translateY(-50%)",
          zIndex: 9990,
          display: "flex",
          flexDirection: "column",
          gap: 4,
          padding: "8px 6px",
          background: "rgba(20,20,20,0.85)",
          backdropFilter: "blur(12px)",
          borderRadius: "12px 0 0 12px",
          border: "1px solid rgba(255,255,255,0.08)",
          borderRight: "none",
        }}
      >
        <button
          title="Search"
          style={iconBtnStyle(false)}
          onClick={() => handleIconClick("search")}
          onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.08)" }}
          onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
        >
          <SearchIcon active={false} />
        </button>

        <button
          title="Text Editor"
          style={iconBtnStyle(false)}
          onClick={() => handleIconClick("editor")}
          onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.08)" }}
          onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
        >
          <EditorIcon active={false} />
        </button>

        <div style={{ height: 1, background: "rgba(255,255,255,0.1)", margin: "4px 6px" }} />

        <button
          title="Public Collections"
          style={iconBtnStyle(activePanel === "public")}
          onClick={() => handleIconClick("public")}
          onMouseEnter={(e) => { if (activePanel !== "public") e.currentTarget.style.background = "rgba(255,255,255,0.08)" }}
          onMouseLeave={(e) => { if (activePanel !== "public") e.currentTarget.style.background = "transparent" }}
        >
          <PublicCollectionIcon active={activePanel === "public"} />
        </button>

        <button
          title={user ? "My Collections" : "Sign in to view collections"}
          style={iconBtnStyle(activePanel === "private")}
          onClick={() => handleIconClick("private")}
          onMouseEnter={(e) => { if (activePanel !== "private") e.currentTarget.style.background = "rgba(255,255,255,0.08)" }}
          onMouseLeave={(e) => { if (activePanel !== "private") e.currentTarget.style.background = "transparent" }}
        >
          <PrivateCollectionIcon active={activePanel === "private"} />
        </button>

        <button
          title="Journalist Collections"
          style={iconBtnStyle(activePanel === "journalists")}
          onClick={() => handleIconClick("journalists")}
          onMouseEnter={(e) => { if (activePanel !== "journalists") e.currentTarget.style.background = "rgba(255,255,255,0.08)" }}
          onMouseLeave={(e) => { if (activePanel !== "journalists") e.currentTarget.style.background = "transparent" }}
        >
          <JournalistCollectionIcon active={activePanel === "journalists"} />
        </button>
      </div>

      {/* Slide-out panel */}
      {activePanel && activePanel !== "search" && activePanel !== "editor" && (
        <div
          ref={panelRef}
          style={{
            position: "fixed",
            right: 56,
            top: "50%",
            transform: "translateY(-50%)",
            zIndex: 9989,
            width: 300,
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
          {/* Panel header */}
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
              style={{
                background: "none",
                border: "none",
                color: "#888",
                cursor: "pointer",
                fontSize: 18,
                lineHeight: 1,
                padding: "0 2px",
              }}
            >
              ✕
            </button>
          </div>

          {/* Panel content */}
          <div style={{ flex: 1, overflowY: "auto", padding: "8px 0" }}>
            {loadingCollections && (
              <p style={{ color: "#888", fontSize: 13, textAlign: "center", padding: 20 }}>
                Loading…
              </p>
            )}

            {collectionError && (
              <p style={{ color: "#e55", fontSize: 13, textAlign: "center", padding: 20 }}>
                {activePanel === "journalists"
                  ? "Coming soon"
                  : `Error: ${collectionError}`}
              </p>
            )}

            {!loadingCollections && !collectionError && collections.length === 0 && (
              <p style={{ color: "#777", fontSize: 13, textAlign: "center", padding: 20 }}>
                {activePanel === "journalists"
                  ? "Coming soon"
                  : "No collections found"}
              </p>
            )}

            {collections.map((col) => (
              <button
                key={col.id}
                onClick={() => {
                  // Navigate to collection results (you can adjust this route later)
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
