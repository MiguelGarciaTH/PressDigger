import { useState, useEffect, useCallback } from 'react'
import { AUTH_USER_URL, AUTH_LOGOUT_URL } from '../config'

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
    fetch(AUTH_USER_URL, { credentials: 'include' })
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
    await fetch(AUTH_LOGOUT_URL, {
      method: 'POST',
      credentials: 'include'
    })
    setUser(null)
    window.location.reload()
  }

  return { user, loading, logout }
}