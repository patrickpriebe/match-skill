/** Convert an explicit account-zone wall clock into a unique UTC instant.
 * Candidate offsets are sampled around the date to detect DST gaps and folds.
 * Never silently shift a nonexistent time or pick one side of an ambiguous hour.
 */
export function localToInstant(date: string, time: string, timeZone: string): { iso: string | null; error: string | null } {
  if (!date || !time) return { iso: null, error: null }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) return { iso: null, error: 'Enter a valid date and time.' }
  const target = Date.parse(`${date}T${time}:00Z`)
  if (!Number.isFinite(target) || new Date(target).toISOString().slice(0, 16) !== `${date}T${time}`) {
    return { iso: null, error: 'Enter a valid date and time.' }
  }
  try {
    const format = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })
    const wallClock = (instant: number) => {
      const parts = Object.fromEntries(format.formatToParts(new Date(instant)).map((p) => [p.type, p.value]))
      return Date.parse(`${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}Z`)
    }
    const offsets = new Set<number>()
    for (let hour = -36; hour <= 36; hour += 6) {
      const sample = target + hour * 3600000
      offsets.add(wallClock(sample) - sample)
    }
    const candidates = [...offsets].map((offset) => target - offset).filter((instant) => wallClock(instant) === target)
    if (candidates.length !== 1) return { iso: null, error: candidates.length === 0
      ? 'This local time does not exist because the clocks change. Choose another time.'
      : 'This local time occurs twice because the clocks change. Choose an unambiguous time.' }
    return { iso: new Date(candidates[0]).toISOString(), error: null }
  } catch { return { iso: null, error: 'The saved time zone is invalid. Update your availability first.' } }
}

export function exchangeDirections(exchange: { requesterId: string; skillFromReceiver: { name: string }; skillFromRequester: { name: string } | null }, meId: string) {
  const requesting = exchange.requesterId === meId
  return { youLearn: requesting ? exchange.skillFromReceiver : exchange.skillFromRequester,
    theyLearn: requesting ? exchange.skillFromRequester : exchange.skillFromReceiver }
}
