import { useState, useEffect } from "react"

export const MOBILE_BREAKPOINT = 768
/** Height in px of the mobile bottom navigation bar */
export const MOBILE_NAV_H = 56

export function useIsMobile() {
  const [isMobile, setIsMobile] = useState(() => window.innerWidth < MOBILE_BREAKPOINT)
  useEffect(() => {
    const handler = () => setIsMobile(window.innerWidth < MOBILE_BREAKPOINT)
    window.addEventListener("resize", handler)
    return () => window.removeEventListener("resize", handler)
  }, [])
  return isMobile
}
