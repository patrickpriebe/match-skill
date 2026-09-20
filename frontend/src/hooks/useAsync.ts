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
export function useAsync<T>(
  run: (signal: AbortSignal) => Promise<T>,
  deps: unknown[] = [],
): AsyncState<T> & { reload: () => void } {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading', data: null, error: null })
  const [nonce, setNonce] = useState(0)

  const reload = useCallback(() => setNonce((n) => n + 1), [])

  useEffect(() => {
    // Ignoring a late answer was never enough: the request itself kept running,
    // and leaving a screen mid-load left its work in flight against an instance
    // that is slow precisely when someone is clicking around impatiently.
    const controller = new AbortController()
    setState({ status: 'loading', data: null, error: null })
    run(controller.signal)
      .then((data) => { if (!controller.signal.aborted) setState({ status: 'ready', data, error: null }) })
      .catch((err) => {
        if (controller.signal.aborted) return
        const error = err instanceof ApiError
          ? err
          : new ApiError(0, { code: 'UNKNOWN', message: 'Something went wrong.' })
        setState({ status: 'error', data: null, error })
      })
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { ...state, reload }
}
