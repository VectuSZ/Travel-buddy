# Travel Buddy — etap 1

Lokalne wyprawy z PostgreSQL, Quarkus i Nuxt SSR. Wymagane Java 21, Node 24, npm i Docker Compose. Bez instalacji globalnych narzędzi.

## Uruchomienie

Jednorazowo w repozytorium:

```sh
cp .env.example .env
cd frontend
npm ci
cd ..
```

Dane dostępowe w .env.example są wyłącznie developerskie. Nie używaj ich poza lokalnym środowiskiem.

Terminal 1 (katalog repozytorium):

```sh
docker compose up -d --wait
docker compose logs -f postgres
```

Terminal 2 (katalog repozytorium):

```sh
./scripts/backend.sh quarkus:dev
```

Skrypt eksportuje główny .env i wybiera Java 21 przez macOS java_home. Quarkus sam nie wczytuje .env z katalogu repozytorium. Plik musi mieć składnię zgodną z powłoką.

Terminal 3:

```sh
cd frontend
npm run dev
```

Otwórz http://localhost:3000. Backend: http://localhost:8080/api/v1/trips. Baza: 127.0.0.1:5433. Oba serwery obsługują hot reload.

## Sprawdzenia

Przy działającej bazie:

```sh
./scripts/backend.sh test
cd frontend
npm run typecheck
npm run build
cd ..
```

Test API używa PostgreSQL z Compose w osobnym schemacie travel_buddy_test. Nie usuwa danych developerskich. Przy uruchomionych aplikacjach:

```sh
node scripts/check-ssr.mjs
curl -f http://localhost:8080/q/health/ready
```

Ręcznie otwórz listę, szczegóły i przełącz PL/EN, również na wąskim ekranie. Treści autora nie są tłumaczone. Zatrzymaj backend i odśwież stronę, aby zobaczyć komunikat błędu. Nieistniejący UUID daje 404.

## Zatrzymanie, trwałość, diagnostyka

Ctrl+C zatrzymuje aplikacje i podgląd logów. Baza zachowuje nazwany wolumen:

```sh
docker compose stop
docker compose up -d --wait
```

Po ponownym uruchomieniu sprawdź te same UUID w API. Nie używaj down -v.

```sh
docker compose ps
docker compose logs --tail=100 postgres
./scripts/backend.sh -version
node --version
npm --version
lsof -i :3000 -i :8080 -i :5433
curl -i http://localhost:3000/api/v1/trips
```

Jeśli Docker jest niedostępny, uruchom Docker Desktop. Zmiana hasła w .env nie zmienia hasła istniejącej bazy w wolumenie.

## Ograniczenia

Wyprawy pozostają demonstracyjne i tylko do odczytu. Konta lokalne opisuje sekcja etapu 2A; formularze wypraw i filtrowanie są poza zakresem. Seedy są aktywne wyłącznie w dev/test; nie używaj tego wolumenu dla profilu produkcyjnego. Testowy schemat pozostaje po testach.

## Wyniki lokalnej weryfikacji

2026-11-06: dwa testy API przeszły na PostgreSQL; typecheck i build przeszły.
Skrypt SSR przeszedł dla listy i szczegółów PL/EN, z pominięciem skryptów
oraz head w HTML. Healthcheck UP, strona nieistniejącej wyprawy HTTP 404.
Po restarcie PostgreSQL oba rekordy zachowały UUID. Pierwsze żądanie po
restarcie zwróciło 500 przez zerwane połączenie z puli; kolejne działały.
Nie wykonano automatycznego testu kliknięć w przeglądarce ani oceny wizualnej
na telefonie. Te czynności pozostają do ręcznej weryfikacji.

# Etap 2A — konta lokalne

Dotychczasowe polecenia uruchamiają teraz również Mailpit. W terminalu 1:

```sh
docker compose up -d --wait
docker compose ps
```

PostgreSQL pozostaje w swoim wolumenie. Mailpit: panel
http://localhost:8025, SMTP 127.0.0.1:1025. Nie wysyła poczty do internetu.
Oba porty Mailpit są dostępne tylko na loopback. Wiadomości Mailpit nie
mają trwałego wolumenu — mogą zniknąć po odtworzeniu kontenera.

Backend i frontend uruchom w osobnych terminalach jak wcześniej:

```sh
./scripts/backend.sh quarkus:dev
```

```sh
cd frontend
npm ci
npm run dev
```

Korzystaj z **http://localhost:3000**, nie zamiennie z 127.0.0.1:3000:
backend porównuje Origin z dokładnym zaufanym adresem frontendu.

## Ręczna weryfikacja kont

1. Otwórz Rejestracja, podaj testowy e-mail, hasło (12–1024 znaków),
   potwierdź je i zaakceptuj lokalny regulamin roboczy local-draft-1.
   Po rejestracji ekran konta pokazuje wyłącznie własny e-mail i wylogowanie.
2. Wyloguj się, zaloguj ponownie (wielkość liter w e-mailu nie ma znaczenia).
   Lista i szczegóły wypraw działają także bez logowania.
3. Wybierz „Nie pamiętam hasła”. Odpowiedź jest neutralna także dla
   nieistniejącego lub nieaktywnego konta.
4. W panelu Mailpit otwórz wiadomość i jej link. Ustaw nowe hasło;
   dotychczasowe sesje przestają działać. Token jest ważny 30 minut i tylko
   raz. Po odświeżeniu strony resetu otwórz link z wiadomości ponownie:
   aplikacja usuwa jego fragment z paska adresu po odczycie.
5. Sprawdź te same ekrany po przełączeniu EN. Język maila wynika z języka
   wybranego przy rejestracji. Zmiana języka nawigacji nie zmienia konta.

Regulamin jest wyraźnie oznaczonym szkicem lokalnym, a nie dokumentem
zatwierdzonym do publicznego działania. Nie ma jeszcze danych administratora,
pełnych procedur ani przeglądu prawnego. Weryfikacja e-maila nie jest wymagana.

## Cookies, CSRF i konfiguracja

- Dev/test: cookie session, HttpOnly, SameSite=Lax, Path=/, bez Secure.
  Pozostałe profile: __Host-session z Secure; wymagany AUTH_FRONTEND_URL.
- Sesja jest egzekwowana przez backend przez maksymalnie 7 dni. Baza
  zawiera tylko hash tokenu. Logout usuwa bieżącą sesję, reset wszystkie.
- Przed POST frontend pobiera GET /api/v1/auth/csrf (no-store). Quarkus
  ustawia HttpOnly cookie CSRF i zwraca token w JSON. Frontend przesyła
  X-CSRF-TOKEN, a przeglądarka Cookie i Origin. Żądanie bez poprawnego
  tokenu albo z innym/brakującym Origin zostaje odrzucone.
- Całość idzie przez /api/v1 w Nuxt. Nie ma CORS ani localStorage tokenów.
  Prywatny stan konta jest pobierany po stronie klienta, nie w publicznym SSR.
- Link resetu powstaje z auth.frontend-url, nigdy z Host. Dev/test używają
  http://localhost:3000; produkcyjny AUTH_FRONTEND_URL musi wskazywać
  zaufany origin bez końcowego ukośnika. NUXT_BACKEND_URL wskazuje prywatny
  adres backendu (domyślnie http://127.0.0.1:8080).
- Skrypt backend.sh eksportuje zmienne z .env, więc można tam dopisać
  poniższe nadpisania. Nie commituj rzeczywistych sekretów.

| Zmienna | Domyślnie |
| --- | --- |
| AUTH_RATE_LOGIN_IP | 10 |
| AUTH_RATE_LOGIN_ACCOUNT | 10 |
| AUTH_RATE_LOGIN_WINDOW | PT15M |
| AUTH_RATE_REGISTER_IP | 5 |
| AUTH_RATE_REGISTER_WINDOW | PT1H |
| AUTH_RATE_RESET_IP | 10 |
| AUTH_RATE_RESET_ACCOUNT | 3 |
| AUTH_RATE_RESET_WINDOW | PT1H |
| AUTH_RATE_CAPACITY | 10000 |

Limiter w pamięci dotyczy pojedynczego procesu; restart zeruje liczniki.
Przy lokalnym proxy wszyscy klienci współdzielą loopback IP i jego limit.
Dowolne X-Forwarded-For nie zmienia limitu. Testy licznika nie czekają na
upływ czasu: używają kontrolowanego zegara. Po zapełnieniu magazynu nowe
klucze otrzymują 429; wygasłe wpisy są usuwane przy kolejnych żądaniach.
Nie używaj tego jako gotowej ochrony publicznego wdrożenia.

## Testy etapu 2A

Przy uruchomionym PostgreSQL (testy API używają MockMailbox, bez SMTP):

```sh
./scripts/backend.sh test
```

Typecheck i build (wykonuj przed testami przeglądarkowymi; równoległy build
może przeładować serwer dev i formularze):

```sh
cd frontend
npm run typecheck
npm run build
```

Przy działającym backendzie, Nuxt i Mailpit:

```sh
cd frontend
npx playwright install chromium
npm run test:e2e
cd ..
node scripts/check-ssr.mjs
```

E2E sprawdza PL/EN: rejestracja → konto → logout → login → Mailpit → reset
→ login nowym hasłem, a także cookies, no-store i brak e-maila w publicznym
HTML. Konta e2e-…@example.test i ich wiadomości pozostają lokalnie.
Nie uruchamiaj wielokrotnie E2E z rzędu przeciw temu samemu procesowi bez
uwzględnienia limitów: celowo działają także dla testów przeglądarkowych.
Testy API resetują wyłącznie swój licznik w pamięci i własne rekordy
w schemacie testowym. Nie używają H2 i nie czyszczą wolumenów.

Zatrzymanie: Ctrl+C dla Nuxt i Quarkusa; docker compose stop zatrzyma
PostgreSQL i Mailpit bez usuwania wolumenu danych.

## Wyniki weryfikacji etapu 2A (2026-11-06)

- 19 testów backendu przeszło: 16 kont, 2 publicznego API wypraw i 1
  deterministyczny test okien/pojemności limitera. Testy integracyjne
  pracowały na PostgreSQL 17.6, bez H2.
- Typecheck i build frontendu przeszły.
- Oba scenariusze Playwright PL/EN przeszły na serwerze developerskim
  i na lokalnie uruchomionym zbudowanym Nuxt, z rzeczywistym SMTP Mailpit.
- SSR listy i szczegółów PL/EN przeszedł także po buildzie. Dotychczasowe
  wyprawy zachowały swoje UUID. Healthcheck backendu: UP.
- Sprawdzono brak linków resetujących w aktualnych logach aplikacji/testów.
- Nie wykonywano publicznego wdrożenia, testów Google OIDC ani HTTPS
  w przeglądarce. Secure/__Host- są skonfigurowane dla profilu produkcyjnego,
  ale lokalne E2E celowo korzysta z HTTP. Nie wykonano wizualnego przeglądu
  na fizycznym telefonie ani testów innych silników niż Chromium.
