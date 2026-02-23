import React, { useEffect, useRef, useState, useCallback } from "react"
import { useSearchParams, useLocation, useNavigate } from "react-router-dom"
import { SEARCH_URL, publicCollectionArticlesUrl, privateCollectionArticlesUrl } from "../config"
import { createWorker } from 'tesseract.js'
import SiteFilter from "../components/SiteFilter"
import DateRangeFilter, { DEFAULT_START, todayStr } from "../components/DateRangeFilter"
import BookmarkButton from "../components/BookmarkButton"

function isHttpUrl(s?: string) {
  return typeof s === "string" && /^https?:\/\//i.test(s)
}

function getImageUrl(filePath?: string, size: 'small' | 'original' = 'original') {
  if (!filePath) return ""
  if (/^https?:\/\//.test(filePath)) return filePath
  const match = filePath.match(/images\/(small|original)\/([^/]+)$/)
  if (match) {
    const [, folder, filename] = match
    return `/images/${folder}/${filename}`
  }
  const filename = filePath.split('/').pop()
  if (filename) {
    return `/images/${size}/${filename}`
  }
  return ""
}

export default function ResultsPage() {
  const [params] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const state = (location.state || {}) as any

  const initialQuery = state.query ?? params.get("q") ?? ""
  const initialItems = (state.results as any)?.content ?? []

  // Collection mode detection
  const collectionId = params.get("collectionId") ? Number(params.get("collectionId")) : null
  const collectionType = params.get("type") as "public" | "private" | null // "public" or "private"
  const collectionName = params.get("name") ?? "Collection"
  const isCollectionMode = collectionId !== null && collectionType !== null

  const [query, setQuery] = useState<string>(initialQuery)

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
  const [ocrText, setOcrText] = useState<string>("")
  const [ocrLoading, setOcrLoading] = useState(false)
  const [showOcrModal, setShowOcrModal] = useState(false)
  const [searchExpanded, setSearchExpanded] = useState(false)
  const [searchInput, setSearchInput] = useState("")
  const [searching, setSearching] = useState(false)
  const [selectedSiteIds, setSelectedSiteIds] = useState<number[]>(state.selectedSiteIds ?? [])
  const [startDate, setStartDate] = useState<string>(state.startDate ?? DEFAULT_START)
  const [endDate, setEndDate] = useState<string>(state.endDate ?? todayStr())
  const tooltipRef = useRef<HTMLDivElement | null>(null)
  const searchInputRef = useRef<HTMLInputElement | null>(null)

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

  const handleSearch = useCallback(async (e?: React.FormEvent) => {
    e?.preventDefault()
    if (!searchInput.trim() || searching) return
    setSearching(true)
    const queryText = searchInput.trim()
    try {
      const siteParam = selectedSiteIds.length > 0 ? `&siteIds=${selectedSiteIds.join(',')}` : ''
      const dateParams = `&startDate=${startDate}T00:00:00&endDate=${endDate}T23:59:59`
      const res = await fetch(`${SEARCH_URL}?page=0&size=20${siteParam}${dateParams}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: queryText }),
      })
      if (!res.ok) throw new Error(`Failed: ${res.status}`)
      const data = await res.json()
      setFrames(data.content ?? [])
      setPage(0)
      setLast(!!data.last)
      setSelectedIndex(0)
      setTranslate({ x: 0, y: 0 })
      setQuery(queryText)
      setSearchExpanded(false)
      setSearchInput("")
      window.history.replaceState({}, '', `/results?q=${encodeURIComponent(queryText)}`)
    } catch (error) {
      console.error("Search error:", error)
    } finally {
      setSearching(false)
    }
  }, [searchInput, searching, selectedSiteIds])

  // Focus input when expanded
  useEffect(() => {
    if (searchExpanded && searchInputRef.current) {
      searchInputRef.current.focus()
    }
  }, [searchExpanded])

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
    const art = frames[selectedIndex] ?? {}
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
  }, [frames.length])

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
  }, [frames.length])

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
  }, [onSelect, frames.length])

  // Thumbnail hover preview — pure DOM, no React state, no re-renders
  useEffect(() => {
    const strip = stripRef.current
    if (!strip) return

    // Create tooltip element once, append to document.body
    const tip = document.createElement('div')
    tip.style.cssText = 'position:fixed;width:280px;background:rgba(10,10,10,0.95);backdrop-filter:blur(12px);border:1px solid rgba(255,255,255,0.15);border-radius:8px;padding:12px;box-shadow:0 8px 32px rgba(0,0,0,0.6);z-index:100000;pointer-events:none;opacity:0;transition:opacity 120ms;'
    const arrow = document.createElement('div')
    arrow.style.cssText = 'position:absolute;bottom:-6px;left:50%;transform:translateX(-50%);width:0;height:0;border-left:6px solid transparent;border-right:6px solid transparent;border-top:6px solid rgba(10,10,10,0.95);'
    tip.appendChild(arrow)
    document.body.appendChild(tip)
    tooltipRef.current = tip

    let currentIdx: number | null = null

    const show = (thumbEl: Element, idx: number) => {
      const art = frames[idx] ?? {}
      const summary = art.summary || 'No summary available.'
      const trimmed = summary.length > 180 ? summary.substring(0, 180) + '...' : summary

      // Build content (keep arrow as last child)
      while (tip.childNodes.length > 1) tip.removeChild(tip.firstChild!)
      const titleEl = document.createElement('div')
      titleEl.style.cssText = 'font-size:13px;font-weight:600;color:#eee;margin-bottom:6px;line-height:1.3;'
      titleEl.textContent = art.title || 'Untitled'
      tip.insertBefore(titleEl, arrow)

      if (art.publishedDate) {
        const dateEl = document.createElement('div')
        dateEl.style.cssText = 'font-size:11px;color:#999;margin-bottom:8px;'
        dateEl.textContent = art.publishedDate
        tip.insertBefore(dateEl, arrow)
      }

      const sumEl = document.createElement('div')
      sumEl.style.cssText = 'font-size:12px;color:#ccc;line-height:1.5;'
      sumEl.textContent = trimmed
      tip.insertBefore(sumEl, arrow)

      const rect = thumbEl.getBoundingClientRect()
      tip.style.left = (rect.left + rect.width / 2) + 'px'
      tip.style.bottom = (window.innerHeight - rect.top + 12) + 'px'
      tip.style.transform = 'translateX(-50%)'
      tip.style.opacity = '1'
    }

    const hide = () => {
      tip.style.opacity = '0'
      currentIdx = null
    }

    const onOver = (e: MouseEvent) => {
      const thumbEl = (e.target as HTMLElement).closest('[data-thumb-idx]')
      if (thumbEl) {
        const idx = parseInt(thumbEl.getAttribute('data-thumb-idx') || '-1', 10)
        if (idx >= 0 && idx !== currentIdx) {
          currentIdx = idx
          show(thumbEl, idx)
        }
      } else if (currentIdx !== null) {
        hide()
      }
    }

    const onOut = (e: MouseEvent) => {
      if (!strip.contains(e.relatedTarget as Node)) hide()
    }

    strip.addEventListener('mouseover', onOver)
    strip.addEventListener('mouseout', onOut)

    return () => {
      strip.removeEventListener('mouseover', onOver)
      strip.removeEventListener('mouseout', onOut)
      if (tip.parentNode) tip.parentNode.removeChild(tip)
      tooltipRef.current = null
    }
  }, [frames])

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
  }, [viewerHeight, frames.length])

  const extractText = useCallback(async () => {
    if (!viewerSrc || ocrLoading || !imgRef.current || !paperRef.current) return
    setOcrLoading(true)
    setShowOcrModal(true)
    setOcrText("")
    
    try {
      const img = imgRef.current
      const paper = paperRef.current
      const paperRect = paper.getBoundingClientRect()
      
      // Calculate visible area in image coordinates
      const scaledW = img.naturalWidth * scale
      const scaledH = img.naturalHeight * scale
      
      // Image position relative to paper (centered + translate)
      const imgLeft = (paperRect.width - scaledW) / 2 + translate.x
      const imgTop = translate.y
      
      // Visible bounds in scaled image coordinates
      const visibleLeft = Math.max(0, -imgLeft)
      const visibleTop = Math.max(0, -imgTop)
      const visibleRight = Math.min(scaledW, paperRect.width - imgLeft)
      const visibleBottom = Math.min(scaledH, paperRect.height - imgTop)
      
      // Convert to original image coordinates
      const cropX = visibleLeft / scale
      const cropY = visibleTop / scale
      const cropW = (visibleRight - visibleLeft) / scale
      const cropH = (visibleBottom - visibleTop) / scale
      
      // Create canvas with cropped visible area
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(cropW))
      canvas.height = Math.max(1, Math.round(cropH))
      const ctx = canvas.getContext('2d')
      
      if (!ctx) throw new Error("Could not get canvas context")
      
      // Draw the cropped image
      ctx.drawImage(
        img,
        Math.round(cropX), Math.round(cropY), Math.round(cropW), Math.round(cropH),
        0, 0, canvas.width, canvas.height
      )
      
      // Preprocess: increase contrast and apply threshold for better OCR
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const data = imageData.data
      
      for (let i = 0; i < data.length; i += 4) {
        // Convert to grayscale
        const gray = data[i] * 0.299 + data[i + 1] * 0.587 + data[i + 2] * 0.114
        // Apply contrast enhancement
        const contrast = 1.5
        const adjusted = ((gray / 255 - 0.5) * contrast + 0.5) * 255
        // Threshold to make text sharper (binarization)
        const final = adjusted > 140 ? 255 : 0
        data[i] = final
        data[i + 1] = final
        data[i + 2] = final
      }
      
      ctx.putImageData(imageData, 0, 0)
      
      const croppedDataUrl = canvas.toDataURL('image/png')
      
      const worker = await createWorker('eng')
      const { data: { text } } = await worker.recognize(croppedDataUrl)
      setOcrText(text)
      await worker.terminate()
    } catch (error) {
      console.error("OCR error:", error)
      setOcrText("Failed to extract text. Please try again.")
    } finally {
      setOcrLoading(false)
    }
  }, [viewerSrc, ocrLoading, scale, translate.x, translate.y])

  const loadMore = useCallback(async () => {
    if (loading || last) return
    if (!isCollectionMode && !query) return
    setLoading(true)
    const nextPage = page + 1
    try {
      let res: Response
      if (isCollectionMode) {
        const url = collectionType === "private"
          ? privateCollectionArticlesUrl(collectionId!, nextPage)
          : publicCollectionArticlesUrl(collectionId!, nextPage)
        res = await fetch(url, { credentials: "include" })
      } else {
        const siteParam = selectedSiteIds.length > 0 ? `&siteIds=${selectedSiteIds.join(',')}` : ''
        const dateParams = `&startDate=${startDate}T00:00:00&endDate=${endDate}T23:59:59`
        res = await fetch(`${SEARCH_URL}?page=${nextPage}&size=20${siteParam}${dateParams}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: query }),
        })
      }
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
  }, [query, page, last, loading, selectedSiteIds, isCollectionMode, collectionId, collectionType])

  // Load initial — for search mode (from state) or collection mode
  useEffect(() => {
    if (frames.length > 0 || loading) return
    if (isCollectionMode) {
      // Fetch first page of collection articles
      setLoading(true)
      const url = collectionType === "private"
        ? privateCollectionArticlesUrl(collectionId!, 0)
        : publicCollectionArticlesUrl(collectionId!, 0)
      fetch(url, { credentials: "include" })
        .then((r) => { if (!r.ok) throw new Error(`${r.status}`); return r.json() })
        .then((data) => {
          setFrames(data.content ?? [])
          setPage(0)
          setLast(!!data.last)
        })
        .catch((err) => console.error("Collection load error:", err))
        .finally(() => setLoading(false))
      return
    }
    if (!query) return
    loadMore()
  }, [query, isCollectionMode])

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

  // Re-search when site filter changes (only in search mode)
  useEffect(() => {
    if (!query || isCollectionMode) return
    const doSearch = async () => {
      setLoading(true)
      try {
        const siteParam = selectedSiteIds.length > 0 ? `&siteIds=${selectedSiteIds.join(',')}` : ''
        const dateParams = `&startDate=${startDate}T00:00:00&endDate=${endDate}T23:59:59`
        const res = await fetch(`${SEARCH_URL}?page=0&size=20${siteParam}${dateParams}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: query }),
        })
        if (!res.ok) throw new Error(`Failed: ${res.status}`)
        const data = await res.json()
        setFrames(data.content ?? [])
        setPage(0)
        setLast(!!data.last)
        setSelectedIndex(0)
        setTranslate({ x: 0, y: 0 })
      } catch (error) {
        console.error("Filter search error:", error)
      } finally {
        setLoading(false)
      }
    }
    doSearch()
  }, [selectedSiteIds, startDate, endDate])

  if (!isCollectionMode && !query) return <div className="max-w-3xl mx-auto p-6"><h2 className="text-xl font-semibold mb-4">Microfilm</h2><p className="text-gray-500">No query provided.</p></div>
  if (frames.length === 0 && !loading) return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", color: "#eee", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
      <p style={{ color: "#888", fontSize: 15 }}>{isCollectionMode ? "No articles in this collection." : `No results for "${query}".`}</p>
      <button onClick={() => navigate(isCollectionMode ? `/collections/${collectionType}` : "/")} style={{ marginTop: 16, background: "rgba(255,255,255,0.1)", border: "1px solid rgba(255,255,255,0.2)", borderRadius: 8, padding: "8px 16px", color: "#eee", cursor: "pointer", fontSize: 14 }}>
        <span style={{ fontSize: 18, fontWeight: 900 }}>←</span> Back
      </button>
    </div>
  )

  const backPath = isCollectionMode ? `/collections/${collectionType}` : "/"

  const selected = frames[selectedIndex] ?? {}
  const article = selected

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", padding: "20px 72px 20px 20px", color: "#eee", display: "flex", flexDirection: "column", overflow: "hidden", zIndex: 60, fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
      
      {/* Header with Search and Results label */}
      <div style={{ display: "flex", alignItems: "center", gap: 16, marginBottom: 12 }}>
        <button
          onClick={() => navigate(backPath)}
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
        {isCollectionMode ? (
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>{collectionName}</h2>
        ) : (
          <>
        <SiteFilter selectedSiteIds={selectedSiteIds} onChangeSelection={setSelectedSiteIds} variant="dark" />
        <DateRangeFilter startDate={startDate} endDate={endDate} onChangeRange={(s, e) => { setStartDate(s); setEndDate(e) }} variant="dark" />
        <form 
          onSubmit={handleSearch}
          onMouseEnter={() => setSearchExpanded(true)}
          onMouseLeave={() => !searchInput && setSearchExpanded(false)}
          style={{
            display: "flex",
            alignItems: "center",
            background: searchExpanded ? "rgba(255,255,255,0.95)" : "rgba(255,255,255,0.1)",
            borderRadius: 24,
            padding: "8px 12px",
            width: searchExpanded ? 360 : 40,
            minWidth: 40,
            height: 40,
            transition: "all 300ms cubic-bezier(0.4, 0, 0.2, 1)",
            overflow: "hidden",
            cursor: searchExpanded ? "text" : "pointer",
            flexShrink: 0,
          }}
          onClick={() => setSearchExpanded(true)}
        >
          <svg 
            width="20" 
            height="20" 
            viewBox="0 0 24 24" 
            fill="none" 
            stroke={searchExpanded ? "#333" : "#fff"} 
            strokeWidth="2" 
            strokeLinecap="round" 
            strokeLinejoin="round"
            style={{ flexShrink: 0, transition: "stroke 200ms" }}
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            ref={searchInputRef}
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onFocus={() => setSearchExpanded(true)}
            onBlur={() => !searchInput && setTimeout(() => setSearchExpanded(false), 200)}
            placeholder="Search articles..."
            style={{
              flex: 1,
              border: "none",
              outline: "none",
              background: "transparent",
              fontSize: 14,
              color: "#333",
              marginLeft: 12,
              width: searchExpanded ? "100%" : 0,
              opacity: searchExpanded ? 1 : 0,
              transition: "opacity 200ms",
              minWidth: 0,
            }}
          />
          {searchExpanded && searchInput && (
            <button
              type="submit"
              disabled={searching}
              style={{
                background: "#333",
                border: "none",
                borderRadius: 16,
                padding: "6px 14px",
                color: "#fff",
                fontSize: 12,
                cursor: searching ? "wait" : "pointer",
                opacity: searching ? 0.6 : 1,
                flexShrink: 0,
              }}
            >
              {searching ? "..." : "Go"}
            </button>
          )}
        </form>
        
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500, color: "#888", transition: "opacity 200ms", opacity: searchExpanded && searchInput ? 0.4 : 1 }}>
          Results for <span style={{ color: "#eee", fontWeight: 600 }}>"{searchInput && searchExpanded ? searchInput : query}"</span>
        </h2>
          </>
        )}
      </div>

      <div ref={viewerRef} onPointerDown={handlePointerDown} style={{ flex: 1, height: viewerHeight, display: "flex", gap: 0, overflow: "hidden", cursor: "grab" }}>
        <div style={{ flex: 1, display: "flex", justifyContent: "center" }}>
          <div ref={paperRef} style={{ width: "100%", height: "100%", background: "#1a1a1a", padding: 12, borderRadius: 6, boxShadow: "0 8px 30px rgba(0,0,0,0.5)", overflow: "hidden", position: "relative" }}>
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
            <div onPointerDown={(e) => e.stopPropagation()} style={{ position: "absolute", left: "50%", bottom: 24, transform: "translateX(-50%)", display: "flex", alignItems: "center", gap: 6, background: "rgba(0,0,0,0.75)", padding: "6px 12px", borderRadius: 24 }}>
              <button onClick={extractText} disabled={ocrLoading} style={{ height: 32, padding: "0 12px", borderRadius: 16, border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 12, cursor: ocrLoading ? "wait" : "pointer", opacity: ocrLoading ? 0.5 : 1, display: "flex", alignItems: "center", gap: 4 }}>
                📝 Text
              </button>
              <button onClick={() => zoomAt(scale - 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center" }}>−</button>
              <div style={{ minWidth: 52, height: 32, textAlign: "center", padding: "0 8px", background: "rgba(255,255,255,0.1)", borderRadius: 16, fontSize: 13, color: "#fff", display: "flex", alignItems: "center", justifyContent: "center" }}>{Math.round((scale / baselineScale) * 100)}%</div>
              <button onClick={() => zoomAt(scale + 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center" }}>+</button>
              <button onClick={() => zoomAt(baselineScale)} style={{ height: 32, padding: "0 12px", borderRadius: 16, border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 12, cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }}>
                ⛶ Fit
              </button>
              <BookmarkButton articleId={article.id} />
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
              href={article.linkToArchive} 
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

      {/* OCR Modal */}
      {showOcrModal && (
        <div 
          style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.85)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 200 }}
          onClick={() => !ocrLoading && setShowOcrModal(false)}
        >
          <div 
            onClick={(e) => e.stopPropagation()}
            style={{ background: "#1a1a1a", borderRadius: 12, padding: 24, width: "90%", maxWidth: 700, maxHeight: "80vh", display: "flex", flexDirection: "column", color: "#eee" }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
              <h3 style={{ margin: 0, fontSize: 18 }}>Extracted Text</h3>
              <button 
                onClick={() => setShowOcrModal(false)} 
                disabled={ocrLoading}
                style={{ background: "none", border: "none", color: "#888", fontSize: 24, cursor: "pointer", padding: 0, lineHeight: 1 }}
              >
                ×
              </button>
            </div>
            {ocrLoading ? (
              <div style={{ textAlign: "center", padding: 60 }}>
                <div style={{ fontSize: 32, marginBottom: 16 }}>⏳</div>
                <div style={{ color: "#aaa" }}>Extracting text from image...</div>
                <div style={{ color: "#666", fontSize: 12, marginTop: 8 }}>This may take 10-30 seconds on first use</div>
              </div>
            ) : (
              <>
                <pre style={{ 
                  flex: 1, 
                  whiteSpace: "pre-wrap", 
                  wordBreak: "break-word",
                  fontSize: 13, 
                  lineHeight: 1.6, 
                  background: "#111", 
                  padding: 16, 
                  borderRadius: 8, 
                  overflow: "auto", 
                  userSelect: "text",
                  margin: 0,
                  minHeight: 200
                }}>
                  {ocrText || "No text found."}
                </pre>
                <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
                  <button 
                    onClick={() => navigator.clipboard.writeText(ocrText)}
                    style={{ padding: "10px 20px", background: "#333", border: "none", borderRadius: 8, color: "#fff", cursor: "pointer", fontSize: 14 }}
                  >
                    📋 Copy to Clipboard
                  </button>
                  <button 
                    onClick={() => setShowOcrModal(false)}
                    style={{ padding: "10px 20px", background: "#222", border: "1px solid #444", borderRadius: 8, color: "#aaa", cursor: "pointer", fontSize: 14 }}
                  >
                    Close
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      <div id="viewer-resizer" style={{ height: 8, cursor: "row-resize", background: "linear-gradient(90deg,#222,#111,#222)", borderRadius: 4, margin: "12px 0" }} />

      <div ref={stripRef} style={{ display: "flex", gap: 12, padding: 12, overflowX: "auto", background: "#060606", borderRadius: 8, height: 160, cursor: "grab" }}>
        <div className="strip-spacer" style={{ width: 40, flexShrink: 0 }} />
        {frames.map((it: any, idx: number) => {
          const thumbUrl = getImageUrl(it.smallImagePath, 'small')
          return (
            <div key={idx} ref={el => { thumbRefs.current[idx] = el }} 
              data-thumb-idx={idx}
              style={{ flexShrink: 0, width: 200, height: 136, background: "#111", borderRadius: 6, overflow: "hidden", border: selectedIndex === idx ? "2px solid #3aa" : "1px solid #222", cursor: "pointer", position: "relative", transform: selectedIndex === idx ? "scale(1.03)" : "none", transition: "transform 120ms" }}>
              {thumbUrl && <img src={thumbUrl} alt={it.title ?? "thumb"} style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top", filter: "grayscale(1)", pointerEvents: "none" }} />}
              <div style={{ position: "absolute", left: 6, top: 6, fontSize: 11, color: "#fff", background: "rgba(0,0,0,0.6)", padding: "2px 6px", borderRadius: 4, fontWeight: 500, pointerEvents: "none" }}>{idx + 1}</div>
            </div>
          )
        })}
        <div className="strip-spacer" style={{ width: 120, flexShrink: 0 }} />
      </div>
    </div>
  )
}

