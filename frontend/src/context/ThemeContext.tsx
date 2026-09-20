import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

/**
 * Three states, not two: light, dark, and *system*.
 *
 * A complete dark palette already existed in globals.css and nothing in the
 * application ever added the class that switches it on — a whole theme
 * written, shipped in every bundle, and unreachable. This is the switch.
 *
 * The third state matters more than it looks. With only a light/dark toggle,
 * whoever picks once is stuck with that choice for good, including at
 * midnight, and almost nobody goes back to fix it.
 */

export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'match-skill.theme'

function stored(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark' || saved === 'system') return saved
  } catch {
    // Private mode, or site data blocked. Fall back to following the system.
  }
  return 'system'
}

interface ThemeValue {
  preference: ThemePreference
  /** What is actually on screen once 'system' has been resolved. */
  resolved: 'light' | 'dark'
  setPreference: (next: ThemePreference) => void
}

const ThemeCtx = createContext<ThemeValue | null>(null)

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(stored)
  const [systemDark, setSystemDark] = useState(
    () => typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches,
  )

  useEffect(() => {
    if (typeof matchMedia !== 'function') return
    const query = matchMedia('(prefers-color-scheme: dark)')
    const listen = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    query.addEventListener('change', listen)
    return () => query.removeEventListener('change', listen)
  }, [])

  const resolved: 'light' | 'dark' =
    preference === 'system' ? (systemDark ? 'dark' : 'light') : preference

  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
    // Native controls -- scrollbars, form widgets, the address bar on mobile --
    // follow this and nothing else.
    document.documentElement.style.colorScheme = resolved
  }, [resolved])

  const setPreference = (next: ThemePreference) => {
    setPreferenceState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Ignore: the theme just will not survive a reload.
    }
  }

  const value = useMemo(() => ({ preference, resolved, setPreference }), [preference, resolved])
  return <ThemeCtx.Provider value={value}>{children}</ThemeCtx.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme(): ThemeValue {
  const context = useContext(ThemeCtx)
  if (!context) throw new Error('useTheme must be used within a ThemeProvider')
  return context
}
