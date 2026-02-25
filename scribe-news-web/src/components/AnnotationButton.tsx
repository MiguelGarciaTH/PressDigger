import { useState, useEffect, useRef, useCallback } from "react"
import { createPortal } from "react-dom"
import { ANNOTATIONS_URL, annotationByArticleUrl, annotationUrl } from "../config"
import { useAuth } from "./useAuth"
import { useLang } from "../contexts/LanguageContext"

interface Annotation {
  id: number
  text: string
}

interface Props {
  articleId: number | undefined
  style?: React.CSSProperties
  /** When provided, parent controls the annotation state (skips internal fetch) */
  controlledAnnotation?: Annotation | null
  /** Called after a successful save so the parent can update its own state */
  onAnnotationSaved?: (annotation: Annotation) => void
}

export default function AnnotationButton({ articleId, style, controlledAnnotation, onAnnotationSaved }: Props) {
  const { user } = useAuth()
  const { t } = useLang()
  const isControlled = controlledAnnotation !== undefined
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [internalAnnotation, setInternalAnnotation] = useState<Annotation | null>(null)
  const annotation = isControlled ? controlledAnnotation : internalAnnotation
  const setAnnotation = (a: Annotation | null) => {
    if (!isControlled) setInternalAnnotation(a)
    if (a) onAnnotationSaved?.(a)
  }
  const [draft, setDraft] = useState("")
  const [saving, setSaving] = useState(false)
  const [closing, setClosing] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const btnRef = useRef<HTMLButtonElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const [popoverPos, setPopoverPos] = useState<{ top: number; left: number } | null>(null)

  const closePopover = useCallback(() => {
    setClosing(true)
    setTimeout(() => {
      setOpen(false)
      setClosing(false)
    }, 200)
  }, [])

  // Close when clicking outside
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      const target = e.target as Node
      if (ref.current?.contains(target)) return
      const portal = document.getElementById("annotation-popover")
      if (portal?.contains(target)) return
      closePopover()
    }
    document.addEventListener("mousedown", onClick)
    return () => document.removeEventListener("mousedown", onClick)
  }, [open, closePopover])

  // Focus textarea when opening with no annotation, or entering edit mode
  useEffect(() => {
    if (open && (!annotation || editing) && textareaRef.current) {
      setTimeout(() => textareaRef.current?.focus(), 50)
    }
  }, [open, annotation, editing])

  const updatePopoverPos = useCallback(() => {
    if (!btnRef.current) return
    const rect = btnRef.current.getBoundingClientRect()
    const popoverW = 280
    setPopoverPos({
      top: rect.top - 10,
      left: rect.left + rect.width / 2 - popoverW / 2,
    })
  }, [])

  const openPopover = useCallback(() => {
    if (!user || !articleId) return
    updatePopoverPos()
    setEditing(false)
    setDraft(annotation?.text ?? "")
    setOpen(true)
  }, [user, articleId, annotation, updatePopoverPos])

  // Fetch existing annotation whenever articleId changes (only in uncontrolled mode)
  useEffect(() => {
    if (isControlled) return
    setInternalAnnotation(null)
    setDraft("")
    if (!user || !articleId) return
    let cancelled = false

    ;(async () => {
      try {
        const res = await fetch(annotationByArticleUrl(articleId), { credentials: "include" })
        if (!res.ok || cancelled) return
        const data = await res.json()
        if (cancelled) return
        if (data && data.id) {
          setInternalAnnotation({ id: data.id, text: data.text })
        }
      } catch {
        // ignore — unauthenticated or no annotation
      }
    })()

    return () => { cancelled = true }
  }, [user, articleId, isControlled])

  const handleSave = async () => {
    if (!articleId || saving || !draft.trim()) return
    setSaving(true)
    try {
      let res: Response
      if (annotation?.id) {
        // Edit existing annotation via PATCH
        res = await fetch(annotationUrl(annotation.id), {
          method: "PATCH",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ content: draft.trim() }),
        })
      } else {
        // Create new annotation via POST
        res = await fetch(ANNOTATIONS_URL, {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ articleId, content: draft.trim() }),
        })
      }
      if (!res.ok) throw new Error(`${res.status}`)
      const data = await res.json()
      setAnnotation({ id: data.id ?? annotation?.id ?? 0, text: data.text ?? draft.trim() })
      setEditing(false)
      await new Promise((r) => setTimeout(r, 300))
      closePopover()
    } catch {
      // silently fail
    } finally {
      setSaving(false)
    }
  }

  if (!user || !articleId) return null

  const hasAnnotation = annotation !== null

  return (
    <div ref={ref} style={{ position: "relative", display: "inline-flex", ...style }}>
      <button
        ref={btnRef}
        onClick={(e) => {
          e.stopPropagation()
          e.preventDefault()
          open ? closePopover() : openPopover()
        }}
        onPointerDown={(e) => e.stopPropagation()}
        title={hasAnnotation ? t.viewAnnotation : t.addAnnotation}
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
        {/* Annotation / note icon */}
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill={hasAnnotation ? "#fff" : "none"}
          stroke="#fff"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M12 20h9" />
          <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
        </svg>
        {t.note}
      </button>

      {open && popoverPos && createPortal(
        <div
          id="annotation-popover"
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => e.stopPropagation()}
          style={{
            position: "fixed",
            top: popoverPos.top,
            left: popoverPos.left,
            transform: closing ? "translateY(calc(-100% + 6px))" : "translateY(-100%)",
            width: 280,
            background: "rgba(12,12,12,0.82)",
            backdropFilter: "blur(16px)",
            WebkitBackdropFilter: "blur(16px)",
            border: "1px solid rgba(255,255,255,0.12)",
            borderRadius: 10,
            boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
            zIndex: 10000,
            overflow: "hidden",
            opacity: closing ? 0 : 1,
            transition: "opacity 200ms ease-out, transform 200ms ease-out",
          }}
        >
          {/* Header */}
          <div style={{
            padding: "10px 14px 8px",
            fontSize: 11,
            color: "#777",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: 0.5,
            borderBottom: "1px solid rgba(255,255,255,0.07)",
          }}>
            {hasAnnotation ? t.yourAnnotation : t.addAnnotation}
          </div>

          {/* Body */}
          <div style={{ padding: "10px 14px 12px" }}>
            {hasAnnotation && !editing ? (
              /* Display mode */
              <>
                <p style={{
                  margin: "0 0 10px",
                  fontSize: 13,
                  color: "#ccc",
                  lineHeight: 1.6,
                  whiteSpace: "pre-wrap",
                  wordBreak: "break-word",
                }}>
                  {annotation.text}
                </p>
                <div style={{ display: "flex", justifyContent: "flex-end" }}>
                  <button
                    onClick={() => { setDraft(annotation.text); setEditing(true) }}
                    style={{
                      background: "none",
                      border: "1px solid rgba(255,255,255,0.15)",
                      borderRadius: 6,
                      padding: "4px 12px",
                      color: "#aaa",
                      fontSize: 12,
                      cursor: "pointer",
                      display: "flex",
                      alignItems: "center",
                      gap: 5,
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = "rgba(255,255,255,0.3)"; e.currentTarget.style.color = "#eee" }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = "rgba(255,255,255,0.15)"; e.currentTarget.style.color = "#aaa" }}
                  >
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                    {t.edit}
                  </button>
                </div>
              </>
            ) : (
              /* Edit mode */
              <>
                <textarea
                  ref={textareaRef}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  placeholder={t.writeANote}
                  rows={4}
                  onKeyDown={(e) => {
                    if (e.key === "Escape") closePopover()
                    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) handleSave()
                  }}
                  style={{
                    width: "100%",
                    boxSizing: "border-box",
                    background: "rgba(255,255,255,0.05)",
                    border: "1px solid rgba(255,255,255,0.12)",
                    borderRadius: 6,
                    padding: "8px 10px",
                    color: "#ddd",
                    fontSize: 13,
                    lineHeight: 1.6,
                    outline: "none",
                    fontFamily: "inherit",
                    resize: "vertical",
                    transition: "border-color 150ms",
                  }}
                  onFocus={(e) => { e.currentTarget.style.borderColor = "rgba(255,255,255,0.3)" }}
                  onBlur={(e) => { e.currentTarget.style.borderColor = "rgba(255,255,255,0.12)" }}
                />
                <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8, gap: 8 }}>
                  <button
                    onClick={closePopover}
                    style={{
                      background: "none",
                      border: "1px solid rgba(255,255,255,0.12)",
                      borderRadius: 6,
                      padding: "5px 12px",
                      color: "#777",
                      fontSize: 12,
                      cursor: "pointer",
                    }}
                  >
                    {t.cancel}
                  </button>
                  <button
                    onClick={handleSave}
                    disabled={!draft.trim() || saving}
                    style={{
                      background: draft.trim() ? "#3aa" : "rgba(58,170,170,0.25)",
                      border: "none",
                      borderRadius: 6,
                      padding: "5px 14px",
                      color: draft.trim() ? "#000" : "#555",
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: draft.trim() && !saving ? "pointer" : "default",
                      transition: "all 150ms",
                    }}
                  >
                    {saving ? t.saving : t.save}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>,
        document.getElementById("root")!
      )}
    </div>
  )
}
