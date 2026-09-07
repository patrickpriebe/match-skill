import type {
  AcceptExchangePayload,
  AuthResponse,
  AvailabilityWindow,
  CreateExchangePayload,
  CreateFeedbackPayload,
  Exchange,
  ExchangeStatus,
  Feedback,
  LoginPayload,
  Match,
  MySkills,
  Paginated,
  RegisterPayload,
  ReplaceAvailabilityPayload,
  ReplaceMySkillsPayload,
  ScheduleExchangePayload,
  Skill,
  SuggestSkillPayload,
  User,
} from '../types'

// One interface, two implementations (real.ts backed by fetch, mock/ backed
// by in-memory data). Every screen imports `api` from `./index` and never
// touches either implementation directly, so flipping VITE_USE_MOCK_API is
// the only thing that changes when the backend comes online.
export interface ApiClient {
  register(payload: RegisterPayload): Promise<AuthResponse>
  login(payload: LoginPayload): Promise<AuthResponse>
  googleLoginUrl(): string
  me(): Promise<User>

  searchSkills(query: string): Promise<Paginated<Skill>>
  suggestSkill(payload: SuggestSkillPayload): Promise<Skill>

  getMySkills(): Promise<MySkills>
  replaceMySkills(payload: ReplaceMySkillsPayload): Promise<MySkills>
  deleteMySkill(id: string): Promise<void>

  getMyAvailability(): Promise<{ timezone: string; windows: AvailabilityWindow[] }>
  replaceMyAvailability(
    payload: ReplaceAvailabilityPayload,
  ): Promise<{ timezone: string; windows: AvailabilityWindow[] }>

  getMatches(): Promise<Match[]>
  search(skill: string): Promise<Paginated<Match['user']>>
  getUser(id: string): Promise<Match['user']>

  createExchange(payload: CreateExchangePayload): Promise<Exchange>
  listExchanges(status?: ExchangeStatus): Promise<Exchange[]>
  getExchange(id: string): Promise<Exchange>
  acceptExchange(id: string, payload: AcceptExchangePayload): Promise<Exchange>
  declineExchange(id: string): Promise<Exchange>
  scheduleExchange(id: string, payload: ScheduleExchangePayload): Promise<Exchange>
  completeExchange(id: string): Promise<Exchange>
  cancelExchange(id: string): Promise<Exchange>

  submitFeedback(exchangeId: string, payload: CreateFeedbackPayload): Promise<Feedback>
  getUserFeedback(userId: string): Promise<Feedback[]>
}
