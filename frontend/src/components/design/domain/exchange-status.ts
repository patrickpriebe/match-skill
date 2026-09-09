import type { ComponentType } from 'react'
import type { ExchangeStatus } from '@/lib/api/types'
import {
  IconClock, IconCheck, IconCalendar, IconCheckCircle, IconXCircle, IconReturn,
} from '../ui/icons'

interface StatusStyle {
  className: string
  Icon: ComponentType<{ size?: number }>
}

/**
 * One typed map, so a new status cannot be added without deciding how it
 * looks. Every entry pairs a colour with a distinct icon shape — the text
 * label itself is locale-dependent and comes from i18n `exchangeStatus.*`.
 *
 * DECLINED reads lighter than CANCELLED on purpose: it is someone else's
 * decision, not a failure of yours.
 */
export const EXCHANGE_STATUS: Record<ExchangeStatus, StatusStyle> = {
  REQUESTED: { className: 'badge-requested', Icon: IconClock },
  ACCEPTED: { className: 'badge-accepted', Icon: IconCheck },
  SCHEDULED: { className: 'badge-scheduled', Icon: IconCalendar },
  COMPLETED: { className: 'badge-completed', Icon: IconCheckCircle },
  CANCELLED: { className: 'badge-cancelled', Icon: IconXCircle },
  DECLINED: { className: 'badge-declined', Icon: IconReturn },
}

