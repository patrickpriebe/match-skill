# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: pending-fixes.spec.ts >> visual closure scroll and hit tests at 375x667
- Location: tests\pending-fixes.spec.ts:16:3

# Error details

```
Error: expect(received).toBeGreaterThanOrEqual(expected)

Expected: >= 6
Received:    0
```

# Page snapshot

```yaml
- generic [ref=f5e3]:
  - link "Skip to content" [ref=f5e4] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=f5e5]:
    - generic [ref=f5e7]:
      - link "match·skill" [ref=f5e8] [cursor=pointer]:
        - /url: /matches
      - button "Sign out" [ref=f5e12] [cursor=pointer]
    - main [ref=f5e13]:
      - generic [ref=f5e14]:
        - heading "Invitations" [level=1] [ref=f5e15]
        - paragraph [ref=f5e16]: A request is a commitment to a stranger's time. Nothing is automatic — you answer each one, and only you can accept or decline what was sent to you.
      - tablist "Invitation direction" [ref=f5e17]:
        - tab "Received1" [selected] [ref=f5e18] [cursor=pointer]
        - tab "Sent3" [ref=f5e19] [cursor=pointer]
      - paragraph [ref=f5e20]: Received and sent counts refer to this page. Use Next to see older exchanges.
      - generic [ref=f5e21]:
        - generic [ref=f5e22]:
          - generic [ref=f5e23]:
            - generic [aria-hidden] [ref=f5e24]: SR
            - generic [ref=f5e25]:
              - generic [ref=f5e26]: Sam Rivera
              - text: Asia/Tokyo
          - generic [ref=f5e27]:
            - generic [ref=f5e28]: Requested
            - generic [ref=f5e33]: Partial
        - generic [ref=f5e34]:
          - generic [ref=f5e35]:
            - generic [ref=f5e36]: They learn
            - generic [ref=f5e37]:
              - text: Java
              - generic [ref=f5e38]: from you
          - generic [ref=f5e39]:
            - generic [ref=f5e40]: You learn
            - generic [ref=f5e41]: not settled yet
        - generic [ref=f5e42]:
          - button "Accept…" [ref=f5e43] [cursor=pointer]
          - button "Decline" [ref=f5e44] [cursor=pointer]
      - navigation "Pagination" [ref=f5e45]:
        - generic [ref=f5e46]: 1–5 of 5
        - generic [ref=f5e47]:
          - button "Previous" [disabled]
          - button "Next" [disabled]
    - navigation "Main" [ref=f5e48]:
      - link "Matches" [ref=f5e49] [cursor=pointer]:
        - /url: /home
      - link "Search" [ref=f5e50] [cursor=pointer]:
        - /url: /search
      - link "Invites" [ref=f5e51] [cursor=pointer]:
        - /url: /invitations
      - link "History" [ref=f5e52] [cursor=pointer]:
        - /url: /history
      - link "You" [ref=f5e53] [cursor=pointer]:
        - /url: /profile/me
```

# Test source

```ts
  1  | import { test, expect, type Locator } from '@playwright/test'
  2  | import { fixtureApi } from './fixtures'
  3  | 
  4  | async function hitTest(target: Locator) {
  5  |   await target.evaluate((node) => node.scrollIntoView({ block: 'center' }))
  6  |   return target.evaluate((node) => {
  7  |     const r = node.getBoundingClientRect()
  8  |     const hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
  9  |     return { top: r.top, bottom: r.bottom, hit: node === hit || node.contains(hit),
  10 |       scroll: scrollY, maxScroll: document.documentElement.scrollHeight - innerHeight,
  11 |       obstruction: hit?.tagName + '.' + hit?.className }
  12 |   })
  13 | }
  14 | 
  15 | for (const [width, height] of [[375, 900], [375, 667], [320, 568], [375, 320]]) {
  16 |   test(`visual closure scroll and hit tests at ${width}x${height}`, async ({ page }, info) => {
  17 |     await page.setViewportSize({ width, height })
  18 |     await fixtureApi(page)
  19 |     const measurements: Record<string, unknown> = {}
  20 |     await page.goto('/skills/register')
  21 |     const input = page.getByRole('combobox').nth(1)
  22 |     await expect(input).toBeEnabled()
  23 |     await page.screenshot({ path: info.outputPath('onboarding-initial.png') })
  24 |     measurements.onboardingInput = await hitTest(input)
  25 |     expect.soft(measurements.onboardingInput).toMatchObject({ hit: true })
  26 |     await input.click()
  27 |     await input.fill('Python')
  28 |     const option = page.getByRole('option', { name: 'Python approved' })
  29 |     await expect(option).toBeVisible()
  30 |     measurements.onboardingOption = await hitTest(option)
  31 |     expect.soft(measurements.onboardingOption).toMatchObject({ hit: true })
  32 |     await option.click()
  33 |     await page.evaluate(() => scrollTo(0, document.documentElement.scrollHeight))
  34 |     measurements.onboardingFooter = await hitTest(page.getByRole('button', { name: 'Save and find matches' }))
  35 |     expect.soft(measurements.onboardingFooter).toMatchObject({ hit: true })
  36 |     await page.screenshot({ path: info.outputPath('onboarding-bottom.png') })
  37 | 
  38 |     for (const route of ['/availability', '/feedback/completed', '/scheduled/accepted', '/profile/sam']) {
  39 |       await page.goto(route)
  40 |       await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  41 |       await expect(page.getByRole('status', { name: /Loading/ })).toHaveCount(0)
  42 |       const lastControl = page.locator('main button:enabled, main a[href], main input').last()
  43 |       measurements[route] = await hitTest(lastControl)
  44 |       expect.soft(measurements[route], route).toMatchObject({ hit: true })
  45 |       await page.evaluate(() => scrollTo(0, document.documentElement.scrollHeight))
  46 |       const geometry = await page.locator('main').evaluate((main) => {
  47 |         const nav = document.querySelector('.tabbar')!
  48 |         return { contentBottom: main.getBoundingClientRect().bottom, navTop: nav.getBoundingClientRect().top,
  49 |           navPosition: getComputedStyle(nav).position, viewport: innerHeight }
  50 |       })
  51 |       measurements[route + ':bottom'] = geometry
  52 |       expect.soft(geometry.contentBottom, route).toBeLessThanOrEqual(geometry.navTop + 1)
  53 |       await page.screenshot({ path: info.outputPath(route.replaceAll('/', '_') + '-bottom.png') })
  54 |     }
  55 |     await page.goto('/invitations')
  56 |     await expect(page.getByRole('tab').first()).toBeVisible()
  57 |     measurements.tab = await page.getByRole('tab').first().evaluate((tab) => {
  58 |       const label = document.createRange()
  59 |       label.selectNodeContents(tab.firstChild!)
  60 |       const labelRect = label.getBoundingClientRect()
  61 |       const count = tab.querySelector('.count')!.getBoundingClientRect()
  62 |       return { gap: count.left - labelRect.right, display: getComputedStyle(tab).display }
  63 |     })
  64 |     await page.screenshot({ path: info.outputPath('invitation-tabs.png') })
  65 |     await info.attach('geometry', { body: JSON.stringify(measurements, null, 2), contentType: 'application/json' })
> 66 |     expect.soft((measurements.tab as { gap: number }).gap).toBeGreaterThanOrEqual(6)
     |                                                            ^ Error: expect(received).toBeGreaterThanOrEqual(expected)
  67 |   })
  68 | }
  69 | 
```