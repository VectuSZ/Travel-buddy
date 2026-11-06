export default defineEventHandler(event => {
 const path = getRouterParam(event, 'path') || ''
 if (event.method !== 'GET' || !/^trips(?:\/[0-9a-f-]+)?$/i.test(path)) throw createError({statusCode:404})
 return proxyRequest(event, `${useRuntimeConfig(event).backendUrl}/api/v1/${path}`)
})
