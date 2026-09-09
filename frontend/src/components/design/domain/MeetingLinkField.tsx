import { Field, Input } from '../ui/Form'

import { DEFAULT_MEETING_HOSTS } from './meeting-hosts'
import { meetingUrlError, MEETING_URL_MAX_LENGTH } from '@/lib/meetingUrl'
import { useT } from '@/i18n/I18nContext'

function recognise(value: string, hosts: typeof DEFAULT_MEETING_HOSTS) {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:') return { ok: false as const }
    const match = hosts.find((h) => url.hostname === h.host || url.hostname.endsWith('.' + h.host))
    return match
      ? { ok: true as const, name: match.name }
      : { ok: false as const }
  } catch {
    return { ok: false as const }
  }
}

/**
 * An exchange is a channel to a stranger, so the link is checked server-side
 * against the allowlist and refused otherwise. The check here is convenience
 * only: the field never approves what the server refused, and never
 * pre-refuses something the server might accept — it just reports what it
 * recognises and shows the server's own message on rejection.
 */
export function MeetingLinkField({ value, onChange, serverError, hosts = DEFAULT_MEETING_HOSTS }: {
  value: string
  onChange: (value: string) => void
  serverError?: string | null
  hosts?: typeof DEFAULT_MEETING_HOSTS
}) {
  const t = useT()
  const local = value.trim() ? recognise(value.trim(), hosts) : null
  const error = serverError ?? (value.trim() ? meetingUrlError(value) : null)
  const names = hosts.map((h) => h.name)
  const listed = names.slice(0, -1).join(', ') + t('meetingLink.and') + names[names.length - 1]

  return (
    <Field
      label={t('meetingLink.label')}
      error={error}
      ok={!error && local?.ok ? t('meetingLink.recognisedAs', { name: local.name }) : undefined}
      hint={t('meetingLink.hint', { max: MEETING_URL_MAX_LENGTH, listed })}
    >
      {(id, invalid) => (
        <Input
          id={id}
          type="url"
          value={value}
          invalid={invalid}
          placeholder="https://"
          onChange={(e) => onChange(e.target.value)}
        />
      )}
    </Field>
  )
}
