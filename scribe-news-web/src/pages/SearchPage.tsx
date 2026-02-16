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

      <button
        onClick={() => navigate("/editor-search")}
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
        Use Text Editor
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
            color: "#aaa",
            fontSize: 13,
            textDecoration: "none",
            padding: "6px 10px",
            borderRadius: 12,
            border: "1px solid rgba(255,255,255,0.12)",
            background: "rgba(255,255,255,0.04)",
            transition: "all 200ms",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = "#eee"
            e.currentTarget.style.borderColor = "rgba(255,255,255,0.3)"
            e.currentTarget.style.background = "rgba(255,255,255,0.08)"
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = "#aaa"
            e.currentTarget.style.borderColor = "rgba(255,255,255,0.12)"
            e.currentTarget.style.background = "rgba(255,255,255,0.04)"
          }}
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
            color: "#aaa",
            fontSize: 13,
            textDecoration: "none",
            padding: "6px 10px",
            borderRadius: 12,
            border: "1px solid rgba(255,255,255,0.12)",
            background: "rgba(255,255,255,0.04)",
            transition: "all 200ms",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = "#eee"
            e.currentTarget.style.borderColor = "rgba(255,255,255,0.3)"
            e.currentTarget.style.background = "rgba(255,255,255,0.08)"
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = "#aaa"
            e.currentTarget.style.borderColor = "rgba(255,255,255,0.12)"
            e.currentTarget.style.background = "rgba(255,255,255,0.04)"
          }}
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
            color: "#aaa",
            fontSize: 13,
            textDecoration: "none",
            padding: "6px 10px",
            borderRadius: 12,
            border: "1px solid rgba(255,255,255,0.12)",
            background: "rgba(255,255,255,0.04)",
            transition: "all 200ms",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = "#eee"
            e.currentTarget.style.borderColor = "rgba(255,255,255,0.3)"
            e.currentTarget.style.background = "rgba(255,255,255,0.08)"
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = "#aaa"
            e.currentTarget.style.borderColor = "rgba(255,255,255,0.12)"
            e.currentTarget.style.background = "rgba(255,255,255,0.04)"
          }}
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
      </div>

      {loading && <div style={{ color: "#aaa", fontSize: 14 }}>Searching…</div>}
      {error && <div style={{ color: "#e55", fontSize: 14 }}>{error}</div>}
    </div>
  )
}
