import { ApiError, type ApiErrorBody } from './types'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '')

let authToken: string | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | undefined>
}

function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return ''
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}${buildQuery(options.query)}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  }).catch(() => { throw new ApiError(0, { code: 'NETWORK', message: 'Could not reach the server. Check your connection and try again.' }) })

  if (res.status === 204) return undefined as T

  const data = await res.json().catch(() => null)

  if (!res.ok) {
    const body: ApiErrorBody = data ?? {
      code: 'UNKNOWN_ERROR',
      message: res.statusText || 'Request failed',
    }
    if (res.status === 401 && authToken && !path.startsWith('/auth/') && typeof window !== 'undefined') {
      window.dispatchEvent(new Event('match-skill:unauthorized'))
    }
    throw new ApiError(res.status, body)
  }

  return data as T
}
