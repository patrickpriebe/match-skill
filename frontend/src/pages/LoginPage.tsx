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
import { LanguageSwitcher } from '@/components/design/ui/LanguageSwitcher'
import { useT } from '@/i18n/I18nContext'
import type { ApiError } from '@/lib/api/types'

/**
 * Entry point. Local credentials handled by the Java backend, plus Google
 * OAuth 2 as a second route. The error is inline and persistent, and worded
 * so it cannot be used to enumerate accounts.
 */
export function LoginPage() {
  const t = useT()
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
    if (mode === 'signup' && new TextEncoder().encode(password).length > 72) { setError(t('login.passwordTooLong')); return }
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
        <div className="row-between">
          <BrandLockup to="/" />
          <LanguageSwitcher />
        </div>
        <div style={{ margin: 'auto 0', maxWidth: '34ch' }}>
          <p className="eyebrow" style={{ marginBottom: 14 }}>{t('login.eyebrow')}</p>
          <p className="h1" style={{ fontSize: 'clamp(26px, 2.6vw, 34px)', lineHeight: 1.14 }}>
            {t('login.headline')}
          </p>
          <TradeLedger
            style={{ marginTop: 28 }}
            sides={[
              { direction: t('login.ledgerYouList'), skill: t('login.ledgerYouListSkill') },
              { direction: t('login.ledgerAnd'), skill: t('login.ledgerAndSkill') },
              { direction: t('login.ledgerWeFind'), skill: t('login.ledgerWeFindSkill') },
            ]}
          />
        </div>
        <p className="small dim" style={{ maxWidth: '40ch' }}>
          {t('login.meetings')}
        </p>
      </aside>

      <main className="auth-main">
        <div className="auth-box">
          <div className="only-m row-between" style={{ marginBottom: 28 }}>
            <BrandLockup to="/" />
            <LanguageSwitcher />
          </div>

          <h1 className="h1 is-detail" style={{ marginBottom: 6 }}>
            {mode === 'signin' ? t('login.signIn') : t('login.createAccount')}
          </h1>
          <p className="dim small" style={{ marginBottom: 26 }}>
            {mode === 'signin' ? t('login.newHere') : t('login.alreadyHaveAccount')}
            <button
              type="button"
              className="link"
              style={{ background: 'none', border: 0, padding: 0 }}
              onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null) }}
            >
              {mode === 'signin' ? t('login.createAccount') : t('login.signIn')}
            </button>
          </p>

          <form className="stack" onSubmit={submit}>
            {mode === 'signup' && <>
              <Field label={t('login.displayName')}>{(id) => <Input id={id} required maxLength={255} autoComplete="name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />}</Field>
              <Field label={t('login.timeZone')} hint={t('login.timeZoneHint')}>{(id) => <Input id={id} required value={timeZone} onChange={(e) => setTimeZone(e.target.value)} />}</Field>
            </>}
            <Field label={t('login.email')}>
              {(id) => (
                <Input
                  id={id} type="email" autoComplete="email" value={email} required
                  placeholder="you@example.com" maxLength={254}
                  onChange={(e) => setEmail(e.target.value)}
                />
              )}
            </Field>

            <Field label={t('login.password')} hint={mode === 'signup' ? t('login.passwordHintSignup') : undefined}>
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
              {mode === 'signin' ? t('login.signIn') : t('login.createAccountButton')}
            </Button>
          </form>

          <div className="row" style={{ margin: '26px 0', gap: 14 }}>
            <hr className="block-rule soft" style={{ flex: 1, margin: 0 }} />
            <span className="meta">{t('login.or')}</span>
            <hr className="block-rule soft" style={{ flex: 1, margin: 0 }} />
          </div>

          <a className="btn btn-secondary btn-wide" href={api.googleAuthUrl()}>
            <IconGoogle /> {t('login.continueWithGoogle')}
          </a>

          <p className="small dim" style={{ marginTop: 26, lineHeight: 1.6 }}>
            {t('login.googleLinkNote')}
          </p>
        </div>
      </main>
    </div>
  )
}
