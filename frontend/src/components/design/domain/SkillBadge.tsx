import type { Skill } from '@/lib/api/types'

const cx = (...p: Array<string | false | undefined>) => p.filter(Boolean).join(' ')

export type SkillTone = 'offered' | 'wanted' | 'reciprocal'

/**
 * A skill chip. `reciprocal` is the only variant allowed to carry the accent —
 * it marks the skill that closes a trade, which is the one fact on the chip
 * worth spending the accent on.
 *
 * A PENDING_REVIEW skill is shown as pending rather than hidden: it does not
 * participate in matching yet, and the user who suggested it deserves to know.
 */
export function SkillBadge({ skill, tone = 'offered', onRemove }: {
  skill: Skill
  tone?: SkillTone
  onRemove?: (skill: Skill) => void
}) {
  const pending = skill.status === 'PENDING_REVIEW'

  return (
    <span
      className={cx(
        'chip',
        tone === 'wanted' && 'chip-want',
        tone === 'reciprocal' && 'chip-recip',
        pending && 'chip-pending',
      )}
      title={pending ? 'Waiting for review — it will not match until approved.' : undefined}
    >
      {skill.name}
      {pending && <span className="tail">pending review</span>}
      {onRemove && (
        <button type="button" aria-label={'Remove ' + skill.name} onClick={() => onRemove(skill)}>
          ×
        </button>
      )}
    </span>
  )
}

export function SkillBadgeList({ skills, tone, recipIds, onRemove }: {
  skills: Skill[]
  tone?: SkillTone
  /** Skills that connect to the viewer, drawn as reciprocal. */
  recipIds?: Set<string>
  onRemove?: (skill: Skill) => void
}) {
  return (
    <div className="chips">
      {skills.map((s) => (
        <SkillBadge
          key={s.id}
          skill={s}
          tone={recipIds?.has(s.id) ? 'reciprocal' : tone}
          onRemove={onRemove}
        />
      ))}
    </div>
  )
}
