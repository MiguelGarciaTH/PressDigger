import { useState, useEffect, useRef, useCallback } from "react"
import { useNavigate } from "react-router-dom"
import { PUBLIC_COLLECTIONS_URL } from "../config"
import { useLang } from "../contexts/LanguageContext"

interface Collection {
  id: number
  name: string
  descriptiom?: string
  articleCount: number
}

const PAGE_SIZE = 10

export default function PublicCollectionsPage() {
  const [collections, setCollections] = useState<Collection[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const sentinelRef = useRef<HTMLDivElement | null>(null)
  const navigate = useNavigate()
  const { t } = useLang()

  const fetchPage = useCallback(async (pageIndex: number) => {
    setLoading(true)
    try {
      const url = `${PUBLIC_COLLECTIONS_URL}?page=${pageIndex}&size=${PAGE_SIZE}`
      const r = await fetch(url, { credentials: "include" })
      if (!r.ok) throw new Error(`${r.status}`)
      const data = await r.json()
      // Support both Spring Page wrapper ({ content, last }) and plain arrays
      const items: Collection[] = Array.isArray(data) ? data : data.content ?? []
      const isLast: boolean = Array.isArray(data) ? items.length < PAGE_SIZE : (data.last ?? true)
      setCollections((prev) => pageIndex === 0 ? items : [...prev, ...items])
      setHasMore(!isLast)
    } catch (err: any) {
      setError(err.message ?? "Failed to load")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    document.title = "Public Collections — PressDigger"
    fetchPage(0)
  }, [fetchPage])

  useEffect(() => {
    if (!hasMore || loading) return
    const sentinel = sentinelRef.current
    if (!sentinel) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          setPage((prev) => {
            const next = prev + 1
            fetchPage(next)
            return next
          })
        }
      },
      { threshold: 0.1 }
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasMore, loading, fetchPage])

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", color: "#eee", display: "flex", flexDirection: "column", overflow: "hidden", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
      {/* Bottom fade overlay */}
      <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 260, background: "linear-gradient(to bottom, transparent 0%, rgba(15,15,15,0.6) 40%, rgba(15,15,15,0.92) 70%, #0f0f0f 100%)", pointerEvents: "none", zIndex: 10 }} />
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
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>{t.publicCollections}</h2>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: "auto", padding: "calc(20vh - 60px) 24px 32px", display: "flex", justifyContent: "center", alignItems: "flex-start" }}>
        <div style={{ width: "100%", maxWidth: 900 }}>
        {loading && collections.length === 0 && (
          <p style={{ color: "#888", fontSize: 14, textAlign: "center", padding: 40 }}>{t.loading}</p>
        )}

        {error && (
          <p style={{ color: "#e55", fontSize: 14, textAlign: "center", padding: 40 }}>
            {t.failedToLoad}
          </p>
        )}

        {!loading && !error && collections.length === 0 && (
          <p style={{ color: "#777", fontSize: 14, textAlign: "center", padding: 40 }}>
            {t.noCollectionsFound}
          </p>
        )}

        {collections.length > 0 && (
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
                    {col.articleCount} {col.articleCount === 1 ? t.article : t.articles}
                  </span>
                </div>
              </button>
            ))}
          </div>
        )}

        {/* Sentinel for infinite scroll */}
        <div ref={sentinelRef} style={{ height: 1 }} />

        {loading && collections.length > 0 && (
          <p style={{ color: "#888", fontSize: 13, textAlign: "center", padding: "16px 0" }}>{t.loading}</p>
        )}
        </div>
      </div>
    </div>
  )
}
