import { Notice } from '../ui/Surface'
import { Button } from '../ui/Button'
import { useT } from '@/i18n/I18nContext'
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
  const t = useT()
  const isNetwork = error.code === 'NETWORK' || error.status === 0

  return (
    <Notice tone="stop">
      <b>{isNetwork ? t('errorState.networkTitle') : (context ?? t('errorState.genericTitle'))}</b>
      <br />
      {isNetwork
        ? t('errorState.networkBody')
        : error.message}
      {onRetry && (
        <>
          <br />
          <Button size="sm" onClick={onRetry} style={{ marginTop: 10 }}>{t('errorState.tryAgain')}</Button>
        </>
      )}
    </Notice>
  )
}
