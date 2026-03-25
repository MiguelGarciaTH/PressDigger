import { useState, useRef, useCallback, useEffect } from "react"
import { useLocation, useNavigate } from "react-router-dom"
import { useLang } from "../contexts/LanguageContext"
import { articleUrl, getImageUrl } from "../config"
import BookmarkButton from "../components/BookmarkButton"
import AnnotationButton from "../components/AnnotationButton"
import { useAuth } from "../components/useAuth"
import { useIsMobile, MOBILE_NAV_H } from "../hooks/useIsMobile"

interface NarrativeReference {
  marker: string
  title: string
  articleId: number
  author: string | null
  publishedDate: string
  summary: string
  linkToArchive: string
  site: string
}

interface NarrativeResult {
  text: string
  references: NarrativeReference[]
}

interface ArticleDetail {
  id: number
  title: string
  publishedDate: string
  linkToArchive: string
  originalImagePath?: string
  smallImagePath?: string
}



function formatDate(iso: string) {
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" })
  } catch {
    return iso
  }
}

export default function DigestPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { t } = useLang()
  const { user } = useAuth()
  const state = location.state as { query: string; narrative: NarrativeResult } | null
  const isMobile = useIsMobile()

  // Image viewer state
  const [viewerArticle, setViewerArticle] = useState<ArticleDetail | null>(null)
  const [viewerLoading, setViewerLoading] = useState(false)
  const [tooltipMarker, setTooltipMarker] = useState<string | null>(null)
  const [scale, setScale] = useState(1)
  const [baselineScale, setBaselineScale] = useState(0.5)
  const [translate, setTranslate] = useState({ x: 0, y: 0 })
  const viewerRef = useRef<HTMLDivElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  const isDraggingRef = useRef(false)
  const startXRef = useRef(0)
  const startYRef = useRef(0)
  const originRef = useRef({ x: 0, y: 0 })

  const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))

  const zoomAt = useCallback((next: number) => {
    setScale(clamp(next, 0.3, 20))
    setTranslate({ x: 0, y: 0 })
  }, [])

  const onImgLoad = useCallback((e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget
    imgRef.current = img
    const el = viewerRef.current
    if (el && img.naturalWidth > 0) {
      const rect = el.getBoundingClientRect()
      const s = clamp((rect.width * 0.7) / img.naturalWidth, 0.1, 3)
      setScale(s)
      setBaselineScale(s)
    } else {
      setScale(1)
      setBaselineScale(1)
    }
    setTranslate({ x: 0, y: 0 })
  }, [])

  const handlePointerMove = useCallback((e: PointerEvent) => {
    if (!isDraggingRef.current) return
    setTranslate({
      x: Math.round(originRef.current.x + (e.clientX - startXRef.current)),
      y: Math.round(originRef.current.y + (e.clientY - startYRef.current)),
    })
  }, [])

  const handlePointerUp = useCallback(() => {
    isDraggingRef.current = false
    if (viewerRef.current) viewerRef.current.style.cursor = "grab"
    document.body.style.userSelect = ""
    window.removeEventListener("pointermove", handlePointerMove)
    window.removeEventListener("pointerup", handlePointerUp)
  }, [handlePointerMove])

  const handlePointerDown = useCallback(
    (e: React.PointerEvent) => {
      if (e.button !== 0) return
      if ((e.target as HTMLElement).closest("button, a, [role='button']")) return
      e.preventDefault()
      isDraggingRef.current = true
      startXRef.current = e.clientX
      startYRef.current = e.clientY
      originRef.current = { x: translate.x, y: translate.y }
      if (viewerRef.current) viewerRef.current.style.cursor = "grabbing"
      document.body.style.userSelect = "none"
      window.addEventListener("pointermove", handlePointerMove)
      window.addEventListener("pointerup", handlePointerUp)
    },
    [handlePointerMove, handlePointerUp, translate.x, translate.y]
  )

  useEffect(() => {
    const el = viewerRef.current
    if (!el || (!viewerArticle && !viewerLoading)) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      setScale((s) => clamp(s * Math.pow(1.0025, -e.deltaY), 0.3, 20))
    }
    el.addEventListener("wheel", onWheel, { passive: false })
    return () => el.removeEventListener("wheel", onWheel)
  }, [viewerArticle, viewerLoading])

  useEffect(() => {
    if (scale <= 1) setTranslate({ x: 0, y: 0 })
  }, [scale])

  async function openViewer(ref: NarrativeReference) {
    setViewerLoading(true)
    setViewerArticle(null)
    setScale(1)
    setTranslate({ x: 0, y: 0 })
    try {
      const res = await fetch(articleUrl(ref.articleId))
      if (res.ok) {
        setViewerArticle(await res.json())
      } else {
        setViewerArticle({ id: ref.articleId, title: ref.title, publishedDate: ref.publishedDate, linkToArchive: ref.linkToArchive })
      }
    } catch {
      setViewerArticle({ id: ref.articleId, title: ref.title, publishedDate: ref.publishedDate, linkToArchive: ref.linkToArchive })
    } finally {
      setViewerLoading(false)
    }
  }

  function closeViewer() {
    setViewerArticle(null)
    setViewerLoading(false)
  }

  if (!state?.narrative) {
    return (
      <div style={{ position: "fixed", inset: 0, background: "#0a0a0a", display: "flex", alignItems: "center", justifyContent: "center", color: "#888", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
        <div style={{ textAlign: "center" }}>
          <p>{t.digestNoResult}</p>
          <button
            onClick={() => navigate("/")}
            style={{ marginTop: 12, background: "none", border: "1px solid #444", borderRadius: 8, padding: "8px 16px", color: "#aaa", cursor: "pointer", fontSize: 14 }}
          >
            {t.back}
          </button>
        </div>
      </div>
    )
  }

  const { query, narrative } = state
  const viewerSrc = viewerArticle
    ? getImageUrl(viewerArticle.originalImagePath ?? viewerArticle.smallImagePath, "original")
    : ""

  function renderText(text: string) {
    const parts = text.split(/(\[\d+\])/)
    return parts.map((part, i) => {
      const match = part.match(/^\[(\d+)\]$/)
      if (match) {
        const ref = narrative.references.find((r) => r.marker === match[1])
        return (
          <sup key={i}>
            <button
              onClick={() => ref && openViewer(ref)}
              title={ref?.title}
              style={{ color: "#3aa", cursor: ref ? "pointer" : "default", background: "none", border: "none", padding: "0 1px", fontSize: "0.78em", fontWeight: 700, lineHeight: 1, transition: "color 150ms" }}
              onMouseEnter={(e) => (e.currentTarget.style.color = "#5cc")}
              onMouseLeave={(e) => (e.currentTarget.style.color = "#3aa")}
            >
              [{match[1]}]
            </button>
          </sup>
        )
      }
      return <span key={i}>{part}</span>
    })
  }

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: isMobile ? MOBILE_NAV_H : 0,
        background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)",
        color: "#ddd",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          flexShrink: 0,
          background: "rgba(7,7,7,0.95)",
          backdropFilter: "blur(8px)",
          borderBottom: "1px solid #1a1a1a",
          padding: "12px 20px",
          display: "flex",
          alignItems: "center",
          gap: 16,
        }}
      >
        <button
          onClick={() => navigate(-1)}
          style={{ background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.12)", borderRadius: 8, padding: "6px 12px", color: "#aaa", fontSize: 13, cursor: "pointer", display: "flex", alignItems: "center", gap: 6, flexShrink: 0, transition: "all 150ms" }}
          onMouseEnter={(e) => { e.currentTarget.style.color = "#eee"; e.currentTarget.style.borderColor = "rgba(255,255,255,0.25)" }}
          onMouseLeave={(e) => { e.currentTarget.style.color = "#aaa"; e.currentTarget.style.borderColor = "rgba(255,255,255,0.12)" }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          {t.back}
        </button>

        <div style={{ display: "flex", alignItems: "center", gap: 6, color: "#3aa", fontSize: 13, fontWeight: 600, flexShrink: 0 }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74Z" />
            <path d="M19 2l.9 2.7L22 6l-2.1.7L19 9l-.9-2.7L16 6l2.1-.7Z" opacity=".6" />
            <path d="M5 16l.6 1.8L7.4 18l-1.4.5L5 20l-.6-1.8L2.6 18l1.4-.5Z" opacity=".4" />
          </svg>
          {t.aiDigest}
        </div>

        <div style={{ flex: 1, fontSize: 14, color: "#666", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={query}>
          "{query}"
        </div>
      </div>

      {/* Scrollable content */}
      <div style={{ flex: 1, overflowY: "auto" }}>
        <div style={{ maxWidth: 780, margin: "0 auto", padding: "32px 20px 64px" }}>

          {/* Narrative text */}
          <div
            style={{
              background: "#111",
              borderRadius: 12,
              border: "1px solid #222",
              padding: "28px 32px",
              lineHeight: 1.85,
              fontSize: 15,
              color: "#ccc",
              boxShadow: "0 4px 24px rgba(0,0,0,0.4)",
            }}
          >
            <p style={{ margin: 0 }}>{renderText(narrative.text)}</p>
          </div>

          {/* References */}
          {narrative.references.length > 0 && (
            <div style={{ marginTop: 32 }}>
              <h3 style={{ margin: "0 0 14px", fontSize: 11, fontWeight: 600, letterSpacing: "0.08em", textTransform: "uppercase", color: "#555" }}>
                {t.digestReferences}
              </h3>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {narrative.references.map((ref) => (
                  <div
                    key={ref.marker}
                    style={{ position: "relative", display: "flex", gap: 12, alignItems: "center", padding: "10px 14px", background: "#0f0f0f", borderRadius: 8, border: "1px solid #1c1c1c", transition: "border-color 150ms", cursor: "pointer" }}
                    onClick={() => openViewer(ref)}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = "#2a4a4a"; setTooltipMarker(ref.marker) }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = "#1c1c1c"; setTooltipMarker(null) }}
                  >
                    {tooltipMarker === ref.marker && ref.summary && (
                      <div style={{ position: "absolute", ...(isMobile ? { top: "calc(100% + 4px)", left: 0, right: 0 } : { top: 0, left: "calc(100% + 8px)", width: 280 }), background: "#1a1a1a", border: "1px solid #2a4a4a", borderRadius: 8, padding: "10px 14px", fontSize: 12, color: "#ccc", lineHeight: 1.6, zIndex: 10, pointerEvents: "none", boxShadow: "0 4px 16px rgba(0,0,0,0.6)" }}>
                        {ref.summary}
                      </div>
                    )}
                    <span style={{ color: "#3aa", fontWeight: 700, fontSize: 12, minWidth: 22, flexShrink: 0 }}>[{ref.marker}]</span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, color: "#ccc", fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", marginBottom: 3 }}>
                        {ref.title}
                      </div>
                      <div style={{ fontSize: 11, color: "#555" }}>
                        {[ref.author, ref.site, formatDate(ref.publishedDate)].filter(Boolean).join(" · ")}
                      </div>
                    </div>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#555" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
                      <circle cx="11" cy="11" r="8" /><path d="m21 21-4.35-4.35" />
                    </svg>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Image viewer modal */}
      {(viewerArticle !== null || viewerLoading) && (
        <div
          style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.9)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(8px)" }}
          onClick={closeViewer}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{ width: "90%", height: "90%", background: "#0a0a0a", borderRadius: 12, overflow: "hidden", display: "flex", flexDirection: "column", border: "2px solid #3aa", boxShadow: "0 0 20px rgba(58,170,170,0.4), 0 16px 64px rgba(0,0,0,0.7)" }}
          >
            {/* Modal header */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: 16, borderBottom: "1px solid #222", flexShrink: 0 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <a
                  href={viewerArticle?.linkToArchive}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ fontSize: 16, fontWeight: 600, color: "#eee", textDecoration: "none", display: "inline-flex", alignItems: "center", gap: 6, transition: "color 150ms" }}
                  onMouseEnter={(e) => (e.currentTarget.style.color = "#3aa")}
                  onMouseLeave={(e) => (e.currentTarget.style.color = "#eee")}
                >
                  {viewerArticle?.title ?? "…"}
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                    <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
                  </svg>
                </a>
                {viewerArticle?.publishedDate && (
                  <div style={{ fontSize: 12, color: "#888", marginTop: 4 }}>{viewerArticle.publishedDate}</div>
                )}
              </div>
              <button
                onClick={closeViewer}
                style={{ background: "rgba(255,255,255,0.1)", border: "none", borderRadius: 8, padding: "8px 16px", color: "#eee", fontSize: 20, cursor: "pointer", lineHeight: 1, flexShrink: 0 }}
              >
                ×
              </button>
            </div>

            {/* Image viewer */}
            <div
              ref={viewerRef}
              onPointerDown={handlePointerDown}
              style={{ flex: 1, display: "flex", justifyContent: "center", alignItems: "flex-start", overflow: "hidden", position: "relative", cursor: "grab", background: "#000" }}
            >
              {viewerLoading ? (
                <div style={{ color: "#666", alignSelf: "center", fontSize: 14 }}>Loading…</div>
              ) : viewerSrc ? (
                <img
                  ref={imgRef}
                  src={viewerSrc}
                  alt={viewerArticle?.title ?? "article"}
                  onLoad={onImgLoad}
                  draggable={false}
                  style={{ transform: `translate(${translate.x}px, ${translate.y}px) scale(${scale})`, transition: "transform 120ms", width: "auto", height: "auto", display: "block", transformOrigin: "center top", filter: "grayscale(1) contrast(1.05)", margin: "0 auto", userSelect: "none" }}
                />
              ) : (
                <div style={{ color: "#666", alignSelf: "center", fontSize: 14 }}>{t.noImage}</div>
              )}

              {/* Zoom controls */}
              {!viewerLoading && (
                <div style={{ position: "absolute", left: "50%", bottom: 24, transform: "translateX(-50%)", display: "flex", gap: 8, background: "rgba(0,0,0,0.75)", padding: "8px 16px", borderRadius: 24 }}>
                  <button onClick={() => zoomAt(scale - 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>−</button>
                  <div style={{ minWidth: 60, textAlign: "center", padding: "4px 8px", background: "rgba(255,255,255,0.1)", borderRadius: 12, fontSize: 13, color: "#fff" }}>
                    {Math.round((scale / baselineScale) * 100)}%
                  </div>
                  <button onClick={() => zoomAt(scale + 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>+</button>
                  <button onClick={() => zoomAt(baselineScale)} style={{ padding: "4px 10px", borderRadius: 12, border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 12, cursor: "pointer" }}>
                    ⛶ {t.fit}
                  </button>
                  {viewerArticle && <BookmarkButton articleId={viewerArticle.id} />}
                  {viewerArticle && user && <AnnotationButton articleId={viewerArticle.id} />}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
