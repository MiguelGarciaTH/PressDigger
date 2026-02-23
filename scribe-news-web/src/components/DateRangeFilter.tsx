import { useState, useEffect, useRef } from "react"

interface DateRangeFilterProps {
  startDate: string
  endDate: string
  onChangeRange: (startDate: string, endDate: string) => void
  variant?: "dark" | "light"
}

const DEFAULT_START = "1996-01-01"

function todayStr() {
  const d = new Date()
  return d.toISOString().slice(0, 10)
}

export default function DateRangeFilter({
  startDate,
  endDate,
  onChangeRange,
  variant = "dark",
}: DateRangeFilterProps) {
  const [open, setOpen] = useState(false)
  const [localStart, setLocalStart] = useState(startDate)
  const [localEnd, setLocalEnd] = useState(endDate)
  const containerRef = useRef<HTMLDivElement | null>(null)

  // Sync local state when props change
  useEffect(() => {
    setLocalStart(startDate)
    setLocalEnd(endDate)
  }, [startDate, endDate])

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

  const isDark = variant === "dark"
  const isCustom = startDate !== DEFAULT_START || endDate !== todayStr()

  const handleApply = () => {
    onChangeRange(localStart, localEnd)
    setOpen(false)
  }

  const handleReset = () => {
    const today = todayStr()
    setLocalStart(DEFAULT_START)
    setLocalEnd(today)
    onChangeRange(DEFAULT_START, today)
    setOpen(false)
  }

  return (
    <div ref={containerRef} style={{ position: "relative", display: "inline-flex" }}>
      <button
        onClick={() => setOpen(o => !o)}
        style={{
          background: isDark
            ? (isCustom ? "rgba(58,170,170,0.15)" : "rgba(255,255,255,0.1)")
            : (isCustom ? "rgba(58,170,170,0.1)" : "transparent"),
          border: isDark
            ? `1px solid ${isCustom ? "rgba(58,170,170,0.4)" : "rgba(255,255,255,0.2)"}`
            : `1px solid ${isCustom ? "rgba(58,170,170,0.4)" : "rgba(0,0,0,0.15)"}`,
          borderRadius: 8,
          padding: "7px 8px",
          cursor: "pointer",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          transition: "all 200ms",
          flexShrink: 0,
        }}
        title="Filter by date range"
      >
        {/* Calendar / clock icon */}
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke={isDark ? (isCustom ? "#3aa" : "#aaa") : (isCustom ? "#3aa" : "#555")}
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
          <line x1="16" y1="2" x2="16" y2="6" />
          <line x1="8" y1="2" x2="8" y2="6" />
          <line x1="3" y1="10" x2="21" y2="10" />
        </svg>
        {isCustom && (
          <span style={{
            position: "absolute",
            top: -4,
            right: -4,
            width: 8,
            height: 8,
            borderRadius: "50%",
            background: "#3aa",
          }} />
        )}
      </button>

      {open && (
        <div style={{
          position: "absolute",
          top: "calc(100% + 8px)",
          left: 0,
          minWidth: 240,
          background: "rgba(15,15,15,0.97)",
          backdropFilter: "blur(12px)",
          border: "1px solid rgba(255,255,255,0.15)",
          borderRadius: 10,
          padding: "12px 14px",
          boxShadow: "0 8px 32px rgba(0,0,0,0.6)",
          zIndex: 9999,
        }}>
          <div style={{ fontSize: 11, color: "#666", fontWeight: 600, textTransform: "uppercase", letterSpacing: 0.5, marginBottom: 10 }}>
            Date range
          </div>

          <label style={{ display: "block", marginBottom: 4 }}>
            <div style={{ fontSize: 12, color: "#999", marginBottom: 4 }}>From</div>
            <input
              type="date"
              value={localStart}
              max={localEnd}
              onChange={(e) => setLocalStart(e.target.value)}
              style={{
                width: "100%",
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid rgba(255,255,255,0.15)",
                background: "rgba(255,255,255,0.06)",
                color: "#eee",
                fontSize: 13,
                outline: "none",
                colorScheme: "dark",
                boxSizing: "border-box",
              }}
            />
          </label>
          <div style={{ display: "flex", gap: 6, marginBottom: 10 }}>
            <button
              onClick={() => setLocalStart(todayStr())}
              style={{ flex: 1, padding: "4px 0", borderRadius: 4, border: "1px solid rgba(255,255,255,0.12)", background: "transparent", color: "#aaa", fontSize: 11, cursor: "pointer", transition: "all 150ms" }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
            >Today</button>
            <button
              onClick={() => setLocalStart("")}
              style={{ flex: 1, padding: "4px 0", borderRadius: 4, border: "1px solid rgba(255,255,255,0.12)", background: "transparent", color: "#aaa", fontSize: 11, cursor: "pointer", transition: "all 150ms" }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
            >Clear</button>
          </div>

          <label style={{ display: "block", marginBottom: 4 }}>
            <div style={{ fontSize: 12, color: "#999", marginBottom: 4 }}>To</div>
            <input
              type="date"
              value={localEnd}
              min={localStart}
              max={todayStr()}
              onChange={(e) => setLocalEnd(e.target.value)}
              style={{
                width: "100%",
                padding: "6px 8px",
                borderRadius: 6,
                border: "1px solid rgba(255,255,255,0.15)",
                background: "rgba(255,255,255,0.06)",
                color: "#eee",
                fontSize: 13,
                outline: "none",
                colorScheme: "dark",
                boxSizing: "border-box",
              }}
            />
          </label>
          <div style={{ display: "flex", gap: 6, marginBottom: 14 }}>
            <button
              onClick={() => setLocalEnd(todayStr())}
              style={{ flex: 1, padding: "4px 0", borderRadius: 4, border: "1px solid rgba(255,255,255,0.12)", background: "transparent", color: "#aaa", fontSize: 11, cursor: "pointer", transition: "all 150ms" }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
            >Today</button>
            <button
              onClick={() => setLocalEnd("")}
              style={{ flex: 1, padding: "4px 0", borderRadius: 4, border: "1px solid rgba(255,255,255,0.12)", background: "transparent", color: "#aaa", fontSize: 11, cursor: "pointer", transition: "all 150ms" }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
            >Clear</button>
          </div>

          <div style={{ display: "flex", gap: 8 }}>
            <button
              onClick={handleReset}
              style={{
                flex: 1,
                padding: "6px 10px",
                borderRadius: 6,
                border: "1px solid rgba(255,255,255,0.15)",
                background: "transparent",
                color: "#aaa",
                fontSize: 12,
                cursor: "pointer",
                transition: "all 150ms",
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(255,255,255,0.06)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}
            >
              Reset
            </button>
            <button
              onClick={handleApply}
              style={{
                flex: 1,
                padding: "6px 10px",
                borderRadius: 6,
                border: "1px solid #3aa",
                background: "rgba(58,170,170,0.2)",
                color: "#3aa",
                fontSize: 12,
                fontWeight: 600,
                cursor: "pointer",
                transition: "all 150ms",
              }}
              onMouseEnter={(e) => e.currentTarget.style.background = "rgba(58,170,170,0.3)"}
              onMouseLeave={(e) => e.currentTarget.style.background = "rgba(58,170,170,0.2)"}
            >
              Apply
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export { DEFAULT_START, todayStr }
