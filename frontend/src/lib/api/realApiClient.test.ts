import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { realApiClient as api, type ProfileDto } from './realApiClient'
import { setAuthToken } from './http'
import type { Exchange, Skill, User } from './types'

const react: Skill = { id: 'react', name: 'React', slug: 'react', status: 'APPROVED' }
const java: Skill = { id: 'java', name: 'Java', slug: 'java', status: 'APPROVED' }
const me: User = { id: 'me', email: 'me@example.com', displayName: 'Alex', timeZone: 'America/Sao_Paulo', skillsRegistered: true }
const profile: ProfileDto = { id: 'other', displayName: 'Sam', bio: null, timeZone: 'Asia/Tokyo', skillsOffered: [react], skillsWanted: [java], reputationAverage: 4.5, reputationCount: 2, availability: [] }
const exchange: Exchange = { id: 'exchange', requesterId: 'me', receiverId: 'other', skillFromReceiver: react, skillFromRequester: java, status: 'REQUESTED', strength: 'MUTUAL', scheduledAt: null, meetingUrl: null, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00Z' }
function respond(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }) }
beforeEach(() => { setAuthToken(null) })
afterEach(() => { vi.unstubAllGlobals() })

describe('Spring wire contracts', () => {
  it('restores the user from a token-only login with bearer auth', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(respond({ token: 'session-token' })).mockResolvedValueOnce(respond(me))
    vi.stubGlobal('fetch', fetch)
    const result = await api.login({ email: me.email, password: 'Secret123!' })
    expect(result.user).toEqual(me)
    expect(fetch.mock.calls[1][0]).toBe('/api/auth/me')
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe('Bearer session-token')
  })
  it('sends all required registration fields', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(respond({ token: 'new-token' })).mockResolvedValueOnce(respond(me))
    vi.stubGlobal('fetch', fetch)
    const payload = { email: me.email, password: 'Secret123!', displayName: me.displayName, timeZone: me.timeZone }
    await api.register(payload)
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(payload)
  })
  it('unwraps UserSkillResponse and sends actual replacement field names', async () => {
    const fetch = vi.fn().mockResolvedValue(respond({ offered: [{ id: 'row1', skill: java, direction: 'OFFERED' }], wanted: [{ id: 'row2', skill: react, direction: 'WANTED' }] }))
    vi.stubGlobal('fetch', fetch)
    expect(await api.replaceMySkills({ offered: ['java'], wanted: ['react'] })).toEqual({ offered: [java], wanted: [react] })
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ offeredSkillIds: ['java'], wantedSkillIds: ['react'] })
  })
  it('unwraps paginated vocabulary', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({ items: [react], page: 0, size: 20, total: 1 })))
    expect(await api.searchSkills('React')).toEqual([react])
  })
  it('preserves match pagination, strength, published reputation and profile skills', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(respond({ items: [{ userId: 'other', displayName: 'Sam', bio: null, strength: 'MUTUAL', reputationAverage: 4.5, reputationCount: 2 }], page: 1, size: 12, total: 13 })).mockResolvedValueOnce(respond(profile))
    vi.stubGlobal('fetch', fetch)
    const result = await api.getMatches(1)
    expect(fetch.mock.calls[0][0]).toBe('/api/matches?page=1&size=12')
    expect(result.total).toBe(13)
    expect(result.items[0]).toMatchObject({ strength: 'MUTUAL', user: { offeredSkills: [react], wantedSkills: [java], timeZone: 'Asia/Tokyo', reputation: { average: 4.5, count: 2 } } })
  })
  it('sends a chosen acceptance skill for either strength and does not fetch after mutation', async () => {
    const fetch = vi.fn().mockResolvedValue(respond({ ...exchange, status: 'ACCEPTED' }))
    vi.stubGlobal('fetch', fetch)
    await api.acceptExchange('exchange', { skillFromRequester: 'java' })
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ skillFromRequester: 'java' })
  })
  it('checks later pages for an open exchange', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(respond({ items: [{ ...exchange, status: 'COMPLETED' }], page: 0, size: 1, total: 2 })).mockResolvedValueOnce(respond({ items: [exchange], page: 1, size: 1, total: 2 }))
    vi.stubGlobal('fetch', fetch)
    expect((await api.findOpenExchange('other'))?.id).toBe('exchange')
    expect(fetch.mock.calls[1][0]).toContain('page=1')
  })
  it('preserves API error codes and messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({ code: 'EXCHANGE_INVALID_STATUS', message: 'Only accepted exchanges can be scheduled.' }, 409)))
    await expect(api.scheduleExchange('exchange', { scheduledAt: '2027-01-01T00:00:00Z', meetingUrl: 'https://meet.google.com/test' })).rejects.toMatchObject({ status: 409, code: 'EXCHANGE_INVALID_STATUS', message: 'Only accepted exchanges can be scheduled.' })
  })
  it('reports network failures with an actionable error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(api.getMySkills()).rejects.toMatchObject({ code: 'NETWORK', status: 0 })
  })
})

it('saves timeZone with windows without changing the array response contract', async () => {
  const windows = [{ dayOfWeek: 'MONDAY' as const, startTime: '09:00', endTime: '10:00' }]
  const fetch = vi.fn().mockResolvedValue(respond(windows))
  vi.stubGlobal('fetch', fetch)
  await api.replaceMyAvailability({ timezone: 'America/New_York', windows })
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ timeZone: 'America/New_York', windows })
})
it('reads the complete private feedback state without dropping fields', async () => {
  const state = { mine: { id: 'f', exchangeId: 'exchange', authorId: 'me', rating: 4, comment: 'Clear explanation', createdAt: '2026-09-05T10:00:00Z' }, theirs: null, counterpartSubmitted: false }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(state)))
  expect(await api.getExchangeFeedback('exchange')).toEqual(state)
})
it('reads both published reviews', async () => {
  const state = { mine: { id: 'a', rating: 4 }, theirs: { id: 'b', rating: 5 }, counterpartSubmitted: true }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(state)))
  expect(await api.getExchangeFeedback('exchange')).toEqual(state)
})
it.each([[403, 'NOT_A_PARTICIPANT'], [404, 'EXCHANGE_NOT_FOUND'], [409, 'EXCHANGE_NOT_COMPLETED']])('preserves feedback status %i and code %s', async (status, code) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({ code, message: 'Cannot read this feedback.' }, status)))
  await expect(api.getExchangeFeedback('exchange')).rejects.toMatchObject({ status, code })
})
