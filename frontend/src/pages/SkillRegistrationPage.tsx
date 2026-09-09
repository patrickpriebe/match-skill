import { useAuth } from '@/context/AuthContext'
import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAsync } from '../hooks/useAsync'
import { BrandLockup } from '@/components/design/domain/BrandMark'
import { SkillAutocomplete } from '@/components/design/domain/SkillAutocomplete'
import { Panel, Notice } from '@/components/design/ui/Surface'
import { Button } from '@/components/design/ui/Button'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LanguageSwitcher } from '@/components/design/ui/LanguageSwitcher'
import { useT } from '@/i18n/I18nContext'
import type { ApiError, Skill } from '@/lib/api/types'

/**
 * Runs once. Two lists decide every match the user will ever see, so the
 * screen says that plainly instead of presenting a neutral form.
 *
 * PUT /me/skills replaces both lists and completes registration, so the draft
 * is held here and saved atomically.
 */
export function SkillRegistrationPage() {
  const t = useT()
  const navigate = useNavigate()
  const { refreshUser, user, logout } = useAuth()
  const existing = useAsync(() => api.getMySkills(), [])
  const [offered, setOffered] = useState<Skill[] | null>(null)
  const [wanted, setWanted] = useState<Skill[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const search = useCallback((q: string) => api.searchSkills(q), [])
  const suggest = useCallback((name: string) => api.suggestSkill({ name }), [])

  if (existing.status === 'error') {
    return <ErrorState error={existing.error} onRetry={existing.reload} />
  }

  const offeredValue = offered ?? existing.data?.offered ?? []
  const wantedValue = wanted ?? existing.data?.wanted ?? []
  const hasPending = [...offeredValue, ...wantedValue].some((skill) => skill.status !== 'APPROVED')
  const disabled = existing.status !== 'ready' || saving

  async function save() {
    if (disabled || hasPending) return
    setSaving(true)
    setError(null)
    try {
      await api.replaceMySkills({
        offered: offeredValue.map((s) => s.id),
        wanted: wantedValue.map((s) => s.id),
      })
      await refreshUser()
      navigate('/home')
    } catch (err) {
      setError((err as ApiError).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="reg">
      <header style={{ marginBottom: 'var(--gap-lg)' }}>
        <div className="row-between">
          <BrandLockup to="/" />
          <div className="row" style={{ gap: 10 }}>
            <LanguageSwitcher />
            <Button variant="quiet" onClick={logout}>{t('skillRegistration.signOut')}</Button>
          </div>
        </div>
        <p className="eyebrow" style={{ margin: '22px 0 10px' }}>{user?.skillsRegistered ? t('skillRegistration.stepEyebrowReturning') : t('skillRegistration.stepEyebrowFirst')}</p>
        <h1 className="h1">{t('skillRegistration.headline')}</h1>
        <p className="lead" style={{ marginTop: 10 }}>
          {t('skillRegistration.lead')}
        </p>
      </header>

      <div className="reg-cols">
        <Panel title={t('skillRegistration.canTeachTitle')}>
          <p className="small dim" style={{ marginTop: -8, marginBottom: 14 }}>
            {t('skillRegistration.canTeachNote')}
          </p>
          <SkillAutocomplete
            label={t('skillRegistration.addSkill')}
            value={offeredValue}
            onChange={setOffered}
            onSearch={search}
            onSuggest={suggest}
            disabled={disabled}
            hint={<>{t('skillRegistration.canTeachHintPrefix')}<span className="num">JS</span>{t('skillRegistration.canTeachHintAnd')}<span className="num">JavaScript</span>{t('skillRegistration.canTeachHintSuffix')}</>}
          />
        </Panel>

        <Panel title={t('skillRegistration.wantToLearnTitle')}>
          <p className="small dim" style={{ marginTop: -8, marginBottom: 14 }}>
            {t('skillRegistration.wantToLearnNote')}
          </p>
          <SkillAutocomplete
            label={t('skillRegistration.addSkill')}
            value={wantedValue}
            onChange={setWanted}
            onSearch={search}
            onSuggest={suggest}
            disabled={disabled}
            hint={t('skillRegistration.wantToLearnHint')}
          />
        </Panel>
      </div>

      <Notice className="reg-note">
        {t('skillRegistration.sameSkillNote')}
      </Notice>

      {error && <Notice tone="stop" className="reg-note">{error}</Notice>}
      {hasPending && <Notice className="reg-note">{t('skillRegistration.pendingNote')}</Notice>}

      <div className="reg-foot row-between">
        <span className="meta">{t('skillRegistration.counts', { offered: offeredValue.length, wanted: wantedValue.length })}</span>
        <Button
          variant="primary"
          loading={saving}
          disabled={disabled || hasPending || offeredValue.length === 0 || wantedValue.length === 0}
          onClick={() => void save()}
        >
          {t('skillRegistration.save')}
        </Button>
      </div>
    </div>
  )
}
