import { useT } from '@/i18n/I18nContext'
import type { SkillMarket as Market, SkillStanding } from '@/lib/api/types'

/**
 * What each of your skills is actually worth here.
 *
 * The vocabulary is shared and normalised, so the platform has always known
 * that eighteen people teach Java and that exactly one person teaches Libras.
 * It had never said so. Two counts change what someone does next far more
 * than any ranking does: the rare skill is the one to lead with, and a skill
 * nobody wants is worth knowing about before waiting a month for a request.
 */

type Tone = 'rare' | 'wanted' | 'crowded' | 'quiet'

function toneOf(standing: SkillStanding): Tone {
  if (standing.learners === 0) return 'quiet'
  if (standing.teachers === 0) return 'rare'
  return standing.learners > standing.teachers ? 'wanted' : 'crowded'
}

function Row({ standing, tone }: { standing: SkillStanding; tone: Tone }) {
  const t = useT()
  return (
    <li className="mkt-row">
      <span className="mkt-name">{standing.skill.name}</span>
      <span className={`mkt-tag mkt-${tone}`}>{t(`skillMarket.tag.${tone}`)}</span>
      <span className="mkt-counts meta">
        {t('skillMarket.counts', { teachers: standing.teachers, learners: standing.learners })}
      </span>
    </li>
  )
}

export function SkillMarket({ market }: { market: Market }) {
  const t = useT()
  if (market.offered.length === 0 && market.wanted.length === 0) return null

  // The headline is the scarcest thing you teach that somebody actually wants.
  // Rarity with no demand is trivia; rarity with demand is a reason to act.
  const spotlight = [...market.offered]
    .filter((s) => s.learners > 0)
    .sort((a, b) => a.teachers - b.teachers || b.learners - a.learners)[0]

  return (
    <div className="mkt">
      {spotlight && (
        <p className="mkt-lead">
          {spotlight.teachers === 0
            ? t('skillMarket.spotlightOnly', {
                skill: spotlight.skill.name, learners: spotlight.learners,
              })
            : t('skillMarket.spotlightScarce', {
                skill: spotlight.skill.name, teachers: spotlight.teachers, learners: spotlight.learners,
              })}
        </p>
      )}

      {market.offered.length > 0 && (
        <>
          <p className="mkt-head meta">{t('skillMarket.youTeach')}</p>
          <ul className="mkt-list">
            {market.offered.map((s) => <Row key={s.skill.id} standing={s} tone={toneOf(s)} />)}
          </ul>
        </>
      )}

      {market.wanted.length > 0 && (
        <>
          <p className="mkt-head meta">{t('skillMarket.youWant')}</p>
          <ul className="mkt-list">
            {market.wanted.map((s) => (
              // On a skill you want, a crowd of teachers is good news, so the
              // tag is read from the other side of the same two numbers.
              <Row key={s.skill.id} standing={s} tone={s.teachers === 0 ? 'quiet' : 'wanted'} />
            ))}
          </ul>
        </>
      )}

      <p className="small dim">{t('skillMarket.savedOnly')}</p>
    </div>
  )
}
