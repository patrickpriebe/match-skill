import { expect, it } from 'vitest'
import { validWindow, windowsOverlap } from './availability'
const window = { dayOfWeek: 'MONDAY' as const, startTime: '09:00', endTime: '10:00:00' }
it('allows adjacent windows despite mixed LocalTime precision', () => {
  expect(windowsOverlap([window, { ...window, startTime: '10:00', endTime: '11:00' }])).toBe(false)
})
it('detects overlapping windows only within the same day', () => {
  expect(windowsOverlap([window, { ...window, startTime: '09:30', endTime: '11:00' }])).toBe(true)
  expect(windowsOverlap([window, { ...window, dayOfWeek: 'TUESDAY' }])).toBe(false)
})
it('rejects blank, equal, reversed or malformed clock values', () => {
  expect(validWindow(window)).toBe(true)
  for (const value of ['', '25:00', '09:99', '10:00', '11:00']) expect(validWindow({ ...window, startTime: value })).toBe(false)
})
