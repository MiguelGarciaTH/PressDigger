import { useEffect, useRef, useState } from "react"

const CLIENT_ID = "807515834617-frk9phljibrjdknohfuau39o1j6ovgjt.apps.googleusercontent.com"

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
      const res = await fetch('http://localhost:8085/api/auth/google/callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ credential: response.credential })
      })

      if (res.ok) {
        const user = await res.json()
        console.log('Logged in:', user)
        window.location.reload()
      }
    } catch (error) {
      console.error('Login failed:', error)
    }
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
      {/* GSI-rendered button goes here */}
      <div ref={buttonRef} />

      {/* Fallback if GSI never loads */}
      {!gsiReady && (
        <button
          onClick={() => {
            window.location.href = "http://localhost:8085/oauth2/authorization/google"
          }}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            padding: "8px 16px",
            backgroundColor: "#131314",
            color: "#e3e3e3",
            border: "1px solid #747775",
            borderRadius: 20,
            cursor: "pointer",
            fontFamily: "'Roboto', sans-serif",
            fontSize: 14,
            fontWeight: 500,
          }}
        >
          <img
            src="https://developers.google.com/identity/images/g-logo.png"
            alt="Google"
            style={{ width: 18, height: 18 }}
          />
          Sign in with Google
        </button>
      )}
    </div>
  )
}