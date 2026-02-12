import React, { useEffect, useRef, useState, useCallback } from "react"
import { useSearchParams, useLocation } from "react-router-dom"
import { SEARCH_URL } from "../config"

function isHttpUrl(s?: string) {
  return typeof s === "string" && /^https?:\/\//i.test(s)
}

function getImageUrl(filePath?: string, size: 'small' | 'original' = 'original') {
  if (!filePath) return ""
  if (/^https?:\/\//.test(filePath)) return filePath
  const match = filePath.match(/images\/(small|original)\/([^/]+)$/)
  if (match) {
    const [, folder, filename] = match
    return `/scribe-ref/images/${folder}/${filename}`
  }
  const filename = filePath.split('/').pop()
  if (filename) {
    return `/scribe-ref/images/${size}/${filename}`
  }
  return ""
}

export default function ResultsPage() {
  const [params] = useSearchParams()
  const location = useLocation()
  const state = (location.state || {}) as any

  const query = state.query ?? params.get("q") ?? ""
  const initialItems = (state.results as any)?.content ?? []

  const [frames, setFrames] = useState<any[]>(initialItems)
  const [page, setPage] = useState<number>(0)
  const [last, setLast] = useState<boolean>((state.results as any)?.last ?? false)
  const [loading, setLoading] = useState(false)
  const [selectedIndex, setSelectedIndex] = useState<number>(0)
  const [scale, setScale] = useState<number>(1)
  const [translate, setTranslate] = useState<{ x: number; y: number }>({ x: 0, y: 0 })
  const [viewerHeight, setViewerHeight] = useState<number>(Math.round(window.innerHeight * 0.68))

  const viewerRef = useRef<HTMLDivElement | null>(null)
  const paperRef = useRef<HTMLDivElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  const stripRef = useRef<HTMLDivElement | null>(null)
  const thumbRefs = useRef<(HTMLDivElement | null)[]>([])

  const pointerIdRef = useRef<number | null>(null)
  const isDraggingRef = useRef(false)
  const startXRef = useRef(0)
  const startYRef = useRef(0)
  const originRef = useRef({ x: 0, y: 0 })

  const [viewerSrc, setViewerSrc] = useState<string>("")

  const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))

  const handlePointerMove = useCallback((e: PointerEvent) => {
    if (!isDraggingRef.current) return
    const dx = e.clientX - startXRef.current
    const dy = e.clientY - startYRef.current
    setTranslate({ x: Math.round(originRef.current.x + dx), y: Math.round(originRef.current.y + dy) })
  }, [])

  const handlePointerUp = useCallback(() => {
    isDraggingRef.current = false
    if (viewerRef.current) viewerRef.current.style.cursor = "grab"
    document.body.style.userSelect = ""
    window.removeEventListener("pointermove", handlePointerMove)
    window.removeEventListener("pointerup", handlePointerUp)
  }, [handlePointerMove])

  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    if (e.button !== 0) return
    e.preventDefault()
    isDraggingRef.current = true
    startXRef.current = e.clientX
    startYRef.current = e.clientY
    originRef.current = { x: translate.x, y: translate.y }
    if (viewerRef.current) viewerRef.current.style.cursor = "grabbing"
    document.body.style.userSelect = "none"
    window.addEventListener("pointermove", handlePointerMove)
    window.addEventListener("pointerup", handlePointerUp)
  }, [handlePointerMove, handlePointerUp, translate.x, translate.y])

  const onSelect = (idx: number) => {
    setSelectedIndex(idx)
    setScale(1)
    setTranslate({ x: 0, y: 0 })
    const el = thumbRefs.current[idx]
    if (el && stripRef.current) {
      el.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" })
    }
  }

  useEffect(() => {
    const sel = frames[selectedIndex] ?? {}
    const art = sel.article ?? {}
    const url = art.smallImagePath ?? art.originalImagePath
    if (url) {
      setViewerSrc(getImageUrl(url, 'small'))
    }
  }, [selectedIndex, frames])

  useEffect(() => {
    if (scale <= 1) setTranslate({ x: 0, y: 0 })
  }, [scale])

  useEffect(() => {
    const handle = document.getElementById("viewer-resizer")
    if (!handle) return
    let dragging = false
    let startY = 0
    let startH = viewerHeight

    const onDown = (e: PointerEvent) => {
      dragging = true
      startY = e.clientY
      startH = viewerHeight
    }
    const onMove = (e: PointerEvent) => {
      if (!dragging) return
      const dy = e.clientY - startY
      const next = Math.max(300, Math.min(window.innerHeight - 200, startH + dy))
      setViewerHeight(next)
    }
    const onUp = () => {
      dragging = false
    }

    handle.addEventListener("pointerdown", onDown)
    window.addEventListener("pointermove", onMove)
    window.addEventListener("pointerup", onUp)
    return () => {
      handle.removeEventListener("pointerdown", onDown)
      window.removeEventListener("pointermove", onMove)
      window.removeEventListener("pointerup", onUp)
    }
  }, [viewerHeight])

  const loadMore = useCallback(async () => {
    if (loading || last || !query) return
    setLoading(true)
    try {
      setPage((currentPage) => {
        const nextPage = currentPage + 1
        const params = new URLSearchParams({
          page: nextPage.toString(),
          size: "20"
        })
        const fullUrl = `${SEARCH_URL}?${params.toString()}`
        fetch(fullUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: query }),
        })
        .then(res => {
          if (!res.ok) {
            throw new Error(`Failed: ${res.status}`)
          }
          return res.json()
        })
        .then(data => {
          const content = data.content ?? []
          setFrames((prev) => [...prev, ...content])
          setPage(nextPage)
          setLast(!!data.last)
        })
        .catch(error => {
          console.error("Load error:", error)
        })
        .finally(() => {
          setLoading(false)
        })
        return currentPage
      })
    } catch (error) {
      console.error("Load error:", error)
      setLoading(false)
    }
  }, [query, last, loading])

  // Load initial results if frames is empty
  useEffect(() => {
    if (frames.length > 0 && page === 0) {
      return
    }
    if (query && page === 0 && !loading) {
      loadMore()
    }
  }, [query])

  // Load more when scrolling near bottom
  useEffect(() => {
    const handleScroll = () => {
      if (stripRef.current) {
        const { scrollLeft, scrollWidth, clientWidth } = stripRef.current
        const distance = scrollWidth - (scrollLeft + clientWidth)
        if (distance < 500 && !loading && !last) {
          loadMore()
        }
      }
    }
    const strip = stripRef.current
    if (strip) {
      strip.addEventListener("scroll", handleScroll, { passive: true })
      return () => strip.removeEventListener("scroll", handleScroll)
    }
  }, [loadMore, loading, last])

  if (!query) {
    return (
      <div className="max-w-3xl mx-auto p-6">
        <h2 className="text-xl font-semibold mb-4">Microfilm</h2>
        <p className="text-gray-500">No query provided. Use the search box to start.</p>
      </div>
    )
  }

  if (frames.length === 0) {
    return (
      <div className="max-w-3xl mx-auto p-6">
        <h2 className="text-xl font-semibold mb-4">Results for "{query}"</h2>
        <p className="text-gray-500">No results available.</p>
      </div>
    )
  }

  const selected = frames[selectedIndex] ?? {}
  const article = selected.article ?? {}
  const paperBg = { background: "#faf6ef", padding: 12, borderRadius: 6, boxShadow: "0 8px 30px rgba(0,0,0,0.5)" }

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", padding: 20, color: "#eee", display: "flex", flexDirection: "column", overflow: "hidden", zIndex: 60 }}>
      <div style={{ display: "flex", gap: 20, alignItems: "center", marginBottom: 12 }}>
        <h2 className="text-xl font-semibold">Microfilm — Results for "{query}"</h2>
      </div>

      <div ref={viewerRef} onPointerDown={handlePointerDown} style={{ flex: 1, height: viewerHeight, display: "flex", alignItems: "center", justifyContent: "center", position: "relative", padding: 18, overflow: "hidden", gap: 16 }}>
        <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "flex-start", justifyContent: "center", flex: 1 }}>
          <div style={{ width: "92%", height: "100%", position: "relative", ...paperBg, overflow: "hidden" }} ref={paperRef}>
            {viewerSrc ? (
              <img
                ref={imgRef}
                src={viewerSrc}
                alt={article.title ?? "article"}
                onError={(e) => console.error("Viewer image failed to load:", viewerSrc)}
                onLoad={() => console.log("Viewer image loaded:", viewerSrc)}
                style={{
                  transform: `translate(${translate.x}px, ${translate.y}px) scale(${scale})`,
                  transition: "transform 120ms ease-out",
                  width: "auto",
                  maxWidth: "100%",
                  height: "auto",
                  maxHeight: "100%",
                  objectFit: "contain" as const,
                  display: "block",
                  transformOrigin: "center top",
                  filter: "grayscale(1) contrast(1.05) brightness(0.98) sepia(0.02)",
                  margin: "0 auto",
                }}
                draggable={false}
              />
            ) : (
              <div style={{ color: "#666" }}>No image available</div>
            )}
          </div>
        </div>

        <div style={{ width: 280, flex: "0 0 auto", display: "flex", flexDirection: "column", gap: 12, paddingRight: 8 }}>
          <a href={article.linkToArchiveTrimmed ?? article.linkToArchive} target="_blank" rel="noopener noreferrer" style={{ color: "#9bd", fontWeight: 700, textDecoration: "none", fontSize: 15, lineHeight: 1.3 }}>
            {article.title ?? "Untitled"}
          </a>
          {article.publishedDate && <div style={{ fontSize: 12, color: "#9aa", marginTop: 4 }}>{article.publishedDate}</div>}
          <p style={{ marginTop: 8, fontSize: 12, color: "#ddd", lineHeight: 1.5, flex: 1, overflow: "auto" }}>{selected.content ?? article.summary ?? "No summary available."}</p>
        </div>
      </div>

      <div id="viewer-resizer" style={{ height: 8, cursor: "row-resize", background: "linear-gradient(90deg,#222,#111,#222)", borderRadius: 4, marginBottom: 12 }} />

      <div style={{ flex: "0 0 auto", display: "flex", flexDirection: "column", gap: 12, height: 240 }}>
        <div ref={stripRef} style={{ display: "flex", gap: 12, padding: "12px 8px", overflowX: "auto", alignItems: "flex-start", borderRadius: 8, background: "#060606", border: "1px solid rgba(255,255,255,0.03)", height: 160, boxSizing: "border-box" }}>
          <div style={{ width: 40, flex: "0 0 auto" }} />

          {frames.map((it: any, idx: number) => {
            const art = it.article ?? {}
            const thumbUrl = getImageUrl(art.smallImagePath, 'small') || ""
            return (
              <div
                key={idx}
                ref={(el) => {
                  thumbRefs.current[idx] = el
                }}
                onClick={() => onSelect(idx)}
                style={{
                  flex: "0 0 auto",
                  width: 200,
                  height: 150,
                  background: selectedIndex === idx ? "linear-gradient(180deg,#222,#111)" : "#111",
                  borderRadius: 6,
                  overflow: "hidden",
                  border: selectedIndex === idx ? "2px solid #3aa" : "1px solid rgba(255,255,255,0.03)",
                  cursor: "pointer",
                  position: "relative",
                  transform: selectedIndex === idx ? "scale(1.03)" : "none",
                  transition: "transform 120ms",
                }}
              >
                {thumbUrl && <img
                  src={thumbUrl}
                  alt={art.title ?? "thumb"}
                  onError={(e) => console.error("Thumb failed:", thumbUrl)}
                  onLoad={() => console.log("Thumb loaded:", thumbUrl)}
                  style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top center", display: "block", filter: "grayscale(1) contrast(1.05) brightness(0.95)" }}
                />}

                <div style={{ position: "absolute", left: 8, top: 8, fontSize: 12, color: "#ccc" }}>{idx + 1}</div>
              </div>
            )
          })}

          <div style={{ width: 120, flex: "0 0 auto" }} />
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button onClick={() => onSelect(Math.max(0, selectedIndex - 1))} className="px-3 py-2 bg-gray-800 text-white rounded">◀</button>
          <button onClick={() => onSelect(Math.min(frames.length - 1, selectedIndex + 1))} className="px-3 py-2 bg-gray-800 text-white rounded">▶</button>
        </div>
      </div>
    </div>
  )
}
