// UI models. Wire DTOs and their mappings are defined in realApiClient.ts.

export type SkillStatus = 'APPROVED' | 'PENDING_REVIEW'

export interface Skill {
  id: string
  name: string
  slug: string
  status: SkillStatus
}

export type SkillDirection = 'OFFERED' | 'WANTED'

export interface User {
  id: string
  email: string
  displayName: string
  bio?: string | null
  timeZone: string
  skillsRegistered: boolean
  createdAt?: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface RegisterPayload {
  email: string
  password: string
  displayName: string
  timeZone: string
}

export interface LoginPayload {
  email: string
  password: string
}

export interface MySkills {
  offered: Skill[]
  wanted: Skill[]
}

export interface ReplaceMySkillsPayload {
  offered: string[]
  wanted: string[]
}

export interface SuggestSkillPayload {
  name: string
}

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export const DAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
export interface Reputation { average: number; count: number }

export interface AvailabilityWindow {
  id: string
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
}

export interface ReplaceAvailabilityPayload {
  timezone: string
  windows: Array<Pick<AvailabilityWindow, 'dayOfWeek' | 'startTime' | 'endTime'>>
}

export type MatchStrength = 'PARTIAL' | 'MUTUAL'

export interface MatchUser {
  id: string
  displayName: string
  bio?: string | null
  reputation: { average: number; count: number }
  offeredSkills: Skill[]
  wantedSkills: Skill[]
  timeZone?: string
}

/**
 * One stretch during which both people are free, already converted by the
 * server into the asking user's own zone. `endTime` of '00:00' closes the day
 * rather than opening the next one.
 */
export interface SharedWindow {
  dayOfWeek: DayOfWeek
  startTime: string
  endTime: string
  minutes: number
}

export interface Match {
  user: MatchUser
  strength: MatchStrength
  /** Absent on a match assembled from a profile rather than from the feed. */
  overlapMinutes?: number
  sharedWindows?: SharedWindow[]
}

export type ExchangeStatus =
  | 'REQUESTED'
  | 'DECLINED'
  | 'ACCEPTED'
  | 'SCHEDULED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface Exchange {
  id: string
  requesterId: string
  receiverId: string
  skillFromReceiver: Skill
  skillFromRequester: Skill | null
  strength: MatchStrength
  status: ExchangeStatus
  scheduledAt: string | null
  meetingUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface Participant {
  id: string
  displayName: string
  timeZone?: string
  reputation?: Reputation
}

export interface ExchangeView extends Exchange {
  requester: Participant
  receiver: Participant
}

export interface FeedbackView extends Feedback {
  author: Participant
  exchangeSummary?: string
}

export interface PublicProfile {
  user: Participant & { bio?: string | null }
  reputation: Reputation
  offeredSkills: Skill[]
  wantedSkills: Skill[]
  availability: AvailabilityWindow[]
  timezone: string
}

export interface MyAvailability {
  timezone: string
  windows: AvailabilityWindow[]
}

export interface CreateExchangePayload {
  receiverId: string
  skillFromReceiver: string
}

export interface AcceptExchangePayload {
  skillFromRequester?: string
}

export interface ScheduleExchangePayload {
  scheduledAt: string
  meetingUrl: string
}

export interface Feedback {
  id: string
  exchangeId: string
  authorId: string
  rating: number
  comment?: string | null
  createdAt: string
}

export interface CreateFeedbackPayload {
  rating: number
  comment?: string
}

export interface Paginated<T> {
  items: T[]
  page: number
  size: number
  total: number
}

// Stable {code, message} shape returned by every failing endpoint.
export interface ApiErrorBody {
  code: string
  message: string
}

export class ApiError extends Error {
  code: string
  status: number

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
  }
}

export interface ExchangeFeedback { mine: Feedback | null; theirs: Feedback | null; counterpartSubmitted: boolean }

export interface RingMember {
  userId: string
  displayName: string
  timeZone: string
  reputation: { average: number; count: number }
  /** Goes to the next member in the list, wrapping at the end. */
  teaches: Skill
  /** Comes from the previous member. Always the previous member's `teaches`. */
  learns: Skill
}

/**
 * A closed chain of teaching in which nobody needed a double coincidence of
 * wants. The viewer is always the first member.
 */
export interface Ring {
  members: RingMember[]
  /** The smallest weekly overlap among the consecutive pairs, in minutes. */
  weakestLinkMinutes: number
}
