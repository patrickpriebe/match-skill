import { request, setAuthToken } from './http'
import type {
  AcceptExchangePayload, AuthResponse, AvailabilityWindow, CreateExchangePayload,
  CreateFeedbackPayload, Exchange, ExchangeFeedback, ExchangeStatus, ExchangeView, Feedback, FeedbackView,
  LoginPayload, Match, MatchStrength, MyAvailability, MySkills, Paginated,
  PublicProfile, RegisterPayload, ReplaceAvailabilityPayload, ReplaceMySkillsPayload,
  ScheduleExchangePayload, SharedWindow, Skill, SkillMarket, SuggestSkillPayload, User,
} from './types'

// Explicit Spring response shapes. View models are mapped only at this boundary.
export interface ProfileDto {
  id: string; displayName: string; bio: string | null; timeZone: string
  skillsOffered: Skill[]; skillsWanted: Skill[]
  reputationAverage: number; reputationCount: number; availability: AvailabilityWindow[]
}
export interface MatchDto {
  userId: string; displayName: string; bio: string | null; timeZone: string; strength: MatchStrength
  reputationAverage: number; reputationCount: number
  offeredSkills: Skill[]; wantedSkills: Skill[]
  overlapMinutes: number; sharedWindows: SharedWindow[]
}
export interface RingDto {
  weakestLinkMinutes: number
  members: Array<{
    userId: string; displayName: string; timeZone: string
    reputationAverage: number; reputationCount: number
    teaches: Skill; learns: Skill
  }>
}
export interface MySkillsDto {
  offered: Array<{ id: string; skill: Skill; direction: string }>
  wanted: Array<{ id: string; skill: Skill; direction: string }>
}
export function mapProfile(p: ProfileDto): PublicProfile {
  const reputation = { average: p.reputationAverage, count: p.reputationCount }
  return {
    user: { id: p.id, displayName: p.displayName, bio: p.bio, timeZone: p.timeZone, reputation },
    reputation, offeredSkills: p.skillsOffered, wantedSkills: p.skillsWanted,
    availability: p.availability, timezone: p.timeZone,
  }
}
export function mapMySkills(data: MySkillsDto): MySkills {
  return { offered: data.offered.map((r) => r.skill), wanted: data.wanted.map((r) => r.skill) }
}
const base = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '')
const encode = encodeURIComponent

// Deduplicate enrichment within one load, with no cross-session profile cache.
function profileResolver() {
  const pending = new Map<string, Promise<PublicProfile>>()
  return (id: string) => {
    let result = pending.get(id)
    if (!result) {
      result = request<ProfileDto>(`/users/${encode(id)}`).then(mapProfile)
      pending.set(id, result)
    }
    return result
  }
}
async function authenticate(path: string, body: LoginPayload | RegisterPayload): Promise<AuthResponse> {
  const { token } = await request<{ token: string }>(path, { method: 'POST', body })
  setAuthToken(token)
  try {
    return { token, user: await request<User>('/auth/me') }
  } catch (error) {
    setAuthToken(null)
    throw error
  }
}
/**
 * One request per page, not one per row. The server sends the skills, the zone
 * and the shared windows it already had in hand while ranking; fetching a
 * profile per match used to turn a page of twelve into thirteen round trips
 * against an instance that can take a minute to wake.
 */
function matchPage(path: string, page: number, skill?: string, signal?: AbortSignal): Promise<Paginated<Match>> {
  return request<Paginated<MatchDto>>(path, { query: { page, size: 12, skill }, signal }).then((data) => ({
    ...data,
    items: data.items.map((m) => ({
      user: {
        id: m.userId,
        displayName: m.displayName,
        bio: m.bio,
        timeZone: m.timeZone,
        reputation: { average: m.reputationAverage, count: m.reputationCount },
        offeredSkills: m.offeredSkills,
        wantedSkills: m.wantedSkills,
      },
      strength: m.strength,
      overlapMinutes: m.overlapMinutes,
      sharedWindows: m.sharedWindows,
    })),
  }))
}
async function enrichExchange(e: Exchange, resolve = profileResolver()): Promise<ExchangeView> {
  const [requester, receiver] = await Promise.all([resolve(e.requesterId), resolve(e.receiverId)])
  return { ...e, requester: requester.user, receiver: receiver.user }
}
function transition(id: string, action: string, body?: unknown): Promise<Exchange> {
  // A successful mutation remains successful even if later profile loading fails.
  return request<Exchange>(`/exchanges/${encode(id)}/${action}`, { method: 'POST', body })
}
export const realApiClient = {
  login: (p: LoginPayload) => authenticate('/auth/login', p),
  register: (p: RegisterPayload) => authenticate('/auth/register', p),
  me: () => request<User>('/auth/me'),
  googleLoginUrl: () => `${base}/auth/google`,
  googleAuthUrl: () => `${base}/auth/google`,
  searchSkills: async (query: string): Promise<Skill[]> =>
    (await request<Paginated<Skill>>('/skills', { query: { query, page: 0, size: 20 } })).items,
  suggestSkill: (p: SuggestSkillPayload) => request<Skill>('/skills/suggest', { method: 'POST', body: p }),
  getMySkills: async () => mapMySkills(await request<MySkillsDto>('/me/skills')),
  getSkillMarket: (signal?: AbortSignal) => request<SkillMarket>('/me/skills/market', { signal }),
  replaceMySkills: async (p: ReplaceMySkillsPayload) => mapMySkills(await request<MySkillsDto>('/me/skills', {
    method: 'PUT', body: { offeredSkillIds: p.offered, wantedSkillIds: p.wanted },
  })),
  deleteMySkill: (id: string) => request<void>(`/me/skills/${encode(id)}`, { method: 'DELETE' }),
  getMyAvailability: async (): Promise<MyAvailability> => {
    const [windows, user] = await Promise.all([request<AvailabilityWindow[]>('/me/availability'), request<User>('/auth/me')])
    return { windows, timezone: user.timeZone }
  },
  replaceMyAvailability: async (p: ReplaceAvailabilityPayload): Promise<void> => {
    await request<AvailabilityWindow[]>('/me/availability', { method: 'PUT', body: { timeZone: p.timezone, windows: p.windows } })
  },
  getRings: (limit = 3, signal?: AbortSignal) => request<RingDto[]>('/rings', { query: { limit }, signal }).then((rings) =>
    rings.map((ring) => ({
      ...ring,
      members: ring.members.map((m) => ({
        userId: m.userId, displayName: m.displayName, timeZone: m.timeZone,
        reputation: { average: m.reputationAverage, count: m.reputationCount },
        teaches: m.teaches, learns: m.learns,
      })),
    }))),
  getMatches: (page = 0, signal?: AbortSignal) => matchPage('/matches', page, undefined, signal),
  search: (skill: string, page = 0, signal?: AbortSignal) => matchPage('/search', page, skill, signal),
  getProfile: async (id: string) => mapProfile(await request<ProfileDto>(`/users/${encode(id)}`)),
  getExchanges: async (status?: ExchangeStatus, page = 0): Promise<Paginated<ExchangeView>> => {
    const result = await request<Paginated<Exchange>>('/exchanges', { query: { status, page, size: 12 } })
    const resolve = profileResolver()
    return { ...result, items: await Promise.all(result.items.map((e) => enrichExchange(e, resolve))) }
  },
  getExchange: async (id: string) => enrichExchange(await request<Exchange>(`/exchanges/${encode(id)}`)),
  createExchange: (p: CreateExchangePayload) => request<Exchange>('/exchanges', { method: 'POST', body: p }),
  acceptExchange: (id: string, p: AcceptExchangePayload) => transition(id, 'accept', p),
  declineExchange: (id: string) => transition(id, 'decline'),
  scheduleExchange: (id: string, p: ScheduleExchangePayload) => transition(id, 'schedule', p),
  completeExchange: (id: string) => transition(id, 'complete'),
  cancelExchange: (id: string) => transition(id, 'cancel'),
  createFeedback: (id: string, p: CreateFeedbackPayload) =>
    request<Feedback>(`/exchanges/${encode(id)}/feedback`, { method: 'POST', body: p }),
  getUserFeedback: async (id: string): Promise<FeedbackView[]> => {
    const records = await request<Feedback[]>(`/users/${encode(id)}/feedback`)
    const resolve = profileResolver()
    return Promise.all(records.map(async (f) => ({ ...f, author: (await resolve(f.authorId)).user })))
  },
  getExchangeFeedback: (id: string) => request<ExchangeFeedback>(`/exchanges/${encode(id)}/feedback`),
  /**
   * One request. This used to page through the caller's entire exchange
   * history fifty rows at a time and filter in JavaScript — an unbounded
   * number of round trips to learn whether a single row exists. The server
   * answers 204 when there is none.
   */
  findOpenExchange: (otherId: string, signal?: AbortSignal): Promise<Exchange | null> =>
    request<Exchange | undefined>(`/exchanges/open-with/${encode(otherId)}`, { signal })
      .then((exchange) => exchange ?? null),
}
