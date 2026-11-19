export default defineEventHandler(event => {
  setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
  return 'User-agent: *\nAllow: /\nSitemap: ' + new URL('/sitemap.xml', useRuntimeConfig(event).public.siteUrl).href + '\n'
})
