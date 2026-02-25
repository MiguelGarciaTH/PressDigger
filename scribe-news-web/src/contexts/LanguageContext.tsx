import { createContext, useContext, useState, ReactNode } from "react"
import { translations } from "../i18n/translations"
import type { Lang, Translations } from "../i18n/translations"

interface LanguageContextValue {
  lang: Lang
  setLang: (lang: Lang) => void
  t: Translations
}

const LanguageContext = createContext<LanguageContextValue | null>(null)

function getInitialLang(): Lang {
  try {
    const stored = localStorage.getItem("lang")
    if (stored === "en" || stored === "pt") return stored
  } catch {
    // ignore
  }
  return "en"
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(getInitialLang)

  const setLang = (next: Lang) => {
    setLangState(next)
    try { localStorage.setItem("lang", next) } catch { /* ignore */ }
  }

  return (
    <LanguageContext.Provider value={{ lang, setLang, t: translations[lang] as Translations }}>
      {children}
    </LanguageContext.Provider>
  )
}

export function useLang() {
  const ctx = useContext(LanguageContext)
  if (!ctx) throw new Error("useLang must be used inside LanguageProvider")
  return ctx
}
