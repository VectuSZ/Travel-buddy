export default defineNuxtConfig({
 compatibilityDate: '2026-11-06',
 modules: ['@nuxtjs/i18n'],
 runtimeConfig: { backendUrl: 'http://127.0.0.1:8080' },
 i18n: { defaultLocale: 'pl', strategy: 'prefix_except_default', detectBrowserLanguage: false,
 locales: [{code:'pl',language:'pl-PL',file:'pl.json'},{code:'en',language:'en-GB',file:'en.json'}] }
})
