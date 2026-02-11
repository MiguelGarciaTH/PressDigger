function getApiBase() {
  // try Vite (import.meta may not be readable in some environments)
  let vite: string | undefined
  try {
    vite = (import.meta as any)?.env?.VITE_API_URL
  } catch {
    vite = undefined
  }
  if (vite) return vite

  // try CRA / Node env
  if (typeof process !== "undefined" && (process.env as any)?.REACT_APP_API_URL) {
    return (process.env as any).REACT_APP_API_URL
  }

  // fallback
  return "http://localhost:8085"
}

export const API_BASE = getApiBase().replace(/\/$/, "")
export const SEARCH_URL = `${API_BASE}/scribe-ref/search`
