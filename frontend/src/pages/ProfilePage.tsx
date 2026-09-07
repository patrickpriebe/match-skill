import { useState } from 'react'
import type { Match } from '@/lib/api'
import { RequestExchangeDialog } from '@/components/design/domain/RequestExchangeDialog'
import { LinkButton } from '@/components/design/ui/Button'
import { useParams } from 'react-router-dom'
import { api } from '@/lib/api'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { Section } from '@/components/design/layout/Section'
import { ProfileHeader } from '@/components/design/domain/ProfileHeader'
import { SkillBadgeList } from '@/components/design/domain/SkillBadge'
import { TradeLedger } from '@/components/design/domain/TradeLedger'
import { FeedbackCard } from '@/components/design/domain/FeedbackCard'
import { MatchStrengthBadge } from '@/components/design/domain/ExchangeStatusBadge'
import { AvailabilityPreview } from '@/components/design/domain/AvailabilitySelector'
import { summarise } from '@/components/design/domain/availability-helpers'
import { Card, Panel, Notice } from '@/components/design/ui/Surface'
import { Button } from '@/components/design/ui/Button'
import { EmptyState } from '@/components/design/feedback/EmptyState'
import { ErrorState } from '@/components/design/feedback/ErrorState'
import { LoadingState, SkeletonRows } from '@/components/design/feedback/LoadingState'

/**
 * Where the trust decision happens. Density of proof beats aesthetics: the
 * reciprocal skills are marked, reputation carries its count, and the weekly
 * windows are shown because scheduling across zones is the normal case.
 */
export function ProfilePage() {
  const [requesting, setRequesting] = useState(false)
  const { id = '' } = useParams()
  const { user } = useAuth()
  const isMe = id === 'me' || id === user?.id

  const profile = useAsync(() => api.getProfile(isMe ? (user?.id ?? '') : id), [id, isMe, user?.id])
  const feedback = useAsync(() => api.getUserFeedback(isMe ? (user?.id ?? '') : id), [id, isMe, user?.id])
  const mine = useAsync(() => api.getMySkills(), [])

  if (mine.status === 'error') return <ErrorState error={mine.error} onRetry={mine.reload} />

  if (profile.status === 'error') {
    return (
      <ErrorState
        error={profile.error}
        onRetry={profile.reload}
        context={profile.error.status === 404 ? 'That profile is no longer available.' : undefined}
      />
    )
  }
  if (profile.status === 'loading' || mine.status !== 'ready') {
    return <LoadingState label="Loading profile"><SkeletonRows count={4} /></LoadingState>
  }

  const p = profile.data
  const first = p.user.displayName.split(' ')[0]

  const myWanted = new Set(mine.data.wanted.filter((s) => s.status === 'APPROVED').map((s) => s.id))
  const myOffered = new Set(mine.data.offered.filter((s) => s.status === 'APPROVED').map((s) => s.id))
  const youLearn = p.offeredSkills.find((s) => s.status === 'APPROVED' && myWanted.has(s.id)) ?? null
  const theyLearn = p.wantedSkills.find((s) => s.status === 'APPROVED' && myOffered.has(s.id)) ?? null
  const strength = youLearn && theyLearn ? 'MUTUAL' : 'PARTIAL'

  const match: Match = { user: { id: p.user.id, displayName: p.user.displayName, bio: p.user.bio, offeredSkills: p.offeredSkills, wantedSkills: p.wantedSkills, reputation: p.reputation }, strength, matchingSkills: [] }
  return (
    <>
      {requesting && <RequestExchangeDialog match={match} skills={p.offeredSkills.filter((s) => myWanted.has(s.id))} onClose={() => setRequesting(false)} />}
      {isMe && <div className="row wrap" style={{ gap: 8, marginBottom: 20 }}><LinkButton to="/skills/register">Edit skills</LinkButton><LinkButton to="/availability">Edit availability</LinkButton></div>}
      <ProfileHeader
        name={p.user.displayName}
        bio={p.user.bio}
        timeZone={p.timezone}
        reputation={p.reputation}
      />

      <div className="prof-grid">
        <div>
          {!isMe && youLearn && (
            <Section title={'Your trade with ' + first} emphasis>
              <Card accent>
                <div className="row-between" style={{ marginBottom: 10 }}>
                  <MatchStrengthBadge strength={strength} />
                  <span className="meta">
                    {strength === 'MUTUAL' ? 'both sides already declared' : 'one direction covered'}
                  </span>
                </div>
                <TradeLedger
                  variant={strength === 'MUTUAL' ? 'full' : 'half'}
                  sides={[
                    { direction: 'You learn', skill: youLearn.name, from: 'from ' + first },
                    theyLearn
                      ? { direction: 'They learn', skill: theyLearn.name, from: 'from you' }
                      : { direction: 'They learn', open: 'they pick when they accept' },
                  ]}
                />
                <div className="row" style={{ marginTop: 16, gap: 8 }}>
                  <Button variant={strength === 'MUTUAL' ? 'primary' : 'secondary'} size="sm" onClick={() => setRequesting(true)}>Send request</Button>
                  <span className="small dim">
                    You pick which skill you want to learn on the next step.
                  </span>
                </div>
              </Card>
            </Section>
          )}

          <Section title="Skills">
            <div className="stack">
              <div>
                <p className="eyebrow" style={{ marginBottom: 8 }}>Teaches</p>
                <SkillBadgeList skills={p.offeredSkills} recipIds={isMe ? undefined : myWanted} />
              </div>
              <div>
                <p className="eyebrow" style={{ marginBottom: 8 }}>Wants to learn</p>
                <SkillBadgeList skills={p.wantedSkills} tone="wanted" recipIds={isMe ? undefined : myOffered} />
              </div>
              {!isMe && (
                <p className="small dim">Highlighted skills are the ones that connect to your lists.</p>
              )}
            </div>
          </Section>

          <Section
            title="Feedback received"
            count={p.reputation.count}
            note="Public reputation counts only exchanges with both ratings submitted. Your own saved feedback can appear here while still private."
          >
            {feedback.status === 'loading' && <SkeletonRows count={2} />}
            {feedback.status === 'error' && <ErrorState error={feedback.error} onRetry={feedback.reload} />}
            {feedback.status === 'ready' && (
              feedback.data.length === 0
                ? <EmptyState title="No ratings yet">
                    No published ratings are available for {first} yet.
                  </EmptyState>
                : feedback.data.map((f) => <FeedbackCard key={f.id} feedback={f} />)
            )}
          </Section>
        </div>

        <aside>
          <Panel title="Usually free" aside={<span className="meta">{p.timezone}</span>}>
            {p.availability.length === 0 ? (
              <p className="small dim">
                No weekly windows recorded. You can still send a request — settling a time will
                just take a round trip.
              </p>
            ) : (
              <>
                <AvailabilityPreview windows={p.availability} />
                <p className="small" style={{ marginTop: 14 }}>
                  <b>In words:</b> {summarise(p.availability)}.
                </p>
              </>
            )}
          </Panel>

          <Notice className="aside-note">
            Availability narrows matching as well as scheduling: two complementary lists that
            never overlap in time are not a usable match.
          </Notice>
        </aside>
      </div>
    </>
  )
}
