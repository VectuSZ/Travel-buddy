export default defineNuxtConfig({
 compatibilityDate: '2026-11-06',
 modules: ['@nuxtjs/i18n'],
 routeRules: {
   '/moderation': { headers: { 'Cache-Control': 'private, no-store' } },
   '/en/moderation': { headers: { 'Cache-Control': 'private, no-store' } },
   '/report': { headers: { 'Cache-Control': 'private, no-store' } },
   '/en/report': { headers: { 'Cache-Control': 'private, no-store' } },
   '/my-trips/**': { headers: { 'Cache-Control': 'private, no-store' } },
   '/en/my-trips/**': { headers: { 'Cache-Control': 'private, no-store' } },
   '/profiles/**': { headers: { 'Cache-Control': 'private, no-store' } },
   '/en/profiles/**': { headers: { 'Cache-Control': 'private, no-store' } },
   '/google-result': { headers: { 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' } },
   '/en/google-result': { headers: { 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' } },
   '/reset-password': { headers: { 'Referrer-Policy': 'no-referrer', 'Cache-Control': 'no-store' } },
   '/en/reset-password': { headers: { 'Referrer-Policy': 'no-referrer', 'Cache-Control': 'no-store' } },
   '/account': { headers: { 'Cache-Control': 'no-store' } },
   '/en/account': { headers: { 'Cache-Control': 'no-store' } }
 },
 runtimeConfig: { public: { siteUrl: 'http://localhost:3000' }, backendUrl: 'http://127.0.0.1:8080' },
 i18n: { defaultLocale: 'pl', strategy: 'prefix_except_default', detectBrowserLanguage: false,
 locales: [{code:'pl',language:'pl-PL',file:'pl.json'},{code:'en',language:'en-GB',file:'en.json'}] }
})
