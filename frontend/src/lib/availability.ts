import type { AvailabilityWindow } from './api/types'
type Window = Pick<AvailabilityWindow, 'dayOfWeek' | 'startTime' | 'endTime'>
export function timeInMinutes(value: string): number {
  if (!/^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d+)?)?$/.test(value)) return NaN
  const [hour, minute, second = '0'] = value.split(':')
  return Number(hour) * 60 + Number(minute) + Number(second) / 60
}
export function validWindow(window: Window): boolean {
  return timeInMinutes(window.endTime) > timeInMinutes(window.startTime)
}
export function windowsOverlap(windows: Window[]): boolean {
  return windows.some((window, index) => windows.some((other, next) => next > index && window.dayOfWeek === other.dayOfWeek &&
    timeInMinutes(window.startTime) < timeInMinutes(other.endTime) && timeInMinutes(other.startTime) < timeInMinutes(window.endTime)))
}
