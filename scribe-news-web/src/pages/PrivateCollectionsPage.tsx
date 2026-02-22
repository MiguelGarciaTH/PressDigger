import { useState, useEffect, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { PRIVATE_COLLECTIONS_URL } from "../config"
import { useAuth } from "../components/useAuth"

interface Collection {
  id: number
  name: string
  descriptiom?: string
  articleCount: number
}

export default function PrivateCollectionsPage() {
  const { user, loading: authLoading } = useAuth()
  const [collections, setCollections] = useState<Collection[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newName, setNewName] = useState("")
  const [newDesc, setNewDesc] = useState("")
  const [creating, setCreating] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const nameInputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()

  useEffect(() => {
    document.title = "My Collections — PressDigger"
  }, [])

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!authLoading && !user) {
      window.location.href = "http://localhost:8085/oauth2/authorization/google"
    }
  }, [authLoading, user])

  // Fetch collections once authenticated
  useEffect(() => {
    if (!user) return
    setLoading(true)
    fetch(PRIVATE_COLLECTIONS_URL, { credentials: "include" })
      .then((r) => {
        if (!r.ok) throw new Error(`${r.status}`)
        return r.json()
      })
      .then((data) => setCollections(data))
      .catch((err) => setError(err.message ?? "Failed to load"))
      .finally(() => setLoading(false))
  }, [user])

  // Focus name input when create form opens
  useEffect(() => {
    if (showCreateForm) nameInputRef.current?.focus()
  }, [showCreateForm])

  const handleCreate = async () => {
    const trimmed = newName.trim()
    if (!trimmed || creating) return
    setCreating(true)
    try {
      const res = await fetch(PRIVATE_COLLECTIONS_URL, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: trimmed, description: newDesc.trim() }),
      })
      if (!res.ok) throw new Error(`${res.status}`)
      const created: Collection = await res.json()
      setCollections((prev) => [...prev, created])
      setNewName("")
      setNewDesc("")
      setShowCreateForm(false)
    } catch (err: any) {
      alert("Failed to create collection")
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: number, name: string) => {
    setDeletingId(id)
    try {
      const res = await fetch(`${PRIVATE_COLLECTIONS_URL}/${id}`, {
        method: "DELETE",
        credentials: "include",
      })
      if (!res.ok) throw new Error(`${res.status}`)
      setCollections((prev) => prev.filter((c) => c.id !== id))
    } catch {
      alert("Failed to delete collection")
    } finally {
      setDeletingId(null)
    }
  }

  if (authLoading) {
    return (
      <div style={{ position: "fixed", inset: 0, background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)", color: "#888", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" }}>
        Loading…
      </div>
    )
  }

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
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>My Collections</h2>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: "auto", padding: "32px 24px", display: "flex", justifyContent: "center" }}>
        <div style={{ width: "100%", maxWidth: 900 }}>
          {loading && (
            <p style={{ color: "#888", fontSize: 14, textAlign: "center", padding: 40 }}>Loading…</p>
          )}

          {error && (
            <p style={{ color: "#e55", fontSize: 14, textAlign: "center", padding: 40 }}>
              Failed to load collections
            </p>
          )}

          {!loading && !error && collections.length === 0 && !showCreateForm && (
            <p style={{ color: "#777", fontSize: 14, textAlign: "center", padding: 40 }}>
              No collections yet
            </p>
          )}

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
              gap: 16,
            }}
          >
            {/* Existing collection cards */}
            {collections.map((col) => (
              <div
                key={col.id}
                style={{
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.08)",
                  borderRadius: 12,
                  padding: "20px 22px",
                  textAlign: "left",
                  transition: "all 180ms",
                  display: "flex",
                  flexDirection: "column",
                  gap: 12,
                  minHeight: 140,
                  position: "relative",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = "rgba(255,255,255,0.08)"
                  e.currentTarget.style.borderColor = "rgba(255,255,255,0.18)"
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = "rgba(255,255,255,0.04)"
                  e.currentTarget.style.borderColor = "rgba(255,255,255,0.08)"
                }}
              >
                {/* Clickable area */}
                <div
                  onClick={() => navigate(`/results?collectionId=${col.id}&type=private&name=${encodeURIComponent(col.name)}`)}
                  style={{ cursor: "pointer", display: "flex", flexDirection: "column", gap: 12, flex: 1 }}
                >
                  <span style={{ color: "#eee", fontSize: 16, fontWeight: 600, lineHeight: 1.3 }}>
                    {col.name}
                  </span>
                  <span style={{ color: "#666", fontSize: 13, lineHeight: 1.4, flex: 1 }}>
                    {col.descriptiom ?? ""}
                  </span>
                </div>

                {/* Footer: article count + delete */}
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: "auto" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#888" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                    </svg>
                    <span style={{ color: "#888", fontSize: 13 }}>
                      {col.articleCount} {col.articleCount === 1 ? "article" : "articles"}
                    </span>
                  </div>
                  <button
                    title="Delete collection"
                    onClick={(e) => { e.stopPropagation(); handleDelete(col.id, col.name) }}
                    disabled={deletingId === col.id}
                    style={{
                      background: "none",
                      border: "none",
                      padding: 4,
                      cursor: deletingId === col.id ? "wait" : "pointer",
                      opacity: deletingId === col.id ? 0.4 : 0.35,
                      transition: "opacity 150ms",
                      display: "flex",
                      alignItems: "center",
                    }}
                    onMouseEnter={(e) => { if (deletingId !== col.id) e.currentTarget.style.opacity = "0.9" }}
                    onMouseLeave={(e) => { if (deletingId !== col.id) e.currentTarget.style.opacity = "0.35" }}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#e55" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="3 6 5 6 21 6" />
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                    </svg>
                  </button>
                </div>
              </div>
            ))}

            {/* Create new collection: "+" card / inline form */}
            {!loading && !error && (
              showCreateForm ? (
                <div
                  style={{
                    background: "rgba(255,255,255,0.04)",
                    border: "1px solid rgba(58,170,170,0.4)",
                    borderRadius: 12,
                    padding: "20px 22px",
                    display: "flex",
                    flexDirection: "column",
                    gap: 12,
                    minHeight: 140,
                  }}
                >
                  <input
                    ref={nameInputRef}
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    placeholder="Collection name"
                    onKeyDown={(e) => { if (e.key === "Enter") handleCreate(); if (e.key === "Escape") { setShowCreateForm(false); setNewName(""); setNewDesc("") } }}
                    style={{
                      background: "rgba(255,255,255,0.06)",
                      border: "1px solid rgba(255,255,255,0.15)",
                      borderRadius: 8,
                      padding: "8px 12px",
                      color: "#eee",
                      fontSize: 14,
                      outline: "none",
                      fontFamily: "inherit",
                    }}
                  />
                  <textarea
                    value={newDesc}
                    onChange={(e) => setNewDesc(e.target.value)}
                    placeholder="Description (optional)"
                    rows={2}
                    onKeyDown={(e) => { if (e.key === "Escape") { setShowCreateForm(false); setNewName(""); setNewDesc("") } }}
                    style={{
                      background: "rgba(255,255,255,0.06)",
                      border: "1px solid rgba(255,255,255,0.15)",
                      borderRadius: 8,
                      padding: "8px 12px",
                      color: "#eee",
                      fontSize: 13,
                      outline: "none",
                      fontFamily: "inherit",
                      resize: "none",
                      flex: 1,
                    }}
                  />
                  <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                    <button
                      onClick={() => { setShowCreateForm(false); setNewName(""); setNewDesc("") }}
                      style={{
                        background: "none",
                        border: "1px solid rgba(255,255,255,0.15)",
                        borderRadius: 8,
                        padding: "6px 14px",
                        color: "#888",
                        fontSize: 13,
                        cursor: "pointer",
                      }}
                    >
                      Cancel
                    </button>
                    <button
                      onClick={handleCreate}
                      disabled={!newName.trim() || creating}
                      style={{
                        background: newName.trim() ? "#3aa" : "rgba(58,170,170,0.3)",
                        border: "none",
                        borderRadius: 8,
                        padding: "6px 14px",
                        color: newName.trim() ? "#000" : "#666",
                        fontSize: 13,
                        fontWeight: 600,
                        cursor: newName.trim() && !creating ? "pointer" : "default",
                        transition: "all 150ms",
                      }}
                    >
                      {creating ? "Saving…" : "Save"}
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => setShowCreateForm(true)}
                  style={{
                    background: "rgba(255,255,255,0.02)",
                    border: "2px dashed rgba(255,255,255,0.12)",
                    borderRadius: 12,
                    padding: "20px 22px",
                    cursor: "pointer",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 8,
                    minHeight: 140,
                    transition: "all 180ms",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.06)"
                    e.currentTarget.style.borderColor = "rgba(58,170,170,0.4)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.02)"
                    e.currentTarget.style.borderColor = "rgba(255,255,255,0.12)"
                  }}
                >
                  <span style={{ fontSize: 32, color: "#666", lineHeight: 1 }}>+</span>
                  <span style={{ fontSize: 13, color: "#666" }}>New collection</span>
                </button>
              )
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
