import { describe, expect, it } from 'vitest'
import { exchangeDirections, localToInstant } from './scheduling'
describe('account-zone scheduling', () => {
  it('uses the account zone independently of the browser zone', () => {
    expect(localToInstant('2027-01-12', '14:00', 'America/Sao_Paulo').iso).toBe('2027-01-12T17:00:00.000Z')
    expect(localToInstant('2027-01-12', '14:00', 'Asia/Tokyo').iso).toBe('2027-01-12T05:00:00.000Z')
  })
  it('supports fractional offsets', () => expect(localToInstant('2027-01-12', '14:00', 'Asia/Kathmandu').iso).toBe('2027-01-12T08:15:00.000Z'))
  it('rejects nonexistent and ambiguous daylight-saving local times', () => {
    expect(localToInstant('2027-03-14', '02:30', 'America/New_York')).toMatchObject({ iso: null, error: expect.stringContaining('does not exist') })
    expect(localToInstant('2027-11-07', '01:30', 'America/New_York')).toMatchObject({ iso: null, error: expect.stringContaining('occurs twice') })
  })
  it('rejects invalid dates and zones without crashing render', () => {
    expect(localToInstant('2027-02-30', '12:00', 'UTC').iso).toBeNull()
    expect(localToInstant('2027-01-12', '12:00', 'bad-zone').iso).toBeNull()
    expect(localToInstant('', '', 'UTC')).toEqual({ iso: null, error: null })
  })
  it('shows both trade directions correctly for each participant', () => {
    const ex = { requesterId: 'a', skillFromReceiver: { name: 'React' }, skillFromRequester: { name: 'Java' } }
    expect(exchangeDirections(ex, 'a').youLearn?.name).toBe('React')
    expect(exchangeDirections(ex, 'b').youLearn?.name).toBe('Java')
    expect(exchangeDirections(ex, 'b').theyLearn?.name).toBe('React')
  })
})
