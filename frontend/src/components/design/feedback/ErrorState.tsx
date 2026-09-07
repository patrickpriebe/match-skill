import { Notice } from '../ui/Surface'
import { Button } from '../ui/Button'
import type { ApiError } from '@/lib/api/types'

/**
 * A network failure and a rule violation are different things, and the copy
 * says which. `code` is what we branch on; `message` is what the server wrote
 * for people, and it is shown verbatim rather than paraphrased.
 */
export function ErrorState({ error, onRetry, context }: {
  error: ApiError
  onRetry?: () => void
  context?: string
}) {
  const isNetwork = error.code === 'NETWORK' || error.status === 0

  return (
    <Notice tone="stop">
      <b>{isNetwork ? 'We could not reach the server.' : (context ?? 'That did not work.')}</b>
      <br />
      {isNetwork
        ? 'Nothing was changed on our side — this is a connection problem, not lost data.'
        : error.message}
      {onRetry && (
        <>
          <br />
          <Button size="sm" onClick={onRetry} style={{ marginTop: 10 }}>Try again</Button>
        </>
      )}
    </Notice>
  )
}
