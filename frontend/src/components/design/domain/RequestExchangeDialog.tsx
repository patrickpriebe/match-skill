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

export function RequestExchangeDialog({ match, skills, onClose }: {
  match: Match; skills: Skill[]; onClose: () => void
}) {
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
    } catch (err) { setError(err instanceof Error ? err.message : 'The request could not be sent.') }
    finally { setBusy(false) }
  }
  return <Dialog title={'Learn from ' + match.user.displayName} onClose={() => { if (!busy) onClose() }}
    footer={<><Button disabled={busy} onClick={onClose}>Back</Button>
      <Button variant="primary" loading={busy} disabled={!selected || existing.status !== 'ready' || Boolean(existing.data)} onClick={() => void submit()}>Send request</Button></>}>
    {existing.status === 'loading' && <LoadingState label="Checking your exchanges" />}
    {existing.status === 'error' && <ErrorState error={existing.error} onRetry={existing.reload} />}
    {existing.status === 'ready' && (existing.data
      ? <Notice>You already have an open exchange with this person. <LinkButton to={'/exchanges/' + existing.data.id}>Open exchange</LinkButton></Notice>
      : <><p className="small dim">Choose the skill you want to learn. They will choose what to learn from you when accepting.</p>
        <div role="radiogroup" aria-label="Skill to learn">
          {approved.map((skill) => <PickOption key={skill.id} selected={skill.id === selected} onSelect={() => setSelected(skill.id)} title={skill.name} />)}
        </div>{!approved.length && <Notice>No approved matching skill is currently available.</Notice>}</>)}
    {error && <Notice tone="stop">{error}</Notice>}
  </Dialog>
}
