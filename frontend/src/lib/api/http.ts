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
  /** Aborted by the caller when the result stopped being wanted. */
  signal?: AbortSignal
}

/**
 * Generous on purpose. The API runs on an instance that sleeps, and a measured
 * cold start is 71 seconds: a conventional ten- or thirty-second timeout would
 * cancel exactly the request that was about to succeed and report it as a
 * network failure. Past this ceiling something is genuinely wrong, and hanging
 * forever is worse than saying so.
 */
const TIMEOUT_MS = 90_000

function deadline(caller?: AbortSignal): AbortSignal {
  const timeout = AbortSignal.timeout(TIMEOUT_MS)
  if (!caller) return timeout
  // Either reason ends the request: the caller gave up, or time ran out.
  return typeof AbortSignal.any === 'function' ? AbortSignal.any([caller, timeout]) : caller
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
    signal: deadline(options.signal),
  }).catch((cause: unknown) => {
    // A caller that walked away is not a failure and must not paint an error
    // over whatever screen they moved to. Only the timeout and real network
    // faults are reportable.
    if (options.signal?.aborted) throw new ApiError(0, { code: 'ABORTED', message: 'Request cancelled.' })
    if (cause instanceof DOMException && cause.name === 'TimeoutError') {
      throw new ApiError(0, { code: 'TIMEOUT', message: 'The server took too long to answer. Try again.' })
    }
    throw new ApiError(0, { code: 'NETWORK', message: 'Could not reach the server. Check your connection and try again.' })
  })

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
