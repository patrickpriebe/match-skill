export const MEETING_URL_MAX_LENGTH = 2048

export function meetingUrlError(value: string): string | null {
  const link = value.trim()
  if (link.length > MEETING_URL_MAX_LENGTH) return `Meeting links can contain at most ${MEETING_URL_MAX_LENGTH} characters.`
  try {
    const url = new URL(link)
    if (url.protocol === 'https:' && url.hostname) return null
  } catch { /* Keep the user's input intact so it can be corrected. */ }
  return 'Enter a valid HTTPS meeting link.'
}
