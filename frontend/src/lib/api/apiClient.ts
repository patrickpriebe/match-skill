import type { realApiClient } from './realApiClient'

/** The production API contract used by every screen. */
export type ApiClient = typeof realApiClient
