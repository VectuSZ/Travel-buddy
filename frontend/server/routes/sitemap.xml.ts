interface Entry { id: string; slug: string; contentLanguage: string; updatedAt: string }
export default defineEventHandler(async event => {
  const config = useRuntimeConfig(event)
  const trips = await $fetch<Entry[]>(config.backendUrl + '/api/v1/sitemap-trips')
  const paths = ['/pl/wyprawy', '/en/trips', '/terms', '/en/terms', '/privacy', '/en/privacy', '/cookies', '/en/cookies']
  const url = (path: string, updated?: string) => {
    const href = new URL(path, config.public.siteUrl).href.replaceAll('&', '&amp;').replaceAll('<', '&lt;')
    return '<url><loc>' + href + '</loc>' + (updated ? '<lastmod>' + new Date(updated).toISOString() + '</lastmod>' : '') + '</url>'
  }
  setHeader(event, 'Content-Type', 'application/xml; charset=utf-8')
  setHeader(event, 'Cache-Control', 'no-store')
  return '<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' +
    paths.map(path => url(path)).join('') + trips.map(trip => url(
      (trip.contentLanguage === 'en' ? '/en/trips/' : '/pl/wyprawy/') + trip.id + '/' + encodeURIComponent(trip.slug), trip.updatedAt
    )).join('') + '</urlset>'
})
