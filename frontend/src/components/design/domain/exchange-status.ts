import type { ComponentType } from 'react'
import type { ExchangeStatus } from '@/lib/api/types'
import {
  IconClock, IconCheck, IconCalendar, IconCheckCircle, IconXCircle, IconReturn,
} from '../ui/icons'

interface StatusStyle {
  label: string
  className: string
  Icon: ComponentType<{ size?: number }>
}

/**
 * One typed map, so a new status cannot be added without deciding how it
 * looks. Every entry pairs a colour with a distinct icon shape and a text
 * label — colour is never the only carrier of meaning.
 *
 * DECLINED reads lighter than CANCELLED on purpose: it is someone else's
 * decision, not a failure of yours.
 */
export const EXCHANGE_STATUS: Record<ExchangeStatus, StatusStyle> = {
  REQUESTED: { label: 'Requested', className: 'badge-requested', Icon: IconClock },
  ACCEPTED: { label: 'Accepted', className: 'badge-accepted', Icon: IconCheck },
  SCHEDULED: { label: 'Scheduled', className: 'badge-scheduled', Icon: IconCalendar },
  COMPLETED: { label: 'Completed', className: 'badge-completed', Icon: IconCheckCircle },
  CANCELLED: { label: 'Cancelled', className: 'badge-cancelled', Icon: IconXCircle },
  DECLINED: { label: 'Declined', className: 'badge-declined', Icon: IconReturn },
}

