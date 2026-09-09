import type { Skill } from '@/lib/api/types'
import { useT } from '@/i18n/I18nContext'

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
  const t = useT()
  const pending = skill.status === 'PENDING_REVIEW'

  return (
    <span
      className={cx(
        'chip',
        tone === 'wanted' && 'chip-want',
        tone === 'reciprocal' && 'chip-recip',
        pending && 'chip-pending',
      )}
      title={pending ? t('skillBadge.pendingTitle') : undefined}
    >
      {skill.name}
      {pending && <span className="tail">{t('skillBadge.pendingTail')}</span>}
      {onRemove && (
        <button type="button" aria-label={t('skillBadge.remove', { name: skill.name })} onClick={() => onRemove(skill)}>
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
