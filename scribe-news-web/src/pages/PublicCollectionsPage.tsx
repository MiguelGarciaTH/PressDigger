import { useState, useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { PUBLIC_COLLECTIONS_URL } from "../config"

interface Collection {
  id: number
  name: string
  descriptiom?: string
  articleCount: number
}

export default function PublicCollectionsPage() {
  const [collections, setCollections] = useState<Collection[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    document.title = "Public Collections — PressDigger"
    fetch(PUBLIC_COLLECTIONS_URL, { credentials: "include" })
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status}`)
        return r.json()
      })
      .then((data) => setCollections(data))
      .catch((err) => setError(err.message ?? "Failed to load"))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", color: "#eee", display: "flex", flexDirection: "column", overflow: "hidden", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "16px 20px", borderBottom: "1px solid #222" }}>
        <button
          onClick={() => navigate("/")}
          style={{
            background: "rgba(255,255,255,0.1)",
            border: "1px solid rgba(255,255,255,0.2)",
            borderRadius: 8,
            padding: "8px 12px",
            color: "#eee",
            fontSize: 14,
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            gap: 4,
          }}
        >
          <span style={{ fontSize: 20, fontWeight: 900, textShadow: "0 0 2px rgba(238,238,238,0.8)" }}>←</span>
        </button>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>Public Collections</h2>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: "auto", padding: "32px 24px", display: "flex", justifyContent: "center" }}>
        <div style={{ width: "100%", maxWidth: 900 }}>
        {loading && (
          <p style={{ color: "#888", fontSize: 14, textAlign: "center", padding: 40 }}>Loading…</p>
        )}

        {error && (
          <p style={{ color: "#e55", fontSize: 14, textAlign: "center", padding: 40 }}>
            Failed to load collections
          </p>
        )}

        {!loading && !error && collections.length === 0 && (
          <p style={{ color: "#777", fontSize: 14, textAlign: "center", padding: 40 }}>
            No collections found
          </p>
        )}

        {!loading && !error && collections.length > 0 && (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
              gap: 16,
            }}
          >
            {collections.map((col) => (
              <button
                key={col.id}
                onClick={() => navigate(`/results?collectionId=${col.id}&type=public&name=${encodeURIComponent(col.name)}`)}
                style={{
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.08)",
                  borderRadius: 12,
                  padding: "20px 22px",
                  cursor: "pointer",
                  textAlign: "left",
                  transition: "all 180ms",
                  display: "flex",
                  flexDirection: "column",
                  gap: 12,
                  minHeight: 140,
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = "rgba(255,255,255,0.08)"
                  e.currentTarget.style.borderColor = "rgba(255,255,255,0.18)"
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = "rgba(255,255,255,0.04)"
                  e.currentTarget.style.borderColor = "rgba(255,255,255,0.08)"
                }}
              >
                {/* Collection name */}
                <span style={{ color: "#eee", fontSize: 16, fontWeight: 600, lineHeight: 1.3 }}>
                  {col.name}
                </span>

                {/* Description placeholder — ready for future DTO field */}
                <span style={{ color: "#666", fontSize: 13, lineHeight: 1.4, flex: 1 }}>
                  {col.descriptiom ?? ""}
                </span>

                {/* Footer: article count */}
                <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: "auto" }}>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#888" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                  </svg>
                  <span style={{ color: "#888", fontSize: 13 }}>
                    {col.articleCount} {col.articleCount === 1 ? "article" : "articles"}
                  </span>
                </div>
              </button>
            ))}
          </div>
        )}
        </div>
      </div>
    </div>
  )
}
