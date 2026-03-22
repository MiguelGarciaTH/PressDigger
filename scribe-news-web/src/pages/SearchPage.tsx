import { useState, useRef, useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { SEARCH_URL, NARRATIVE_URL, NARRATIVE_USAGE_URL } from "../config"
import SiteFilter from "../components/SiteFilter"
import DateRangeFilter, { DEFAULT_START, todayStr } from "../components/DateRangeFilter"
import { useLang } from "../contexts/LanguageContext"
import { useAuth } from "../components/useAuth"

interface NarrativeUsage {
  usageCount: number
  canUse: boolean
  resetOn: string
}

export default function SearchPage() {
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedSiteIds, setSelectedSiteIds] = useState<number[]>([])
  const [startDate, setStartDate] = useState(DEFAULT_START)
  const [endDate, setEndDate] = useState(todayStr())
  const [mode, setMode] = useState<"search" | "digest">("search")
  const [usage, setUsage] = useState<NarrativeUsage | null>(null)
  const navigate = useNavigate()
  const abortRef = useRef<AbortController | null>(null)
  const { t } = useLang()
  const { user } = useAuth()

  useEffect(() => {
    document.title = "PressDigger"
    return () => {
      abortRef.current?.abort()
    }
  }, [])

  // Fetch usage whenever digest mode is active and user is logged in
  useEffect(() => {
    if (mode !== "digest" || !user) return
    let cancelled = false
    fetch(NARRATIVE_USAGE_URL, { credentials: "include" })
      .then(r => r.ok ? r.json() : null)
      .then(data => { if (!cancelled) setUsage(data) })
      .catch(() => {})
    return () => { cancelled = true }
  }, [mode, user])

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!query.trim()) return
    if (selectedSiteIds.length === 0) {
      setError(t.selectAtLeastOneSite)
      return
    }

    if (mode === "digest") {
      if (!user) { setError(t.digestLoginRequired); return }
      await submitDigest()
    } else {
      await submitSearch()
    }
  }

  async function submitSearch() {
    setLoading(true)
    setError(null)
    const controller = new AbortController()
    abortRef.current = controller
    try {
      const siteParam = selectedSiteIds.length > 0 ? `&siteIds=${selectedSiteIds.join(',')}` : ''
      const dateParams = `&startDate=${startDate}T00:00:00&endDate=${endDate}T23:59:59`
      const res = await fetch(`${SEARCH_URL}?page=0&size=20${siteParam}${dateParams}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: query }),
        signal: controller.signal,
      })
      if (!res.ok) {
        const txt = await res.text()
        throw new Error(txt || `Request failed: ${res.status}`)
      }
      const data = await res.json()
      navigate("/results", { state: { query, results: data, selectedSiteIds, startDate, endDate } })
    } catch (err: any) {
      if (err?.name === "AbortError") return
      setError(err?.message ?? "Unknown error")
    } finally {
      setLoading(false)
      abortRef.current = null
    }
  }

  async function submitDigest() {
    setLoading(true)
    setError(null)
    const controller = new AbortController()
    abortRef.current = controller
    try {
      const siteParam = selectedSiteIds.length > 0 ? `&siteIds=${selectedSiteIds.join(',')}` : ''
      const dateParams = `&startDate=${startDate}T00:00:00&endDate=${endDate}T23:59:59`
      const res = await fetch(`${NARRATIVE_URL}?page=0&size=20${siteParam}${dateParams}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text: query }),
        signal: controller.signal,
        credentials: "include",
      })
      if (res.status === 429) {
        const body = await res.json()
        setError(body?.message ?? t.digestLimitReached)
        // Refresh usage to show updated reset date
        fetch(NARRATIVE_USAGE_URL, { credentials: "include" })
          .then(r => r.ok ? r.json() : null)
          .then(data => setUsage(data))
          .catch(() => {})
        return
      }
      if (!res.ok) {
        let msg = `Request failed: ${res.status}`
        try {
          const body = await res.json()
          if (body?.message?.toLowerCase().includes("not enough")) {
            msg = t.digestNotEnoughArticles
          } else if (body?.message) {
            msg = body.message
          }
        } catch { /* ignore parse error */ }
        throw new Error(msg)
      }
      const narrative = await res.json()
      navigate("/digest", { state: { query, narrative, selectedSiteIds, startDate, endDate } })
    } catch (err: any) {
      if (err?.name === "AbortError") return
      setError(err?.message ?? "Unknown error")
    } finally {
      setLoading(false)
      abortRef.current = null
    }
  }

  function formatResetDate(iso: string) {
    try {
      return new Date(iso).toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })
    } catch {
      return iso
    }
  }

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 24, fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
      <svg width="340" viewBox="0 0 500 175" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ maxWidth: "90vw" }}>
        {/* Microfilm reader machine */}
        <g transform="translate(25, 18)">
          {/* Machine base */}
          <rect x="5" y="45" width="75" height="48" rx="2" fill="#505050" stroke="#606060" strokeWidth="1.5"/>
          {/* Screen */}
          <rect x="12" y="51" width="61" height="36" rx="1" fill="#e8e8e0" stroke="#404040" strokeWidth="1"/>
          {/* Newspaper on screen */}
          <rect x="16" y="55" width="26" height="3" rx="0.5" fill="#111" opacity="0.9"/>
          <rect x="16" y="60" width="53" height="1.5" rx="0.5" fill="#333" opacity="0.7"/>
          <rect x="16" y="64" width="53" height="1.5" rx="0.5" fill="#333" opacity="0.7"/>
          <rect x="16" y="68" width="42" height="1.5" rx="0.5" fill="#333" opacity="0.7"/>
          <rect x="16" y="72" width="53" height="1.5" rx="0.5" fill="#333" opacity="0.7"/>
          <rect x="16" y="76" width="36" height="1.5" rx="0.5" fill="#333" opacity="0.7"/>
          <rect x="16" y="80" width="53" height="1.5" rx="0.5" fill="#333" opacity="0.7"/>

          {/* Film reels */}
          <circle cx="22" cy="38" r="7.5" fill="#505050" stroke="#707070" strokeWidth="1.2"/>
          <circle cx="22" cy="38" r="4.5" fill="none" stroke="#606060" strokeWidth="0.8"/>
          <circle cx="22" cy="38" r="2.2" fill="#404040"/>
          <circle cx="68" cy="38" r="7.5" fill="#505050" stroke="#707070" strokeWidth="1.2"/>
          <circle cx="68" cy="38" r="4.5" fill="none" stroke="#606060" strokeWidth="0.8"/>
          <circle cx="68" cy="38" r="2.2" fill="#404040"/>
          {/* Film path */}
          <path d="M 29.5 38 L 38 38 L 38 45 L 52 45 L 52 38 L 60.5 38" stroke="#606060" strokeWidth="1.8" fill="none"/>
          {/* Glow */}
          <rect x="12" y="51" width="61" height="36" rx="1" fill="#707070" opacity="0.08"/>
        </g>
        {/* Text */}
        <text x="130" y="105" fontFamily="system-ui, -apple-system, sans-serif" fontSize="58" fontWeight="800" fill="#d0d0d0" letterSpacing="-1">
          Press<tspan fill="#b0b0b0">Digger</tspan>
        </text>
      </svg>

      <div style={{ position: "relative" }}>
      {/* Mode toggle */}
      <div style={{ display: "flex", justifyContent: "center", marginBottom: 10 }}>
        <div style={{ display: "flex", background: "rgba(255,255,255,0.07)", borderRadius: 20, padding: 3 }}>
          <button
            type="button"
            onClick={() => { setMode("search"); setError(null) }}
            style={{
              background: mode === "search" ? "rgba(255,255,255,0.13)" : "transparent",
              border: "none",
              borderRadius: 17,
              padding: "6px 16px",
              color: mode === "search" ? "#eee" : "#777",
              fontSize: 13,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 6,
              transition: "all 150ms",
            }}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
            {t.articleSearch}
          </button>
          <button
            type="button"
            onClick={() => { setMode("digest"); setError(null) }}
            style={{
              background: mode === "digest" ? "rgba(58,170,170,0.18)" : "transparent",
              border: "none",
              borderRadius: 17,
              padding: "6px 16px",
              color: mode === "digest" ? "#3aa" : "#777",
              fontSize: 13,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              gap: 6,
              transition: "all 150ms",
            }}
          >
            {/* Sparkle/AI icon */}
            <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74Z"/>
              <path d="M19 2l.9 2.7L22 6l-2.1.7L19 9l-.9-2.7L16 6l2.1-.7Z" opacity=".6"/>
              <path d="M5 16l.6 1.8L7.4 18l-1.4.5L5 20l-.6-1.8L2.6 18l1.4-.5Z" opacity=".4"/>
            </svg>
            {t.aiDigest}
          </button>
        </div>
      </div>

      <div style={{ position: "relative" }}>
      <form onSubmit={onSubmit} style={{
        display: "flex",
        alignItems: "center",
        background: "rgba(255,255,255,0.95)",
        borderRadius: 24,
        padding: "8px 12px",
        width: 560,
        maxWidth: "80vw",
        height: 44,
      }}>
        <svg 
          width="20" 
          height="20" 
          viewBox="0 0 24 24" 
          fill={mode === "digest" ? "#3aa" : "#333"}
          style={{ flexShrink: 0 }}
        >
          {mode === "digest" ? (
            <path d="M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74Z"/>
          ) : (
            <>
              <circle cx="11" cy="11" r="8" fill="none" stroke="#333" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="m21 21-4.35-4.35" fill="none" stroke="#333" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </>
          )}
        </svg>
        <input
          autoFocus
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={mode === "digest" ? t.digestPlaceholder : t.searchPlaceholder}
          disabled={loading}
          style={{
            flex: 1,
            border: "none",
            outline: "none",
            background: "transparent",
            fontSize: 14,
            color: "#333",
            marginLeft: 12,
            minWidth: 0,
          }}
        />
        {query && (
          <button
            type="submit"
            disabled={loading}
            style={{
              background: "#333",
              border: "none",
              borderRadius: 16,
              padding: "6px 14px",
              color: "#fff",
              fontSize: 12,
              cursor: loading ? "wait" : "pointer",
              opacity: loading ? 0.6 : 1,
              flexShrink: 0,
            }}
          >
            {loading
              ? "..."
              : t.go}
          </button>
        )}
      </form>

      <div style={{ position: "absolute", left: "calc(100% + 10px)", top: "50%", transform: "translateY(-50%)", display: "flex", gap: 10, alignItems: "center" }}>
        <SiteFilter selectedSiteIds={selectedSiteIds} onChangeSelection={setSelectedSiteIds} variant="dark" />
        <DateRangeFilter startDate={startDate} endDate={endDate} onChangeRange={(s, e) => { setStartDate(s); setEndDate(e) }} variant="dark" />
      </div>
      </div>

      {/* Usage/error — always rendered at fixed height to keep layout stable */}
      <div style={{ height: 28, textAlign: "center", display: "flex", alignItems: "center", justifyContent: "center" }}>
        {error ? (
          <span style={{ fontSize: 12, color: mode === "search" ? "#e05555" : "#e8a838", display: "inline-flex", alignItems: "center", gap: 5 }}>
            {mode === "digest" && <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L1 21h22L12 2zm0 3.5L20.5 19h-17L12 5.5zm-1 5.5v4h2v-4h-2zm0 6v2h2v-2h-2z"/></svg>}
            {error}
          </span>
        ) : mode === "digest" && !user ? (
          <span style={{ fontSize: 12, color: "#666" }}>{t.digestLoginRequired}</span>
        ) : mode === "digest" && usage?.canUse ? (
          <span style={{ fontSize: 12, color: "#666" }}>
            {t.usesLeftThisWeek(5 - usage.usageCount)}
          </span>
        ) : mode === "digest" && usage && !usage.canUse ? (
          <span style={{ fontSize: 12, color: "#e8a838", display: "inline-flex", alignItems: "center", gap: 5 }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L1 21h22L12 2zm0 3.5L20.5 19h-17L12 5.5zm-1 5.5v4h2v-4h-2zm0 6v2h2v-2h-2z"/></svg>
            {t.digestLimitReached} · {t.digestResetsOn(formatResetDate(usage.resetOn))}
          </span>
        ) : null}
      </div>
      </div>

      <button
        onClick={() => navigate("/editor-search", query.trim() ? { state: { query } } : undefined)}
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          background: "rgba(255,255,255,0.1)",
          border: "1px solid rgba(255,255,255,0.2)",
          borderRadius: 16,
          padding: "10px 20px",
          color: "#eee",
          fontSize: 14,
          cursor: "pointer",
          transition: "all 200ms",
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.background = "rgba(255,255,255,0.15)"
          e.currentTarget.style.borderColor = "rgba(255,255,255,0.3)"
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = "rgba(255,255,255,0.1)"
          e.currentTarget.style.borderColor = "rgba(255,255,255,0.2)"
        }}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
        </svg>
        {t.textEditor}
      </button>

      <div
        style={{
          position: "absolute",
          bottom: 24,
          left: "50%",
          transform: "translateX(-50%)",
          display: "flex",
          alignItems: "center",
          gap: 12,
        }}
      >
        <a
          href="https://github.com/MiguelGarciaTH/ScribeRef"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            color: "#484848",
            fontSize: 13,
            textDecoration: "none",
            padding: "6px 10px",
            borderRadius: 12,
            border: "none",
            background: "transparent",
            transition: "color 200ms",
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = "#888" }}
          onMouseLeave={(e) => { e.currentTarget.style.color = "#484848" }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path d="M12 2C6.48 2 2 6.58 2 12.26c0 4.52 2.87 8.35 6.84 9.7.5.1.68-.22.68-.49 0-.24-.01-.86-.01-1.69-2.78.62-3.37-1.38-3.37-1.38-.45-1.18-1.11-1.49-1.11-1.49-.9-.64.07-.63.07-.63 1 .07 1.53 1.06 1.53 1.06.89 1.57 2.34 1.12 2.91.86.09-.66.35-1.12.64-1.38-2.22-.26-4.56-1.14-4.56-5.08 0-1.12.39-2.03 1.03-2.75-.1-.26-.45-1.31.1-2.74 0 0 .84-.28 2.75 1.05A9.3 9.3 0 0 1 12 6.8c.83 0 1.67.11 2.45.33 1.91-1.33 2.75-1.05 2.75-1.05.55 1.43.2 2.48.1 2.74.64.72 1.03 1.63 1.03 2.75 0 3.95-2.34 4.82-4.57 5.08.36.32.68.95.68 1.92 0 1.38-.01 2.5-.01 2.84 0 .27.18.6.69.49A10.03 10.03 0 0 0 22 12.26C22 6.58 17.52 2 12 2z" />
          </svg>
          <span>GitHub</span>
        </a>
        <a
          href="https://www.linkedin.com/in/miguelgarciath/"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            color: "#484848",
            fontSize: 13,
            textDecoration: "none",
            padding: "6px 10px",
            borderRadius: 12,
            border: "none",
            background: "transparent",
            transition: "color 200ms",
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = "#888" }}
          onMouseLeave={(e) => { e.currentTarget.style.color = "#484848" }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path d="M20.45 20.45H17.2v-5.4c0-1.29-.02-2.95-1.8-2.95-1.8 0-2.07 1.4-2.07 2.85v5.5H10.1V9h3.12v1.56h.04c.43-.82 1.5-1.69 3.08-1.69 3.3 0 3.9 2.17 3.9 5v6.58zM5.34 7.53a1.88 1.88 0 1 1 0-3.76 1.88 1.88 0 0 1 0 3.76zM6.98 20.45H3.7V9h3.28v11.45zM22 2H2a2 2 0 0 0-2 2v18a2 2 0 0 0 2 2h20a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z" />
          </svg>
          <span>Miguel Garcia</span>
        </a>
        <a
          href="https://arquivo.pt/"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            color: "#484848",
            fontSize: 13,
            textDecoration: "none",
            padding: "6px 10px",
            borderRadius: 12,
            border: "none",
            background: "transparent",
            transition: "color 200ms",
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = "#888" }}
          onMouseLeave={(e) => { e.currentTarget.style.color = "#484848" }}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M21 8v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8" />
            <path d="M7 8V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v3" />
            <path d="M3 8h18" />
          </svg>
          <span>Arquivo.pt</span>
        </a>
        <button
          onClick={() => navigate("/about")}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            color: "#484848",
            fontSize: 13,
            padding: "6px 10px",
            borderRadius: 12,
            border: "none",
            background: "transparent",
            cursor: "pointer",
            transition: "color 200ms",
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = "#888" }}
          onMouseLeave={(e) => { e.currentTarget.style.color = "#484848" }}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          <span>{t.navAbout}</span>
        </button>
      </div>

      {loading && <div style={{ color: "#aaa", fontSize: 14 }}>{mode === "digest" ? t.digestGenerating : t.searching}</div>}
    </div>
  )
}
