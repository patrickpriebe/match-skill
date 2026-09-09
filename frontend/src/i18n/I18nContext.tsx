import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { en, type Dict } from './en'
import { pt } from './pt'

export type Locale = 'en' | 'pt'

const DICTS: Record<Locale, Dict> = { en, pt }
const STORAGE_KEY = 'match-skill.locale'

function resolve(dict: Dict, path: string): unknown {
  return path.split('.').reduce<unknown>(
    (node, key) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[key] : undefined),
    dict,
  )
}

function interpolate(template: string, vars?: Record<string, string | number>): string {
  if (!vars) return template
  return template.replace(/\{(\w+)\}/g, (match, key: string) => (key in vars ? String(vars[key]) : match))
}

function detectDefault(): Locale {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'en' || saved === 'pt') return saved
  } catch {
    // localStorage unavailable (private mode, etc). Fall through to detection.
  }
  return navigator.language.toLowerCase().startsWith('pt') ? 'pt' : 'en'
}

interface I18nValue {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: string, vars?: Record<string, string | number>) => string
}

const I18nCtx = createContext<I18nValue | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(detectDefault)

  useEffect(() => {
    document.documentElement.lang = locale === 'pt' ? 'pt-BR' : 'en'
  }, [locale])

  const setLocale = (next: Locale) => {
    setLocaleState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Ignore: language just will not persist across reloads.
    }
  }

  const t = useMemo(() => {
    return (key: string, vars?: Record<string, string | number>) => {
      const value = resolve(DICTS[locale], key) ?? resolve(en, key)
      if (typeof value !== 'string') return key
      return interpolate(value, vars)
    }
  }, [locale])

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, t])

  return <I18nCtx.Provider value={value}>{children}</I18nCtx.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useI18n(): I18nValue {
  const ctx = useContext(I18nCtx)
  if (!ctx) throw new Error('useI18n must be used within an I18nProvider')
  return ctx
}

// eslint-disable-next-line react-refresh/only-export-components
export function useT() {
  return useI18n().t
}
