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
import type { ApiError, Skill } from '@/lib/api/types'

/**
 * Runs once. Two lists decide every match the user will ever see, so the
 * screen says that plainly instead of presenting a neutral form.
 *
 * PUT /me/skills replaces both lists and completes registration, so the draft
 * is held here and saved atomically.
 */
export function SkillRegistrationPage() {
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
        <div className="row-between"><BrandLockup to="/" /><Button variant="quiet" onClick={logout}>Sign out</Button></div>
        <p className="eyebrow" style={{ margin: '22px 0 10px' }}>{user?.skillsRegistered ? 'Your skills' : 'Step 1 of 1 - get started'}</p>
        <h1 className="h1">Two lists decide every match you will ever see.</h1>
        <p className="lead" style={{ marginTop: 10 }}>
          A match exists when someone offers a skill you want. When your lists complete each
          other in both directions, it becomes a complete trade — and those come first everywhere.
        </p>
      </header>

      <div className="reg-cols">
        <Panel title="What you can teach">
          <p className="small dim" style={{ marginTop: -8, marginBottom: 14 }}>
            Others will see these and ask to learn them.
          </p>
          <SkillAutocomplete
            label="Add a skill"
            value={offeredValue}
            onChange={setOffered}
            onSearch={search}
            onSuggest={suggest}
            disabled={disabled}
            hint={<>Skills come from a shared vocabulary, so <span className="num">JS</span> and <span className="num">JavaScript</span> never split into two.</>}
          />
        </Panel>

        <Panel title="What you want to learn">
          <p className="small dim" style={{ marginTop: -8, marginBottom: 14 }}>
            This is what the match engine searches for.
          </p>
          <SkillAutocomplete
            label="Add a skill"
            value={wantedValue}
            onChange={setWanted}
            onSearch={search}
            onSuggest={suggest}
            disabled={disabled}
            hint="A suggested term waits for approval before it participates in matching, so it stays marked on your list until then."
          />
        </Panel>
      </div>

      <Notice className="reg-note">
        The same skill may sit on both lists. Teaching the basics while wanting the advanced
        level is a normal case, not a mistake.
      </Notice>

      {error && <Notice tone="stop" className="reg-note">{error}</Notice>}
      {hasPending && <Notice className="reg-note">Pending suggestions must be approved before they can be saved to your profile. Remove them from these lists to save your approved skills; your submitted suggestions remain in review.</Notice>}

      <div className="reg-foot row-between">
        <span className="meta">{offeredValue.length} offered · {wantedValue.length} wanted</span>
        <Button
          variant="primary"
          loading={saving}
          disabled={disabled || hasPending || offeredValue.length === 0 || wantedValue.length === 0}
          onClick={() => void save()}
        >
          Save and find matches
        </Button>
      </div>
    </div>
  )
}
