import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api'
import type { Match, Skill } from '@/lib/api'
import { useAsync } from '@/hooks/useAsync'
import { Dialog, PickOption } from '../ui/Dialog'
import { Button, LinkButton } from '../ui/Button'
import { Notice } from '../ui/Surface'
import { ErrorState } from '../feedback/ErrorState'
import { LoadingState } from '../feedback/LoadingState'
import { useT } from '@/i18n/I18nContext'

export function RequestExchangeDialog({ match, skills, onClose }: {
  match: Match; skills: Skill[]; onClose: () => void
}) {
  const t = useT()
  const navigate = useNavigate()
  const existing = useAsync(() => api.findOpenExchange(match.user.id), [match.user.id])
  const approved = skills.filter((s) => s.status === 'APPROVED')
  const [selected, setSelected] = useState(approved.length === 1 ? approved[0].id : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function submit() {
    if (!selected || existing.status !== 'ready' || existing.data || busy) return
    setBusy(true)
    setError(null)
    try {
      const ex = await api.createExchange({ receiverId: match.user.id, skillFromReceiver: selected })
      navigate('/exchanges/' + ex.id)
    } catch (err) { setError(err instanceof Error ? err.message : t('requestExchangeDialog.couldNotSend')) }
    finally { setBusy(false) }
  }
  return <Dialog title={t('requestExchangeDialog.title', { name: match.user.displayName })} onClose={() => { if (!busy) onClose() }}
    footer={<><Button disabled={busy} onClick={onClose}>{t('requestExchangeDialog.back')}</Button>
      <Button variant="primary" loading={busy} disabled={!selected || existing.status !== 'ready' || Boolean(existing.data)} onClick={() => void submit()}>{t('requestExchangeDialog.sendRequest')}</Button></>}>
    {existing.status === 'loading' && <LoadingState label={t('requestExchangeDialog.checking')} />}
    {existing.status === 'error' && <ErrorState error={existing.error} onRetry={existing.reload} />}
    {existing.status === 'ready' && (existing.data
      ? <Notice>{t('requestExchangeDialog.alreadyOpen')} <LinkButton to={'/exchanges/' + existing.data.id}>{t('requestExchangeDialog.openExchange')}</LinkButton></Notice>
      : <><p className="small dim">{t('requestExchangeDialog.chooseSkill')}</p>
        <div role="radiogroup" aria-label={t('invitations.whatYouWillLearn')}>
          {approved.map((skill) => <PickOption key={skill.id} selected={skill.id === selected} onSelect={() => setSelected(skill.id)} title={skill.name} />)}
        </div>{!approved.length && <Notice>{t('requestExchangeDialog.noApprovedSkill')}</Notice>}</>)}
    {error && <Notice tone="stop">{error}</Notice>}
  </Dialog>
}
