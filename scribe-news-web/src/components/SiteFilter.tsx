import { useState, useEffect, useRef } from "react"
import { SITES_URL } from "../config"
import { useLang } from "../contexts/LanguageContext"

export interface Site {
  id: number
  name: string
  acronym: string | null
  url: string
}

interface SiteFilterProps {
  selectedSiteIds: number[]
  onChangeSelection: (siteIds: number[]) => void
  /** "dark" for dark-background pages (default), "light" for inside white forms */
  variant?: "dark" | "light"
}

export default function SiteFilter({ selectedSiteIds, onChangeSelection, variant = "dark" }: SiteFilterProps) {
  const [sites, setSites] = useState<Site[]>([])
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const { t } = useLang()

  // Fetch sites once
  useEffect(() => {
    let cancelled = false
    fetch(SITES_URL)
      .then(res => res.json())
      .then((data: Site[]) => {
        if (!cancelled) {
          setSites(data)
          // Default: select all sites
          if (selectedSiteIds.length === 0) {
            onChangeSelection(data.map(s => s.id))
          }
        }
      })
      .catch(err => console.error("Failed to fetch sites:", err))
    return () => { cancelled = true }
  }, [])

  // Close on outside click
  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener("mousedown", handler)
    return () => document.removeEventListener("mousedown", handler)
  }, [open])

  const toggle = (id: number) => {
    if (selectedSiteIds.includes(id)) {
      onChangeSelection(selectedSiteIds.filter(s => s !== id))
    } else {
      onChangeSelection([...selectedSiteIds, id])
    }
  }

  const allSelected = sites.length > 0 && selectedSiteIds.length === sites.length
  const noneSelected = selectedSiteIds.length === 0

  const toggleAll = () => {
    if (allSelected || noneSelected) {
      // If all selected or none selected, toggle to the opposite
      onChangeSelection(allSelected ? [] : sites.map(s => s.id))
    } else {
      // Partial: select all
      onChangeSelection(sites.map(s => s.id))
    }
  }

  const isDark = variant === "dark"
  const hasFilter = selectedSiteIds.length > 0 && selectedSiteIds.length < sites.length

  return (
    <div ref={containerRef} style={{ position: "relative", display: "inline-flex" }}>
      <button
        onClick={() => setOpen(o => !o)}
        style={{
          background: isDark
            ? (hasFilter ? "rgba(58,170,170,0.15)" : "rgba(255,255,255,0.1)")
            : (hasFilter ? "rgba(58,170,170,0.1)" : "transparent"),
          border: isDark
            ? `1px solid ${hasFilter ? "rgba(58,170,170,0.4)" : "rgba(255,255,255,0.2)"}`
            : `1px solid ${hasFilter ? "rgba(58,170,170,0.4)" : "rgba(0,0,0,0.15)"}`,
          borderRadius: 8,
          padding: "7px 8px",
          cursor: "pointer",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          transition: "all 200ms",
          flexShrink: 0,
        }}
        title={t.filterBySite}
      >
        {/* Sliders/control icon */}
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke={isDark ? (hasFilter ? "#3aa" : "#aaa") : (hasFilter ? "#3aa" : "#555")}
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <line x1="4" y1="21" x2="4" y2="14" />
          <line x1="4" y1="10" x2="4" y2="3" />
          <line x1="12" y1="21" x2="12" y2="12" />
          <line x1="12" y1="8" x2="12" y2="3" />
          <line x1="20" y1="21" x2="20" y2="16" />
          <line x1="20" y1="12" x2="20" y2="3" />
          <line x1="1" y1="14" x2="7" y2="14" />
          <line x1="9" y1="8" x2="15" y2="8" />
          <line x1="17" y1="16" x2="23" y2="16" />
        </svg>
        {hasFilter && (
          <span style={{
            position: "absolute",
            top: -4,
            right: -4,
            width: 14,
            height: 14,
            borderRadius: "50%",
            background: "#3aa",
            color: "#fff",
            fontSize: 9,
            fontWeight: 700,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            lineHeight: 1,
          }}>
            {selectedSiteIds.length}
          </span>
        )}
      </button>

      {open && (
        <div style={{
          position: "absolute",
          top: "calc(100% + 8px)",
          left: 0,
          minWidth: 220,
          background: "rgba(15,15,15,0.97)",
          backdropFilter: "blur(12px)",
          border: "1px solid rgba(255,255,255,0.15)",
          borderRadius: 10,
          padding: "8px 0",
          boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
          zIndex: 9999,
        }}>
          <div style={{ padding: "6px 14px 8px", fontSize: 11, color: "#666", fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5 }}>
            {t.filterBySite}
          </div>

          {/* Select all / none */}
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: 10,
              padding: "6px 14px",
              cursor: "pointer",
              fontSize: 13,
              color: "#ccc",
              borderBottom: "1px solid rgba(255,255,255,0.08)",
              marginBottom: 4,
              transition: "background 120ms",
            }}
            onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
            onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
          >
            <input
              type="checkbox"
              checked={allSelected}
              ref={(el) => { if (el) el.indeterminate = !allSelected && !noneSelected }}
              onChange={toggleAll}
              style={{ accentColor: "#3aa", width: 15, height: 15, cursor: "pointer" }}
            />
            <span style={{ fontWeight: 500 }}>All sites</span>
          </label>

          {sites.map(site => (
            <label
              key={site.id}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                padding: "6px 14px",
                cursor: "pointer",
                fontSize: 13,
                color: selectedSiteIds.includes(site.id) ? "#eee" : "#888",
                transition: "all 120ms",
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
            >
              <input
                type="checkbox"
                checked={selectedSiteIds.includes(site.id)}
                onChange={() => toggle(site.id)}
                style={{ accentColor: "#3aa", width: 15, height: 15, cursor: "pointer" }}
              />
              <span>{site.acronym ? `${site.name} (${site.acronym})` : site.name}</span>
            </label>
          ))}

          {sites.length === 0 && (
            <div style={{ padding: "8px 14px", fontSize: 12, color: "#666" }}>{t.loading}</div>
          )}
        </div>
      )}
    </div>
  )
}
