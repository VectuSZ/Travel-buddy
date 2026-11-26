import { test, expect } from '@playwright/test'

for (const lang of ['pl', 'en']) {
  test(`account and Mailpit password reset (${lang})`, async ({ page, context, request, baseURL }) => {
    const prefix = lang === 'en' ? '/en' : ''
    const email = `e2e-${Date.now()}-${lang}@example.test`
    const password = 'Local test long password 123!'
    const changed = 'Changed local password 456!'
    const submit = () => page.locator('main button[type=submit]')
    await page.goto(prefix + '/register')
    expect((await (await page.request.get('/api/v1/auth/google/config')).json()).enabled).toBe(false)
    await expect(page.getByRole('button', { name: /Google/ })).toHaveCount(0)
    await page.locator('input[name=email]').fill(email)
    const first = page.locator('input[name=password]')
    const confirm = page.locator('input[name=confirmation]')
    const registrationRequests: string[] = []
    page.on('request', r => {
      if (r.url().endsWith('/api/v1/auth/register')) registrationRequests.push(r.method())
    })
    await expect(page.locator('main')).not.toContainText(/długiej frazy|Long passphrases/)
    await page.locator('input[type=checkbox]').check()
    await first.fill('short')
    await confirm.fill('short')
    await submit().click()
    await expect(first).toBeFocused()
    await expect(first).toHaveJSProperty('validationMessage', lang === 'pl'
      ? 'Hasło musi mieć od 12 do 1024 znaków.' : 'Password must be between 12 and 1024 characters.')
    await first.fill(password)
    await confirm.fill(password + 'x')
    await submit().click()
    await expect(confirm).toBeFocused()
    await expect(confirm).toHaveJSProperty('validationMessage', lang === 'pl'
      ? 'Hasła muszą być takie same.' : 'Passwords must match.')
    expect(registrationRequests).toHaveLength(0)
    await confirm.fill(password)
    await expect(confirm).toHaveJSProperty('validationMessage', '')
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

    await page.getByTestId('logout').click()
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
    const link = mail.Text.match(/https?:\/\/[^\s]+/)[0]
    expect(new URL(link).origin).toBe(baseURL)
    expect(link).toContain(prefix + '/reset-password#token=')
    const external: string[] = []
    page.on('request', r => { if (new URL(r.url().replace(/^ws/, 'http')).origin !== baseURL) external.push(r.url()) })
    const response = await page.goto(link)
    expect(response!.headers()['referrer-policy']).toBe('no-referrer')
    await expect(page).toHaveURL(new RegExp(prefix + '/reset-password$'))
    await expect(page.locator('main')).not.toContainText(/długiej frazy|Long passphrases/)
    const resetRequests: string[] = []
    page.on('request', r => {
      if (r.url().endsWith('/api/v1/auth/password-reset/confirm')) resetRequests.push(r.method())
    })
    await expect(page.locator('main')).not.toContainText(/długiej frazy|Long passphrases/)
    await submit().click()
    await expect(first).toBeFocused()
    expect(await first.evaluate((input: HTMLInputElement) => input.validity.valueMissing)).toBe(true)
    const lengthMessage = lang === 'pl' ? 'Hasło musi mieć od 12 do 1024 znaków.' : 'Password must be between 12 and 1024 characters.'
    await first.fill('x'.repeat(11))
    await submit().click()
    await expect(first).toHaveJSProperty('validationMessage', lengthMessage)
    await expect(first).toHaveAttribute('maxlength', '1024')
    // Native maxlength caps typing. Exercise validation of an overlong value supplied by script.
    await first.evaluate((input: HTMLInputElement) => {
      input.value = 'x'.repeat(1025)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    })
    await submit().click()
    await expect(first).toHaveJSProperty('validationMessage', lengthMessage)
    await first.fill(changed)
    await expect(first).toHaveJSProperty('validationMessage', '')
    await confirm.fill(changed + 'x')
    await submit().click()
    await expect(confirm).toBeFocused()
    await expect(confirm).toHaveJSProperty('validationMessage', lang === 'pl' ? 'Hasła muszą być takie same.' : 'Passwords must match.')
    await confirm.fill(changed)
    await expect(confirm).toHaveJSProperty('validationMessage', '')
    expect(resetRequests).toHaveLength(0)
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
    await page.getByTestId('logout').click()
  })
}
