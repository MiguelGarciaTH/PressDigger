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
  const stripDraggingRef = useRef(false)
  const stripStartXRef = useRef(0)
  const stripScrollLeftRef = useRef(0)
  const stripClickTargetRef = useRef<number | null>(null)
  const stripDragDistanceRef = useRef(0)
  const sidebarRef = useRef<HTMLDivElement | null>(null)

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
    // Don't start drag if clicking on a button, interactive element, or sidebar
    if ((e.target as HTMLElement).closest('button, a, [role="button"], [data-sidebar]')) return
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
      // Don't zoom if mouse is over sidebar
      if (sidebarRef.current?.contains(e.target as Node)) return
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

  // Mouse drag scrolling for thumbnail strip
  useEffect(() => {
    const strip = stripRef.current
    if (!strip) return
    
    const onMouseDown = (e: MouseEvent) => {
      stripDraggingRef.current = true
      stripStartXRef.current = e.pageX - strip.offsetLeft
      stripScrollLeftRef.current = strip.scrollLeft
      stripDragDistanceRef.current = 0
      // Check if clicking on a thumbnail
      const thumbEl = (e.target as HTMLElement).closest('[data-thumb-idx]')
      stripClickTargetRef.current = thumbEl ? parseInt(thumbEl.getAttribute('data-thumb-idx') || '-1', 10) : null
      strip.style.cursor = 'grabbing'
    }
    
    const onMouseUp = () => {
      // If we didn't drag much, treat it as a click
      if (stripClickTargetRef.current !== null && stripDragDistanceRef.current < 5) {
        onSelect(stripClickTargetRef.current)
      }
      stripDraggingRef.current = false
      stripClickTargetRef.current = null
      strip.style.cursor = 'grab'
    }
    
    const onMouseMove = (e: MouseEvent) => {
      if (!stripDraggingRef.current) return
      e.preventDefault()
      const x = e.pageX - strip.offsetLeft
      const walk = (x - stripStartXRef.current) * 1.5
      stripDragDistanceRef.current = Math.abs(x - stripStartXRef.current)
      strip.scrollLeft = stripScrollLeftRef.current - walk
    }
    
    const onMouseLeave = () => {
      stripDraggingRef.current = false
      stripClickTargetRef.current = null
      strip.style.cursor = 'grab'
    }
    
    strip.addEventListener('mousedown', onMouseDown)
    strip.addEventListener('mouseup', onMouseUp)
    strip.addEventListener('mousemove', onMouseMove)
    strip.addEventListener('mouseleave', onMouseLeave)
    
    return () => {
      strip.removeEventListener('mousedown', onMouseDown)
      strip.removeEventListener('mouseup', onMouseUp)
      strip.removeEventListener('mousemove', onMouseMove)
      strip.removeEventListener('mouseleave', onMouseLeave)
    }
  }, [onSelect])

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

      <div ref={viewerRef} onPointerDown={handlePointerDown} style={{ flex: 1, height: viewerHeight, display: "flex", gap: 0, overflow: "hidden", cursor: "grab" }}>
        <div style={{ flex: 1, display: "flex", justifyContent: "center" }}>
          <div ref={paperRef} style={{ width: "100%", height: "100%", background: "#faf6ef", padding: 12, borderRadius: 6, boxShadow: "0 8px 30px rgba(0,0,0,0.5)", overflow: "hidden", position: "relative" }}>
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
            {viewerSrc && imgRef.current && (() => {
              const img = imgRef.current
              const aspectRatio = img.naturalHeight / img.naturalWidth
              // Dynamic height: min 80px, max 160px, based on aspect ratio
              const minimapW = 120
              const minimapH = Math.min(160, Math.max(80, minimapW * aspectRatio))
              
              return (
                <div
                  onPointerDown={(e) => e.stopPropagation()}
                  style={{
                    position: "absolute",
                    right: 8,
                    top: 8,
                    width: minimapW,
                    height: minimapH,
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
                  {paperRef.current && (() => {
                    const paper = paperRef.current
                    const scaledW = img.naturalWidth * scale
                    const scaledH = img.naturalHeight * scale
                    
                    const vpW = Math.min((paper.clientWidth / scaledW) * 100, 100)
                    const vpH = Math.min((paper.clientHeight / scaledH) * 100, 100)
                    
                    const centerX = 50 - (translate.x / scaledW) * 100
                    const centerY = (vpH / 2) - (translate.y / scaledH) * 100
                    
                    return (
                      <div
                        style={{
                          position: "absolute",
                          border: "2px solid #0f0",
                          pointerEvents: "none",
                          left: `${clamp(centerX, vpW / 2, 100 - vpW / 2)}%`,
                          top: `${clamp(centerY, vpH / 2, 100 - vpH / 2)}%`,
                          width: `${vpW}%`,
                          height: `${Math.max(vpH, 8)}%`,
                          transform: "translate(-50%, -50%)",
                          boxSizing: "border-box",
                        }}
                      />
                    )
                  })()}
                </div>
              )
            })()}

            {/* Go to Top button - centered at top, only visible when scrolled */}
            {(translate.x !== 0 || translate.y !== 0) && (
              <button
                onPointerDown={(e) => e.stopPropagation()}
                onClick={() => setTranslate({ x: 0, y: 0 })}
                style={{
                  position: "absolute",
                  left: "50%",
                  top: 12,
                  transform: "translateX(-50%)",
                  background: "rgba(0,0,0,0.75)",
                  border: "none",
                  borderRadius: 24,
                  padding: "10px 20px",
                  color: "#fff",
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  backdropFilter: "blur(10px)",
                }}
                title="Go to top"
              >
                <span style={{ fontSize: 16 }}>↑</span> Top
              </button>
            )}

            {/* Zoom controls */}
            <div onPointerDown={(e) => e.stopPropagation()} style={{ position: "absolute", left: "50%", bottom: 24, transform: "translateX(-50%)", display: "flex", gap: 8, background: "rgba(0,0,0,0.75)", padding: "8px 16px", borderRadius: 24 }}>
              <button onClick={() => zoomAt(scale - 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>−</button>
              <div style={{ minWidth: 60, textAlign: "center", padding: "4px 8px", background: "rgba(255,255,255,0.1)", borderRadius: 12, fontSize: 13, color: "#fff" }}>{Math.round((scale / baselineScale) * 100)}%</div>
              <button onClick={() => zoomAt(scale + 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>+</button>
              <button onClick={() => zoomAt(baselineScale)} style={{ padding: "4px 10px", borderRadius: 12, border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 12, cursor: "pointer" }}>Fit</button>
            </div>
          </div>
        </div>
        {/* Sidebar */}
        <div 
          ref={sidebarRef}
          data-sidebar
          onPointerDown={(e) => e.stopPropagation()}
          onWheel={(e) => e.stopPropagation()}
          style={{ width: 320, flexShrink: 0, display: "flex", flexDirection: "column", justifyContent: "center", gap: 12, padding: "16px 16px 16px 24px", cursor: "default" }}
        >
          <div style={{ display: "flex", alignItems: "flex-start", gap: 8 }}>
            <span style={{ color: "#eee", fontWeight: 600, fontSize: 16, lineHeight: 1.4, flex: 1 }}>
              {article.title ?? "Untitled"}
            </span>
            <a 
              href={article.linkToArchiveTrimmed ?? article.linkToArchive} 
              target="_blank" 
              rel="noopener noreferrer" 
              title="Open in archive"
              style={{ 
                color: "#888", 
                textDecoration: "none", 
                flexShrink: 0,
                padding: 4,
                borderRadius: 4,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
              onMouseEnter={(e) => e.currentTarget.style.color = '#aaa'}
              onMouseLeave={(e) => e.currentTarget.style.color = '#888'}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
              </svg>
            </a>
          </div>
          
          {article.publishedDate && (
            <div style={{ fontSize: 13, color: "#888" }}>{article.publishedDate}</div>
          )}
          
          <p style={{ 
            fontSize: 14, 
            color: "#ccc", 
            lineHeight: 1.6, 
            margin: 0
          }}>
            {article.summary ?? "No summary available."}
          </p>
        </div>
      </div>

      <div id="viewer-resizer" style={{ height: 8, cursor: "row-resize", background: "linear-gradient(90deg,#222,#111,#222)", borderRadius: 4, margin: "12px 0" }} />

      <div ref={stripRef} style={{ display: "flex", gap: 12, padding: 12, overflowX: "auto", background: "#060606", borderRadius: 8, height: 160, cursor: "grab" }}>
        <div className="strip-spacer" style={{ width: 40, flexShrink: 0 }} />
        {frames.map((it: any, idx: number) => {
          const art = it.article ?? {}
          const thumbUrl = getImageUrl(art.smallImagePath, 'small')
          return (
            <div key={idx} ref={el => { thumbRefs.current[idx] = el }} 
              data-thumb-idx={idx}
              style={{ flexShrink: 0, width: 200, height: 136, background: "#111", borderRadius: 6, overflow: "hidden", border: selectedIndex === idx ? "2px solid #3aa" : "1px solid #222", cursor: "pointer", position: "relative", transform: selectedIndex === idx ? "scale(1.03)" : "none", transition: "transform 120ms" }}>
              {thumbUrl && <img src={thumbUrl} alt={art.title ?? "thumb"} style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top", filter: "grayscale(1)", pointerEvents: "none" }} />}
              <div style={{ position: "absolute", left: 6, top: 6, fontSize: 11, color: "#fff", background: "rgba(0,0,0,0.6)", padding: "2px 6px", borderRadius: 4, fontWeight: 500, pointerEvents: "none" }}>{idx + 1}</div>
            </div>
          )
        })}
        <div className="strip-spacer" style={{ width: 120, flexShrink: 0 }} />
      </div>
    </div>
  )
}

