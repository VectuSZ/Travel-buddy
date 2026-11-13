export default defineEventHandler(async event => {
  const path = getRouterParam(event, 'path') || ''
  const publicRead = event.method === 'GET' && /^trips(?:\/[0-9a-f-]+)?$/i.test(path)
  const authRead = event.method === 'GET' && /^auth\/(csrf|session|google\/(config|authorize|callback|error))$/.test(path)
  const authWrite = event.method === 'POST' && /^auth\/(register|login|logout|google\/start|password-reset\/(request|confirm))$/.test(path)
  const profileRead = event.method === 'GET' && (path === 'me' || /^profiles\/[0-9a-f-]+$/i.test(path))
  const profileWrite = (event.method === 'PATCH' && path === 'me/profile') || (event.method === 'PUT' && path === 'me/contacts')
  if (profileRead || profileWrite) setHeader(event, 'Cache-Control', 'private, no-store')
  if (!publicRead && !authRead && !authWrite && !profileRead && !profileWrite) throw createError({ statusCode: 404 })
  if (authRead || authWrite) setHeader(event, 'Cache-Control', 'no-store')
  // Never promote client-supplied forwarding headers to a trusted client IP.
  for (const header of ['forwarded', 'x-forwarded-for', 'x-real-ip']) delete event.node.req.headers[header]
  return proxyRequest(event, `${useRuntimeConfig(event).backendUrl}/api/v1/${path}${getRequestURL(event).search}`, { fetchOptions: { redirect: 'manual' } })
})
