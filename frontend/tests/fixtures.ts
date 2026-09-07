import type { Page, Route } from '@playwright/test'
import type { Exchange, Feedback, Skill } from '../src/lib/api/types'
export const skills: Skill[] = [
  { id: 'java', name: 'Java', slug: 'java', status: 'APPROVED' },
  { id: 'react', name: 'React', slug: 'react', status: 'APPROVED' },
  { id: 'python', name: 'Python', slug: 'python', status: 'APPROVED' },
]
export async function fixtureApi(page: Page, { signedIn = true, registered = true, completedRating = false } = {}) {
  const user = { id: 'me', email: 'alex@example.com', displayName: 'Alex Morgan', timeZone: 'America/Sao_Paulo', skillsRegistered: registered }
  const windows = [{ id: 'window', dayOfWeek: 'MONDAY', startTime: '09:00:00', endTime: '11:00:00' }]
  const profiles = Object.fromEntries(['me', 'sam', 'lee'].map((id) => [id, {
    id, displayName: id === 'me' ? 'Alex Morgan' : id === 'sam' ? 'Sam Rivera' : 'Lee Chen',
    bio: 'I enjoy learning through practical examples and sharing what I know.', timeZone: id === 'me' ? user.timeZone : 'Asia/Tokyo',
    skillsOffered: id === 'me' ? [skills[0]] : [skills[2], skills[1]],
    skillsWanted: id === 'sam' ? [skills[0]] : [skills[1]], reputationAverage: 0, reputationCount: 0, availability: windows,
  }]))
  const make = (id: string, status: Exchange['status'], incoming = false): Exchange => ({
    id, requesterId: incoming ? 'sam' : 'me', receiverId: incoming ? 'me' : 'sam',
    skillFromReceiver: incoming ? skills[0] : skills[1], skillFromRequester: status === 'REQUESTED' ? null : incoming ? skills[1] : skills[0],
    strength: 'PARTIAL', status, scheduledAt: ['SCHEDULED', 'COMPLETED'].includes(status) ? '2027-01-12T17:00:00Z' : null,
    meetingUrl: ['SCHEDULED', 'COMPLETED'].includes(status) ? 'https://meet.google.com/test-room' : null,
    createdAt: '2026-09-01T10:00:00Z', updatedAt: '2026-09-05T10:00:00Z',
  })
  const exchanges = [make('incoming', 'REQUESTED', true), make('accepted', 'ACCEPTED'), make('scheduled', 'SCHEDULED'), make('completed', 'COMPLETED'), make('declined', 'DECLINED')]
  const feedback: Record<string, Feedback | null> = { completed: completedRating ? { id: 'mine', exchangeId: 'completed', authorId: 'me', rating: 4, comment: 'Useful examples', createdAt: '2026-09-05T10:00:00Z' } : null }
  let counterpartSubmitted = false
  const requests: Array<{ path: string; method: string; body: Record<string, unknown> | null }> = []
  const fail: Record<string, number> = {}
  const json = (route: Route, data: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  await page.addInitScript(({ signedIn }) => { if (signedIn) localStorage.setItem('match-skill-token', 'fixture-token') }, { signedIn })
  await page.route('**/api/**', async (route) => {
    const req = route.request(), url = new URL(req.url())
    if (!url.pathname.startsWith('/api/')) return route.continue()
    const path = url.pathname.replace('/api', '')
    const body = req.postDataJSON() as Record<string, unknown> | null
    requests.push({ path, method: req.method(), body })
    if (fail[path]) return json(route, { code: 'TEST_FAILURE', message: 'The server rejected this action.' }, fail[path])
    if (path === '/auth/login' || path === '/auth/register') return json(route, { token: 'fixture-token' })
    if (path === '/auth/me') return json(route, user)
    if (path === '/skills') return json(route, { items: skills.filter((s) => s.name.toLowerCase().includes((url.searchParams.get('query') ?? '').toLowerCase())), total: 3, page: 0, size: 20 })
    if (path === '/skills/suggest') return json(route, { id: 'pending', name: body?.name, slug: 'pending', status: 'PENDING_REVIEW' })
    if (path === '/me/skills') {
      if (req.method() === 'PUT') user.skillsRegistered = true
      return json(route, { offered: registered || user.skillsRegistered ? [{ id: 'row1', skill: skills[0], direction: 'OFFERED' }] : [], wanted: registered || user.skillsRegistered ? [{ id: 'row2', skill: skills[1], direction: 'WANTED' }] : [] })
    }
    if (path === '/me/availability') {
      if (req.method() === 'PUT') { user.timeZone = body?.timeZone as string; profiles.me.timeZone = user.timeZone }
      return json(route, windows)
    }
    if (path === '/matches' || path === '/search') return json(route, { items: ['sam', 'lee'].map((id) => ({ userId: id, displayName: profiles[id].displayName, bio: profiles[id].bio, strength: id === 'sam' ? 'MUTUAL' : 'PARTIAL', reputationAverage: 0, reputationCount: 0 })), page: Number(url.searchParams.get('page') ?? 0), size: 12, total: 2 })
    if (path.startsWith('/users/') && path.endsWith('/feedback')) return json(route, [])
    if (path.startsWith('/users/')) return json(route, profiles[path.split('/')[2]])
    if (path === '/exchanges') {
      if (req.method() === 'POST') { const ex = { ...make('new', 'REQUESTED'), receiverId: body?.receiverId as string, skillFromReceiver: skills.find((s) => s.id === body?.skillFromReceiver)! }; exchanges.push(ex); return json(route, ex, 201) }
      const items = exchanges.filter((e) => !url.searchParams.get('status') || e.status === url.searchParams.get('status'))
      return json(route, { items, page: 0, size: 12, total: items.length })
    }
    if (path.startsWith('/exchanges/')) {
      const [, , id, action] = path.split('/')
      const exchange = exchanges.find((e) => e.id === id)
      if (!exchange) return json(route, { code: 'EXCHANGE_NOT_FOUND', message: 'Exchange not found' }, 404)
      if (action === 'feedback') {
        if (req.method() === 'POST') { feedback[id] = { id: 'review', exchangeId: id, authorId: 'me', rating: body?.rating as number, comment: body?.comment as string, createdAt: '2026-09-05T12:00:00Z' }; return json(route, feedback[id], 201) }
        return json(route, { mine: feedback[id] ?? null, theirs: feedback[id] && counterpartSubmitted ? { id: 'theirs', exchangeId: id, authorId: 'sam', rating: 5, comment: 'A thoughtful teacher', createdAt: '2026-09-05T12:00:00Z' } : null, counterpartSubmitted })
      }
      if (action === 'accept') { exchange.status = 'ACCEPTED'; exchange.skillFromRequester = skills.find((s) => s.id === body?.skillFromRequester)! }
      if (action === 'decline') exchange.status = 'DECLINED'
      if (action === 'cancel') exchange.status = 'CANCELLED'
      if (action === 'complete') exchange.status = 'COMPLETED'
      if (action === 'schedule') { exchange.status = 'SCHEDULED'; exchange.scheduledAt = body?.scheduledAt as string; exchange.meetingUrl = body?.meetingUrl as string }
      return json(route, exchange)
    }
    return json(route, { code: 'UNEXPECTED_TEST_REQUEST', message: path }, 501)
  })
  return { user, exchanges, feedback, requests, fail, publish: () => { counterpartSubmitted = true } }
}
