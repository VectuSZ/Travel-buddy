import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
const observed = []
const upstream = createServer((req, res) => {
  observed.push({ url: req.url, cookie: req.headers.cookie, origin: req.headers.origin, forwarded: req.headers['x-forwarded-for'] })
  res.writeHead(302, { Location: 'https://accounts.google.com/test-only', 'Cache-Control': 'no-store',
    'Set-Cookie': ['google-flow=test-binding; Path=/; HttpOnly; SameSite=Lax', 'q_auth_test=test-state; Path=/; HttpOnly'] })
  res.end()
})
upstream.listen(0, '127.0.0.1')
await once(upstream, 'listening')
const portServer = createServer().listen(0, '127.0.0.1')
await once(portServer, 'listening')
const port = portServer.address().port
await new Promise(resolve => portServer.close(resolve))
const child = spawn(process.execPath, ['frontend/.output/server/index.mjs'], {
  cwd: process.cwd(), stdio: 'ignore', env: { ...process.env, HOST: '127.0.0.1', PORT: String(port), NUXT_BACKEND_URL: `http://127.0.0.1:${upstream.address().port}` }
})
try {
  let ready = false
  for (let i = 0; i < 100; i++) {
    try { await fetch(`http://127.0.0.1:${port}/api/v1/auth/google/config`, { redirect: 'manual' }); ready = true; break }
    catch { await new Promise(resolve => setTimeout(resolve, 100)) }
  }
  assert.ok(ready, 'Nuxt did not start')
  for (const path of ['authorize', 'callback?code=fake-code&state=fake-state', 'error?error=access_denied']) {
    const response = await fetch(`http://127.0.0.1:${port}/api/v1/auth/google/${path}`, {
      redirect: 'manual', headers: { Cookie: 'google-flow=browser-binding', Origin: 'http://localhost:3000', 'X-Forwarded-For': '192.0.2.1' }
    })
    assert.equal(response.status, 302)
    assert.equal(response.headers.get('location'), 'https://accounts.google.com/test-only')
    assert.equal(response.headers.getSetCookie().length, 2)
    assert.equal(response.headers.get('cache-control'), 'no-store')
    const seen = observed.at(-1)
    assert.equal(seen.url, '/api/v1/auth/google/' + path)
    assert.equal(seen.cookie, 'google-flow=browser-binding')
    assert.equal(seen.origin, 'http://localhost:3000')
    assert.equal(seen.forwarded, undefined)
  }
  console.log('Google proxy OK: redirects, query, Cookie, Set-Cookie, Origin, no-store')
} finally {
  child.kill('SIGTERM')
  await once(child, 'exit')
  await new Promise(resolve => upstream.close(resolve))
}
