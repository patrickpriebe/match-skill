import { test, expect } from '@playwright/test'

test.describe('Responsive & Visual Hit-Testing (320px & 375px Viewports)', () => {

  test('Onboarding 375px: Verify .reg-foot clearance and input interactability', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    const timestamp = Date.now()

    // 1. Register a user to access /skills/register
    await page.goto('/login')
    await page.getByRole('button', { name: 'Create an account' }).click()
    await page.getByLabel('Display name').fill(`Visual User ${timestamp}`)
    await page.getByLabel('Time zone').fill('America/Sao_Paulo')
    await page.getByLabel('Email').fill(`visual.${timestamp}@example.com`)
    await page.getByLabel('Password').fill('Password123!')
    await page.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(page).toHaveURL(/.*\/skills\/register/)

    // 2. Locate panels and footer
    const teachSection = page.locator('.reg-cols > div').first()
    const learnSection = page.locator('.reg-cols > div').nth(1)
    const foot = page.locator('.reg-foot')

    // 3. Test teach section input
    const teachInput = teachSection.getByRole('combobox')
    await teachInput.scrollIntoViewIfNeeded()
    await expect(teachInput).toBeVisible()
    await teachInput.fill('React')
    const reactOpt = page.getByRole('option', { name: /React/i })
    await expect(reactOpt).toBeVisible()
    await reactOpt.click()
    await expect(teachSection.getByText('React')).toBeVisible()

    // 4. Test learn section input (Check whether .reg-foot covers learnSection input or chip)
    const learnInput = learnSection.getByRole('combobox')
    await learnInput.scrollIntoViewIfNeeded()
    await expect(learnInput).toBeVisible()

    // Hit-test: verify learnInput receives pointer focus and clicks without interception
    await learnInput.click()
    await learnInput.fill('Java')
    const javaOpt = page.getByRole('option', { name: /Java/i })
    await expect(javaOpt).toBeVisible()
    await javaOpt.click()
    await expect(learnSection.getByText('Java')).toBeVisible()

    // 5. Bounding box audit between footer and chip
    if (await foot.isVisible()) {
      const footBox = await foot.boundingBox()
      const chip = learnSection.locator('.chips').first()
      if (footBox && (await chip.isVisible())) {
        const chipBox = await chip.boundingBox()
        if (chipBox) {
          // Check vertical overlap when scrolled
          const overlaps = chipBox.y + chipBox.height > footBox.y && chipBox.y < footBox.y + footBox.height
          // Record finding: if overlaps, footer occludes content in viewport
          console.log(`[Visual Audit] 375px Onboarding footer overlap with learn chips: ${overlaps}`)
        }
      }
    }

    // 6. Action button must be clickable
    const saveBtn = page.getByRole('button', { name: 'Save and find matches' })
    await expect(saveBtn).toBeEnabled()
    await saveBtn.click()
    await expect(page).toHaveURL(/.*\/home/)
  })

  let sharedEmail = ''

  test('Mobile Bottom Navigation 375px: Check clearance and hit-testing on /availability', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    const timestamp = Date.now()
    sharedEmail = `nav.${timestamp}@example.com`

    // Login user
    await page.goto('/login')
    await page.getByRole('button', { name: 'Create an account' }).click()
    await page.getByLabel('Display name').fill(`Nav User ${timestamp}`)
    await page.getByLabel('Time zone').fill('UTC')
    await page.getByLabel('Email').fill(sharedEmail)
    await page.getByLabel('Password').fill('Password123!')
    await page.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(page).toHaveURL(/.*\/skills\/register/)
    const teachInput = page.locator('.reg-cols > div').first().getByRole('combobox')
    await teachInput.fill('React')
    await page.getByRole('option', { name: /React/i }).click()
    const learnInput = page.locator('.reg-cols > div').nth(1).getByRole('combobox')
    await learnInput.fill('Java')
    await page.getByRole('option', { name: /Java/i }).click()
    await page.getByRole('button', { name: 'Save and find matches' }).click()
    await expect(page).toHaveURL(/.*\/home/)

    // Navigate to /availability
    await page.goto('/availability')
    await expect(page.getByRole('heading', { name: 'Availability' })).toBeVisible()

    // Scroll to bottom and test "Save availability" button
    const saveBtn = page.getByRole('button', { name: 'Save availability' })
    await saveBtn.scrollIntoViewIfNeeded()
    await expect(saveBtn).toBeVisible()

    // Hit-test: click save availability button
    await saveBtn.click()
    await expect(page.getByText('Availability saved.')).toBeVisible()
  })

  test('Tab formatting 375px: Inspect spacing between label and count on /invitations', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })

    await page.goto('/login')
    await page.getByLabel('Email').fill(sharedEmail)
    await page.getByLabel('Password').fill('Password123!')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page).toHaveURL(/.*\/home/)

    await page.goto('/invitations')
    await expect(page.getByRole('heading', { name: 'Invitations' })).toBeVisible()

    // Check tab trigger texts
    const tabs = page.getByRole('tab')
    const count = await tabs.count()
    for (let i = 0; i < count; i++) {
      const text = await tabs.nth(i).innerText()
      console.log(`[Visual Audit] Tab ${i} text: "${text}"`)
      // Check whether text matches label followed by immediate unspaced digit e.g. "Received0"
      const hasUnspacedCount = /[a-zA-Z]+\d+/.test(text.replace(/\s+/g, ''))
      console.log(`[Visual Audit] Tab "${text}" has unspaced count pattern: ${hasUnspacedCount}`)
    }
  })

  test('Viewport 320px: Fluid layout without horizontal overflow', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 })
    await page.goto('/login')

    // Check horizontal scroll width
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    const clientWidth = await page.evaluate(() => document.documentElement.clientWidth)
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth)

    // Heading and inputs fully visible
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible()
    await expect(page.getByLabel('Email')).toBeVisible()
    await expect(page.getByLabel('Password')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
  })
})
