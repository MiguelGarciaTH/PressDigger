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
console.log("[config] API_BASE:", API_BASE)
// Strip /api suffix to get the server root (for Spring Security OAuth2 endpoints)
const SERVER_BASE = API_BASE.replace(/\/api$/, "")
export const GOOGLE_AUTH_URL = `${SERVER_BASE}/oauth2/authorization/google`
export const AUTH_USER_URL = `${API_BASE}/auth/user`
export const AUTH_LOGOUT_URL = `${API_BASE}/auth/logout`
export const AUTH_GOOGLE_CALLBACK_URL = `${API_BASE}/auth/google/callback`
export const SEARCH_URL = `${API_BASE}/articles/search`
export const NARRATIVE_URL = `${API_BASE}/articles/narrative`
export const NARRATIVE_USAGE_URL = `${API_BASE}/articles/narrative/usage`
export const SITES_URL = `${API_BASE}/sites`
export function articleUrl(articleId: number) {
  return `${API_BASE}/articles/${articleId}`
}
export const PUBLIC_COLLECTIONS_URL = `${API_BASE}/collections/public`
export const PRIVATE_COLLECTIONS_URL = `${API_BASE}/collections`
export const JOURNALIST_COLLECTIONS_URL = `${API_BASE}/collections/journalists`
export const AUTHORS_URL = `${API_BASE}/authors`
export const ANNOTATIONS_URL = `${API_BASE}/annotations`
export function annotationByArticleUrl(articleId: number) {
  return `${API_BASE}/annotations/article/${articleId}`
}
export function annotationUrl(annotationId: number) {
  return `${API_BASE}/annotations/${annotationId}`
}

export function authorArticlesUrl(authorId: number, page = 0, size = 20) {
  return `${API_BASE}/authors/${authorId}/articles?page=${page}&size=${size}`
}

export function authorArticleCountUrl(authorId: number) {
  return `${API_BASE}/authors/${authorId}/articles/count`
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
