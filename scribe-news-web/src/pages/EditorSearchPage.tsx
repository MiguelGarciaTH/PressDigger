import React, { useEffect, useRef, useState, useCallback } from "react"
import { useNavigate } from "react-router-dom"
import { SEARCH_URL } from "../config"

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

interface ParagraphResults {
  paragraphIndex: number
  paragraphText: string
  results: any[]
  totalResults: number
  searching: boolean
}

export default function EditorSearchPage() {
  const navigate = useNavigate()
  const [paragraphResults, setParagraphResults] = useState<ParagraphResults[]>([])
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null)
  const [expandedParagraphIndex, setExpandedParagraphIndex] = useState<number | null>(null)
  const [expandedDeckIndex, setExpandedDeckIndex] = useState<number | null>(null)
  const [expandedTextCardKey, setExpandedTextCardKey] = useState<string | null>(null)
  const [summaryTopOffset, setSummaryTopOffset] = useState<number>(0)
  const [scale, setScale] = useState<number>(1)
  const [translate, setTranslate] = useState<{ x: number; y: number }>({ x: 0, y: 0 })
  const [baselineScale, setBaselineScale] = useState<number>(0.5)
  const [showTopFade, setShowTopFade] = useState(false)
  const [showBottomFade, setShowBottomFade] = useState(false)
  
  const editorRef = useRef<HTMLDivElement | null>(null)
  const searchTimeoutsRef = useRef<Map<number, number>>(new Map())
  const lastQueriesRef = useRef<Map<number, string>>(new Map())
  const viewerRef = useRef<HTMLDivElement | null>(null)
  const imgRef = useRef<HTMLImageElement | null>(null)
  const isDraggingRef = useRef(false)
  const startXRef = useRef(0)
  const startYRef = useRef(0)
  const originRef = useRef({ x: 0, y: 0 })
  const resultsContainerRef = useRef<HTMLDivElement | null>(null)
  const cardRefs = useRef<Map<string, HTMLDivElement>>(new Map())

  const clamp = (v: number, a: number, b: number) => Math.min(b, Math.max(a, v))

  const performSearch = useCallback(async (paragraphIndex: number, query: string) => {
    if (!query.trim() || query === lastQueriesRef.current.get(paragraphIndex)) return
    
    const wordCount = query.trim().split(/\s+/).length
    if (wordCount < 4) {
      setParagraphResults(prev => prev.map(p => 
        p.paragraphIndex === paragraphIndex 
          ? { ...p, results: [], totalResults: 0, searching: false }
          : p
      ))
      return
    }

    lastQueriesRef.current.set(paragraphIndex, query)
    
    setParagraphResults(prev => prev.map(p => 
      p.paragraphIndex === paragraphIndex ? { ...p, searching: true } : p
    ))

    try {
      const res = await fetch(`${SEARCH_URL}?page=0&size=20`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: query }),
      })
      if (!res.ok) throw new Error(`Failed: ${res.status}`)
      const data = await res.json()
      
      setParagraphResults(prev => prev.map(p => 
        p.paragraphIndex === paragraphIndex 
          ? { ...p, results: data.content ?? [], totalResults: data.totalElements ?? 0, searching: false }
          : p
      ))
    } catch (error) {
      console.error("Search error:", error)
      setParagraphResults(prev => prev.map(p => 
        p.paragraphIndex === paragraphIndex ? { ...p, searching: false } : p
      ))
    }
  }, [])

  const handleInput = useCallback(() => {
    const text = editorRef.current?.innerText ?? ""
    
    // Split into paragraphs (by double line breaks or single line breaks)
    const paragraphs = text.split(/\n+/).filter(p => p.trim().length > 0)
    
    // Update paragraph results structure
    setParagraphResults(prev => {
      const newResults: ParagraphResults[] = paragraphs.map((para, idx) => {
        const existing = prev.find(p => p.paragraphIndex === idx)
        return {
          paragraphIndex: idx,
          paragraphText: para,
          results: existing?.results ?? [],
          totalResults: existing?.totalResults ?? 0,
          searching: existing?.searching ?? false,
        }
      })
      return newResults
    })
    
    // Clear existing timeouts
    searchTimeoutsRef.current.forEach(timeout => clearTimeout(timeout))
    searchTimeoutsRef.current.clear()

    // Set new timeouts for each paragraph
    paragraphs.forEach((para, idx) => {
      const timeout = window.setTimeout(() => {
        performSearch(idx, para)
      }, 800)
      searchTimeoutsRef.current.set(idx, timeout)
    })
  }, [performSearch])

  const zoomAt = useCallback((nextScale: number) => {
    const ns = clamp(nextScale, 0.3, 20)
    setTranslate({ x: 0, y: 0 })
    setScale(ns)
  }, [])

  const onViewerImgLoad = useCallback((e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget
    imgRef.current = img
    const viewerEl = viewerRef.current
    if (viewerEl && img.naturalHeight > 0 && img.naturalWidth > 0) {
      const viewerRect = viewerEl.getBoundingClientRect()
      const targetWidth = viewerRect.width * 0.7
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
    if ((e.target as HTMLElement).closest('button, a, [role="button"]')) return
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

  // Wheel zoom
  useEffect(() => {
    const el = viewerRef.current
    if (!el || expandedIndex === null) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const factor = Math.pow(1.0025, -e.deltaY)
      setScale((current) => clamp(current * factor, 0.3, 20))
    }
    el.addEventListener("wheel", onWheel, { passive: false })
    return () => el.removeEventListener("wheel", onWheel)
  }, [expandedIndex])

  // Reset translate when scale <= 1
  useEffect(() => {
    if (scale <= 1) setTranslate({ x: 0, y: 0 })
  }, [scale])

  // Update summary position on card selection or scroll
  useEffect(() => {
    const updateSummaryPosition = () => {
      if (!expandedTextCardKey) return
      
      const cardEl = cardRefs.current.get(expandedTextCardKey)
      const containerEl = resultsContainerRef.current
      
      if (cardEl && containerEl) {
        const cardRect = cardEl.getBoundingClientRect()
        const containerRect = containerEl.getBoundingClientRect()
        // Position relative to visible container area, not document position
        const offset = cardRect.top - containerRect.top
        setSummaryTopOffset(offset)
      }
    }

    updateSummaryPosition()

    const containerEl = resultsContainerRef.current
    if (containerEl) {
      containerEl.addEventListener('scroll', updateSummaryPosition)
      return () => containerEl.removeEventListener('scroll', updateSummaryPosition)
    }
  }, [expandedTextCardKey, paragraphResults])

  // Load more results on scroll
  const loadMoreResults = useCallback(async (paraIndex: number) => {
    const paraResult = paragraphResults.find(p => p.paragraphIndex === paraIndex)
    if (!paraResult || paraResult.searching) return

    const currentPage = Math.floor(paraResult.results.length / 20)
    const queryText = paraResult.paragraphText

    setParagraphResults(prev => prev.map(p => 
      p.paragraphIndex === paraIndex ? { ...p, searching: true } : p
    ))

    try {
      const res = await fetch(`${SEARCH_URL}?page=${currentPage}&size=20`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: queryText }),
      })
      if (!res.ok) throw new Error(`Failed: ${res.status}`)
      const data = await res.json()
      
      setParagraphResults(prev => prev.map(p => 
        p.paragraphIndex === paraIndex 
          ? { ...p, results: [...p.results, ...(data.content ?? [])], searching: false }
          : p
      ))
    } catch (error) {
      console.error("Load more error:", error)
      setParagraphResults(prev => prev.map(p => 
        p.paragraphIndex === paraIndex ? { ...p, searching: false } : p
      ))
    }
  }, [paragraphResults])

  const selectedArticle = expandedIndex !== null && expandedParagraphIndex !== null 
    ? paragraphResults.find(p => p.paragraphIndex === expandedParagraphIndex)?.results[expandedIndex]
    : null
  const viewerSrc = selectedArticle ? getImageUrl(selectedArticle.originalImagePath ?? selectedArticle.smallImagePath, 'original') : ""

  const hasAnyResults = paragraphResults.some(p => p.results.length > 0)
  const isSearching = paragraphResults.some(p => p.searching)

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
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>Editor Search</h2>
        {isSearching && <div style={{ color: "#888", fontSize: 13 }}>Searching...</div>}
      </div>

      {/* Main Content */}
      <div style={{ flex: 1, display: "flex", overflow: "hidden", padding: 20, paddingTop: 60, position: "relative" }}>
        {/* Editor Area - Absolutely centered */}
        <div style={{ 
          position: "absolute",
          left: "50%",
          transform: "translateX(-50%)",
          width: 1100, 
          display: "flex", 
          flexDirection: "column" 
        }}>
          <div
            ref={editorRef}
            contentEditable
            onInput={handleInput}
            style={{
              height: 850,
              padding: 40,
              background: "#111",
              borderRadius: 12,
              border: "1px solid #333",
              outline: "none",
              fontSize: 16,
              lineHeight: 1.8,
              color: "#ddd",
              fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
              overflow: "auto",
            }}
            data-placeholder="Start typing your search query... (minimum 4 words per paragraph)"
          />
          <style>{`
            [contenteditable][data-placeholder]:empty:before {
              content: attr(data-placeholder);
              color: #666;
              cursor: text;
            }
          `}</style>
          <div style={{ marginTop: 12, fontSize: 12, color: "#666", textAlign: "center" }}>
            💡 Tip: Each paragraph is searched independently. Type at least 4 words per paragraph.
          </div>
        </div>

        {/* Results Decks - Appears right next to editor */}
        {hasAnyResults && (
          <div style={{ 
            position: "absolute",
            left: "50%",
            marginLeft: 550 + 24, // Half of editor width (1100/2) + gap
            display: "flex", 
            gap: 16, 
            alignItems: "flex-start",
          }}>
            {/* Compact boxes for each paragraph */}
            <div style={{ width: 180, display: "flex", flexDirection: "column", gap: 12, overflow: "auto", maxHeight: 850 }}>
              {paragraphResults.map((paraResult) => {
                if (paraResult.results.length === 0 && !paraResult.searching) return null
                
                const isExpanded = expandedDeckIndex === paraResult.paragraphIndex

                return (
                  <div 
                    key={paraResult.paragraphIndex}
                    onClick={() => {
                      setExpandedDeckIndex(isExpanded ? null : paraResult.paragraphIndex)
                      setExpandedTextCardKey(null)
                    }}
                    style={{ 
                      background: isExpanded ? "#111" : "#0a0a0a", 
                      borderRadius: 8, 
                      border: isExpanded ? "2px solid #3aa" : "1px solid #222",
                      padding: 12,
                      cursor: "pointer",
                      transition: "all 200ms",
                      boxShadow: isExpanded ? "0 0 12px rgba(58, 170, 170, 0.3)" : "none",
                    }}
                    onMouseEnter={(e) => {
                      if (!isExpanded) e.currentTarget.style.borderColor = "#444"
                    }}
                    onMouseLeave={(e) => {
                      if (!isExpanded) e.currentTarget.style.borderColor = "#222"
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}>
                      <div style={{ fontSize: 11, color: "#888", fontWeight: 500 }}>
                        Paragraph {paraResult.paragraphIndex + 1}
                      </div>
                      <div style={{ 
                        fontSize: 14, 
                        color: isExpanded ? "#3aa" : "#666",
                        transition: "all 200ms",
                      }}>
                        »
                      </div>
                    </div>
                    
                    {paraResult.searching ? (
                      <div style={{ fontSize: 12, color: "#888" }}>Searching...</div>
                    ) : (
                      <div style={{ fontSize: 18, fontWeight: 600, color: "#eee" }}>
                        {paraResult.totalResults}
                      </div>
                    )}
                    <div style={{ fontSize: 10, color: "#666", marginTop: 2 }}>results</div>
                  </div>
                )
              })}
            </div>

            {/* Expanded card list - appears to the right of boxes */}
            {expandedDeckIndex !== null && (() => {
              const expandedPara = paragraphResults.find(p => p.paragraphIndex === expandedDeckIndex)
              if (!expandedPara || expandedPara.results.length === 0) return null

              const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
                const target = e.currentTarget
                const scrollBottom = target.scrollTop + target.clientHeight
                const isNearBottom = target.scrollHeight - scrollBottom < 200
                
                if (isNearBottom && !expandedPara.searching && expandedPara.results.length < expandedPara.totalResults) {
                  loadMoreResults(expandedPara.paragraphIndex)
                }
                
                // Update fade flags based on scroll position
                const { scrollTop, scrollHeight, clientHeight } = target
                setShowTopFade(scrollTop > 8)
                setShowBottomFade(scrollHeight - (scrollTop + clientHeight) > 8)
              }

              return (
                <div style={{ 
                  position: "relative", 
                  display: "flex", 
                  gap: 16,
                  height: 850,
                }}>
                  <div style={{ position: "relative", height: "100%" }}>
                    <div 
                      ref={resultsContainerRef}
                      onScroll={handleScroll}
                      style={{ 
                        width: 420, 
                        flexShrink: 0,
                        height: 850,
                        overflow: "auto",
                        animation: "slideIn 200ms ease-out",
                      }}
                    >
                  <style>{`
                    @keyframes slideIn {
                      from {
                        opacity: 0;
                        transform: translateX(-20px);
                      }
                      to {
                        opacity: 1;
                        transform: translateX(0);
                      }
                    }
                  `}</style>
                  <div style={{ 
                    background: "#0a0a0a", 
                    borderRadius: 12, 
                    border: "1px solid #222",
                    overflow: "hidden",
                  }}>
                    {/* Card Stack */}
                    <div style={{ padding: 12 }}>
                      {expandedPara.results.map((article: any, idx: number) => {
                        const thumbUrl = getImageUrl(article.smallImagePath, 'small')
                        const cardKey = `${expandedPara.paragraphIndex}-${idx}`
                        const isTextExpanded = expandedTextCardKey === cardKey
                        const isSelected = expandedIndex === idx && expandedParagraphIndex === expandedPara.paragraphIndex
                        
                        return (
                          <div 
                            key={idx} 
                            ref={(el) => {
                              if (el) cardRefs.current.set(cardKey, el)
                              else cardRefs.current.delete(cardKey)
                            }}
                            style={{ marginBottom: 12 }}
                          >
                            <div
                              data-card-key={cardKey}
                              style={{
                                background: "#0d0d0d",
                                borderRadius: 8,
                                overflow: "hidden",
                                border: (isSelected || isTextExpanded) ? "2px solid #3aa" : "1px solid #1a1a1a",
                                transition: "all 150ms",
                                position: "relative",
                                boxShadow: (isSelected || isTextExpanded) ? "0 0 12px rgba(58, 170, 170, 0.3)" : "none",
                              }}
                            >
                              {thumbUrl && (
                                <div 
                                  onClick={() => setExpandedTextCardKey(isTextExpanded ? null : cardKey)}
                                  style={{ 
                                    position: "relative", 
                                    cursor: "pointer",
                                    width: "100%",
                                    height: 100,
                                  }}
                                >
                                  <img 
                                    src={thumbUrl} 
                                    alt={article.title ?? "thumbnail"} 
                                    style={{ 
                                      width: "100%", 
                                      height: "100%", 
                                      objectFit: "cover", 
                                      objectPosition: "top",
                                      filter: "grayscale(1)",
                                      display: "block",
                                    }} 
                                  />
                                  {/* Card number */}
                                  <div style={{
                                    position: "absolute",
                                    left: 6,
                                    top: 6,
                                    fontSize: 11,
                                    color: "#fff",
                                    background: "rgba(0,0,0,0.6)",
                                    padding: "2px 6px",
                                    borderRadius: 4,
                                    fontWeight: 500,
                                    pointerEvents: "none",
                                  }}>
                                    {idx + 1}
                                  </div>
                                  {/* Small hint overlay */}
                                  <div style={{
                                    position: "absolute",
                                    top: 6,
                                    right: 6,
                                    background: isTextExpanded ? "rgba(58, 170, 170, 0.9)" : "rgba(0, 0, 0, 0.7)",
                                    backdropFilter: "blur(4px)",
                                    borderRadius: 4,
                                    padding: "4px 8px",
                                    fontSize: 12,
                                    color: isTextExpanded ? "#000" : "#aaa",
                                    fontWeight: 500,
                                    transition: "all 150ms",
                                    border: isTextExpanded ? "1px solid #3aa" : "1px solid rgba(255,255,255,0.2)",
                                  }}>
                                    »
                                  </div>
                                </div>
                              )}
                              <div style={{ padding: 10 }}>
                                <div style={{ fontSize: 12, fontWeight: 600, color: "#eee", marginBottom: 3, lineHeight: 1.3 }}>
                                  {article.title ?? "Untitled"}
                                </div>
                                {article.publishedDate && (
                                  <div style={{ fontSize: 10, color: "#888", marginBottom: 8 }}>{article.publishedDate}</div>
                                )}
                                
                                {/* Action button */}
                                <div style={{ display: "flex", marginTop: 8 }}>
                                  <button
                                    onClick={() => {
                                      setExpandedIndex(idx)
                                      setExpandedParagraphIndex(expandedPara.paragraphIndex)
                                      setScale(baselineScale)
                                      setTranslate({ x: 0, y: 0 })
                                      setExpandedTextCardKey(null)
                                    }}
                                    style={{
                                      width: "100%",
                                      background: isSelected ? "#1a3333" : "#1a1a1a",
                                      border: isSelected ? "1px solid #3aa" : "1px solid #333",
                                      borderRadius: 6,
                                      padding: "6px 10px",
                                      color: isSelected ? "#3aa" : "#aaa",
                                      fontSize: 12,
                                      cursor: "pointer",
                                      display: "flex",
                                      alignItems: "center",
                                      justifyContent: "center",
                                      gap: 4,
                                      transition: "all 150ms",
                                    }}
                                  >
                                    <span style={{ fontSize: 14 }}>🔍</span>
                                    <span>View</span>
                                  </button>
                                </div>
                              </div>
                            </div>
                          </div>
                        )
                      })}
                      
                      {expandedPara.searching && (
                        <div style={{ 
                          textAlign: "center", 
                          padding: 16, 
                          fontSize: 12, 
                          color: "#888" 
                        }}>
                          Loading more...
                        </div>
                      )}
                    </div>
                  </div>
                    </div>
                    
                    {/* Scroll indicator */}
                    {expandedPara.results.length < expandedPara.totalResults && (
                      <div style={{
                        position: "absolute",
                        bottom: 0,
                        left: 0,
                        right: 0,
                        height: 60,
                        background: "linear-gradient(to bottom, transparent, rgba(10, 10, 10, 0.95))",
                        pointerEvents: "none",
                        display: "flex",
                        alignItems: "flex-end",
                        justifyContent: "center",
                        paddingBottom: 12,
                      }}>
                        <div style={{
                          fontSize: 11,
                          color: "#ddd",
                          display: "flex",
                          alignItems: "center",
                          gap: 4,
                          fontWeight: 500,
                        }}>
                          <span>↓</span>
                          <span>Scroll for more</span>
                          <span>↓</span>
                        </div>
                      </div>
                    )}
                  </div>
                  
                  {/* Expanded text summary - appears to the right */}
                  {expandedTextCardKey !== null && (() => {
                    const [paraIndexStr, cardIndexStr] = expandedTextCardKey.split('-')
                    const paraIndex = parseInt(paraIndexStr, 10)
                    const cardIndex = parseInt(cardIndexStr, 10)
                    const paraResult = paragraphResults.find(p => p.paragraphIndex === paraIndex)
                    const article = paraResult?.results[cardIndex]
                    
                    if (!article) return null
                    
                    // Calculate visibility based on position relative to container bounds (0 to 850)
                    // Hide completely if scrolled significantly out of view
                    if (summaryTopOffset < -200 || summaryTopOffset > 950) return null
                    
                    // Apply gradient fade when summary scrolls near boundaries
                    const showSummaryTopFade = summaryTopOffset < 40
                    const showSummaryBottomFade = summaryTopOffset > 810
                    
                    return (
                      <div style={{
                        position: "absolute",
                        left: 436,
                        top: summaryTopOffset,
                        width: 400,
                        maxHeight: 850,
                        overflow: "auto",
                        padding: 16,
                        background: "#0a0a0a",
                        borderRadius: 12,
                        border: "1px solid #3aa",
                        boxShadow: "0 0 12px rgba(58, 170, 170, 0.3)",
                        animation: "slideIn 200ms ease-out",
                      }}>
                  {/* Gradient fade overlay when scrolling near top - aligns with card list fade */}
                  {showSummaryTopFade && (
                    <div
                      style={{
                        position: "absolute",
                        top: 0,
                        left: 0,
                        right: 0,
                        height: 80,
                        background:
                          "linear-gradient(to bottom, rgba(7,7,7,0.98), transparent)",
                        pointerEvents: "none",
                        zIndex: 10,
                        borderRadius: "12px 12px 0 0",
                      }}
                    />
                  )}
                  {/* Gradient fade overlay when scrolling near bottom */}
                  {showSummaryBottomFade && (
                    <div
                      style={{
                        position: "absolute",
                        bottom: 0,
                        left: 0,
                        right: 0,
                        height: 80,
                        background:
                          "linear-gradient(to top, rgba(7,7,7,0.98), transparent)",
                        pointerEvents: "none",
                        zIndex: 10,
                        borderRadius: "0 0 12px 12px",
                      }}
                    />
                  )}
                  <div style={{ marginBottom: 12, paddingBottom: 12, borderBottom: "1px solid #222" }}>
                    <a
                      href={article.linkToArchive}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        fontSize: 14,
                        fontWeight: 600,
                        color: "#eee",
                        textDecoration: "none",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: 6,
                        transition: "color 150ms",
                        marginBottom: 4,
                        lineHeight: 1.3,
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.color = "#3aa"}
                      onMouseLeave={(e) => e.currentTarget.style.color = "#eee"}
                    >
                      {article.title ?? "Untitled"}
                      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                      </svg>
                    </a>
                    {article.publishedDate && (
                      <div style={{ fontSize: 11, color: "#888" }}>{article.publishedDate}</div>
                    )}
                  </div>
                  <div style={{
                    fontSize: 13,
                    lineHeight: 1.7,
                    color: "#ccc",
                  }}>
                    {article.summary || 'No summary available.'}
                  </div>
                </div>
                    )
                  })()}
                  
                  {/* Top/Bottom fade overlays - cover both card list and summary */}
                  {showTopFade && (
                    <div
                      style={{
                        position: "absolute",
                        top: 0,
                        left: 0,
                        right: 0,
                        height: 40,
                        background:
                          "linear-gradient(to bottom, rgba(7,7,7,0.95), transparent)",
                        pointerEvents: "none",
                        zIndex: 10,
                      }}
                    />
                  )}
                  {showBottomFade && (
                    <div
                      style={{
                        position: "absolute",
                        bottom: 0,
                        left: 0,
                        right: 0,
                        height: 40,
                        background:
                          "linear-gradient(to top, rgba(7,7,7,0.95), transparent)",
                        pointerEvents: "none",
                        zIndex: 10,
                      }}
                    />
                  )}
                </div>
              )
            })()}
          </div>
        )}
      </div>

      {/* Expanded Image Viewer Modal */}
      {expandedIndex !== null && expandedParagraphIndex !== null && selectedArticle && (() => {
        const currentParagraph = paragraphResults.find(p => p.paragraphIndex === expandedParagraphIndex)
        return (
        <div 
          style={{ 
            position: "fixed", 
            inset: 0, 
            background: "rgba(0,0,0,0.9)", 
            display: "flex", 
            alignItems: "center", 
            justifyContent: "center", 
            zIndex: 1000,
            backdropFilter: "blur(8px)",
          }}
          onClick={() => {
            setExpandedIndex(null)
            setExpandedParagraphIndex(null)
          }}
        >
          <div 
            onClick={(e) => e.stopPropagation()}
            style={{ 
              width: "90%", 
              height: "90%", 
              background: "#0a0a0a", 
              borderRadius: 12, 
              overflow: "hidden",
              display: "flex",
              flexDirection: "column",
              border: "2px solid #3aa",
              boxShadow: "0 0 20px rgba(58, 170, 170, 0.4), 0 16px 64px rgba(0,0,0,0.7)",
            }}
          >
            {/* Modal Header */}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: 16, borderBottom: "1px solid #222" }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 11, color: "#666", marginBottom: 4 }}>
                  Paragraph {expandedParagraphIndex + 1}
                </div>
                <a
                  href={selectedArticle.linkToArchive}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{
                    fontSize: 16,
                    fontWeight: 600,
                    color: "#eee",
                    textDecoration: "none",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 6,
                    transition: "color 150ms",
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.color = "#3aa"}
                  onMouseLeave={(e) => e.currentTarget.style.color = "#eee"}
                >
                  {selectedArticle.title ?? "Untitled"}
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
                    <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
                  </svg>
                </a>
                {selectedArticle.publishedDate && (
                  <div style={{ fontSize: 12, color: "#888", marginTop: 4 }}>{selectedArticle.publishedDate}</div>
                )}
              </div>
              <button
                onClick={() => {
                  setExpandedIndex(null)
                  setExpandedParagraphIndex(null)
                }}
                style={{
                  background: "rgba(255,255,255,0.1)",
                  border: "none",
                  borderRadius: 8,
                  padding: "8px 16px",
                  color: "#eee",
                  fontSize: 20,
                  cursor: "pointer",
                  lineHeight: 1,
                }}
              >
                ×
              </button>
            </div>

            {/* Image Viewer */}
            <div 
              ref={viewerRef}
              onPointerDown={handlePointerDown}
              style={{ 
                flex: 1, 
                display: "flex", 
                justifyContent: "center", 
                alignItems: "flex-start",
                overflow: "hidden", 
                position: "relative",
                cursor: "grab",
                background: "#000",
              }}
            >
              {viewerSrc ? (
                <img 
                  ref={imgRef}
                  src={viewerSrc} 
                  alt={selectedArticle.title ?? "article"} 
                  onLoad={onViewerImgLoad}
                  draggable={false}
                  style={{ 
                    transform: `translate(${translate.x}px, ${translate.y}px) scale(${scale})`, 
                    transition: "transform 120ms", 
                    width: "auto",
                    height: "auto",
                    display: "block", 
                    transformOrigin: "center top", 
                    filter: "grayscale(1) contrast(1.05)", 
                    margin: "0 auto", 
                    userSelect: "none",
                  }} 
                />
              ) : (
                <div style={{ color: "#666" }}>No image available</div>
              )}

              {/* Zoom Controls */}
              <div style={{ position: "absolute", left: "50%", bottom: 24, transform: "translateX(-50%)", display: "flex", gap: 8, background: "rgba(0,0,0,0.75)", padding: "8px 16px", borderRadius: 24 }}>
                <button onClick={() => zoomAt(scale - 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>−</button>
                <div style={{ minWidth: 60, textAlign: "center", padding: "4px 8px", background: "rgba(255,255,255,0.1)", borderRadius: 12, fontSize: 13, color: "#fff" }}>{Math.round((scale / baselineScale) * 100)}%</div>
                <button onClick={() => zoomAt(scale + 0.3)} style={{ width: 32, height: 32, borderRadius: "50%", border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 18, cursor: "pointer" }}>+</button>
                <button onClick={() => zoomAt(baselineScale)} style={{ padding: "4px 10px", borderRadius: 12, border: "none", background: "rgba(255,255,255,0.1)", color: "#fff", fontSize: 12, cursor: "pointer" }}>
                  ⛶ Fit
                </button>
              </div>

              {/* Navigation Arrows - within current paragraph's results */}
              {currentParagraph && expandedIndex > 0 && (
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    setExpandedIndex(expandedIndex - 1)
                    setScale(baselineScale)
                    setTranslate({ x: 0, y: 0 })
                  }}
                  style={{
                    position: "absolute",
                    left: 24,
                    top: "50%",
                    transform: "translateY(-50%)",
                    background: "rgba(0,0,0,0.75)",
                    border: "none",
                    borderRadius: "50%",
                    width: 48,
                    height: 48,
                    color: "#fff",
                    fontSize: 24,
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  ‹
                </button>
              )}
              {currentParagraph && expandedIndex < currentParagraph.results.length - 1 && (
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    setExpandedIndex(expandedIndex + 1)
                    setScale(baselineScale)
                    setTranslate({ x: 0, y: 0 })
                  }}
                  style={{
                    position: "absolute",
                    right: 24,
                    top: "50%",
                    transform: "translateY(-50%)",
                    background: "rgba(0,0,0,0.75)",
                    border: "none",
                    borderRadius: "50%",
                    width: 48,
                    height: 48,
                    color: "#fff",
                    fontSize: 24,
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  ›
                </button>
              )}
            </div>

            {/* Article Summary */}
            {selectedArticle.summary && (
              <div style={{ padding: 16, borderTop: "1px solid #222", background: "#111", maxHeight: 150, overflow: "auto" }}>
                <div style={{ fontSize: 13, color: "#ccc", lineHeight: 1.6 }}>
                  {selectedArticle.summary}
                </div>
              </div>
            )}
          </div>
        </div>
        )
      })()}
    </div>
  )
}
