import { IconStar } from '../ui/icons'
import { useT } from '@/i18n/I18nContext'
import type { Reputation } from '@/lib/api/types'

/**
 * Average and count, always together. The docs are explicit: a single
 * five-star rating is not the same claim as forty.
 *
 * With no ratings this renders a sentence, not five hollow stars — an empty
 * star row reads as a bad score, which would be a lie about a new member.
 *
 * The partial star is a clipped overlay rather than an SVG gradient: gradients
 * across separate roots do not survive every renderer, and a star that rounds
 * 4.8 up to five is a lie about someone's record.
 */
export function ReputationDisplay({ reputation, showStars = true, emptyLabel, countLabel }: {
  reputation: Reputation
  showStars?: boolean
  emptyLabel?: string
  countLabel?: string
}) {
  const t = useT()
  if (reputation.count === 0) {
    return <span className="rep-none">{emptyLabel ?? t('reputation.noRatingsYet')}</span>
  }

  const full = Math.floor(reputation.average)
  const fraction = reputation.average - full

  return (
    <span className="rep">
      {showStars && (
        <span className="stars">
          {Array.from({ length: 5 }, (_, i) => {
            if (i < full) return <IconStar key={i} />
            if (i === full && fraction > 0.05) {
              return (
                <span className="part" key={i}>
                  <IconStar className="base" />
                  <IconStar className="fill" style={{ clipPath: `inset(0 ${(1 - fraction) * 100}% 0 0)` }} />
                </span>
              )
            }
            return <IconStar key={i} className="off" />
          })}
        </span>
      )}
      <span className="rep-val">{reputation.average.toFixed(1)}</span>
      <span className="rep-count">
        {countLabel ?? t(reputation.count === 1 ? 'reputation.rating' : 'reputation.ratings', { count: reputation.count })}
      </span>
    </span>
  )
}
