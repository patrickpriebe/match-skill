import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '@/lib/api/types'

export type AsyncState<T> =
  | { status: 'loading'; data: null; error: null }
  | { status: 'ready'; data: T; error: null }
  | { status: 'error'; data: null; error: ApiError }

/**
 * One place where loading and error states are produced, so every screen
 * reaches them the same way and none of them invents a third representation.
 */
export function useAsync<T>(run: () => Promise<T>, deps: unknown[] = []): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading', data: null, error: null })
  const [nonce, setNonce] = useState(0)

  const reload = useCallback(() => setNonce((n) => n + 1), [])

  useEffect(() => {
    let cancelled = false
    setState({ status: 'loading', data: null, error: null })
    run()
      .then((data) => { if (!cancelled) setState({ status: 'ready', data, error: null }) })
      .catch((err) => {
        if (cancelled) return
        const error = err instanceof ApiError
          ? err
          : new ApiError(0, { code: 'UNKNOWN', message: 'Something went wrong.' })
        setState({ status: 'error', data: null, error })
      })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { ...state, reload }
}
