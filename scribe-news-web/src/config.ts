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
export const SEARCH_URL = `${API_BASE}/articles/search`
export const SITES_URL = `${API_BASE}/sites`
export const PUBLIC_COLLECTIONS_URL = `${API_BASE}/collections/public`
export const PRIVATE_COLLECTIONS_URL = `${API_BASE}/collections`
export const JOURNALIST_COLLECTIONS_URL = `${API_BASE}/collections/journalists`
export const ANNOTATIONS_URL = `${API_BASE}/annotations`
export function annotationByArticleUrl(articleId: number) {
  return `${API_BASE}/annotations/article/${articleId}`
}
export function annotationUrl(annotationId: number) {
  return `${API_BASE}/annotations/${annotationId}`
}

export function publicCollectionArticlesUrl(collectionId: number, page = 0, size = 20) {
  return `${API_BASE}/collections/public/${collectionId}/articles?page=${page}&size=${size}`
}

export function privateCollectionArticlesUrl(collectionId: number, page = 0, size = 20) {
  return `${API_BASE}/collections/${collectionId}/articles?page=${page}&size=${size}`
}

export function collectionArticleUrl(collectionId: number, articleId: number) {
  return `${API_BASE}/collections/${collectionId}/article/${articleId}`
}
