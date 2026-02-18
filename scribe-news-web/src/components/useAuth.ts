import { useState, useEffect, useCallback } from 'react'

const API_URL = 'http://localhost:8085'

interface User {
  googleId: string
  email: string
  name: string
  picture: string
}

export function useAuth() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const checkSession = useCallback(() => {
    fetch(`${API_URL}/api/auth/user`, { credentials: 'include' })
      .then(r => r.ok ? r.json() : null)
      .then(data => setUser(data))
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    // Check session on mount
    checkSession()

    // Re-check when the user returns to the tab (e.g. after being away)
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        checkSession()
      }
    }

    // Also re-check when the window regains focus
    const onFocus = () => checkSession()

    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('focus', onFocus)

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange)
      window.removeEventListener('focus', onFocus)
    }
  }, [checkSession])

  const logout = async () => {
    await fetch(`${API_URL}/api/auth/logout`, {
      method: 'POST',
      credentials: 'include'
    })
    setUser(null)
    window.location.reload()
  }

  return { user, loading, logout }
}