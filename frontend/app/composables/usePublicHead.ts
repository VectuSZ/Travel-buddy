export function usePublicHead(title: string, description: string, pl: string, en: string) {
  const { t, locale } = useI18n()
  const base = useRuntimeConfig().public.siteUrl
  useHead(() => ({
    title: t(title) + ' — Travel Buddy',
    meta: [{ name: 'description', content: t(description) }],
    link: [
      { rel: 'canonical', href: new URL(locale.value === 'en' ? en : pl, base).href },
      ...[{ hreflang: 'pl', path: pl }, { hreflang: 'en', path: en }].map(item => ({
        rel: 'alternate' as const, type: 'text/html', hreflang: item.hreflang, href: new URL(item.path, base).href
      }))
    ]
  }))
}
