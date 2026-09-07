import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiError, setApiAuthToken, type LoginPayload, type RegisterPayload, type User } from '@/lib/api'

const TOKEN_KEY = 'match-skill-token'
interface AuthContextValue {
  user: User | null
  status: 'loading' | 'authenticated' | 'unauthenticated'
  error: string | null
  login: (payload: LoginPayload) => Promise<User>
  register: (payload: RegisterPayload) => Promise<User>
  acceptToken: (token: string) => Promise<User>
  logout: () => void
  refreshUser: () => Promise<void>
  retry: () => void
  googleLoginUrl: string
}
const AuthContext = createContext<AuthContextValue | null>(null)
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthContextValue['status']>('loading')
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    setApiAuthToken(null)
    setUser(null)
    setError(null)
    setStatus('unauthenticated')
  }, [])
  const establish = useCallback((token: string, current: User) => {
    localStorage.setItem(TOKEN_KEY, token)
    setApiAuthToken(token)
    setUser(current)
    setError(null)
    setStatus('authenticated')
    return current
  }, [])
  useEffect(() => {
    // The callback owns token restoration on this route; do not race its request.
    if (window.location.pathname === '/auth/callback') return
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) { setStatus('unauthenticated'); return }
    let cancelled = false
    setApiAuthToken(token)
    setError(null)
    api.me().then((me) => {
      if (!cancelled) establish(token, me)
    }).catch((err: unknown) => {
      if (cancelled) return
      if (err instanceof ApiError && err.status === 401) logout()
      else setError('We could not restore your session. Try again when the connection returns.')
    })
    return () => { cancelled = true }
  }, [attempt, establish, logout])
  useEffect(() => {
    const expired = () => logout()
    window.addEventListener('match-skill:unauthorized', expired)
    return () => window.removeEventListener('match-skill:unauthorized', expired)
  }, [logout])
  const acceptToken = useCallback(async (token: string) => {
    setApiAuthToken(token)
    try { return establish(token, await api.me()) }
    catch (err) { logout(); throw err }
  }, [establish, logout])
  const login = useCallback(async (payload: LoginPayload) => {
    const res = await api.login(payload)
    return establish(res.token, res.user)
  }, [establish])
  const register = useCallback(async (payload: RegisterPayload) => {
    const res = await api.register(payload)
    return establish(res.token, res.user)
  }, [establish])
  const refreshUser = useCallback(async () => {
    setUser(await api.me())
    setStatus('authenticated')
  }, [])
  const retry = useCallback(() => setAttempt((n) => n + 1), [])
  const value = useMemo(() => ({ user, status, error, login, register, acceptToken, logout, refreshUser, retry,
    googleLoginUrl: api.googleLoginUrl() }), [user, status, error, login, register, acceptToken, logout, refreshUser, retry])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
