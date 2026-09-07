import { UserCard } from './UserCard'
import { IconStar } from '../ui/icons'
import { RelativeTime } from './ZonedTime'
import type { FeedbackView } from '@/lib/api/types'

/**
 * One rating on a profile. Only completed exchanges can be rated, so every
 * card here is a session that actually happened — the copy says so once, at
 * the section level, and the card itself stays factual.
 */
export function FeedbackCard({ feedback }: { feedback: FeedbackView }) {
  return (
    <article className="fb-row">
      <div className="row-between" style={{ alignItems: 'baseline' }}>
        <UserCard name={feedback.author.displayName} size="sm" nameSize={14} />
        <RelativeTime iso={feedback.createdAt} />
      </div>

      <div className="row" style={{ gap: 10, margin: '8px 0 6px' }}>
        <span className="stars" aria-label={feedback.rating + ' out of 5'}>
          {Array.from({ length: 5 }, (_, i) => (
            <IconStar key={i} className={i < feedback.rating ? undefined : 'off'} />
          ))}
        </span>
        {feedback.exchangeSummary && <span className="meta">{feedback.exchangeSummary}</span>}
      </div>

      {feedback.comment && <p>{feedback.comment}</p>}
    </article>
  )
}
