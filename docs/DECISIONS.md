# Decyzje — etap 1

- Java 21; Quarkus 3.40.1, Maven Wrapper 3.3.4 / Maven 3.9.11. Systemowy Maven wybierał Javę 27, dlatego skrypt jawnie wybiera 21.
- PostgreSQL 17.6, natywna architektura obrazu, loopback 5433, nazwany wolumen. Dev Services wyłączone.
- Flyway zarządza schematem; Hibernate tylko waliduje. Lokalizacja db/demo w dev/test zawiera deterministyczne UUID i wykonuje się raz, bez nadpisywania danych przy restarcie.
- Jawne DTO; lista sortowana po dacie i UUID. Test API korzysta z PostgreSQL Compose w oddzielnym schemacie travel_buddy_test, bez H2 ani dodatkowego kontenera.
- Nuxt 4.6.4, i18n 10.6.0, Vue 3.5.45, Vue Router 5.4.0, TypeScript 5.9.3, vue-tsc 3.1.1; drzewo zależności zapisane w package-lock.json.
- Nitro proxy obsługuje względne /api/v1 dla SSR i przeglądarki. Prywatny NUXT_BACKEND_URL wskazuje backend. CORS nie jest potrzebny.
- Proste adresy / i /trips/:id oraz warianty /en. Docelowe slugi i pełne SEO są poza etapem 1.
- Dokumentacja: https://quarkus.io/guides/flyway/ oraz https://nuxt.com/docs/4.x/api/composables/use-fetch.

# Decyzje — etap 2A

- Dodano wyłącznie lokalne konta, sesje i reset. Bez OIDC, profili,
  kontaktów i auth_identities. Migracja V3 nie zmienia wypraw ani seedów.
  Dane auth są mapowane przez Panache i walidowane przez Hibernate.
- users.email ma unikalny indeks lower(email); zapisujemy małe litery.
  password_hash i email_verified_at są nullable. Rolę USER i status ACTIVE
  ustala serwer. Akceptowana lokalna wersja regulaminu: local-draft-1,
  jej wersję i czas zapisuje backend. To nie jest regulamin gotowy do publikacji.
- Password4j 1.8.4, Argon2id v19: 19456 KiB pamięci, 2 iteracje,
  parallelism 1, losowa sól 16 bajtów, wynik 32 bajty. Weryfikacja odczytuje
  parametry z zapisanego hasha (nie z domyślnej konfiguracji biblioteki).
  Hasła: 12–1024 jednostek UTF-16, bez obcinania i normalizowania; limit
  całego body HTTP 16 KiB. Długie frazy sprawdzane w teście. Nieistniejące
  konto przechodzi weryfikację zastępczego hasha o tych samych parametrach.
  Parametry wymagają ponownego pomiaru na ewentualnym przyszłym serwerze.
- Sesja i token resetu: SecureRandom, 32 bajty, Base64 URL. Baza przechowuje
  SHA-256 tokenu, nie wartość używaną przez klienta. Ważność odpowiednio
  7 dni i 30 minut. Konto ACTIVE sprawdzane przy każdym odczycie sesji.
  SessionService.create może zostać użyte po przyszłej zweryfikowanej
  tożsamości Google; w tym etapie nie ma żadnego mechanizmu OIDC.
- Cookie produkcyjne: __Host-session, Secure, HttpOnly, SameSite=Lax,
  Path=/, bez Domain. Dev/test: session, bez Secure. Dane konta wracają
  wyłącznie z prywatnego GET auth/session (DTO z własnym e-mailem).
  Żądania sesji są tylko klientowe; odpowiedzi auth mają no-store.
- quarkus-rest-csrf z BOM 3.40.1 generuje 32-bajtowy token przy GET
  /api/v1/auth/csrf. Cookie jest HttpOnly, a token jest zwracany w prywatnym
  JSON. Przed każdym POST frontend pobiera token i przekazuje X-CSRF-TOKEN.
  require-form-url-encoded pozostaje true: JSON bez nagłówka nie może
  ominąć filtra. Dodatkowo każda operacja zapisu wymaga Origin dokładnie
  zgodnego z auth.frontend-url, również login i logout. Brak Origin jest
  odrzucany. Produkcyjne cookie CSRF ma nazwę __Host-csrf i Secure;
  dev/test używa csrf-token. Nie uruchamiamy CORS.
- Proxy Nitro dopuszcza wyłącznie konkretne ścieżki GET/POST, przekazuje
  Cookie i wszystkie Set-Cookie. Playwright sprawdza logowanie, atrybuty
  cookie, logout i reset przez rzeczywiste proxy. Usuwamy Forwarded,
  X-Forwarded-For i X-Real-IP; Quarkus nie interpretuje nagłówków proxy.
- Limiter jest synchronizowanym, ograniczonym do 10000 wpisów magazynem
  okien w pamięci. Usuwa wygasłe wpisy przy kolejnych żądaniach, po
  zapełnieniu odrzuca nowe klucze (429), nie wyrzuca aktywnych limitów.
  Klucze kont są hashami znormalizowanych adresów. Domyślne limity:
  login 10/15 min/IP i 10/15 min/konto; rejestracja 5/h/IP;
  reset request 10/h/IP i 3/h/adres; reset confirm 10/15 min/IP.
  Liczone są także udane próby. Nie ma trwałej blokady użytkownika.
  Lokalny backend widzi IP proxy (loopback), więc przeglądarki współdzielą
  limit IP. To świadome ograniczenie lokalnego etapu. Przed wdrożeniem
  potrzebna jest konfiguracja zaufanego proxy i limitowanie przed aplikacją.
  Restart czyści liczniki; wiele instancji nie współdzieli stanu.
- Reset blokuje najpierw użytkownika, potem token. Sprawdzenie ważności,
  zmiana hasha, zużycie wszystkich jego tokenów i usunięcie wszystkich
  sesji są w jednej transakcji. Login blokuje tego samego użytkownika,
  co zapobiega utworzeniu sesji ze starym hasłem w trakcie resetu.
  Równoczesne użycie tokenu ma dokładnie jednego zwycięzcę (test PostgreSQL).
- quarkus-mailer i quarkus-scheduler z BOM 3.40.1; Mailpit v1.33.0
  dostępny tylko przez loopback. Mail jest tekstowy PL/EN. Zaufana baza
  linku pochodzi z konfiguracji; token w fragmencie #token= nie trafia
  do HTTP URL ani Referer. Frontend usuwa fragment z historii po odczycie.
  Strona resetu ma Referrer-Policy: no-referrer i wyłącznie lokalne zasoby.
  Błąd dostarczenia pozostawia neutralną odpowiedź i usuwa niewysłany token;
  ostrzeżenie nie zawiera odbiorcy, treści ani wyjątku SMTP.
- Kategorie quarkus-mailer i io.quarkus.mailer mają poziom ERROR:
  szczególnie mock mailer domyślnie logowałby pełne treści. Tokeny CSRF
  również nie są logowane. Zadanie co godzinę usuwa wygasłe sesje oraz
  wygasłe/zużyte tokeny; nie usuwa użytkowników.
- Playwright 1.65.0 + Chromium: scenariusze PL/EN z rzeczywistym Mailpit.
  Testy API używają istniejącego schematu travel_buddy_test PostgreSQL,
  unikalnych adresów i usuwają tylko konto utworzone w danym teście.
  E2E zostawia lokalne konta e2e-…@example.test; nie kasuje danych użytkownika.

Źródła integracji:
- https://quarkus.io/guides/security-csrf-prevention/
- https://quarkus.io/guides/mailer-reference/
- https://github.com/Password4j/password4j
- https://github.com/axllent/mailpit
