import { useState, useRef, useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { SEARCH_URL } from "../config"

export default function SearchPage() {
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    return () => {
      abortRef.current?.abort()
    }
  }, [])

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!query.trim()) return

    setLoading(true)
    setError(null)
    const controller = new AbortController()
    abortRef.current = controller

    try {
      const res = await fetch(SEARCH_URL, {
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
      navigate("/results", { state: { query, results: data } })
    } catch (err: any) {
      if (err?.name === "AbortError") return
      setError(err?.message ?? "Unknown error")
    } finally {
      setLoading(false)
      abortRef.current = null
    }
  }

  return (
    <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 24 }}>
      <h1 style={{ fontSize: 28, fontWeight: 600, color: "#eee", letterSpacing: 1 }}>Scribe</h1>

      <form onSubmit={onSubmit} style={{
        display: "flex",
        alignItems: "center",
        background: "rgba(255,255,255,0.95)",
        borderRadius: 24,
        padding: "8px 12px",
        width: 420,
        maxWidth: "90vw",
        height: 44,
      }}>
        <svg 
          width="20" 
          height="20" 
          viewBox="0 0 24 24" 
          fill="none" 
          stroke="#333" 
          strokeWidth="2" 
          strokeLinecap="round" 
          strokeLinejoin="round"
          style={{ flexShrink: 0 }}
        >
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.35-4.35" />
        </svg>
        <input
          autoFocus
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search newspapers…"
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
            {loading ? "..." : "Go"}
          </button>
        )}
      </form>

      {loading && <div style={{ color: "#aaa", fontSize: 14 }}>Searching…</div>}
      {error && <div style={{ color: "#e55", fontSize: 14 }}>{error}</div>}
    </div>
  )
}
