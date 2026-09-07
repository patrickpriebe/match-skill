/**
 * Hosts currently configured on the server. Written as examples, never as a
 * closed list: the allowlist is configuration and can grow without a release,
 * so a UI that promises exactly four starts lying the day ops adds a fifth.
 * When a config endpoint exists, pass the list in and the copy generates
 * itself — open question Q13.
 */
export const DEFAULT_MEETING_HOSTS = [
  { host: 'zoom.us', name: 'Zoom' },
  { host: 'meet.google.com', name: 'Google Meet' },
  { host: 'teams.microsoft.com', name: 'Microsoft Teams' },
  { host: 'whereby.com', name: 'Whereby' },
]

