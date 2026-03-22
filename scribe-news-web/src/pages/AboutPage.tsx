import { useState, useEffect, useRef, useCallback } from "react"
import { useNavigate } from "react-router-dom"
import {
  SITES_URL,
  ARTICLES_COUNT_URL,
  PERSONS_COUNT_URL,
  AUTHORS_COUNT_URL,
  PUBLIC_COLLECTIONS_COUNT_URL,
  PRIVATE_COLLECTIONS_COUNT_URL,
  PERSONS_FIND_URL,
} from "../config"
import { useLang } from "../contexts/LanguageContext"

function Lightbox({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") onClose() }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [onClose])

  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed", inset: 0, zIndex: 9999,
        background: "rgba(0,0,0,0.88)",
        display: "flex", alignItems: "center", justifyContent: "center",
        padding: 24,
      }}
    >
      <button
        onClick={onClose}
        style={{
          position: "absolute", top: 20, right: 24,
          background: "rgba(255,255,255,0.12)",
          border: "1px solid rgba(255,255,255,0.2)",
          borderRadius: 8, padding: "6px 12px",
          color: "#eee", fontSize: 20, lineHeight: 1,
          cursor: "pointer",
        }}
        aria-label="Close"
      >
        ×
      </button>
      <img
        src={src}
        alt={alt}
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: "100%", maxHeight: "90vh", borderRadius: 12, objectFit: "contain", boxShadow: "0 8px 48px rgba(0,0,0,0.8)" }}
      />
    </div>
  )
}

function PipelineCard({ title, desc }: { title: string; desc: string }) {
  const [expanded, setExpanded] = useState(false)
  return (
    <div style={{ border: "1px solid rgba(255,255,255,0.08)", borderRadius: 12, background: "rgba(255,255,255,0.02)", overflow: "hidden" }}>
      <div style={{ padding: "20px 24px 0" }}>
        <p style={{ margin: "0 0 10px", fontSize: 13, fontWeight: 600, color: "#888", fontFamily: "'Menlo', 'Consolas', monospace", letterSpacing: 0.3 }}>{title}</p>
        <div style={{ position: "relative" }}>
          <p style={{
            margin: 0,
            fontSize: 14,
            lineHeight: 1.8,
            color: "#777",
            overflow: "hidden",
            maxHeight: expanded ? "none" : "6.3em",
            transition: "max-height 300ms ease",
          }}>{desc}</p>
          {!expanded && (
            <div style={{
              position: "absolute",
              bottom: 0,
              left: 0,
              right: 0,
              height: 40,
              background: "linear-gradient(transparent, rgba(18,18,18,0.95))",
              pointerEvents: "none",
            }} />
          )}
        </div>
      </div>
      <button
        onClick={() => setExpanded((v) => !v)}
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          width: "100%",
          padding: "10px 0 12px",
          background: "none",
          border: "none",
          cursor: "pointer",
          color: "#555",
          gap: 4,
          fontSize: 12,
        }}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
          style={{ transform: expanded ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 250ms" }}>
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>
    </div>
  )
}

interface Site {
  id: number
  name: string
  url?: string
}

interface Person {
  id: number
  name: string
}

interface PersonPage {
  content: Person[]
  last: boolean
  number: number
}

function formatCount(n: number | null): string {
  if (n === null) return "…"
  return n.toLocaleString()
}

function StatCard({ label, value }: { label: string; value: number | null }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 6,
        padding: "20px 24px",
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.08)",
        borderRadius: 12,
        minWidth: 120,
        flex: "1 1 120px",
      }}
    >
      <span style={{ fontSize: 28, fontWeight: 700, color: "#e8e8e8", letterSpacing: -0.5 }}>
        {formatCount(value)}
      </span>
      <span style={{ fontSize: 12, color: "#888", textTransform: "uppercase" as const, letterSpacing: 0.8 }}>
        {label}
      </span>
    </div>
  )
}

function SectionLabel({ text }: { text: string }) {
  return (
    <p style={{ margin: "0 0 16px", fontSize: 11, fontWeight: 600, color: "#555", textTransform: "uppercase" as const, letterSpacing: 1 }}>
      {text}
    </p>
  )
}

function ExpandableBox({
  title,
  children,
  expandLabel,
  collapseLabel,
}: {
  title: string
  children: React.ReactNode
  expandLabel: string
  collapseLabel: string
}) {
  const [open, setOpen] = useState(false)

  return (
    <div style={{ border: "1px solid rgba(255,255,255,0.1)", borderRadius: 12, overflow: "hidden", background: "rgba(255,255,255,0.02)" }}>
      <button
        onClick={() => setOpen((v) => !v)}
        style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", padding: "16px 20px", background: "none", border: "none", cursor: "pointer", color: "#ddd", fontSize: 15, fontWeight: 600 }}
      >
        <span>{title}</span>
        <span style={{ fontSize: 12, color: "#888", fontWeight: 400, display: "flex", alignItems: "center", gap: 6 }}>
          {open ? collapseLabel : expandLabel}
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
            style={{ transform: open ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 200ms" }}>
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </span>
      </button>
      {open && (
        <div style={{ borderTop: "1px solid rgba(255,255,255,0.06)", maxHeight: 320, overflowY: "auto" }}>
          {children}
        </div>
      )}
    </div>
  )
}

function SitesBox({ sites, title, expandLabel, collapseLabel }: {
  sites: Site[]
  title: string
  expandLabel: string
  collapseLabel: string
}) {
  return (
    <ExpandableBox title={title} expandLabel={expandLabel} collapseLabel={collapseLabel}>
      <div style={{ padding: "12px 20px 16px", display: "flex", flexDirection: "column", gap: 4 }}>
        {sites.map((site) => (
          <div key={site.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.04)" }}>
            <span style={{ width: 5, height: 5, borderRadius: "50%", background: "#555", flexShrink: 0 }} />
            {site.url ? (
              <a href={site.url} target="_blank" rel="noopener noreferrer"
                style={{ color: "#bbb", fontSize: 14, textDecoration: "none" }}
                onMouseEnter={(e) => { e.currentTarget.style.color = "#fff" }}
                onMouseLeave={(e) => { e.currentTarget.style.color = "#bbb" }}>
                {site.name}
              </a>
            ) : (
              <span style={{ color: "#bbb", fontSize: 14 }}>{site.name}</span>
            )}
          </div>
        ))}
      </div>
    </ExpandableBox>
  )
}

function PersonsBox({ title, expandLabel, collapseLabel, loadMoreLabel, noMoreLabel }: {
  title: string
  expandLabel: string
  collapseLabel: string
  loadMoreLabel: string
  noMoreLabel: string
}) {
  const [persons, setPersons] = useState<Person[]>([])
  const [page, setPage] = useState(0)
  const [isLast, setIsLast] = useState(false)
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const loadedRef = useRef(false)

  const loadPage = useCallback(async (pageNum: number) => {
    if (loading || (isLast && pageNum > 0)) return
    setLoading(true)
    try {
      const res = await fetch(`${PERSONS_FIND_URL}?page=${pageNum}&size=30&sort=name,asc`)
      if (!res.ok) return
      const data: PersonPage = await res.json()
      setPersons((prev) => pageNum === 0 ? data.content : [...prev, ...data.content])
      setIsLast(data.last)
      setPage(data.number)
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }, [loading, isLast])

  useEffect(() => {
    if (open && !loadedRef.current) {
      loadedRef.current = true
      loadPage(0)
    }
  }, [open, loadPage])

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    function onScroll() {
      if (!el) return
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80 && !loading && !isLast) {
        loadPage(page + 1)
      }
    }
    el.addEventListener("scroll", onScroll, { passive: true })
    return () => el.removeEventListener("scroll", onScroll)
  }, [loading, isLast, page, loadPage])

  return (
    <div style={{ border: "1px solid rgba(255,255,255,0.1)", borderRadius: 12, overflow: "hidden", background: "rgba(255,255,255,0.02)" }}>
      <button
        onClick={() => setOpen((v) => !v)}
        style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", padding: "16px 20px", background: "none", border: "none", cursor: "pointer", color: "#ddd", fontSize: 15, fontWeight: 600 }}
      >
        <span>{title}</span>
        <span style={{ fontSize: 12, color: "#888", fontWeight: 400, display: "flex", alignItems: "center", gap: 6 }}>
          {open ? collapseLabel : expandLabel}
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
            style={{ transform: open ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 200ms" }}>
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </span>
      </button>
      {open && (
        <div ref={scrollRef} style={{ borderTop: "1px solid rgba(255,255,255,0.06)", maxHeight: 320, overflowY: "auto" }}>
          <div style={{ padding: "12px 20px 16px", display: "flex", flexDirection: "column" }}>
            {persons.map((p) => (
              <div key={p.id} style={{ padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.04)", color: "#bbb", fontSize: 14 }}>
                {p.name}
              </div>
            ))}
            {loading && (
              <div style={{ color: "#666", fontSize: 12, textAlign: "center", padding: "12px 0" }}>{loadMoreLabel}</div>
            )}
            {isLast && !loading && persons.length > 0 && (
              <div style={{ color: "#555", fontSize: 12, textAlign: "center", padding: "12px 0" }}>{noMoreLabel}</div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default function AboutPage() {
  const { t } = useLang()
  const navigate = useNavigate()
  const [lightbox, setLightbox] = useState<{ src: string; alt: string } | null>(null)

  const [stats, setStats] = useState({
    articles: null as number | null,
    persons: null as number | null,
    authors: null as number | null,
    publicCollections: null as number | null,
    privateCollections: null as number | null,
  })
  const [sites, setSites] = useState<Site[]>([])

  useEffect(() => {
    document.title = "About — PressDigger"
    return () => { document.title = "PressDigger" }
  }, [])

  useEffect(() => {
    const fetchCount = async (url: string, key: keyof typeof stats) => {
      try {
        const res = await fetch(url)
        if (!res.ok) return
        const n = await res.json()
        setStats((prev) => ({ ...prev, [key]: n }))
      } catch { /* ignore */ }
    }

    fetchCount(ARTICLES_COUNT_URL, "articles")
    fetchCount(PERSONS_COUNT_URL, "persons")
    fetchCount(AUTHORS_COUNT_URL, "authors")
    fetchCount(PUBLIC_COLLECTIONS_COUNT_URL, "publicCollections")
    fetchCount(PRIVATE_COLLECTIONS_COUNT_URL, "privateCollections")

    fetch(SITES_URL)
      .then((r) => r.ok ? r.json() : [])
      .then((data: Site[]) => setSites(data))
      .catch(() => {})
  }, [])

  return (
    <div style={{
      position: "fixed",
      inset: 0,
      background: "linear-gradient(180deg,#070707 0%,#0f0f0f 100%)",
      color: "#eee",
      display: "flex",
      flexDirection: "column",
      overflow: "hidden",
      fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
    }}>
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
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 500 }}>{t.aboutTitle}</h2>
      </div>

      {/* Scrollable content */}
      <div style={{ flex: 1, overflowY: "auto", padding: "32px 24px", display: "flex", justifyContent: "center" }}>
        <div style={{ width: "100%", maxWidth: 760, display: "flex", flexDirection: "column", gap: 40 }}>

          {/* Description */}
          <p style={{ margin: 0, fontSize: 15, lineHeight: 1.8, color: "#aaa" }}>
            {t.aboutDescription}
          </p>

          {/* Stats */}
          <div>
            <SectionLabel text={t.aboutStatsTitle} />
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <StatCard label={t.aboutArticles} value={stats.articles} />
              <StatCard label={t.aboutPersons} value={stats.persons} />
              <StatCard label={t.aboutAuthors} value={stats.authors} />
              <StatCard label={t.aboutSites} value={sites.length || null} />
              <StatCard label={t.aboutPublicCollections} value={stats.publicCollections} />
              <StatCard label={t.aboutPrivateCollections} value={stats.privateCollections} />
            </div>
          </div>

          {/* Expandable boxes */}
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <SitesBox
              sites={sites}
              title={t.aboutSitesBox}
              expandLabel={t.aboutExpand}
              collapseLabel={t.aboutCollapse}
            />
            <PersonsBox
              title={t.aboutPersonsBox}
              expandLabel={t.aboutExpand}
              collapseLabel={t.aboutCollapse}
              loadMoreLabel={t.aboutLoadMore}
              noMoreLabel={t.aboutNoMorePersons}
            />
          </div>

          {/* Architecture */}
          <div>
            <SectionLabel text={t.aboutArchitectureTitle} />
            <p style={{ margin: "0 0 20px", fontSize: 15, lineHeight: 1.8, color: "#aaa" }}>
              {t.aboutArchitectureDescription}
            </p>
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <img src="/PressDigger.jpg" alt="Architecture diagram" onClick={() => setLightbox({ src: "/PressDigger.jpg", alt: "Architecture diagram" })} style={{ width: "100%", borderRadius: 12, display: "block", cursor: "zoom-in" }} />
              <img src="/PressDigger2.jpg" alt="Architecture diagram 2" onClick={() => setLightbox({ src: "/PressDigger2.jpg", alt: "Architecture diagram 2" })} style={{ width: "100%", borderRadius: 12, display: "block", cursor: "zoom-in" }} />
            </div>

            {lightbox && <Lightbox src={lightbox.src} alt={lightbox.alt} onClose={() => setLightbox(null)} />}

            {/* Component descriptions */}
            <div style={{ display: "flex", flexDirection: "column", gap: 16, marginTop: 24 }}>
              {([
                { title: t.aboutCrawlerTitle, desc: t.aboutCrawlerDesc },
                { title: t.aboutImageTitle, desc: t.aboutImageDesc },
                { title: t.aboutTextTitle, desc: t.aboutTextDesc },
                { title: t.aboutEmbeddingsTitle, desc: t.aboutEmbeddingsDesc },
                { title: t.aboutRestTitle, desc: t.aboutRestDesc },
              ] as { title: string; desc: string }[]).map(({ title, desc }) => (
                <PipelineCard key={title} title={title} desc={desc} />
              ))}
            </div>
          </div>

        </div>
      </div>
    </div>
  )
}
