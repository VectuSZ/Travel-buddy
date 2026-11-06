import assert from 'node:assert/strict'
for (const path of ['/', '/en', '/trips/11111111-1111-4111-8111-111111111111', '/en/trips/11111111-1111-4111-8111-111111111111']) {
 const response = await fetch('http://localhost:3000' + path)
 assert.equal(response.status, 200, path)
 const html = (await response.text()).replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '').replace(/<head\b[^>]*>[\s\S]*?<\/head>/gi, '')
 assert.ok(html.includes('Weekend w Tatrach'), 'Brak treści SSR: ' + path)
 console.log('SSR OK:', path)
}
