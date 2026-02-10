import { useState } from "react"
import { useNavigate } from "react-router-dom"

export default function SearchPage() {
  const [query, setQuery] = useState("")
  const navigate = useNavigate()

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!query.trim()) return
    navigate(`/results?q=${encodeURIComponent(query)}`)
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
        />
      </form>
    </div>
  )
}
