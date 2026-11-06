# Decyzje — etap 1

- Java 21; Quarkus 3.40.1, Maven Wrapper 3.3.4 / Maven 3.9.11. Systemowy Maven wybierał Javę 27, dlatego skrypt jawnie wybiera 21.
- PostgreSQL 17.6, natywna architektura obrazu, loopback 5433, nazwany wolumen. Dev Services wyłączone.
- Flyway zarządza schematem; Hibernate tylko waliduje. Lokalizacja db/demo w dev/test zawiera deterministyczne UUID i wykonuje się raz, bez nadpisywania danych przy restarcie.
- Jawne DTO; lista sortowana po dacie i UUID. Test API korzysta z PostgreSQL Compose w oddzielnym schemacie travel_buddy_test, bez H2 ani dodatkowego kontenera.
- Nuxt 4.6.4, i18n 10.6.0, Vue 3.5.45, Vue Router 5.4.0, TypeScript 5.9.3, vue-tsc 3.1.1; drzewo zależności zapisane w package-lock.json.
- Nitro proxy obsługuje względne /api/v1 dla SSR i przeglądarki. Prywatny NUXT_BACKEND_URL wskazuje backend. CORS nie jest potrzebny.
- Proste adresy / i /trips/:id oraz warianty /en. Docelowe slugi i pełne SEO są poza etapem 1.
- Dokumentacja: https://quarkus.io/guides/flyway/ oraz https://nuxt.com/docs/4.x/api/composables/use-fetch.
