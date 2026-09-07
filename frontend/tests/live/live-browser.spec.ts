import { test, expect } from '@playwright/test'

test.describe('Live Browser & Spring Boot Integration (Zero Mock Interceptions)', () => {

  test('User registration, session reload, skills onboarding, and availability F1 persistence', async ({ page }) => {
    const timestamp = Date.now()
    const email = `live.user.${timestamp}@example.com`
    const password = 'Password123!'
    const displayName = `User ${timestamp}`

    // 1. Visit root -> redirected to /login
    await page.goto('/')
    await expect(page).toHaveURL(/.*\/login/)

    // 2. Switch to Create an Account
    const createAccountLink = page.getByRole('button', { name: 'Create an account' })
    await createAccountLink.click()
    await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible()

    // 3. Fill registration form
    await page.getByLabel('Display name').fill(displayName)
    await page.getByLabel('Time zone').fill('America/Sao_Paulo')
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(password)

    // 4. Submit registration (button name is 'Create account')
    const submitBtn = page.getByRole('button', { name: 'Create account', exact: true })
    await expect(submitBtn).toBeVisible()
    await submitBtn.click()

    // 5. User should automatically be directed to skill registration
    await expect(page).toHaveURL(/.*\/skills\/register/, { timeout: 15000 })
    await expect(page.getByRole('heading', { name: /Two lists decide every match/i })).toBeVisible()

    // 6. Test session reload: reload the page and verify authentication and state persist
    await page.reload()
    await expect(page).toHaveURL(/.*\/skills\/register/)

    // 7. Select Skills in Onboarding
    // 7a. What you can teach: Type "React" and select "React"
    const teachSection = page.locator('.reg-cols > div').first()
    const teachInput = teachSection.getByRole('combobox')
    await teachInput.fill('React')
    const reactOption = page.getByRole('option', { name: /React/i })
    await expect(reactOption).toBeVisible({ timeout: 5000 })
    await reactOption.click()

    // 7b. What you want to learn: Type "Java" and select "Java"
    const learnSection = page.locator('.reg-cols > div').nth(1)
    const learnInput = learnSection.getByRole('combobox')
    await learnInput.fill('Java')
    const javaOption = page.getByRole('option', { name: /Java/i })
    await expect(javaOption).toBeVisible({ timeout: 5000 })
    await javaOption.click()

    // 8. Save skills and navigate to /home
    const saveSkillsBtn = page.getByRole('button', { name: 'Save and find matches' })
    await expect(saveSkillsBtn).toBeEnabled()
    await saveSkillsBtn.click()

    // Verify redirection to /home
    await expect(page).toHaveURL(/.*\/home/, { timeout: 15000 })

    // 9. F1: Availability & Timezone persistence
    await page.goto('/availability')
    await expect(page.getByRole('heading', { name: 'Availability' })).toBeVisible()

    // Check timezone selector and change zone
    const tzSelect = page.getByLabel('Your time zone')
    await expect(tzSelect).toBeVisible()
    await tzSelect.selectOption('America/New_York')

    // Add a window if none exists
    const addFirstBtn = page.getByRole('button', { name: /Add (your first|a) window/i }).first()
    if (await addFirstBtn.isVisible()) {
      await addFirstBtn.click()
    }

    // Save availability
    const saveAvailBtn = page.getByRole('button', { name: 'Save availability' })
    await saveAvailBtn.click()

    // Verify success notification
    await expect(page.getByText('Availability saved.')).toBeVisible()

    // Reload page to verify persistence from real backend
    await page.reload()
    await expect(page.getByLabel('Your time zone')).toHaveValue('America/New_York')
  })

  test('Full Exchange Lifecycle & F2 Double-Blind Feedback with Dual Publication', async ({ page, playwright }) => {
    const timestamp = Date.now()
    const backendUrl = process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:18089'
    const api = await playwright.request.newContext({ baseURL: backendUrl })

    // 1. Create Counterpart User (Bob Receiver) via real backend API
    const bobEmail = `bob.${timestamp}@example.com`
    const bobDisplayName = `Bob ${timestamp}`
    const bobRegRes = await api.post('/api/auth/register', {
      data: {
        email: bobEmail,
        password: 'Password123!',
        displayName: bobDisplayName,
        timeZone: 'Europe/London',
      },
    })
    expect(bobRegRes.status()).toBe(201)
    const bobToken = (await bobRegRes.json()).token

    // Get skill IDs from backend using Bob's authenticated session
    const skillsRes = await api.get('/api/skills', {
      headers: { Authorization: `Bearer ${bobToken}` },
    })
    expect(skillsRes.status()).toBe(200)
    const skillsData = await skillsRes.json()
    const javaSkill = skillsData.items.find((s: { name: string }) => s.name === 'Java')
    const reactSkill = skillsData.items.find((s: { name: string }) => s.name === 'React')
    expect(javaSkill).toBeDefined()
    expect(reactSkill).toBeDefined()

    // Set Bob's skills: Bob offers Java, wants React
    const bobSkillsRes = await api.put('/api/me/skills', {
      headers: { Authorization: `Bearer ${bobToken}` },
      data: {
        offeredSkillIds: [javaSkill.id],
        wantedSkillIds: [reactSkill.id],
      },
    })
    expect(bobSkillsRes.status()).toBe(200)

    // Set Bob's availability (08:00 - 18:00 Europe/London)
    await api.put('/api/me/availability', {
      headers: { Authorization: `Bearer ${bobToken}` },
      data: {
        timeZone: 'Europe/London',
        windows: [{ dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '18:00' }],
      },
    })

    // Get Bob's user ID
    const bobMeRes = await api.get('/api/auth/me', {
      headers: { Authorization: `Bearer ${bobToken}` },
    })
    const bobId = (await bobMeRes.json()).id

    // 2. Register Alice (Requester) via browser UI
    const aliceEmail = `alice.${timestamp}@example.com`
    await page.goto('/login')
    await page.getByRole('button', { name: 'Create an account' }).click()
    await page.getByLabel('Display name').fill('Alice Requester')
    await page.getByLabel('Time zone').fill('America/Sao_Paulo')
    await page.getByLabel('Email').fill(aliceEmail)
    await page.getByLabel('Password').fill('Password123!')
    await page.getByRole('button', { name: 'Create account', exact: true }).click()

    await expect(page).toHaveURL(/.*\/skills\/register/)

    // Alice offers React, wants Java
    const teachSection = page.locator('.reg-cols > div').first()
    await teachSection.getByRole('combobox').fill('React')
    const reactOpt = page.getByRole('option', { name: /React/i })
    await expect(reactOpt).toBeVisible({ timeout: 5000 })
    await reactOpt.click()
    await expect(teachSection.getByText('React')).toBeVisible()

    const learnSection = page.locator('.reg-cols > div').nth(1)
    await learnSection.getByRole('combobox').fill('Java')
    const javaOpt = page.getByRole('option', { name: /Java/i })
    await expect(javaOpt).toBeVisible({ timeout: 5000 })
    await javaOpt.click()
    await expect(learnSection.getByText('Java')).toBeVisible()

    await page.getByRole('button', { name: 'Save and find matches' }).click()
    await expect(page).toHaveURL(/.*\/home/)

    // Set Alice availability (06:00 - 22:00 America/Sao_Paulo) - guaranteed overlap
    const aliceToken = await page.evaluate(() => localStorage.getItem('match-skill-token'))
    expect(aliceToken).toBeTruthy()

    await api.put('/api/me/availability', {
      headers: { Authorization: `Bearer ${aliceToken}` },
      data: {
        timeZone: 'America/Sao_Paulo',
        windows: [{ dayOfWeek: 'MONDAY', startTime: '06:00', endTime: '22:00' }],
      },
    })

    // 3. Check Matches on /home
    await page.reload()
    const bobCard = page.locator('article').filter({ hasText: bobDisplayName })
    await expect(bobCard).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Complete trades' })).toBeVisible()
    await expect(bobCard.getByText('Java')).toBeVisible()
    await expect(bobCard.getByText('React')).toBeVisible()

    // 4. Send Exchange Request from Alice to Bob
    await bobCard.getByRole('button', { name: 'Send request' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('dialog').getByRole('button', { name: 'Send request' }).click()
    await expect(page).toHaveURL(/.*\/exchanges\/.+/, { timeout: 10000 })
    const exchangeId = page.url().split('/').pop()!
    expect(exchangeId).toBeTruthy()

    // 5. Bob accepts the exchange (choosing React from Alice)
    const acceptRes = await api.post(`/api/exchanges/${exchangeId}/accept`, {
      headers: { Authorization: `Bearer ${bobToken}` },
      data: { skillFromRequester: reactSkill.id },
    })
    expect(acceptRes.status()).toBe(200)

    // 6. Schedule the exchange
    const futureDate = new Date(Date.now() + 86400000 * 2).toISOString()
    const scheduleRes = await api.post(`/api/exchanges/${exchangeId}/schedule`, {
      headers: { Authorization: `Bearer ${aliceToken}` },
      data: {
        scheduledAt: futureDate,
        meetingUrl: 'https://meet.google.com/test-live-room',
      },
    })
    expect(scheduleRes.status()).toBe(200)

    // 7. Complete the exchange
    const completeRes = await api.post(`/api/exchanges/${exchangeId}/complete`, {
      headers: { Authorization: `Bearer ${bobToken}` },
    })
    expect(completeRes.status()).toBe(200)

    // 8. F2: Double-Blind Feedback in Browser UI
    // Alice navigates to /history
    await page.goto('/history')
    await expect(page.getByRole('heading', { name: 'History' })).toBeVisible()
    await expect(page.getByText(bobDisplayName)).toBeVisible()

    // Alice clicks "Leave feedback"
    const leaveFeedbackBtn = page.getByRole('link', { name: /Leave feedback/i })
    await expect(leaveFeedbackBtn).toBeVisible()
    await leaveFeedbackBtn.click()
    await expect(page).toHaveURL(new RegExp(`/feedback/${exchangeId}`))

    // Alice fills feedback: rating 5, comment
    await page.getByRole('radio', { name: '5 stars' }).click()
    await page.getByLabel('Comment (optional)').fill('Bob was an outstanding teacher on Java concurrency!')
    await page.getByRole('button', { name: 'Submit rating' }).click()

    // Alice sees feedback submitted message
    await expect(page.getByRole('heading', { name: 'Your rating is saved' })).toBeVisible()

    // 9. F2 Anti-Leakage Verification:
    // Bob checks the exchange feedback via API:
    const bobFbCheck = await api.get(`/api/exchanges/${exchangeId}/feedback`, {
      headers: { Authorization: `Bearer ${bobToken}` },
    })
    expect(bobFbCheck.status()).toBe(200)
    const bobFbData = await bobFbCheck.json()
    // Invariant: Bob has not submitted, so theirs MUST be null, counterpartSubmitted MUST be true!
    expect(bobFbData.mine).toBeNull()
    expect(bobFbData.theirs).toBeNull()
    expect(bobFbData.counterpartSubmitted).toBe(true)

    // Bob checks his own received feedback: Alice's rating MUST NOT be visible!
    const bobProfileFb = await api.get(`/api/users/${bobId}/feedback`, {
      headers: { Authorization: `Bearer ${bobToken}` },
    })
    expect(bobProfileFb.status()).toBe(200)
    expect((await bobProfileFb.json()).length).toBe(0)

    // 10. Bob submits feedback -> Dual publication triggered!
    const bobSubmitRes = await api.post(`/api/exchanges/${exchangeId}/feedback`, {
      headers: { Authorization: `Bearer ${bobToken}` },
      data: { rating: 4, comment: 'Alice grasped everything quickly!' },
    })
    expect(bobSubmitRes.status()).toBe(201)

    // Now Bob's profile feedback is published for everyone!
    const bobPublishedFb = await api.get(`/api/users/${bobId}/feedback`, {
      headers: { Authorization: `Bearer ${aliceToken}` },
    })
    expect(bobPublishedFb.status()).toBe(200)
    const publishedList = await bobPublishedFb.json()
    expect(publishedList.length).toBe(1)
    expect(publishedList[0].rating).toBe(5)
    expect(publishedList[0].comment).toBe('Bob was an outstanding teacher on Java concurrency!')

    // Bob's public profile reputation updated
    const bobPublicProfile = await api.get(`/api/users/${bobId}`, {
      headers: { Authorization: `Bearer ${aliceToken}` },
    })
    expect(bobPublicProfile.status()).toBe(200)
    const bobProfileData = await bobPublicProfile.json()
    expect(bobProfileData.reputationCount).toBe(1)
    expect(bobProfileData.reputationAverage).toBe(5.0)

    // Reload Alice's feedback page: now both feedbacks are visible!
    await page.reload()
    await expect(page.getByText('Alice grasped everything quickly!')).toBeVisible()
  })
})
