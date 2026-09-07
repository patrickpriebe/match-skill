import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { BrandLockup } from '@/components/design/domain/BrandMark'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { Button } from '@/components/design/ui/Button'
import { Field, Input } from '@/components/design/ui/Form'
import { Notice } from '@/components/design/ui/Surface'
import { IconGoogle } from '@/components/design/ui/icons'
import type { ApiError } from '@/lib/api/types'

/**
 * Entry point. Local credentials handled by the Java backend, plus Google
 * OAuth 2 as a second route. The error is inline and persistent, and worded
 * so it cannot be used to enumerate accounts.
 */
export function LoginPage() {
  const { login, register, status, user } = useAuth()
  const location = useLocation()
  const [displayName, setDisplayName] = useState('')
  const [timeZone, setTimeZone] = useState(Intl.DateTimeFormat().resolvedOptions().timeZone)
  const navigate = useNavigate()
  const [mode, setMode] = useState<'signin' | 'signup'>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (busy) return
    if (mode === 'signup' && new TextEncoder().encode(password).length > 72) { setError('Password must be at most 72 UTF-8 bytes.'); return }
    setBusy(true)
    setError(null)
    try {
      const user = mode === 'signin'
        ? await login({ email, password })
        : await register({ email, password, displayName: displayName.trim(), timeZone })
      const from = (location.state as { from?: string } | null)?.from
      navigate(user.skillsRegistered ? (from?.startsWith('/') && !from.startsWith('//') ? from : '/home') : '/skills/register', { replace: true })
    } catch (err) {
      setError((err as ApiError).message)
    } finally {
      setBusy(false)
    }
  }

  if (status === 'authenticated' && user) return <Navigate to={user.skillsRegistered ? '/home' : '/skills/register'} replace />

  return (
    <div className="auth">
      <aside className="auth-aside">
        <BrandLockup to="/" />
        <div style={{ margin: 'auto 0', maxWidth: '34ch' }}>
          <p className="eyebrow" style={{ marginBottom: 14 }}>How it works</p>
          <p className="h1" style={{ fontSize: 'clamp(26px, 2.6vw, 34px)', lineHeight: 1.14 }}>
            You teach what you know. Someone teaches you what you don&rsquo;t.
          </p>
          <TradeLedger
            style={{ marginTop: 28 }}
            sides={[
              { direction: 'You list', skill: 'what you can teach' },
              { direction: 'And', skill: 'what you want to learn' },
              { direction: 'We find', skill: 'people whose lists complete yours' },
            ]}
          />
        </div>
        <p className="small dim" style={{ maxWidth: '40ch' }}>
          Meetings happen on your own tool — Zoom, Meet, Teams or Whereby. match·skill arranges
          the exchange, it does not host it.
        </p>
      </aside>

      <main className="auth-main">
        <div className="auth-box">
          <div className="only-m" style={{ marginBottom: 28 }}>
            <BrandLockup to="/" />
          </div>

          <h1 className="h1 is-detail" style={{ marginBottom: 6 }}>
            {mode === 'signin' ? 'Sign in' : 'Create an account'}
          </h1>
          <p className="dim small" style={{ marginBottom: 26 }}>
            {mode === 'signin' ? 'New here? ' : 'Already have an account? '}
            <button
              type="button"
              className="link"
              style={{ background: 'none', border: 0, padding: 0 }}
              onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null) }}
            >
              {mode === 'signin' ? 'Create an account' : 'Sign in'}
            </button>
          </p>

          <form className="stack" onSubmit={submit}>
            {mode === 'signup' && <>
              <Field label="Display name">{(id) => <Input id={id} required maxLength={255} autoComplete="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />}</Field>
              <Field label="Time zone" hint="Use an IANA zone, for example America/Sao_Paulo.">{(id) => <Input id={id} required value={timeZone} onChange={(e) => setTimeZone(e.target.value)} />}</Field>
            </>}
            <Field label="Email">
              {(id) => (
                <Input
                  id={id} type="email" autoComplete="email" value={email} required
                  placeholder="you@example.com" maxLength={254}
                  onChange={(e) => setEmail(e.target.value)}
                />
              )}
            </Field>

            <Field label="Password" hint={mode === 'signup' ? 'At least 8 characters, with uppercase, lowercase, a number and a symbol. Maximum 72 UTF-8 bytes.' : undefined}>
              {(id) => (
                <Input
                  id={id} type="password" value={password} required minLength={mode === 'signup' ? 8 : undefined}
                  autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                  placeholder="••••••••••"
                  onChange={(e) => setPassword(e.target.value)}
                />
              )}
            </Field>

            {error && <Notice tone="stop">{error}</Notice>}

            <Button type="submit" variant="primary" wide loading={busy}>
              {mode === 'signin' ? 'Sign in' : 'Create account'}
            </Button>
          </form>

          <div className="row" style={{ margin: '26px 0', gap: 14 }}>
            <hr className="block-rule soft" style={{ flex: 1, margin: 0 }} />
            <span className="meta">or</span>
            <hr className="block-rule soft" style={{ flex: 1, margin: 0 }} />
          </div>

          <a className="btn btn-secondary btn-wide" href={api.googleAuthUrl()}>
            <IconGoogle /> Continue with Google
          </a>

          <p className="small dim" style={{ marginTop: 26, lineHeight: 1.6 }}>
            Signing in with Google using an address that already has a password links the two
            accounts rather than creating a second one.
          </p>
        </div>
      </main>
    </div>
  )
}
