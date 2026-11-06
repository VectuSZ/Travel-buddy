import { test, expect } from '@playwright/test'

for (const lang of ['pl', 'en']) {
  test(`account and Mailpit password reset (${lang})`, async ({ page, context, request }) => {
    const prefix = lang === 'en' ? '/en' : ''
    const email = `e2e-${Date.now()}-${lang}@example.test`
    const password = 'Local test long password 123!'
    const changed = 'Changed local password 456!'
    const submit = () => page.locator('main button[type=submit]')
    await page.goto(prefix + '/register')
    await page.locator('input[name=email]').fill(email)
    await page.locator('input[name=password]').fill(password)
    await page.locator('input[name=confirmation]').fill(password)
    await page.locator('input[type=checkbox]').check()
    await submit().click()
    await expect(page).toHaveURL(new RegExp(prefix + '/account$'))
    await expect(page.locator('main')).toContainText(email)
    const cookies = await context.cookies()
    const session = cookies.find(cookie => cookie.name === 'session')!
    expect(session.httpOnly).toBe(true)
    expect(session.sameSite).toBe('Lax')
    expect(session.secure).toBe(false)

    // Public SSR must contain trips but never the private user's email.
    const html = await (await page.request.get(prefix + '/')).text()
    expect(html).toContain('Weekend w Tatrach')
    expect(html).not.toContain(email)
    const privateResponse = await page.request.get('/api/v1/auth/session')
    expect(privateResponse.headers()['cache-control']).toBe('no-store')
    expect(await privateResponse.json()).toEqual({ email })
    expect(await page.evaluate(() => Object.keys(localStorage))).not.toContain('session')

    await page.locator('main button').click()
    await expect(page).toHaveURL(new RegExp(prefix + '/login$'))
    expect((await context.cookies()).some(cookie => cookie.name === 'session')).toBe(false)
    await page.locator('input[name=email]').fill(email.toUpperCase())
    await page.locator('input[name=password]').fill(password)
    await submit().click()
    await expect(page).toHaveURL(new RegExp(prefix + '/account$'))
    await expect(page.locator('main')).toContainText(email)

    const oldSession = (await context.cookies()).find(cookie => cookie.name === 'session')!.value
    await page.goto(prefix + '/forgot-password')
    await page.locator('input[name=email]').fill(email)
    await submit().click()
    await expect(page.locator('[role=status]')).toBeVisible()

    let messageId = ''
    await expect.poll(async () => {
      const response = await request.get('http://localhost:8025/api/v1/messages')
      const result = await response.json()
      messageId = result.messages.find((m: { ID: string; To: { Address: string }[] }) => m.To.some(to => to.Address === email))?.ID || ''
      return messageId
    }).not.toBe('')
    const mail = await (await request.get(`http://localhost:8025/api/v1/message/${messageId}`)).json()
    const link = mail.Text.match(/http:\/\/localhost:3000\/[^\s]+/)[0]
    expect(link).toContain(prefix + '/reset-password#token=')
    const external: string[] = []
    page.on('request', r => { if (!r.url().startsWith('http://localhost:3000') && !r.url().startsWith('ws://localhost:3000')) external.push(r.url()) })
    const response = await page.goto(link)
    expect(response!.headers()['referrer-policy']).toBe('no-referrer')
    await expect(page).toHaveURL(new RegExp(prefix + '/reset-password$'))
    await page.locator('input[name=password]').fill(changed)
    await page.locator('input[name=confirmation]').fill(changed)
    await submit().click()
    await expect(page.locator('[role=status]')).toBeVisible()
    expect(external).toEqual([])
    const revoked = await request.get('/api/v1/auth/session', { headers: { Cookie: `session=${oldSession}` } })
    expect(revoked.status()).toBe(401)
    await page.goto(prefix + '/login')
    await page.locator('input[name=email]').fill(email)
    await page.locator('input[name=password]').fill(changed)
    await submit().click()
    await expect(page).toHaveURL(new RegExp(prefix + '/account$'))
    await page.locator('main button').click()
  })
}
