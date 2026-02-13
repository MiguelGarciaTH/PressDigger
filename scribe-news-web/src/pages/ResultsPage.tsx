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
  const [baselineScale, setBaselineScale] = useState<number>(0.5)
  const [viewerSrc, setViewerSrc] = useState<string>("")

  const viewerRef = useRef<HTMLDivElement | null>(null)
  const paperRef = useRef<HTMLDivElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  const stripRef = useRef<HTMLDivElement | null>(null)
  const thumbRefs = useRef<(HTMLDivElement | null)[]>([])
  const isDraggingRef = useRef(false)
  const startXRef = useRef(0)
  const startYRef = useRef(0)
  const originRef = useRef({ x: 0, y: 0 })

  const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))

  // Zoom function
  const zoomAt = useCallback((nextScale: number) => {
    const ns = clamp(nextScale, 0.3, 20)
    setTranslate({ x: 0, y: 0 })
    setScale(ns)
  }, [])

  // Image load handler - fit image to canvas height for consistent sizing
  const onViewerImgLoad = useCallback((e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget
    imgRef.current = img
    const paperEl = paperRef.current
    if (paperEl && img.naturalHeight > 0 && img.naturalWidth > 0) {
      const paperRect = paperEl.getBoundingClientRect()
      // Calculate scale to fit image width to 70% of canvas width
      const targetWidth = paperRect.width * 0.7
      const scaleToFit = targetWidth / img.naturalWidth
      const clampedScale = clamp(scaleToFit, 0.1, 3)
      setScale(clampedScale)
      setBaselineScale(clampedScale)
    } else {
      setScale(1)
      setBaselineScale(1)
    }
    setTranslate({ x: 0, y: 0 })
  }, [])

  // Double-click to toggle zoom
  const onImageDoubleClick = useCallback(() => {
    setScale(current => {
      const next = current >= 3 ? baselineScale : Math.min(20, current * 2)
      setTranslate({ x: 0, y: 0 })
      return next
    })
  }, [baselineScale])

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

  const onSelect = useCallback((idx: number) => {
    setSelectedIndex(idx)
    setTranslate({ x: 0, y: 0 })
    const el = thumbRefs.current[idx]
    if (el && stripRef.current) {
      el.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" })
    }
  }, [])

  // Update viewer source when selection changes
  useEffect(() => {
    const sel = frames[selectedIndex] ?? {}
    const art = sel.article ?? {}
    const url = art.originalImagePath ?? art.smallImagePath
    if (url) {
      setViewerSrc(getImageUrl(url, 'original'))
    }
  }, [selectedIndex, frames])

  // Reset translate when scale <= 1
  useEffect(() => {
    if (scale <= 1) setTranslate({ x: 0, y: 0 })
  }, [scale])

  // Wheel zoom
  useEffect(() => {
    const el = viewerRef.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const factor = Math.pow(1.0025, -e.deltaY)
      setScale((current) => clamp(current * factor, 0.3, 20))
    }
    el.addEventListener("wheel", onWheel, { passive: false })
    return () => el.removeEventListener("wheel", onWheel)
  }, [])

  // Horizontal scroll on thumbnail strip via mouse wheel
  useEffect(() => {
    const strip = stripRef.current
    if (!strip) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      strip.scrollLeft += e.deltaY
    }
    strip.addEventListener("wheel", onWheel, { passive: false })
    return () => strip.removeEventListener("wheel", onWheel)
  }, [])

  // Keyboard navigation
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight") onSelect(Math.min(frames.length - 1, selectedIndex + 1))
      else if (e.key === "ArrowLeft") onSelect(Math.max(0, selectedIndex - 1))
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [selectedIndex, frames.length, onSelect])

  // Resizer
  useEffect(() => {
    const handle = document.getElementById("viewer-resizer")
    if (!handle) return
    let dragging = false
    let startY = 0
    let startH = viewerHeight
    const onDown = (e: PointerEvent) => { dragging = true; startY = e.clientY; startH = viewerHeight }
    const onMove = (e: PointerEvent) => { if (!dragging) return; setViewerHeight(Math.max(300, Math.min(window.innerHeight - 200, startH + e.clientY - startY))) }
    const onUp = () => { dragging = false }
    handle.addEventListener("pointerdown", onDown)
    window.addEventListener("pointermove", onMove)
    window.addEventListener("pointerup", onUp)
    return () => { handle.removeEventListener("pointerdown", onDown); window.removeEventListener("pointermove", onMove); window.removeEventListener("pointerup", onUp) }
  }, [viewerHeight])

  const loadMore = useCallback(async () => {
    if (loading || last || !query) return
    setLoading(true)
    const nextPage = page + 1
    try {
      const res = await fetch(`${SEARCH_URL}?page=${nextPage}&size=20`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: query }),
      })
      if (!res.ok) throw new Error(`Failed: ${res.status}`)
      const data = await res.json()
      setFrames((prev) => [...prev, ...(data.content ?? [])])
      setPage(nextPage)
      setLast(!!data.last)
    } catch (error) {
      console.error("Load error:", error)
    } finally {
      setLoading(false)
    }
  }, [query, page, last, loading])

  // Load initial
  useEffect(() => {
    if (frames.length > 0 || !query || loading) return
    loadMore()
  }, [query])

  // Load more on scroll
  useEffect(() => {
    const strip = stripRef.current
    if (!strip) return
    const handleScroll = () => {
      const distance = strip.scrollWidth - (strip.scrollLeft + strip.clientWidth)
      if (distance < 500 && !loading && !last) loadMore()
    }
    strip.addEventListener("scroll", handleScroll, { passive: true })
    return () => strip.removeEventListener("scroll", handleScroll)
  }, [loading, last, loadMore])

  if (!query) return <div className="max-w-3xl mx-auto p-6"><h2 className="text-xl font-semibold mb-4">Microfilm</h2><p className="text-gray-500">No query provided.</p></div>
  if (frames.length === 0) return <div className="max-w-3xl mx-auto p-6"><h2 className="text-xl font-semibold mb-4">Results for "{query}"</h2><p className="text-gray-500">No results available.</p></div>

  const selected = frames[selectedIndex] ?? {}
  const article = selected.article ?? {}

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", padding: 20, color: "#eee", display: "flex", flexDirection: "column", overflow: "hidden", zIndex: 60 }}>
      <h2 className="text-xl font-semibold mb-3">Microfilm — Results for "{query}"</h2>

      <div ref={viewerRef} onPointerDown={handlePointerDown} style={{ flex: 1, height: viewerHeight, display: "flex", gap: 16, overflow: "hidden", cursor: "grab" }}>
        <div style={{ flex: 1, display: "flex", justifyContent: "center" }}>
          <div ref={paperRef} style={{ width: "92%", height: "100%", background: "#faf6ef", padding: 12, borderRadius: 6, boxShadow: "0 8px 30px rgba(0,0,0,0.5)", overflow: "hidden", position: "relative" }}>
            {viewerSrc ? (
              <img ref={imgRef} src={viewerSrc} alt={article.title ?? "article"} onLoad={onViewerImgLoad} onDoubleClick={onImageDoubleClick} draggable={false}
                style={{ 
                  transform: `translate(${translate.x}px, ${translate.y}px) scale(${scale})`, 
                  transition: "transform 120ms", 
                  width: "auto",
                  height: "auto",
                  display: "block", 
                  transformOrigin: "center top", 
                  filter: "grayscale(1) contrast(1.05)", 
                  margin: "0 auto", 
                  userSelect: "none" 
                }} />
            ) : <div style={{ color: "#666" }}>No image</div>}

            {/* Minimap */}
            {viewerSrc && (
              <div
                style={{
                  position: "absolute",
                  right: 8,
                  top: 8,
                  width: 120,
                  height: 80,
                  background: "rgba(0, 0, 0, 0.7)",
                  border: "1px solid rgba(255, 255, 255, 0.3)",
                  borderRadius: 4,
                  overflow: "hidden",
                  cursor: "pointer",
                }}
                onClick={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect()
                  const ratioX = (e.clientX - rect.left) / rect.width
                  const ratioY = (e.clientY - rect.top) / rect.height
                  const paperEl = paperRef.current
                  const img = imgRef.current
                  if (paperEl && img) {
                    const imgW = img.naturalWidth * scale
                    const imgH = img.naturalHeight * scale
                    const targetX = -(imgW * ratioX - paperEl.clientWidth / 2)
                    const targetY = -(imgH * ratioY - paperEl.clientHeight / 2)
                    setTranslate({ x: Math.round(targetX), y: Math.round(targetY) })
                  }
                }}
              >
                <div
                  style={{
                    position: "absolute",
                    inset: 0,
                    backgroundImage: `url(${viewerSrc})`,
                    backgroundSize: "contain",
                    backgroundRepeat: "no-repeat",
                    backgroundPosition: "center",
                    opacity: 0.6,
                  }}
                />
                {paperRef.current && imgRef.current && (
                  <div
                    style={{
                      position: "absolute",
                      border: "2px solid #0f0",
                      pointerEvents: "none",
                      left: `${clamp(50 - (translate.x / (imgRef.current.naturalWidth * scale)) * 100, 0, 100)}%`,
                      top: `${clamp(50 - (translate.y / (imgRef.current.naturalHeight * scale)) * 100, 0, 100)}%`,
                      width: `${clamp((paperRef.current.clientWidth / (imgRef.current.naturalWidth * scale)) * 100, 5, 100)}%`,
                      height: `${clamp((paperRef.current.clientHeight / (imgRef.current.naturalHeight * scale)) * 100, 5, 100)}%`,
                      transform: "translate(-50%, -50%)",
                    }}
                  />
                )}
              </div>
            )}

            {/* Zoom controls */}
            <div style={{ position: "absolute", left: "50%", bottom: 12, transform: "translateX(-50%)", display: "flex", gap: 8, background: "rgba(0,0,0,0.75)", padding: "8px 16px", borderRadius: 24 }}>
              <button onClick={() => zoomAt(scale - 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>−</button>
              <div style={{ minWidth: 60, textAlign: "center", padding: "4px 8px", background: "rgba(255,255,255,0.1)", borderRadius: 12, fontSize: 13, color: "#fff" }}>{Math.round((scale / baselineScale) * 100)}%</div>
              <button onClick={() => zoomAt(scale + 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>+</button>
              <button onClick={() => zoomAt(baselineScale)} style={{ padding: "4px 10px", borderRadius: 12, border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 12, cursor: "pointer" }}>Fit</button>
            </div>
          </div>
        </div>
        <div style={{ width: 280, display: "flex", flexDirection: "column", gap: 12 }}>
          <a href={article.linkToArchiveTrimmed ?? article.linkToArchive} target="_blank" rel="noopener noreferrer" style={{ color: "#9bd", fontWeight: 700, fontSize: 15 }}>{article.title ?? "Untitled"}</a>
          {article.publishedDate && <div style={{ fontSize: 12, color: "#9aa" }}>{article.publishedDate}</div>}
          <p style={{ fontSize: 12, color: "#ddd", lineHeight: 1.5, flex: 1, overflow: "auto" }}>{selected.content ?? article.summary ?? "No summary."}</p>
        </div>
      </div>

      <div id="viewer-resizer" style={{ height: 8, cursor: "row-resize", background: "linear-gradient(90deg,#222,#111,#222)", borderRadius: 4, margin: "12px 0" }} />

      <div ref={stripRef} style={{ display: "flex", gap: 12, padding: 12, overflowX: "auto", background: "#060606", borderRadius: 8, height: 160 }}>
        <div style={{ width: 40, flexShrink: 0 }} />
        {frames.map((it: any, idx: number) => {
          const art = it.article ?? {}
          const thumbUrl = getImageUrl(art.smallImagePath, 'small')
          return (
            <div key={idx} ref={el => { thumbRefs.current[idx] = el }} onClick={() => onSelect(idx)}
              style={{ flexShrink: 0, width: 200, height: 136, background: "#111", borderRadius: 6, overflow: "hidden", border: selectedIndex === idx ? "2px solid #3aa" : "1px solid #222", cursor: "pointer", position: "relative", transform: selectedIndex === idx ? "scale(1.03)" : "none", transition: "transform 120ms" }}>
              {thumbUrl && <img src={thumbUrl} alt={art.title ?? "thumb"} style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top", filter: "grayscale(1)" }} />}
              <div style={{ position: "absolute", left: 6, top: 6, fontSize: 11, color: "#fff", background: "rgba(0,0,0,0.6)", padding: "2px 6px", borderRadius: 4, fontWeight: 500 }}>{idx + 1}</div>
            </div>
          )
        })}
        <div style={{ width: 120, flexShrink: 0 }} />
      </div>

      <div style={{ display: "flex", gap: 12, marginTop: 12 }}>
        <button onClick={() => onSelect(Math.max(0, selectedIndex - 1))} className="px-3 py-2 bg-gray-800 text-white rounded">◀</button>
        <button onClick={() => onSelect(Math.min(frames.length - 1, selectedIndex + 1))} className="px-3 py-2 bg-gray-800 text-white rounded">▶</button>
      </div>
    </div>
  )
}
