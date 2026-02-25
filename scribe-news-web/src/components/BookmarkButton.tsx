import { useState, useEffect, useRef, useCallback } from "react"
import { createPortal } from "react-dom"
import { PRIVATE_COLLECTIONS_URL, collectionArticleUrl } from "../config"
import { useAuth } from "./useAuth"
import { useLang } from "../contexts/LanguageContext"

interface Collection {
  id: number
  name: string
  descriptiom?: string
  articleCount: number
}

interface Props {
  articleId: number | undefined
  /** Position style overrides, e.g. { position:"absolute", top:8, right:8 } */
  style?: React.CSSProperties
}

export default function BookmarkButton({ articleId, style }: Props) {
  const { user } = useAuth()
  const { t } = useLang()
  const [open, setOpen] = useState(false)
  const [collections, setCollections] = useState<Collection[]>([])
  const [savedIn, setSavedIn] = useState<Set<number>>(new Set())       // current server state
  const [pending, setPending] = useState<Set<number>>(new Set())       // local pending state (user toggles)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [closing, setClosing] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const btnRef = useRef<HTMLButtonElement>(null)
  const [popoverPos, setPopoverPos] = useState<{ top: number; left: number } | null>(null)

  const closePopover = useCallback(() => {
    setClosing(true)
    setTimeout(() => {
      setOpen(false)
      setClosing(false)
    }, 200)
  }, [])

  // Close when clicking outside (check both the button wrapper and the portal popover)
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      const target = e.target as Node
      if (ref.current?.contains(target)) return
      const portal = document.getElementById("bookmark-popover")
      if (portal?.contains(target)) return
      closePopover()
    }
    document.addEventListener("mousedown", onClick)
    return () => document.removeEventListener("mousedown", onClick)
  }, [open, closePopover])

  // Compute popover position from button rect
  const updatePopoverPos = useCallback(() => {
    if (!btnRef.current) return
    const rect = btnRef.current.getBoundingClientRect()
    const popoverW = 240
    // Position above the button, centered horizontally
    setPopoverPos({
      top: rect.top - 10, // 10px gap above button
      left: rect.left + rect.width / 2 - popoverW / 2,
    })
  }, [])

  // Fetch collections when opening, then check membership for each
  const openPopover = useCallback(async () => {
    if (!user || !articleId) return
    updatePopoverPos()
    setOpen(true)
    setLoading(true)
    try {
      const res = await fetch(PRIVATE_COLLECTIONS_URL, { credentials: "include" })
      if (!res.ok) throw new Error(`${res.status}`)
      const data: Collection[] = await res.json()
      setCollections(data)

      // Check each collection for this article in parallel
      const checks = await Promise.all(
        data.map(async (col) => {
          try {
            const r = await fetch(collectionArticleUrl(col.id, articleId), { credentials: "include" })
            if (!r.ok) return { id: col.id, has: false }
            const has = await r.json()
            return { id: col.id, has: !!has }
          } catch {
            return { id: col.id, has: false }
          }
        })
      )
      const initialSaved = new Set<number>()
      checks.forEach((c) => { if (c.has) initialSaved.add(c.id) })
      setSavedIn(initialSaved)
      setPending(new Set(initialSaved))
    } catch {
      setCollections([])
    } finally {
      setLoading(false)
    }
  }, [user, articleId, updatePopoverPos])

  // Toggle a collection in the local pending state (no API call yet)
  const toggleCollection = (collectionId: number) => {
    setPending((prev) => {
      const next = new Set(prev)
      if (next.has(collectionId)) next.delete(collectionId)
      else next.add(collectionId)
      return next
    })
  }

  // Commit pending changes: diff against savedIn, call add/remove, then close
  const handleSave = async () => {
    if (!articleId || saving) return
    setSaving(true)
    try {
      const toAdd = [...pending].filter((id) => !savedIn.has(id))
      const toRemove = [...savedIn].filter((id) => !pending.has(id))

      await Promise.all([
        ...toAdd.map((colId) =>
          fetch(collectionArticleUrl(colId, articleId), { method: "POST", credentials: "include" })
        ),
        ...toRemove.map((colId) =>
          fetch(collectionArticleUrl(colId, articleId), { method: "DELETE", credentials: "include" })
        ),
      ])
      setSavedIn(new Set(pending))
    } catch {
      // silently fail
    }
    // Brief delay so user sees the "Saved" feedback, then close smoothly
    await new Promise((r) => setTimeout(r, 400))
    setSaving(false)
    closePopover()
  }

  // Reset state and pre-check membership when articleId changes
  useEffect(() => {
    setSavedIn(new Set())
    setPending(new Set())

    if (!user || !articleId) return
    let cancelled = false

    ;(async () => {
      try {
        const res = await fetch(PRIVATE_COLLECTIONS_URL, { credentials: "include" })
        if (!res.ok || cancelled) return
        const data: Collection[] = await res.json()

        const checks = await Promise.all(
          data.map(async (col) => {
            try {
              const r = await fetch(collectionArticleUrl(col.id, articleId), { credentials: "include" })
              if (!r.ok) return { id: col.id, has: false }
              const has = await r.json()
              return { id: col.id, has: !!has }
            } catch {
              return { id: col.id, has: false }
            }
          })
        )
        if (cancelled) return
        const initial = new Set<number>()
        checks.forEach((c) => { if (c.has) initial.add(c.id) })
        setSavedIn(initial)
        setPending(new Set(initial))
      } catch {
        // ignore
      }
    })()

    return () => { cancelled = true }
  }, [user, articleId])

  if (!user || !articleId) return null

  const isSaved = savedIn.size > 0

  return (
    <div ref={ref} style={{ position: "relative", display: "inline-flex", ...style }}>
      {/* Bookmark icon button — styled like sibling zoom-bar buttons */}
      <button
        ref={btnRef}
        onClick={(e) => {
          e.stopPropagation()
          e.preventDefault()
          open ? closePopover() : openPopover()
        }}
        onPointerDown={(e) => e.stopPropagation()}
        title={t.saveToCollection}
        style={{
          height: 32,
          padding: "0 12px",
          borderRadius: 16,
          border: "none",
          background: "rgba(255,255,255,0.1)",
          color: "#fff",
          fontSize: 12,
          cursor: "pointer",
          display: "flex",
          alignItems: "center",
          gap: 4,
          transition: "background 150ms",
        }}
        onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.2)" }}
        onMouseLeave={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.1)" }}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill={isSaved ? "#fff" : "none"} stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
        </svg>
        {t.save}
      </button>

      {/* Collections popover — rendered via portal to escape overflow clipping */}
      {open && popoverPos && createPortal(
        <div
          id="bookmark-popover"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => e.stopPropagation()}
          style={{
            position: "fixed",
            top: popoverPos.top,
            left: popoverPos.left,
            transform: "translateY(-100%)",
            width: 240,
            background: "rgba(15,15,15,0.97)",
            backdropFilter: "blur(12px)",
            border: "1px solid rgba(255,255,255,0.15)",
            borderRadius: 10,
            boxShadow: "0 8px 32px rgba(0,0,0,0.7)",
            zIndex: 10000,
            overflow: "hidden",
            opacity: closing ? 0 : 1,
            transition: "opacity 200ms ease-out, transform 200ms ease-out",
            ...(closing ? { transform: "translateY(calc(-100% + 6px))" } : {}),
          }}
        >
          <div style={{ padding: "10px 12px 6px", fontSize: 11, color: "#888", fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
            {t.saveToCollection}
          </div>

          {loading ? (
            <div style={{ padding: "16px 12px", textAlign: "center", fontSize: 12, color: "#888" }}>{t.loading}</div>
          ) : collections.length === 0 ? (
            <div style={{ padding: "16px 12px", textAlign: "center", fontSize: 12, color: "#666" }}>{t.noCollectionsYetShort}</div>
          ) : (
            <div style={{ maxHeight: 220, overflowY: "auto", padding: "4px 0" }}>
              {collections.map((col) => {
                const checked = pending.has(col.id)
                return (
                  <div
                    key={col.id}
                    title={col.descriptiom ?? ""}
                    onClick={() => toggleCollection(col.id)}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 10,
                      padding: "8px 12px",
                      cursor: "pointer",
                      transition: "background 120ms",
                      background: "transparent",
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.06)" }}
                    onMouseLeave={(e) => { e.currentTarget.style.background = "transparent" }}
                  >
                    {/* Checkbox indicator */}
                    <div style={{
                      width: 18,
                      height: 18,
                      borderRadius: 4,
                      border: checked ? "none" : "1.5px solid #555",
                      background: checked ? "#3aa" : "transparent",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      flexShrink: 0,
                      transition: "all 150ms",
                    }}>
                      {checked && (
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#000" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                          <polyline points="20 6 9 17 4 12" />
                        </svg>
                      )}
                    </div>

                    <span style={{ flex: 1, fontSize: 13, color: "#ddd", lineHeight: 1.3 }}>
                      {col.name}
                    </span>
                  </div>
                )
              })}
            </div>
          )}

          {/* Save button */}
          {!loading && collections.length > 0 && (
            <div style={{ padding: "8px 12px", borderTop: "1px solid rgba(255,255,255,0.1)" }}>
              <button
                onClick={handleSave}
                disabled={saving}
                style={{
                  width: "100%",
                  padding: "7px 0",
                  borderRadius: 6,
                  border: "none",
                  background: "#3aa",
                  color: "#000",
                  fontSize: 12,
                  fontWeight: 600,
                  cursor: saving ? "wait" : "pointer",
                  opacity: saving ? 0.6 : 1,
                  transition: "opacity 150ms",
                }}
              >
                {saving ? t.saving : t.save}
              </button>
            </div>
          )}
        </div>,
        document.getElementById("root")!
      )}
    </div>
  )
}
