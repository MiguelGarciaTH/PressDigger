import React, { useEffect, useRef, useState, useCallback } from "react"
import { useSearchParams, useLocation } from "react-router-dom"
import { SEARCH_URL } from "../config"

// add helper to resolve image URLs (prefer remote URLs, otherwise map repo paths to /images/...)
function isHttpUrl(s?: string) {
  return typeof s === "string" && /^https?:\/\//i.test(s)
}
function basename(path?: string) {
  if (!path) return ""
  return path.split(/[\\/]/).pop() ?? ""
}

// Fast URL resolution for viewer - prefer remote URLs
function resolveViewerUrl(article: any) {
  if (!article) return ""
  // Try archive first (usually remote)
  if (isHttpUrl(article.linkToArchiveImage)) return article.linkToArchiveImage
  // Then original (may be local or remote)
  if (article.originalImagePath) return article.originalImagePath
  // Fall back to small
  if (article.smallImagePath) return article.smallImagePath
  return ""
}

export default function ResultsPage() {
  const [params] = useSearchParams()
  const location = useLocation()
  const state = (location.state || {}) as any

  const query = state.query ?? params.get("q") ?? ""
  const initialItems = state.results?.content ?? []
  const initialPage = state.results?.number ?? 0
  const initialLast = state.results?.last ?? false

  const [frames, setFrames] = useState<any[]>(initialItems)
  const [page, setPage] = useState<number>(initialPage)
  const [last, setLast] = useState<boolean>(initialLast)
  const [loading, setLoading] = useState(false)
  const stripRef = useRef<HTMLDivElement | null>(null)
  const observerRef = useRef<IntersectionObserver | null>(null)
  const lastThumbRef = useRef<HTMLDivElement | null>(null)
  const prefetchQueueRef = useRef<Set<number>>(new Set())
  const prefetchedImagesRef = useRef<Map<string, boolean>>(new Map())
  const loadedThumbsRef = useRef<Set<number>>(new Set())

  const [selectedIndex, setSelectedIndex] = useState<number>(0)
  const [scale, setScale] = useState<number>(1)
  const [translate, setTranslate] = useState<{ x: number; y: number }>({ x: 0, y: 0 })
  const [viewerLoading, setViewerLoading] = useState(true)
  const viewerLoadingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [baselineScale, setBaselineScale] = useState<number>(1)
  const [hasLoadedFirstImage, setHasLoadedFirstImage] = useState(false)
  const [loadedThumbs, setLoadedThumbs] = useState<Set<number>>(new Set())
  const viewerRef = useRef<HTMLDivElement | null>(null)
  const thumbRefs = useRef<(HTMLDivElement | null)[]>([])
  const [viewerHeight, setViewerHeight] = useState<number>(Math.round(window.innerHeight * 0.68)) // larger default

  // add refs for stable drag state
  const pointerIdRef = useRef<number | null>(null)
  const isDraggingRef = useRef(false)
  const startXRef = useRef(0)
  const startYRef = useRef(0)
  const originRef = useRef({ x: 0, y: 0 })

  // robust pointer handlers: start attaches window listeners, end removes them
  const handlePointerMove = useCallback((e: PointerEvent) => {
    if (!isDraggingRef.current) return
    const dx = e.clientX - startXRef.current
    const dy = e.clientY - startYRef.current
    setTranslate({ x: Math.round(originRef.current.x + dx), y: Math.round(originRef.current.y + dy) })
  }, [])

  const handlePointerUp = useCallback((e: PointerEvent) => {
    if (!isDraggingRef.current) return
    isDraggingRef.current = false
    try {
      if (pointerIdRef.current != null && viewerRef.current) {
        viewerRef.current.releasePointerCapture(pointerIdRef.current)
      }
    } catch {}
    pointerIdRef.current = null
    if (viewerRef.current) viewerRef.current.style.cursor = "grab"
    document.body.style.userSelect = ""
    window.removeEventListener("pointermove", handlePointerMove)
    window.removeEventListener("pointerup", handlePointerUp)
    window.removeEventListener("pointercancel", handlePointerUp)
  }, [handlePointerMove])

  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    // only left button
    if (e.button !== 0) return
    const viewerEl = viewerRef.current
    if (!viewerEl) return
    // start drag only when pointer is inside viewer
    e.preventDefault()
    isDraggingRef.current = true
    startXRef.current = e.clientX
    startYRef.current = e.clientY
    originRef.current = { x: translate.x, y: translate.y }
    try {
      viewerEl.setPointerCapture(e.pointerId)
      pointerIdRef.current = e.pointerId
    } catch {}
    viewerEl.style.cursor = "grabbing"
    document.body.style.userSelect = "none"

    // attach move/up listeners
    window.addEventListener("pointermove", handlePointerMove)
    window.addEventListener("pointerup", handlePointerUp)
    window.addEventListener("pointercancel", handlePointerUp)
  }, [handlePointerMove, handlePointerUp, translate.x, translate.y])

  // ensure cleanup if component unmounts while dragging
  useEffect(() => {
    return () => {
      window.removeEventListener("pointermove", handlePointerMove)
      window.removeEventListener("pointerup", handlePointerUp)
      window.removeEventListener("pointercancel", handlePointerUp)
    }
  }, [handlePointerMove, handlePointerUp])

  useEffect(() => {
    setFrames(initialItems)
    setPage(initialPage)
    setLast(initialLast)
    setSelectedIndex(0)
    setScale(1)
    setTranslate({ x: 0, y: 0 })
  }, [initialItems, initialPage, initialLast])

  const loadMore = useCallback(async () => {
    if (loading || last || !query) return
    setLoading(true)
    try {
      const nextPage = page + 1
      const res = await fetch(SEARCH_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: query, page: nextPage }),
      })
      if (!res.ok) throw new Error(`Failed to load: ${res.status}`)
      const data = await res.json()
      const more = data.content ?? []
      setFrames((s) => [...s, ...more])
      setPage(data.number ?? nextPage)
      setLast(!!data.last)
      prefetchQueueRef.current.delete(nextPage)
    } catch {
      // ignore errors for now
      prefetchQueueRef.current.delete(page + 1)
    } finally {
      setLoading(false)
    }
  }, [query, page, last, loading])

  // Prefetch next page silently when user scrolls to 70% of thumbnails
  const checkPrefetch = useCallback(() => {
    if (last || !stripRef.current || loading) return
    const strip = stripRef.current
    const scrollRatio = (strip.scrollLeft + strip.clientWidth) / strip.scrollWidth
    const nextPageNum = page + 1

    // Prefetch if we're past 70% and not already queued
    if (scrollRatio > 0.7 && !prefetchQueueRef.current.has(nextPageNum)) {
      prefetchQueueRef.current.add(nextPageNum)
      // Silently prefetch without showing loading state
      fetch(SEARCH_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: query, page: nextPageNum }),
      })
        .then((res) => res.ok ? res.json() : null)
        .then((data) => {
          if (data?.content) {
            setFrames((s) => [...s, ...data.content])
            setPage(data.number ?? nextPageNum)
            setLast(!!data.last)
          }
          prefetchQueueRef.current.delete(nextPageNum)
        })
        .catch(() => prefetchQueueRef.current.delete(nextPageNum))
    }
  }, [query, page, last, loading])

  // auto-load when last thumbnail visible
  useEffect(() => {
    if (!lastThumbRef.current) return
    observerRef.current?.disconnect()
    observerRef.current = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) loadMore()
        })
      },
      { root: stripRef.current, threshold: 0.9 }
    )
    observerRef.current.observe(lastThumbRef.current)
    return () => observerRef.current?.disconnect()
  }, [frames, loadMore])

  // Monitor horizontal scroll for prefetch
  useEffect(() => {
    const strip = stripRef.current
    if (!strip) return

    const onScroll = () => checkPrefetch()
    strip.addEventListener("scroll", onScroll, { passive: true })
    return () => strip.removeEventListener("scroll", onScroll)
  }, [checkPrefetch])

  // thumbnail click: select and center
  const onSelect = (idx: number) => {
    setSelectedIndex(idx)
    setScale(1)
    setTranslate({ x: 0, y: 0 })
    const el = thumbRefs.current[idx]
    if (el && stripRef.current) {
      el.scrollIntoView({ behavior: "smooth", inline: "center", block: "nearest" })
    }
  }

  // pointer-centered zoom helper (wider bounds)
  // replace signature to accept optional coords (callers may pass them)
  const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))
  const zoomAt = useCallback(
    (nextScale: number, _clientX?: number, _clientY?: number) => {
      const MIN = 0.3
      const MAX = 20
      const ns = clamp(nextScale, MIN, MAX)
      // always reset pan so image remains centered/top-anchored when zooming
      setTranslate({ x: 0, y: 0 })
      setScale(ns)
    },
    []
  )

  // wheel to zoom: smooth continuous zoom (no modifier), pointer-centered factor but keep center
  useEffect(() => {
    const el = viewerRef.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const delta = e.deltaY
      const factor = Math.pow(1.0025, -delta)
      setScale((current) => {
        const desired = clamp(current * factor, 0.3, 20)
        zoomAt(desired)
        return current
      })
    }
    el.addEventListener("wheel", onWheel, { passive: false })
    return () => el.removeEventListener("wheel", onWheel as any)
  }, [zoomAt])

  // horizontal scroll on thumbnail strip via mouse wheel
  useEffect(() => {
    const strip = stripRef.current
    if (!strip) return
    const onWheel = (e: WheelEvent) => {
      // scroll horizontally when wheel is over the strip
      e.preventDefault()
      const delta = e.deltaY
      strip.scrollLeft += delta // positive delta = scroll right, negative = scroll left
    }
    strip.addEventListener("wheel", onWheel, { passive: false })
    return () => strip.removeEventListener("wheel", onWheel as any)
  }, [])

  // keyboard left/right to navigate thumbs
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight") onSelect(Math.min(frames.length - 1, selectedIndex + 1))
      else if (e.key === "ArrowLeft") onSelect(Math.max(0, selectedIndex - 1))
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [selectedIndex, frames.length])

  useEffect(() => {
    if (scale <= 1) setTranslate({ x: 0, y: 0 })
  }, [scale])

  // resizer for viewerHeight (unchanged logic)
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
      try { (e.target as Element).setPointerCapture(e.pointerId) } catch {}
      document.body.style.userSelect = "none"
    }
    const onMove = (e: PointerEvent) => {
      if (!dragging) return
      const dy = e.clientY - startY
      const next = Math.max(300, Math.min(window.innerHeight - 200, startH + dy))
      setViewerHeight(next)
    }
    const onUp = (e: PointerEvent) => {
      dragging = false
      try { (e.target as Element).releasePointerCapture(e.pointerId) } catch {}
      document.body.style.userSelect = ""
    }

    handle.addEventListener("pointerdown", onDown)
    window.addEventListener("pointermove", onMove)
    window.addEventListener("pointerup", onUp)
    window.addEventListener("pointercancel", onUp)
    return () => {
      handle.removeEventListener("pointerdown", onDown)
      window.removeEventListener("pointermove", onMove)
      window.removeEventListener("pointerup", onUp)
      window.removeEventListener("pointercancel", onUp)
    }
  }, [viewerHeight])

  // double-click zoom toggle (uses wider bounds)
  const onImageDoubleClick = (e: React.MouseEvent) => {
    const next = scale >= 3 ? 1 : Math.min(20, scale * 2)
     zoomAt(next, e.clientX, e.clientY)
  }

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
        <h2 className="text-xl font-semibold mb-4">Results for “{query}”</h2>
        <p className="text-gray-500">No results available.</p>
      </div>
    )
  }

  const selected = frames[selectedIndex] ?? {}
  const article = selected.article ?? {}

  const paperBg = { background: "#faf6ef", padding: 12, borderRadius: 6, boxShadow: "0 8px 30px rgba(0,0,0,0.5)" }

  // add refs to paper container and image for measuring
  const paperRef = useRef<HTMLDivElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  // store viewer candidates / src (already present in your file)
  const [viewerSrc, setViewerSrc] = useState<string>("")
  const candidatesRef = useRef<string[]>([])

  // update candidates and initial viewerSrc whenever selection changes
  useEffect(() => {
    const sel = frames[selectedIndex] ?? {}
    const art = sel.article ?? {}

    // Use fast resolution for viewer
    const mainUrl = resolveViewerUrl(art)
    // Build minimal fallback chain (remote -> original -> small)
    const candidates = [
      art.linkToArchiveImage,
      art.originalImagePath,
      art.smallImagePath,
    ].filter(Boolean)

    candidatesRef.current = candidates
    setViewerSrc(mainUrl)
    // Show loading spinner only after 300ms (for cached images, it won't show)
    if (viewerLoadingTimeoutRef.current) clearTimeout(viewerLoadingTimeoutRef.current)
    viewerLoadingTimeoutRef.current = setTimeout(() => setViewerLoading(true), 300)
    // reset transform/zoom when a new image is loaded
    setScale(1)
    setBaselineScale(1)
    setTranslate({ x: 0, y: 0 })

    // Preload next/prev thumbnails
    const nextIdx = Math.min(frames.length - 1, selectedIndex + 1)
    const prevIdx = Math.max(0, selectedIndex - 1)
    if (nextIdx !== selectedIndex && frames[nextIdx]?.article?.smallImagePath) {
      const img = new Image()
      img.src = frames[nextIdx].article.smallImagePath
    }
    if (prevIdx !== selectedIndex && frames[prevIdx]?.article?.smallImagePath) {
      const img = new Image()
      img.src = frames[prevIdx].article.smallImagePath
    }
  }, [selectedIndex, frames])

  // when image loads, compute displayed width and set initial scale so it occupies ~70% of paper width
  const onViewerImgLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget
    imgRef.current = img

    // Clear any pending loading timeout
    if (viewerLoadingTimeoutRef.current) clearTimeout(viewerLoadingTimeoutRef.current)

    const displayedW = img.getBoundingClientRect().width
    const paperEl = paperRef.current
    if (paperEl && displayedW) {
      const paperRect = paperEl.getBoundingClientRect()
      const targetW = paperRect.width * 0.7 // target 70% of paper width
      const initial = clamp(targetW / displayedW, 0.3, 20)
      setScale(Math.round(initial * 100) / 100)
      setBaselineScale(Math.round(initial * 100) / 100)
    } else {
      setScale(1)
      setBaselineScale(1)
    }
    setTranslate({ x: 0, y: 0 })
    setViewerLoading(false)
    setHasLoadedFirstImage(true)
  }

  // Prefetch original images for thumbnails after first viewer image loads
  const prefetchOriginalImages = useCallback(() => {
    if (!stripRef.current || !hasLoadedFirstImage) return

    // Get visible thumbnail range
    const strip = stripRef.current
    const thumbWidth = 212 // 200px + 12px gap
    const visibleStart = Math.floor(strip.scrollLeft / thumbWidth)
    const visibleEnd = Math.ceil((strip.scrollLeft + strip.clientWidth) / thumbWidth)

    // Prefetch visible + adjacent thumbnails
    const rangeToPrefetch = frames.slice(
      Math.max(0, visibleStart - 2),
      Math.min(frames.length, visibleEnd + 5)
    )

    rangeToPrefetch.forEach((it) => {
      const art = it.article ?? {}
      const originalUrl = art.linkToArchiveImage || art.originalImagePath

      if (originalUrl && !prefetchedImagesRef.current.has(originalUrl)) {
        prefetchedImagesRef.current.set(originalUrl, true)
        const img = new Image()
        img.src = originalUrl
      }
    })
  }, [frames, hasLoadedFirstImage])

  // Monitor scroll and prefetch
  useEffect(() => {
    if (!hasLoadedFirstImage) return

    const strip = stripRef.current
    if (!strip) return

    const onScroll = () => {
      prefetchOriginalImages()
    }

    strip.addEventListener("scroll", onScroll, { passive: true })
    // Initial prefetch on first load
    prefetchOriginalImages()

    return () => strip.removeEventListener("scroll", onScroll)
  }, [hasLoadedFirstImage, prefetchOriginalImages])

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)",
        padding: 20,
        color: "#eee",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        zIndex: 60,
      }}
    >
      <div style={{ display: "flex", gap: 20, alignItems: "center", marginBottom: 12 }}>
        <h2 className="text-xl font-semibold">Microfilm — Results for “{query}”</h2>
        <div style={{ color: "#9aa", fontSize: 13 }}></div>
      </div>

      {/* viewer area */}
      <div
        ref={viewerRef}
        onPointerDown={handlePointerDown}
        style={{
          flex: 1,
          height: viewerHeight,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          position: "relative",
          padding: 18,
          overflow: "hidden",
          gap: 16,
        }}
        aria-label="Selected article viewer"
      >
        <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "flex-start", justifyContent: "center", flex: 1 }}>
          <div style={{ width: "92%", height: "100%", position: "relative", ...paperBg, overflow: "hidden" }} ref={paperRef}>
            {viewerSrc ? (
              <>
                {viewerLoading && (
                  <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.2)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 10 }}>
                    <div style={{ width: 40, height: 40, border: "3px solid rgba(255,255,255,0.2)", borderTop: "3px solid #fff", borderRadius: "50%", animation: "spin 0.8s linear infinite" }} />
                  </div>
                )}
                <img
                  key={viewerSrc}
                  ref={imgRef}
                  src={viewerSrc}
                  alt={article.title ?? "article"}
                  onLoad={onViewerImgLoad}
                  decoding="async"
                  onDragStart={(e) => e.preventDefault()}
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
                    willChange: "transform",
                    userSelect: "none",
                    touchAction: "none",
                    margin: "0 auto",
                  }}
                  draggable={false}
                  onDoubleClick={onImageDoubleClick}
                  onError={(e) => {
                    const t = e.currentTarget as HTMLImageElement
                    const cur = t.src
                    const next = candidatesRef.current.find((s) => s && s !== cur)
                    if (next) {
                      setViewerSrc(next)
                    } else {
                      t.style.opacity = "0.4"
                      setViewerLoading(false)
                    }
                  }}
                />
                <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
              </>
            ) : (
              <div style={{ color: "#666" }}>No full image available</div>
            )}

            {/* minimap (right side preview) */}
            {imgRef.current && (
              <div
                style={{
                  position: "absolute",
                  right: 2,
                  top: 2,
                  width: 100,
                  height: 140,
                  background: "rgba(0, 0, 0, 0.6)",
                  border: "1px solid rgba(255, 255, 255, 0.3)",
                  borderRadius: 4,
                  overflow: "hidden",
                  cursor: "pointer",
                }}
                onClick={(e) => {
                  // click on minimap to jump to that position
                  const rect = e.currentTarget.getBoundingClientRect()
                  const minimapX = e.clientX - rect.left
                  const minimapY = e.clientY - rect.top
                  const ratioX = minimapX / rect.width
                  const ratioY = minimapY / rect.height
                  // map back to full image coords
                  const paperRect = paperRef.current?.getBoundingClientRect()
                  if (paperRect && imgRef.current) {
                    const imgRect = imgRef.current.getBoundingClientRect()
                    const targetX = -(imgRect.width * scale * ratioX - paperRect.width / 2)
                    const targetY = -(imgRect.height * scale * ratioY - paperRect.height / 2)
                    setTranslate({ x: Math.round(targetX), y: Math.round(targetY) })
                  }
                }}
              >
                {/* minimap background with scaled image */}
                {imgRef.current && (
                  <div
                    style={{
                      position: "absolute",
                      inset: 0,
                      backgroundImage: `url(${viewerSrc})`,
                      backgroundSize: "contain",
                      backgroundRepeat: "no-repeat",
                      backgroundPosition: "center",
                      opacity: 0.7,
                    }}
                  />
                )}

                {/* viewport indicator rectangle */}
                {paperRef.current && imgRef.current && (
                  <div
                    style={{
                      position: "absolute",
                      border: "2px solid #0f0",
                      pointerEvents: "none",
                      left: `${Math.max(0, Math.min(100, (translate.x / (imgRef.current.width * scale)) * 100 + 50))}%`,
                      top: `${Math.max(0, Math.min(100, (translate.y / (imgRef.current.height * scale)) * 100 + 50))}%`,
                      width: `${Math.min(100, (paperRef.current.clientWidth / (imgRef.current.width * scale)) * 100)}%`,
                      height: `${Math.min(100, (paperRef.current.clientHeight / (imgRef.current.height * scale)) * 100)}%`,
                      transform: "translate(-50%, -50%)",
                    }}
                  />
                )}
              </div>
            )}

            {/* zoom controls - bottom center of paper */}
            <div style={{ position: "absolute", left: "50%", bottom: 8, transform: "translateX(-50%)", display: "flex", alignItems: "center", gap: 6, background: "rgba(0,0,0,0.7)", padding: "8px 12px", borderRadius: 20, backdropFilter: "blur(10px)" }}>
              <button
                onClick={() => {
                  const el = viewerRef.current
                  if (!el) return
                  const r = el.getBoundingClientRect()
                  zoomAt(clamp(scale - 0.5, 0.3, 20), r.left + r.width / 2, r.top + r.height / 2)
                }}
                className="px-2 py-1 bg-gray-700 hover:bg-gray-600 text-white rounded text-sm font-medium transition-colors"
                aria-label="Zoom out"
              >
                −
              </button>
              <div style={{ minWidth: 50, textAlign: "center", padding: "4px 6px", background: "rgba(255,255,255,0.1)", borderRadius: 4, fontSize: 12, fontWeight: 500 }}>{Math.round((scale / baselineScale) * 100)}%</div>
              <button
                onClick={() => {
                  const el = viewerRef.current
                  if (!el) return
                  const r = el.getBoundingClientRect()
                  zoomAt(clamp(scale + 0.5, 0.3, 20), r.left + r.width / 2, r.top + r.height / 2)
                }}
                className="px-2 py-1 bg-gray-700 hover:bg-gray-600 text-white rounded text-sm font-medium transition-colors"
                aria-label="Zoom in"
              >
                +
              </button>
            </div>
          </div>
        </div>

        {/* meta column (right side) */}
        <div style={{ width: 280, flex: "0 0 auto", display: "flex", flexDirection: "column", gap: 12, paddingRight: 8 }}>
          <a href={article.linkToArchiveTrimmed ?? article.linkToArchive} target="_blank" rel="noopener noreferrer" style={{ color: "#9bd", fontWeight: 700, textDecoration: "none", fontSize: 15, lineHeight: 1.3 }}>
            {article.title ?? "Untitled"}
          </a>
          {article.publishedDate && <div style={{ fontSize: 12, color: "#9aa", marginTop: 4 }}>{article.publishedDate}</div>}
          <p style={{ marginTop: 8, fontSize: 12, color: "#ddd", lineHeight: 1.5, flex: 1, overflow: "auto" }}>{selected.content ?? article.summary ?? "No summary available."}</p>
        </div>
      </div>

      {/* resizer handle */}
      <div id="viewer-resizer" style={{ height: 8, cursor: "row-resize", background: "linear-gradient(90deg,#222,#111,#222)", borderRadius: 4, marginBottom: 12 }} aria-hidden />

      {/* thumbnail strip (fixed height so it stays at the bottom) */}
      <div style={{ flex: "0 0 auto", display: "flex", flexDirection: "column", gap: 12, height: 240 }}>
        <div style={{ height: 12, display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }}>
          {Array.from({ length: Math.max(6, Math.floor((window.innerWidth - 200) / 80)) }).map((_, i) => (
            <div key={i} style={{ width: 12, height: 6, background: "#111", borderRadius: 4 }} />
          ))}
        </div>

        <div
          ref={stripRef}
          style={{
            display: "flex",
            gap: 12,
            padding: "12px 8px",
            overflowX: "auto",
            alignItems: "flex-start",
            borderRadius: 8,
            background: "#060606",
            border: "1px solid rgba(255,255,255,0.03)",
            height: 160,
            boxSizing: "border-box",
          }}
          role="list"
          aria-label="Thumbnails"
        >
          <div style={{ width: 40, flex: "0 0 auto" }} />

          {frames.map((it: any, idx: number) => {
            const art = it.article ?? {}
            const isLast = idx === frames.length - 1
            const thumbUrl = isHttpUrl(art.smallImagePath) 
              ? art.smallImagePath 
              : art.linkToArchiveImage || ""
            const isLoaded = loadedThumbs.has(idx)
            return (
              <div
                key={`${art.id ?? it.id}-${it.chunkIndex ?? idx}`}
                ref={(el) => {
                  thumbRefs.current[idx] = el
                  if (isLast) lastThumbRef.current = el
                }}
                role="listitem"
                onClick={() => onSelect(idx)}
                style={{
                  flex: "0 0 auto",
                  width: 200,
                  height: 150,
                  background: selectedIndex === idx ? "linear-gradient(180deg,#222,#111)" : "#111",
                  borderRadius: 6,
                  overflow: "hidden",
                  boxShadow: selectedIndex === idx ? "0 10px 30px rgba(0,0,0,0.6)" : "none",
                  border: selectedIndex === idx ? "2px solid #3aa" : "1px solid rgba(255,255,255,0.03)",
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "stretch",
                  justifyContent: "stretch",
                  position: "relative",
                  transform: selectedIndex === idx ? "scale(1.03)" : "none",
                  transition: "transform 120ms",
                }}
              >
                {thumbUrl ? (
                  <img
                    src={thumbUrl}
                    alt={art.title ?? "thumb"}
                    loading="lazy"
                    decoding="async"
                    onDragStart={(e) => e.preventDefault()}
                    style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top center", display: "block", filter: "grayscale(1) contrast(1.05) brightness(0.95)" }}
                    onLoad={() => {
                      loadedThumbsRef.current.add(idx)
                      setLoadedThumbs(new Set(loadedThumbsRef.current))
                    }}
                    onError={(e) => {
                      const t = e.currentTarget as HTMLImageElement
                      if (art.linkToArchiveImage && !isHttpUrl(art.smallImagePath)) {
                        t.src = art.linkToArchiveImage
                      } else {
                        t.style.opacity = "0.4"
                      }
                    }}
                  />
                ) : (
                  <div style={{ color: "#666" }}>No image</div>
                )}

                <div style={{ position: "absolute", left: 8, top: 8, fontSize: 12, color: "#ccc" }}>{idx + 1}</div>
                {isLoaded && (
                  <div style={{ position: "absolute", right: 6, top: 6, width: 6, height: 6, borderRadius: "50%", background: "rgba(34, 197, 94, 0.5)", boxShadow: "0 0 4px rgba(34, 197, 94, 0.3)" }} />
                )}
              </div>
            )
          })}

          <div style={{ width: 120, flex: "0 0 auto" }} />
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <button onClick={() => onSelect(Math.max(0, selectedIndex - 1))} className="px-3 py-2 bg-gray-800 text-white rounded">◀</button>
          <button onClick={() => onSelect(Math.min(frames.length - 1, selectedIndex + 1))} className="px-3 py-2 bg-gray-800 text-white rounded">▶</button>
          <div className="ml-auto text-sm text-gray-400">{loading ? "Loading…" : last ? "End" : "Scroll to load more"}</div>
        </div>
      </div>
    </div>
  )
}
