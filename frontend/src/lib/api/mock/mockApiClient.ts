import { ApiError, type MySkills, type Skill, type User } from '../types'
import type { ApiClient } from './legacyApiClient'
import { mockUsers, seedSkills } from './data'

// In-memory backend stand-in. State lives for the tab's lifetime (module
// scope), which is enough to build and click through screens before the
// real Spring endpoints exist. Session id persists in localStorage so a
// page refresh doesn't bounce a logged-in user back to /login.

const LATENCY_MS = 300
const SESSION_KEY = 'match-skill-mock-session-user-id'

const users = [...mockUsers]
const skills = [...seedSkills]
const mySkills = new Map<string, MySkills>([['user-demo', { offered: [skills[2]], wanted: [skills[4]] }]])

let nextUserId = users.length + 1
let nextSkillId = skills.length + 1

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY_MS))
}

function fail(status: number, code: string, message: string): never {
  throw new ApiError(status, { code, message })
}

function toPublicUser(user: (typeof users)[number]): User {
  const { password: _password, ...publicUser } = user
  return publicUser
}

function fakeToken(userId: string): string {
  return `mock-token.${userId}.${Date.now()}`
}

function userIdFromToken(token: string | null): string | null {
  if (!token) return null
  const parts = token.split('.')
  return parts.length === 3 && parts[0] === 'mock-token' ? parts[1] : null
}

let currentToken: string | null = null

export const mockApiClient: ApiClient = {
  async register({ email, password }) {
    if (users.some((u) => u.email === email)) {
      fail(409, 'EMAIL_ALREADY_REGISTERED', 'An account with this email already exists.')
    }
    const user = {
      id: `user-${nextUserId++}`,
      email,
      password,
      displayName: email.split('@')[0],
      bio: null,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      skillsRegistered: false,
      createdAt: new Date().toISOString(),
    }
    users.push(user)
    currentToken = fakeToken(user.id)
    localStorage.setItem(SESSION_KEY, user.id)
    return delay({ token: currentToken, user: toPublicUser(user) })
  },

  async login({ email, password }) {
    const user = users.find((u) => u.email === email)
    if (!user || user.password !== password) {
      fail(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect.')
    }
    currentToken = fakeToken(user.id)
    localStorage.setItem(SESSION_KEY, user.id)
    return delay({ token: currentToken, user: toPublicUser(user) })
  },

  googleLoginUrl() {
    // No real OAuth flow in the mock: log straight in as the seed account.
    return '#mock-google-login'
  },

  async me() {
    const userId = userIdFromToken(currentToken) ?? localStorage.getItem(SESSION_KEY)
    const user = users.find((u) => u.id === userId)
    if (!user) fail(401, 'UNAUTHENTICATED', 'No active session.')
    return delay(toPublicUser(user))
  },

  async searchSkills(query) {
    const q = query.trim().toLowerCase()
    const items = q
      ? skills.filter((s) => s.status === 'APPROVED' && s.name.toLowerCase().includes(q))
      : skills.filter((s) => s.status === 'APPROVED')
    return delay({ items, page: 1, size: items.length, total: items.length })
  },

  async suggestSkill({ name }) {
    const existing = skills.find((s) => s.name.toLowerCase() === name.trim().toLowerCase())
    if (existing) return delay(existing)
    const skill: Skill = {
      id: `skill-pending-${nextSkillId++}`,
      name: name.trim(),
      slug: name.trim().toLowerCase().replace(/\s+/g, '-'),
      status: 'PENDING_REVIEW',
    }
    skills.push(skill)
    return delay(skill)
  },

  async getMySkills() {
    const userId = userIdFromToken(currentToken) ?? localStorage.getItem(SESSION_KEY)
    if (!userId) fail(401, 'UNAUTHENTICATED', 'No active session.')
    return delay(mySkills.get(userId) ?? { offered: [], wanted: [] })
  },

  async replaceMySkills(payload) {
    const userId = userIdFromToken(currentToken) ?? localStorage.getItem(SESSION_KEY)
    if (!userId) fail(401, 'UNAUTHENTICATED', 'No active session.')
    const resolve = (ids: string[]) => ids.map((id) => skills.find((s) => s.id === id)).filter(Boolean) as Skill[]
    const result: MySkills = { offered: resolve(payload.offered), wanted: resolve(payload.wanted) }
    mySkills.set(userId, result)
    const user = users.find((u) => u.id === userId)
    if (user) user.skillsRegistered = true
    return delay(result)
  },

  async deleteMySkill(id) {
    const userId = userIdFromToken(currentToken) ?? localStorage.getItem(SESSION_KEY)
    if (!userId) fail(401, 'UNAUTHENTICATED', 'No active session.')
    const current = mySkills.get(userId) ?? { offered: [], wanted: [] }
    mySkills.set(userId, {
      offered: current.offered.filter((s) => s.id !== id),
      wanted: current.wanted.filter((s) => s.id !== id),
    })
    return delay(undefined)
  },

  async getMyAvailability() {
    return delay({ timezone: 'America/Sao_Paulo', windows: [] })
  },
  async replaceMyAvailability(payload) {
    return delay({ timezone: payload.timezone, windows: payload.windows.map((w, i) => ({ id: `avail-${i}`, ...w })) })
  },

  async getMatches() {
    return delay([])
  },
  async search() {
    return delay({ items: [], page: 1, size: 0, total: 0 })
  },
  async getUser(id) {
    const user = users.find((u) => u.id === id)
    if (!user) fail(404, 'USER_NOT_FOUND', 'User not found.')
    const skillsFor = mySkills.get(id) ?? { offered: [], wanted: [] }
    return delay({
      id: user.id,
      displayName: user.displayName,
      bio: user.bio,
      reputation: { average: 0, count: 0 },
      offeredSkills: skillsFor.offered,
      wantedSkills: skillsFor.wanted,
    })
  },

  async createExchange() {
    fail(501, 'NOT_IMPLEMENTED', 'Exchanges are not wired up in the mock yet.')
  },
  async listExchanges() {
    return delay([])
  },
  async getExchange(id) {
    fail(404, 'EXCHANGE_NOT_FOUND', `Exchange ${id} not found.`)
  },
  async acceptExchange() {
    fail(501, 'NOT_IMPLEMENTED', 'Exchanges are not wired up in the mock yet.')
  },
  async declineExchange() {
    fail(501, 'NOT_IMPLEMENTED', 'Exchanges are not wired up in the mock yet.')
  },
  async scheduleExchange() {
    fail(501, 'NOT_IMPLEMENTED', 'Exchanges are not wired up in the mock yet.')
  },
  async completeExchange() {
    fail(501, 'NOT_IMPLEMENTED', 'Exchanges are not wired up in the mock yet.')
  },
  async cancelExchange() {
    fail(501, 'NOT_IMPLEMENTED', 'Exchanges are not wired up in the mock yet.')
  },

  async submitFeedback() {
    fail(501, 'NOT_IMPLEMENTED', 'Feedback is not wired up in the mock yet.')
  },
  async getUserFeedback() {
    return delay([])
  },
}

export function setMockToken(token: string | null) {
  currentToken = token
  if (token) {
    const userId = userIdFromToken(token)
    if (userId) localStorage.setItem(SESSION_KEY, userId)
  } else {
    localStorage.removeItem(SESSION_KEY)
  }
}
