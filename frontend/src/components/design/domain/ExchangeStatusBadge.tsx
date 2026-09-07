import type { ExchangeStatus, MatchStrength } from '@/lib/api/types'
import { EXCHANGE_STATUS } from './exchange-status'

export function ExchangeStatusBadge({ status, className }: {
  status: ExchangeStatus
  className?: string
}) {
  const { label, className: tone, Icon } = EXCHANGE_STATUS[status]
  return (
    <span className={['badge', tone, className].filter(Boolean).join(' ')}>
      <Icon size={12} />
      {label}
    </span>
  )
}

/**
 * Match strength is a different axis from exchange status — quality of the
 * pair versus state of the process. Merging them into one generic badge is
 * how the hierarchy dissolves, so they stay separate components.
 */
export function MatchStrengthBadge({ strength }: { strength: MatchStrength }) {
  return strength === 'MUTUAL'
    ? <span className="strength strength-mutual">Mutual</span>
    : <span className="strength strength-partial">Partial</span>
}
