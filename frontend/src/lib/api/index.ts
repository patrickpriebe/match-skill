import { realApiClient } from './realApiClient'
import { setAuthToken as setHttpAuthToken } from './http'

// The application always uses the Spring API. Fixtures stay in tests only.
export const api = realApiClient

export function setApiAuthToken(token: string | null) {
  setHttpAuthToken(token)
}

export * from './types'
export type { ApiClient } from './apiClient'
