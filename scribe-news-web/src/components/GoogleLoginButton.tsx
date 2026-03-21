import { useEffect, useRef, useState } from "react"
import { AUTH_GOOGLE_CALLBACK_URL, GOOGLE_AUTH_URL } from '../config'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: any) => void
          renderButton: (element: HTMLElement, config: any) => void
        }
      }
    }
  }
}

export default function GoogleLoginButton() {
  const buttonRef = useRef<HTMLDivElement>(null)
  const rendered = useRef(false)
  const [gsiReady, setGsiReady] = useState(false)

  const handleCredentialResponse = async (response: any) => {
    try {
      const res = await fetch(AUTH_GOOGLE_CALLBACK_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ credential: response.credential })
      })

      if (res.ok) {
        await res.json()
        window.location.reload()
      }
    } catch (error) {
      console.error('Login failed:', error)
    }
  }

  // Fallback: redirect to Spring Security OAuth2 login
  const handleFallbackLogin = () => {
    window.location.href = GOOGLE_AUTH_URL
  }

  useEffect(() => {
    if (rendered.current) return

    const tryRender = () => {
      if (!window.google?.accounts?.id || !buttonRef.current) return false

      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: handleCredentialResponse,
      })

      window.google.accounts.id.renderButton(buttonRef.current, {
        type: "standard",
        theme: "filled_black",
        size: "large",
        shape: "pill",
        text: "signin_with",
        logo_alignment: "left"
      })

      rendered.current = true
      setGsiReady(true)
      return true
    }

    if (tryRender()) return

    let attempts = 0
    const interval = setInterval(() => {
      attempts++
      if (tryRender() || attempts > 25) {
        clearInterval(interval)
        // If GSI failed to load after all attempts, show fallback
        if (!rendered.current) {
          setGsiReady(false)
        }
      }
    }, 200)

    return () => clearInterval(interval)
  }, [])

  return (
    <div
      style={{
        position: "fixed",
        top: 16,
        right: 20,
        zIndex: 9998,
      }}
    >
      {/* GSI-rendered button */}
      <div ref={buttonRef} />

      {/* Fallback button if GSI doesn't load */}
      {!gsiReady && (
        <button
          onClick={handleFallbackLogin}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            padding: "10px 20px",
            backgroundColor: "#131314",
            color: "#e3e3e3",
            border: "1px solid #5f6368",
            borderRadius: 20,
            cursor: "pointer",
            fontSize: 14,
            fontFamily: "Google Sans, Roboto, sans-serif",
          }}
        >
          <svg width="18" height="18" viewBox="0 0 18 18">
            <path fill="#4285F4" d="M16.51 8H8.98v3h4.3c-.18 1-.74 1.48-1.6 2.04v2.01h2.6a7.8 7.8 0 0 0 2.38-5.88c0-.57-.05-.66-.15-1.18z"/>
            <path fill="#34A853" d="M8.98 17c2.16 0 3.97-.72 5.3-1.94l-2.6-2a4.8 4.8 0 0 1-7.18-2.54H1.83v2.07A8 8 0 0 0 8.98 17z"/>
            <path fill="#FBBC05" d="M4.5 10.52a4.8 4.8 0 0 1 0-3.04V5.41H1.83a8 8 0 0 0 0 7.18l2.67-2.07z"/>
            <path fill="#EA4335" d="M8.98 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.59a8 8 0 0 0-12.17 2.07l2.67 2.07A4.8 4.8 0 0 1 8.98 3.58z"/>
          </svg>
          Sign in with Google
        </button>
      )}
    </div>
  )
}
