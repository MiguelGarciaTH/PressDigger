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

// For <img src>, use a relative path so the browser resolves it against the
// current origin.  This avoids CSP `img-src 'self'` blocking images when the
// page is served from press-digger.com (non-www) while API_BASE contains
// www.press-digger.com.  fetch() calls are unaffected because connect-src
// already lists the explicit www origin.
const IMAGE_BASE = (() => {
  try {
    const path = new URL(API_BASE).pathname.replace(/\/$/, "")
    return path || ""
  } catch {
    return API_BASE          // dev fallback (plain http://localhost:…)
  }
})()

export function getImageUrl(filePath?: string, size: 'small' | 'original' = 'original'): string {
  if (!filePath) return ""
  if (/^https?:\/\//.test(filePath)) return filePath
  const match = filePath.match(/images\/(small|original)\/([^/]+)$/)
  if (match) {
    const [, folder, filename] = match
    return `${IMAGE_BASE}/images/${folder}/${filename}`
  }
  const filename = filePath.split('/').pop()
  return filename ? `${IMAGE_BASE}/images/${size}/${filename}` : ""
}

export function collectionArticleUrl(collectionId: number, articleId: number) {
  return `${API_BASE}/collections/${collectionId}/article/${articleId}`
}

export const ARTICLES_COUNT_URL = `${API_BASE}/articles/count`
export const PERSONS_COUNT_URL = `${API_BASE}/persons/count`
export const AUTHORS_COUNT_URL = `${API_BASE}/authors/count`
export const PUBLIC_COLLECTIONS_COUNT_URL = `${API_BASE}/collections/count-public`
export const PRIVATE_COLLECTIONS_COUNT_URL = `${API_BASE}/collections/count-private`
export const PERSONS_FIND_URL = `${API_BASE}/persons/find`
