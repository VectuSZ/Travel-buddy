export default defineEventHandler(async event => {
  const path = getRouterParam(event, 'path') || ''
  const publicRead = event.method === 'GET' && /^trips(?:\/[0-9a-f-]+)?$/i.test(path)
  const authRead = event.method === 'GET' && /^auth\/(csrf|session|google\/(config|authorize|callback|error))$/.test(path)
  const authWrite = event.method === 'POST' && /^auth\/(register|login|logout|reauthenticate|google\/start|password-reset\/(request|confirm))$/.test(path)
  const profileRead = event.method === 'GET' && (path === 'me' || /^profiles\/[0-9a-f-]+$/i.test(path))
  const profileWrite = (event.method === 'DELETE' && path === 'me') || (event.method === 'PATCH' && path === 'me/profile') || (event.method === 'PUT' && path === 'me/contacts')
  const tripRead = event.method === 'GET' && /^me\/trips(?:\/[0-9a-f-]+)?$/i.test(path)
  const tripWrite = (event.method === 'POST' && (path === 'trips' || /^trips\/[0-9a-f-]+\/(publish|close)$/i.test(path))) || (event.method === 'PATCH' && /^trips\/[0-9a-f-]+$/i.test(path))
  if (profileRead || profileWrite || tripRead || tripWrite) setHeader(event, 'Cache-Control', 'private, no-store')
  const applications = (event.method === 'GET' && (path === 'me/applications' || /^trips\/[0-9a-f-]+\/(applications|participants|contacts\/[0-9a-f-]+)$/i.test(path))) || (event.method === 'POST' && (/^trips\/[0-9a-f-]+\/applications$/i.test(path) || /^applications\/[0-9a-f-]+\/(accept|reject|withdraw)$/i.test(path)))
  if (applications) setHeader(event, 'Cache-Control', 'private, no-store')
  const moderation = (event.method === 'GET' && /^(reports\/config|admin\/(access|reports))$/.test(path)) || (event.method === 'POST' && (/^reports(?:\/challenge)?$/.test(path) || /^admin\/(reports\/[0-9a-f-]+\/resolve|trips\/[0-9a-f-]+\/(hide|unhide)|users\/[0-9a-f-]+\/(block|unblock))$/i.test(path)))
  if (moderation) setHeader(event, 'Cache-Control', 'private, no-store')
  if (!moderation && !applications && !publicRead && !authRead && !authWrite && !profileRead && !profileWrite && !tripRead && !tripWrite) throw createError({ statusCode: 404 })
  if (authRead || authWrite) setHeader(event, 'Cache-Control', 'no-store')
  // Never promote client-supplied forwarding headers to a trusted client IP.
  for (const header of ['forwarded', 'x-forwarded-for', 'x-real-ip']) delete event.node.req.headers[header]
  return proxyRequest(event, `${useRuntimeConfig(event).backendUrl}/api/v1/${path}${getRequestURL(event).search}`, { fetchOptions: { redirect: 'manual' } })
})
