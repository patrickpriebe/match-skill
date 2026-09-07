import { request, setAuthToken } from './http'
import type {
  AcceptExchangePayload, AuthResponse, AvailabilityWindow, CreateExchangePayload,
  CreateFeedbackPayload, Exchange, ExchangeFeedback, ExchangeStatus, ExchangeView, Feedback, FeedbackView,
  LoginPayload, Match, MatchStrength, MyAvailability, MySkills, Paginated,
  PublicProfile, RegisterPayload, ReplaceAvailabilityPayload, ReplaceMySkillsPayload,
  ScheduleExchangePayload, Skill, SuggestSkillPayload, User,
} from './types'

// Explicit Spring response shapes. View models are mapped only at this boundary.
export interface ProfileDto {
  id: string; displayName: string; bio: string | null; timeZone: string
  skillsOffered: Skill[]; skillsWanted: Skill[]
  reputationAverage: number; reputationCount: number; availability: AvailabilityWindow[]
}
export interface MatchDto {
  userId: string; displayName: string; bio: string | null; strength: MatchStrength
  reputationAverage: number; reputationCount: number
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
async function matchPage(path: string, page: number, skill?: string): Promise<Paginated<Match>> {
  const data = await request<Paginated<MatchDto>>(path, { query: { page, size: 12, skill } })
  const resolve = profileResolver()
  const items = await Promise.all(data.items.map(async (m) => {
    const p = await resolve(m.userId)
    return {
      user: { id: m.userId, displayName: m.displayName, bio: m.bio, timeZone: p.timezone,
        reputation: { average: m.reputationAverage, count: m.reputationCount },
        offeredSkills: p.offeredSkills, wantedSkills: p.wantedSkills },
      strength: m.strength, matchingSkills: [],
    }
  }))
  return { ...data, items }
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
  getMatches: (page = 0) => matchPage('/matches', page),
  search: (skill: string, page = 0) => matchPage('/search', page, skill),
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
  findOpenExchange: async (otherId: string): Promise<Exchange | null> => {
    let page = 0
    while (true) {
      const result = await request<Paginated<Exchange>>('/exchanges', { query: { page, size: 50 } })
      const found = result.items.find((e) =>
        (e.requesterId === otherId || e.receiverId === otherId) &&
        ['REQUESTED', 'ACCEPTED', 'SCHEDULED'].includes(e.status))
      if (found) return found
      if (!result.items.length || (result.page + 1) * result.size >= result.total) return null
      page += 1
    }
  },
}
