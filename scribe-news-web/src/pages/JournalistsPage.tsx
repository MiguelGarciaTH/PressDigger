import { useState, useEffect, useLayoutEffect, useCallback, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { AUTHORS_URL, authorArticleCountUrl } from "../config"
import { useLang } from "../contexts/LanguageContext"
import { useIsMobile, MOBILE_NAV_H } from "../hooks/useIsMobile"

interface Author {
  id: number
  name: string
}

interface Snapshot {
  authors: Author[]
  counts: Record<number, number>
  page: number
  isLast: boolean
  scrollTop: number
}

// Module-level: survives SPA navigation, cleared after restore
let snapshot: Snapshot | null = null

export default function JournalistsPage() {
  const [authors, setAuthors] = useState<Author[]>(() => snapshot?.authors ?? [])
  const [counts, setCounts] = useState<Record<number, number>>(() => snapshot?.counts ?? {})
  const [loading, setLoading] = useState(() => snapshot === null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(() => snapshot?.page ?? 0)
  const [isLast, setIsLast] = useState(() => snapshot?.isLast ?? false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const savedScroll = snapshot?.scrollTop ?? (sessionStorage.getItem("journalists_scroll") ? Number(sessionStorage.getItem("journalists_scroll")) : null)
  const scrollTarget = useRef<number | null>(savedScroll)
  const navigate = useNavigate()
  const { t } = useLang()
  const isMobile = useIsMobile()

  useEffect(() => {
    document.title = "Journalists — PressDigger"
    if (snapshot !== null) {
      snapshot = null
      sessionStorage.removeItem("journalists_scroll")
      return
    }
    sessionStorage.removeItem("journalists_scroll")
    fetch(`${AUTHORS_URL}?sort=name,asc&page=0&size=25`)
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status}`)
        return r.json()
      })
      .then((data) => {
        const newAuthors: Author[] = data.content ?? []
        setAuthors(newAuthors)
        setIsLast(!!data.last)
        setPage(0)
        fetchCounts(newAuthors)
      })
      .catch((err) => setError(err.message ?? "Failed to load"))
      .finally(() => setLoading(false))
  }, [])

  // Restore scroll synchronously after DOM update
  useLayoutEffect(() => {
    if (scrollTarget.current === null) return
    if (!scrollRef.current) return
    scrollRef.current.scrollTop = scrollTarget.current
    scrollTarget.current = null
  }, [authors.length])

  function fetchCounts(batch: Author[]) {
    batch.forEach((a) => {
      fetch(authorArticleCountUrl(a.id))
        .then((r) => r.ok ? r.json() : null)
        .then((n) => { if (typeof n === "number") setCounts((prev) => ({ ...prev, [a.id]: n })) })
        .catch(() => {})
    })
  }

  const loadMore = useCallback(() => {
    if (loadingMore || isLast) return
    const nextPage = page + 1
    setLoadingMore(true)
    fetch(`${AUTHORS_URL}?sort=name,asc&page=${nextPage}&size=25`)
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status}`)
        return r.json()
      })
      .then((data) => {
        const newAuthors: Author[] = data.content ?? []
        setAuthors((prev) => [...prev, ...newAuthors])
        setIsLast(!!data.last)
        setPage(nextPage)
        fetchCounts(newAuthors)
      })
      .catch((err) => console.error("Load more error:", err))
      .finally(() => setLoadingMore(false))
  }, [loadingMore, isLast, page])

  // Auto-load more if content doesn't fill the container
  useEffect(() => {
    if (loading || loadingMore || isLast) return
    const el = scrollRef.current
    if (!el) return
    if (el.scrollHeight <= el.clientHeight + 50) loadMore()
  }, [authors.length, loading, loadingMore, isLast, loadMore])

  // Vertical infinite scroll
  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const onScroll = () => {
      if (el.scrollHeight - el.scrollTop - el.clientHeight < 400) loadMore()
    }
    el.addEventListener("scroll", onScroll, { passive: true })
    return () => el.removeEventListener("scroll", onScroll)
  }, [loadMore, authors.length])

  return (
    <div style={{ position: "fixed", top: 0, left: 0, right: 0, bottom: isMobile ? MOBILE_NAV_H : 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", color: "#eee", display: "flex", flexDirection: "column", overflow: "hidden", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>

      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "16px 20px", borderBottom: "1px solid #222" }}>
        <button
          onClick={() => navigate("/")}
          style={{ background: "rgba(255,255,255,0.1)", border: "1px solid rgba(255,255,255,0.2)", borderRadius: 8, padding: "8px 12px", color: "#eee", fontSize: 14, cursor: "pointer", display: "flex", alignItems: "center" }}
        >
          <span style={{ fontSize: 20, fontWeight: 900, textShadow: "0 0 2px rgba(238,238,238,0.8)" }}>←</span>
        </button>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>{t.journalists}</h2>
      </div>

      {/* Status */}
      {loading && <p style={{ color: "#888", fontSize: 14, textAlign: "center", padding: 40, margin: 0 }}>{t.loading}</p>}
      {error && <p style={{ color: "#e55", fontSize: 14, textAlign: "center", padding: 40, margin: 0 }}>{t.failedToLoad}</p>}
      {!loading && !error && authors.length === 0 && <p style={{ color: "#777", fontSize: 14, textAlign: "center", padding: 40, margin: 0 }}>{t.noJournalistsFound}</p>}

      {/* Scrollable grid with fade overlays */}
      {!loading && !error && authors.length > 0 && (
        <div style={{ flex: 1, position: "relative", overflow: "hidden", marginRight: isMobile ? 0 : 52 }}>
          {/* Top fade */}
          <div style={{ position: "absolute", top: 0, left: 0, right: 0, height: 72, background: "linear-gradient(to bottom, #090909 0%, transparent 100%)", pointerEvents: "none", zIndex: 1 }} />
          {/* Bottom fade */}
          <div style={{ position: "absolute", bottom: 0, left: 0, right: 0, height: 72, background: "linear-gradient(to top, #090909 0%, transparent 100%)", pointerEvents: "none", zIndex: 1 }} />

          <div
            ref={scrollRef}
            style={{
              position: "absolute",
              inset: 0,
              overflowY: "auto",
              overflowX: "hidden",
              padding: isMobile ? "40px 16px" : "60px 40px",
            }}
          >
            <div style={{
              display: "grid",
              gridTemplateColumns: isMobile ? "repeat(2, 1fr)" : "repeat(4, 1fr)",
              gap: 16,
              maxWidth: 860,
              margin: "0 auto",
            }}>
              {authors.map((author) => (
                <button
                  key={author.id}
                  onClick={() => {
                    const st = scrollRef.current?.scrollTop ?? 0
                    snapshot = { authors, counts, page, isLast, scrollTop: st }
                    sessionStorage.setItem("journalists_scroll", String(st))
                    navigate(`/results?authorId=${author.id}&authorName=${encodeURIComponent(author.name)}`)
                  }}
                  style={{
                    background: "rgba(255,255,255,0.04)",
                    border: "1px solid rgba(255,255,255,0.08)",
                    borderRadius: 12,
                    padding: "20px 16px 16px",
                    cursor: "pointer",
                    textAlign: "center",
                    transition: "background 160ms, border-color 160ms",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    gap: 12,
                    minWidth: 0,
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.09)"
                    e.currentTarget.style.borderColor = "rgba(255,255,255,0.2)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.04)"
                    e.currentTarget.style.borderColor = "rgba(255,255,255,0.08)"
                  }}
                >
                  <div style={{
                    width: 44, height: 44, borderRadius: "50%",
                    background: "rgba(255,255,255,0.07)",
                    border: "1px solid rgba(255,255,255,0.14)",
                    display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
                  }}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#999" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                  </div>
                  <div style={{ minWidth: 0, width: "100%" }}>
                    <span style={{
                      color: "#f0f0f0", fontSize: 14, fontWeight: 600, lineHeight: 1.4,
                      overflow: "hidden", display: "-webkit-box",
                      WebkitLineClamp: 2, WebkitBoxOrient: "vertical",
                    }}>
                      {author.name}
                    </span>
                    {counts[author.id] !== undefined && (
                      <span style={{ display: "block", marginTop: 6, color: "#555", fontSize: 12 }}>
                        {counts[author.id]} {counts[author.id] === 1 ? t.article : t.articles}
                      </span>
                    )}
                  </div>
                </button>
              ))}
            </div>

            {loadingMore && (
              <p style={{ color: "#555", fontSize: 13, textAlign: "center", padding: "24px 0", margin: 0 }}>
                {t.loadingMore}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
