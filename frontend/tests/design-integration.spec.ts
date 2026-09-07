import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { fixtureApi } from './fixtures'

for (const width of [320, 375, 768, 1440]) {
  test(`approved screens render without overflow at ${width}px`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 900 })
    await fixtureApi(page)
    const errors: string[] = []
    page.on('pageerror', (e) => errors.push(e.message))
    const screens = [
      ['/home', 'Matches'], ['/search', 'Search'], ['/profile/sam', 'Sam Rivera'],
      ['/invitations', 'Invitations'], ['/scheduled', 'Scheduled exchanges'],
      ['/exchanges/scheduled', 'Exchange with Sam Rivera'], ['/scheduled/accepted', 'Settle a time with Sam'],
      ['/history', 'History'], ['/feedback/completed', 'How did it go with Sam?'],
      ['/availability', 'Availability'], ['/skills/register', 'Two lists decide every match you will ever see.'],
    ]
    for (const [url, heading] of screens) {
      await page.goto(url)
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
      await expect(page.getByRole('status', { name: /Loading/ })).toHaveCount(0)
      const overflow = await page.evaluate(() => ({ body: document.body.scrollWidth, viewport: innerWidth }))
      expect(overflow.body, url).toBeLessThanOrEqual(overflow.viewport)
      if (width === 375 || width === 1440) {
        await page.screenshot({ path: info.outputPath(url.replaceAll('/', '_') + '.png'), fullPage: true })
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()
        expect(results.violations.map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.target) })), url).toEqual([])
      }
    }
    expect(errors).toEqual([])
  })
}

test('registration refreshes the session and opens real matches', async ({ page }) => {
  const fixture = await fixtureApi(page, { signedIn: false, registered: false })
  await page.goto('/login')
  await page.getByRole('button', { name: 'Create an account', exact: true }).click()
  await page.getByLabel('Display name').fill('Alex Morgan')
  await page.getByLabel('Email', { exact: true }).fill('alex@example.com')
  await page.getByLabel('Password', { exact: true }).fill('Secret123!')
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await expect(page).toHaveURL(/skills\/register/)
  const combos = page.getByRole('combobox')
  await combos.nth(0).fill('Java')
  await page.getByRole('option', { name: 'Java approved' }).click()
  await combos.nth(1).fill('React')
  await page.getByRole('option', { name: 'React approved' }).click()
  await page.getByRole('button', { name: 'Save and find matches' }).click()
  await expect(page.getByRole('heading', { name: 'Matches', exact: true })).toBeVisible()
  expect(fixture.requests.find((r) => r.path === '/me/skills' && r.method === 'PUT')?.body).toEqual({ offeredSkillIds: ['java'], wantedSkillIds: ['react'] })
  expect(fixture.requests.find((r) => r.path === '/auth/register')?.body).toMatchObject({ displayName: 'Alex Morgan', timeZone: expect.any(String) })
})

test('home requests the wanted skill, handles an existing exchange, and traps modal focus', async ({ page }) => {
  const fixture = await fixtureApi(page)
  await page.goto('/home')
  await page.getByRole('button', { name: 'Send request' }).first().click()
  await expect(page.getByRole('link', { name: 'Open exchange', exact: true })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: 'Send request' }).nth(1).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('radio', { name: 'React' })).toHaveAttribute('aria-checked', 'true')
  for (let n = 0; n < 8; n++) {
    await page.keyboard.press('Tab')
    expect(await dialog.evaluate((el) => el.contains(document.activeElement))).toBe(true)
  }
  await dialog.getByRole('button', { name: 'Send request', exact: true }).click()
  await expect(page).toHaveURL(/exchanges\/new/)
  expect(fixture.requests.find((r) => r.path === '/exchanges' && r.method === 'POST')?.body).toEqual({ receiverId: 'lee', skillFromReceiver: 'react' })
})

test('acceptance sends a chosen requester skill and scheduled view filters correctly', async ({ page }) => {
  const fixture = await fixtureApi(page)
  await page.goto('/invitations')
  await page.getByRole('button', { name: /^Accept/ }).click()
  await page.getByRole('radio', { name: 'Python' }).click()
  await page.getByRole('button', { name: 'Accept and settle the trade' }).click()
  await expect(page.getByRole('link', { name: 'Settle a time' })).toBeVisible()
  expect(fixture.requests.find((r) => r.path === '/exchanges/incoming/accept')?.body).toEqual({ skillFromRequester: 'python' })
  await page.goto('/scheduled')
  await expect(page.getByRole('heading', { name: 'Scheduled exchanges' })).toBeVisible()
  expect(fixture.requests.some((r) => r.path === '/exchanges')).toBe(true)
  await page.getByRole('tab', { name: /Sent/ }).click()
  await expect(page.locator('.rec-row')).toHaveCount(1)
})

test('scheduling uses the account time zone and preserves input on API failure', async ({ page }) => {
  const fixture = await fixtureApi(page)
  await page.goto('/scheduled/accepted')
  await page.getByLabel('Date', { exact: true }).fill('2027-01-12')
  await page.getByLabel(/Start time/).fill('14:00')
  await page.getByLabel(/Meeting link/).fill('https://meet.google.com/test-room')
  fixture.fail['/exchanges/accepted/schedule'] = 409
  await page.getByRole('button', { name: 'Confirm the meeting' }).click()
  await expect(page.getByRole('alert')).toContainText('The server rejected this action.')
  await expect(page.getByLabel('Date', { exact: true })).toHaveValue('2027-01-12')
  fixture.fail['/exchanges/accepted/schedule'] = 0
  await page.getByRole('button', { name: 'Confirm the meeting' }).click()
  await expect(page).toHaveURL(/exchanges\/accepted/)
  expect(fixture.requests.find((r) => r.path === '/exchanges/accepted/schedule')?.body).toMatchObject({ scheduledAt: '2027-01-12T17:00:00.000Z' })
})

test('availability persists timeZone and refreshes account; feedback is private then published', async ({ page }) => {
  const fixture = await fixtureApi(page)
  await page.goto('/availability')
  await page.getByLabel('Your time zone').selectOption('America/New_York')
  await page.getByRole('button', { name: 'Save availability' }).click()
  await expect(page.getByText('Availability saved.')).toBeVisible()
  expect(fixture.user.timeZone).toBe('America/New_York')
  expect(fixture.requests.find((r) => r.path === '/me/availability' && r.method === 'PUT')?.body).toHaveProperty('timeZone', 'America/New_York')
  await page.goto('/feedback/completed')
  await page.getByRole('radio', { name: '4 stars', exact: true }).click()
  await page.getByLabel('Comment (optional)').fill('Useful examples')
  await page.getByRole('button', { name: 'Submit rating' }).click()
  await expect(page.getByRole('heading', { name: 'Your rating is saved' })).toBeVisible()
  await expect(page.getByText(/Your rating is saved privately/)).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Submit rating' })).toHaveCount(0)
  fixture.publish()
  await page.reload()
  await expect(page.getByText(/Both ratings are published/)).toBeVisible()
  await expect(page.getByText('A thoughtful teacher')).toBeVisible()
})

test('OAuth callback consumes the token and removes it from the URL', async ({ page }) => {
  await fixtureApi(page, { signedIn: false })
  await page.goto('/auth/callback?token=fixture-token')
  await expect(page.getByRole('heading', { name: 'Matches', exact: true })).toBeVisible()
  expect(page.url()).not.toContain('token=')
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Matches', exact: true })).toBeVisible()
})

test('failed skill lookup does not offer to suggest a new term', async ({ page }) => {
  const fixture = await fixtureApi(page)
  fixture.fail['/skills'] = 503
  await page.goto('/skills/register')
  await page.getByRole('combobox').first().fill('Something new')
  await expect(page.getByRole('alert')).toContainText('Cannot reach the skill list')
  await expect(page.getByRole('option', { name: /Suggest/ })).toHaveCount(0)
})

test('login is responsive and dark surfaces remain accessible', async ({ page }, info) => {
  await fixtureApi(page, { signedIn: false })
  for (const width of [320, 375, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.body.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath('login-' + width + '.png'), fullPage: true })
    expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()).violations).toEqual([])
  }
  await page.evaluate(() => { localStorage.setItem('match-skill-token', 'fixture-token') })
  await page.goto('/home')
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await expect(page.getByRole('heading', { name: 'Matches', exact: true })).toBeVisible()
  await page.screenshot({ path: info.outputPath('home-dark.png'), fullPage: true })
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()).violations.map((v) => v.id)).toEqual([])
})

test('match pagination reaches the next page without losing the server ranking', async ({ page }) => {
  await fixtureApi(page)
  await page.route('**/api/matches?*', (route) => {
    const second = new URL(route.request().url()).searchParams.get('page') === '1'
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: [{ userId: second ? 'lee' : 'sam', displayName: second ? 'Lee Chen' : 'Sam Rivera', bio: null, strength: second ? 'PARTIAL' : 'MUTUAL', reputationAverage: 0, reputationCount: 0 }], page: second ? 1 : 0, size: 12, total: 13 }) })
  })
  await page.goto('/home')
  await expect(page.locator('.mcard')).toContainText('Sam Rivera')
  await page.getByRole('button', { name: 'Next', exact: true }).click()
  await expect(page.locator('.mcard')).toContainText('Lee Chen')
  await expect(page.getByRole('navigation', { name: 'Pagination' })).toContainText('13')
  await expect(page.getByRole('button', { name: 'Next', exact: true })).toBeDisabled()
})

test('search and profile request buttons submit supported exchange payloads', async ({ page }) => {
  const fixture = await fixtureApi(page)
  await page.goto('/search')
  await page.getByRole('combobox').fill('React')
  await page.getByRole('option', { name: 'React approved' }).click()
  await page.getByRole('button', { name: 'Send request' }).nth(1).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Send request', exact: true }).click()
  await expect(page).toHaveURL(/exchanges\/new/)
  expect(fixture.requests.find((r) => r.path === '/exchanges' && r.method === 'POST')?.body).toEqual({ receiverId: 'lee', skillFromReceiver: 'react' })
  await page.goto('/profile/lee')
  await page.getByRole('button', { name: 'Send request', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('link', { name: 'Open exchange', exact: true })).toBeVisible()
})
