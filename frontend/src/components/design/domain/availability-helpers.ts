import { timeInMinutes, validWindow } from '@/lib/availability'
import { DAYS } from '@/lib/api/types'
import type { AvailabilityWindow, DayOfWeek } from '@/lib/api/types'

export type DraftWindow = Pick<AvailabilityWindow, 'dayOfWeek' | 'startTime' | 'endTime'>

export const DAY_LABEL: Record<DayOfWeek, string> = {
  MONDAY: 'Monday', TUESDAY: 'Tuesday', WEDNESDAY: 'Wednesday', THURSDAY: 'Thursday',
  FRIDAY: 'Friday', SATURDAY: 'Saturday', SUNDAY: 'Sunday',
}

const minutes = timeInMinutes

export const windowIsValid = validWindow

export const totalHours = (windows: DraftWindow[]) =>
  windows.filter(windowIsValid).reduce((sum, w) => sum + (minutes(w.endTime) - minutes(w.startTime)) / 60, 0)

/** "Tue 18:00–21:00, Thu 18:00–21:00" — the accessible representation of the
 *  grid, and the one people actually check their work against. */
export function summarise(windows: DraftWindow[]) {
  return DAYS.flatMap((day) =>
    windows.filter((w) => w.dayOfWeek === day)
      .map((w) => DAY_LABEL[day].slice(0, 3) + ' ' + w.startTime.slice(0, 5) + '–' + w.endTime.slice(0, 5)),
  ).join(', ')
}

