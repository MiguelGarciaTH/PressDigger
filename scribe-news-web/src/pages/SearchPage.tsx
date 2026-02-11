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
    <div className="min-h-screen flex flex-col items-center justify-center gap-6">
      <h1 className="text-3xl font-semibold">Scribe</h1>

      <form onSubmit={onSubmit} className="w-full max-w-xl">
        <input
          autoFocus
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search newspapers…"
          className="w-full border border-gray-300 rounded-full px-5 py-3 text-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={loading}
        />
      </form>

      {loading && <div aria-live="polite">Searching…</div>}
      {error && <div role="alert" className="text-red-600">{error}</div>}
    </div>
  )
}
