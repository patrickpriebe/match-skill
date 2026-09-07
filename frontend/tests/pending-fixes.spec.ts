import { test, expect, type Locator } from '@playwright/test'
import { fixtureApi } from './fixtures'

test('long meeting URL stays intact, blocks 2049 and submits 2048 after a server validation error', async ({ page }) => {
  const fixture = await fixtureApi(page)
  let rejected = false
  await page.route('**/api/exchanges/accepted/schedule', async (route) => {
    if (!rejected) {
      rejected = true
      return route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'VALIDATION_ERROR', message: 'Meeting URL was rejected by the server.' }) })
    }
    return route.fallback()
  })
  await page.goto('/scheduled/accepted')
  await page.getByLabel('Date', { exact: true }).fill('2027-01-12')
  await page.getByLabel(/Start time/).fill('14:00')
  const input = page.getByLabel('Meeting link', { exact: true })
  const confirm = page.getByRole('button', { name: 'Confirm the meeting' })
  const link = 'https://meet.google.com/room?context='.padEnd(2048, 'a')
  await input.fill(link + 'a')
  await expect(input).toHaveValue(link + 'a')
  await expect(input).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByText('Meeting links can contain at most 2048 characters.')).toBeVisible()
  await expect(confirm).toBeDisabled()
  expect(fixture.requests.filter((r) => r.path.endsWith('/schedule'))).toHaveLength(0)
  await input.fill(link)
  await confirm.click()
  await expect(page.getByRole('alert')).toContainText('Meeting URL was rejected by the server.')
  await expect(input).toHaveValue(link)
  await expect(page.getByLabel('Date', { exact: true })).toHaveValue('2027-01-12')
  await confirm.click()
  await expect(page).toHaveURL(/exchanges\/accepted/)
  expect(fixture.requests.find((r) => r.path.endsWith('/schedule'))?.body?.meetingUrl).toBe(link)
})

for (const [status, code, name, message] of [
  [400, 'INVALID_SKILL_NAME', '+++', 'Enter a meaningful skill name'],
  [409, 'SKILL_IDENTITY_CONFLICT', 'C++', 'This name conflicts with an existing skill'],
] as const) {
  test(`suggestion ${code} preserves draft and only adds a chip after a successful retry`, async ({ page }) => {
    await fixtureApi(page)
    let reject = true
    await page.route('**/api/skills/suggest', (route) => route.fulfill({
      status: reject ? status : 201, contentType: 'application/json',
      body: JSON.stringify(reject ? { code, message: 'Server rejected suggestion.' } : { id: 'new-skill', name: 'C++', slug: 'server-owned-slug', status: 'PENDING_REVIEW' }),
    }))
    await page.goto('/skills/register')
    const input = page.getByRole('combobox').first()
    const field = page.locator('.reg-cols .field').first()
    await input.fill(name)
    await page.getByRole('option', { name: /Suggest/ }).click()
    await expect(field.getByRole('alert')).toContainText(message)
    await expect(field.getByRole('alert')).toContainText('Your suggestion was not saved.')
    await expect(input).toHaveValue(name)
    await expect(field.locator('.chip')).toHaveCount(1)
    reject = false
    if (name === '+++') await input.fill('C++')
    await input.focus()
    await page.getByRole('option', { name: /Suggest/ }).click()
    await expect(field.locator('.chip')).toHaveCount(2)
    await expect(input).toHaveValue('')
    await expect(field.getByRole('alert')).toHaveCount(0)
  })
}

test('single-letter and symbol suggestions remain separate pending entries and cannot be saved to a profile', async ({ page }) => {
  const fixture = await fixtureApi(page)
  await page.route('**/api/skills/suggest', (route) => {
    const name = route.request().postDataJSON().name as string
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 'id-' + name, name, slug: 'opaque-' + name.length, status: 'PENDING_REVIEW' }) })
  })
  await page.goto('/skills/register')
  const input = page.getByRole('combobox').first()
  for (const name of ['C', 'C++', 'C#']) {
    await input.fill(name)
    await page.getByRole('option', { name: /Suggest/ }).click()
    await expect(input).toHaveValue('')
  }
  await expect(page.getByRole('button', { name: 'Save and find matches' })).toBeDisabled()
  await expect(page.getByText(/Pending suggestions must be approved/)).toBeVisible()
  for (const name of ['C', 'C++', 'C#']) await page.getByRole('button', { name: 'Remove ' + name, exact: true }).click()
  await page.getByRole('button', { name: 'Save and find matches' }).click()
  await expect(page).toHaveURL(/home/)
  expect(fixture.requests.find((r) => r.path === '/me/skills' && r.method === 'PUT')?.body?.offeredSkillIds).toEqual(['java'])
})

async function hitTest(target: Locator) {
  await target.evaluate((node) => node.scrollIntoView({ block: 'center' }))
  return target.evaluate((node) => {
    const r = node.getBoundingClientRect()
    const hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
    return { top: r.top, bottom: r.bottom, hit: node === hit || node.contains(hit),
      scroll: scrollY, maxScroll: document.documentElement.scrollHeight - innerHeight,
      obstruction: hit?.tagName + '.' + hit?.className }
  })
}

for (const [width, height] of [[375, 900], [375, 667], [320, 568], [375, 320]]) {
  test(`visual closure scroll and hit tests at ${width}x${height}`, async ({ page }, info) => {
    await page.setViewportSize({ width, height })
    await fixtureApi(page)
    const measurements: Record<string, unknown> = {}
    await page.goto('/skills/register')
    const input = page.getByRole('combobox').nth(1)
    await expect(input).toBeEnabled()
    await page.screenshot({ path: info.outputPath('onboarding-initial.png') })
    measurements.onboardingInput = await hitTest(input)
    expect.soft(measurements.onboardingInput).toMatchObject({ hit: true })
    await input.click()
    await input.fill('Python')
    const option = page.getByRole('option', { name: 'Python approved' })
    await expect(option).toBeVisible()
    measurements.onboardingOption = await hitTest(option)
    expect.soft(measurements.onboardingOption).toMatchObject({ hit: true })
    await option.click()
    await page.evaluate(() => scrollTo(0, document.documentElement.scrollHeight))
    measurements.onboardingFooter = await hitTest(page.getByRole('button', { name: 'Save and find matches' }))
    expect.soft(measurements.onboardingFooter).toMatchObject({ hit: true })
    await page.screenshot({ path: info.outputPath('onboarding-bottom.png') })

    for (const route of ['/availability', '/feedback/completed', '/scheduled/accepted', '/profile/sam']) {
      await page.goto(route)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      await expect(page.getByRole('status', { name: /Loading/ })).toHaveCount(0)
      const lastControl = page.locator('main button:enabled, main a[href], main input').last()
      measurements[route] = await hitTest(lastControl)
      expect.soft(measurements[route], route).toMatchObject({ hit: true })
      await page.evaluate(() => scrollTo(0, document.documentElement.scrollHeight))
      const geometry = await page.locator('main').evaluate((main) => {
        const nav = document.querySelector('.tabbar')!
        return { contentBottom: main.getBoundingClientRect().bottom, navTop: nav.getBoundingClientRect().top,
          navPosition: getComputedStyle(nav).position, viewport: innerHeight }
      })
      measurements[route + ':bottom'] = geometry
      expect.soft(geometry.contentBottom, route).toBeLessThanOrEqual(geometry.navTop + 1)
      await page.screenshot({ path: info.outputPath(route.replaceAll('/', '_') + '-bottom.png') })
    }
    await page.goto('/invitations')
    await expect(page.getByRole('tab').first()).toBeVisible()
    measurements.tab = await page.getByRole('tab').first().evaluate((tab) => {
      const label = document.createRange()
      label.selectNodeContents(tab.firstChild!)
      const labelRect = label.getBoundingClientRect()
      const count = tab.querySelector('.count')!.getBoundingClientRect()
      return { gap: count.left - labelRect.right, display: getComputedStyle(tab).display }
    })
    await page.screenshot({ path: info.outputPath('invitation-tabs.png') })
    await info.attach('geometry', { body: JSON.stringify(measurements, null, 2), contentType: 'application/json' })
    expect.soft((measurements.tab as { gap: number }).gap).toBeGreaterThanOrEqual(6)
  })
}
