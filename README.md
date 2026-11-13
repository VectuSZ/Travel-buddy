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

# Etap 2B — Google OIDC (opcjonalne)

Bez konfiguracji Google aplikacja działa jak wcześniej, a przycisk Google
jest ukryty. Nie trzeba tworzyć projektu w Google, aby uruchomić testy.
Account linking i odłączanie metod logowania nie są zaimplementowane.

## Konfiguracja przez właściciela konta Google

1. Otwórz https://console.cloud.google.com/ i utwórz lub wybierz projekt.
2. Otwórz **Google Auth Platform** (w niektórych widokach: APIs & Services →
   OAuth consent screen). W Branding podaj nazwę aplikacji oraz wymagane
   adresy kontaktowe. W Audience wybierz External, pozostaw status Testing
   i dodaj swój adres do Test users. Nie publikuj aplikacji.
3. W Data Access wybierz wyłącznie dane tożsamości: openid, email i profile.
   Nie włączaj dodatkowych Google API.
4. W Clients utwórz OAuth Client ID typu **Web application**.
   Dodaj Authorized redirect URI dokładnie:
   `http://localhost:3000/api/v1/auth/google/callback`.
   Nie używamy SDK Google w przeglądarce; Authorized JavaScript origins
   nie jest potrzebne do tego serwerowego przepływu.
5. Skopiuj Client ID i Client secret do lokalnego, ignorowanego `.env`:

```dotenv
GOOGLE_OIDC_ENABLED=true
GOOGLE_OIDC_CLIENT_ID=tu_wstaw_client_id
GOOGLE_OIDC_CLIENT_SECRET=tu_wstaw_client_secret
GOOGLE_OIDC_REDIRECT_URI=http://localhost:3000/api/v1/auth/google/callback
```

6. Zrestartuj backend przez Ctrl+C, a następnie:

```sh
./scripts/backend.sh quarkus:dev
```

7. Uruchom frontend przez `npm run dev` i korzystaj z
   **http://localhost:3000**. Nie zamieniaj tego adresu na 127.0.0.1.
   Google musi mieć identyczny redirect URI, łącznie z portem i ścieżką.

Opis konfiguracji Google: https://developers.google.com/identity/openid-connect/openid-connect.
Nie wpisuj sekretu do repozytorium ani nie przesyłaj go w rozmowie.
Aby wyłączyć integrację, ustaw GOOGLE_OIDC_ENABLED=false i zrestartuj backend.

## Ręczny test prawdziwego Google — do wykonania po konfiguracji

- Rejestracja: zaakceptuj regulamin roboczy i wybierz „Kontynuuj z Google”.
  Bez akceptacji przycisk jest nieaktywny. Nie trzeba wypełniać pól hasła.
- Użyj konta testowego Google z adresem, którego jeszcze nie ma w aplikacji.
  Po powrocie powinien otworzyć się ekran konta z własnym e-mailem.
- Wyloguj się. Na ekranie logowania użyj Google ponownie; konto powinno
  pozostać to samo, bez ponownej akceptacji i bez nowej rejestracji.
- Sprawdź PL i EN. Język oraz zgoda pochodzą z zapamiętanego startu,
  a nie z parametrów callbacku.
- Anuluj wybór/zgodę Google, jeżeli dostawca pokazuje taką opcję: aplikacja
  powinna pokazać komunikat anulowania. Zamknięcie karty Google nie wysyła
  callbacku; można wtedy wrócić i rozpocząć nową próbę.
- Dla nowej tożsamości rozpoczętej z ekranu logowania powinno pojawić się
  skierowanie do rejestracji, zamiast utworzenia konta bez zgody.
- Użyj innego konta Google, którego e-mail wcześniej zarejestrujesz hasłem:
  powinien pojawić się konflikt, a logowanie dotychczasowym hasłem nadal
  działać. Nie testuj tego, kasując istniejące dane lub wolumen.
- W Network sprawdź callback przez localhost:3000 oraz końcowe przekierowanie
  do /account lub /en/account. Po zakończeniu działa cookie session;
  nie powinno być aktywnego q_session ani tokenów Google w localStorage.
- Publiczne wyprawy, reset hasła kont lokalnych i przełącznik języka nadal
  powinny działać. Dla kont wyłącznie Google reset hasła nie tworzy hasła.

Przy błędzie sprawdź status Testing/Test users, typ klienta Web application,
Client ID/secret i dokładny redirect URI. Nie włączaj logowania pełnych
odpowiedzi OIDC. Błąd konfiguracji na ekranie samego Google (np. niezgodny
redirect URI) może wymagać poprawki w Console, zanim Google pozwoli wrócić.

## API i testy Google

| Endpoint | Działanie |
| --- | --- |
| GET /api/v1/auth/google/config | Czy przycisk Google jest włączony |
| POST /api/v1/auth/google/start | JSON mode: login/register, locale: pl/en, acceptTerms, termsVersion; wymaga CSRF i Origin |
| GET /api/v1/auth/google/authorize | Przekierowanie do dostawcy dla wcześniej rozpoczętej próby |
| GET /api/v1/auth/google/callback | Callback obsługiwany i weryfikowany przez Quarkus OIDC |
| GET /api/v1/auth/google/error | Bezpieczne mapowanie błędu/anulowania |

Start zwraca wyłącznie wewnętrzną ścieżkę w polu url. Nie obsługuje dowolnego
returnUrl. Próba ważna jest 10 minut; najnowszy start w tej samej przeglądarce
zastępuje wcześniejsze powiązanie. Restart backendu unieważnia rozpoczęte
próby, ale nie utworzone już sesje aplikacji. Magazyn prób jest lokalny,
ograniczony i sprzątany; nie jest rozwiązaniem dla wielu instancji.

Testy backendu używają kontrolowanego serwera OIDC z podpisanymi tokenami
oraz PostgreSQL. Nie potrzebują kont Google ani prawdziwego client secret:

```sh
./scripts/backend.sh test
```

Testy proxy (po buildzie frontendu; skrypt sam uruchamia izolowany Nuxt
na wolnym porcie i lokalny serwer testowy, po czym je zatrzymuje):

```sh
cd frontend
npm run typecheck
npm run build
cd ..
node scripts/check-google-proxy.mjs
```

Dotychczasowe `npm run test:e2e` uruchamiaj z GOOGLE_OIDC_ENABLED=false;
sprawdza również ukrycie przycisku Google. Uruchom potem
`node scripts/check-ssr.mjs` z katalogu głównego. Testy protokołu Google
nie zastępują powyższej ręcznej checklisty prawdziwego dostawcy.

## Wyniki weryfikacji etapu 2B (2026-11-11)

- 29 testów backendu: zaliczone, w tym 9 scenariuszy OIDC z kontrolowanym
  dostawcą, PostgreSQL i rzeczywistą weryfikacją tokenów przez Quarkusa.
- Dotychczasowe E2E PL/EN z Mailpit: 2/2 zaliczone; Google wyłączone,
  przycisk ukryty, logowanie hasłem i reset nadal działają.
- Typecheck, build Nuxta i test proxy OIDC: zaliczone.
- Publiczna lista i szczegóły SSR w PL/EN: 4/4 zaliczone.
- Prawdziwe logowanie Google NIE było wykonywane. Właściciel musi
  skonfigurować klienta w Console i wykonać checklistę powyżej.
- Nie sprawdzano publicznego HTTPS ani wdrożenia. Próby OIDC w pamięci
  wymagają pojedynczego backendu i rozpoczęcia od nowa po jego restarcie.

## Etap 3 — profil podróżnika i prywatne kontakty

Ekran `/account` (EN: `/en/account`) zawiera dwa niezależne formularze.
Nowe konto nie ma profilu, wieku ani kontaktów. Profil publiczny wymaga
nazwy, wieku 18–120, co najmniej jednego języka (maks. 10) i stylu.
Bio jest opcjonalne. Kontakty można zapisać osobno i wszystkie usunąć.
Nie są udostępniane innym użytkownikom na tym etapie.

API: `GET /api/v1/me`, `PATCH /api/v1/me/profile`,
`PUT /api/v1/me/contacts`, `GET /api/v1/profiles/{userId}`.
[OpenAPI nowych endpointów](docs/openapi-profiles.yaml) opisuje payloady,
wersjonowanie i błędy. Przeglądarka korzysta z istniejącego proxy Nuxta,
sesji i tokenu CSRF uzyskanego z `/api/v1/auth/csrf`. Dane prywatne są
pobierane tylko po stronie klienta, z `Cache-Control: private, no-store`.
Publiczny profil ma tylko publiczne DTO, SSR, `noindex` i brak sitemap.

### Ręczna weryfikacja na dwóch kontach

1. Uruchom Compose, backend i frontend poleceniami z początku README.
   Flyway zastosuje V5 bez usuwania dotychczasowych danych.
2. Zarejestruj konto A. W `/account` sprawdź pusty profil i kontakty.
   Wpisz nazwę, wiek, język np. niemiecki i styl; zapisz i odśwież.
   Wpisane dane powinny pozostać, a komunikat wskazywać brak kontaktu.
3. Zapisz e-mail kontaktowy **inny niż e-mail logowania**, Instagram
   `@test_name` i WhatsApp `+48123456789`. Odśwież stronę. Znak @ ma
   zostać usunięty, a komunikat wskazywać gotowość do przyszłego udziału.
   Numer jest sprawdzany tylko pod względem formatu.
4. Otwórz konto w dwóch kartach. Zapisz bio w pierwszej, potem w drugiej.
   Druga pokaże konflikt 409 i zachowa szkic. Dopiero kliknięcie ponownego
   pobrania ma wczytać aktualny profil.
5. Skopiuj link „Zobacz profil publiczny”. Otwórz go w oknie prywatnym,
   a potem zaloguj tam konto B i otwórz ten sam link. W obu przypadkach
   widoczne są tylko nazwa, wiek, bio, języki i style konta A.
   Konto B w swoim `/account` nadal widzi własne dane.
6. Sprawdź źródło HTML strony profilu (wraz z osadzonym payloadem Nuxta)
   i JSON `/api/v1/profiles/{id}`. Nie mogą zawierać e-maila logowania A,
   kontaktowego A, Instagramu, numeru ani danych sesji. JSON ma dokładnie
   sześć pól opisanych w OpenAPI. HTML ma `noindex`.
7. Przełącz PL/EN. Sprawdź nazwy języków i stylów. Spróbuj wieku 17,
   braku języków oraz Instagramu jako URL — zapis ma zostać odrzucony,
   a wpisane dane zachowane. Bez sesji `/api/v1/me` zwraca 401.
8. Na koncie A wyczyść wszystkie kontakty, zapisz i odśwież. Profil
   pozostaje, a gotowość znów wskazuje brak kontaktu. Przeglądanie wypraw
   przez cały czas działa bez uzupełniania profilu.

### Sprawdzenia automatyczne etapu 3

Najpierw testy backendu (PostgreSQL uruchomiony; zatrzymaj dev backend,
aby Maven test nie przebudowywał klas używanych przez hot reload):

```sh
./scripts/backend.sh test
```

Testy używają osobnego schematu PostgreSQL i kontrolowanego dostawcy
OIDC. Nie wymagają prawdziwego konta Google. Następnie uruchom backend
na potrzeby E2E z jawnym wyłączeniem Google, bez zmiany prywatnego `.env`:

```sh
./scripts/backend.sh quarkus:dev -Dauth.google.enabled=false -Dquarkus.oidc.tenant-enabled=false
```

W drugim terminalu:

```sh
cd frontend
npm run typecheck
npm run build
npm run dev
```

W trzecim terminalu (PostgreSQL i Mailpit nadal uruchomione):

```sh
cd frontend
npm run test:e2e
cd ..
node scripts/check-ssr.mjs
node scripts/check-google-proxy.mjs
```

E2E tworzą cztery lokalne konta testowe i wiadomości Mailpit. Pozostają
w developerskiej bazie; testy nie usuwają kont użytkownika ani wolumenów.
Przed powtórzeniem całego zestawu zrestartuj backend, aby wyzerować
lokalny limiter w pamięci (5 rejestracji/IP/godzinę). Po testach zatrzymaj
frontend i backend przez Ctrl+C. Powrót do normalnej konfiguracji Google:
uruchom `./scripts/backend.sh quarkus:dev` bez nadpisań `-D`.

### Wyniki etapu 3 (2026-11-13)

Etap 3 jest zaimplementowany i zweryfikowany lokalnie:

- Backend: **35/35** testów na PostgreSQL 17.6, w tym 6 testów profili,
  regresja kont/sesji/resetu, 9 testów kontrolowanego OIDC i publiczne wyprawy.
- Frontend: `npm run typecheck` i `npm run build` zakończone powodzeniem.
- Playwright: **3/3** na zbudowanym Nuxtcie — dotychczasowe auth/reset PL/EN,
  profil i kontakty, odświeżenie, usuwanie, walidacja, konflikt 409 bez
  utraty szkicu, dwa konta, anonimowy profil, przełącznik języka i brak
  prywatnych danych w publicznym HTML/payloadzie oraz ścisła lista pól API.
- SSR wypraw: **4/4** (lista i szczegóły PL/EN).
- Proxy Google: przekierowania, query, Cookie, Set-Cookie, Origin i no-store
  sprawdzone pomyślnie. `git diff --check` bez błędów.

Prawdziwe logowanie Google nie było ponawiane w etapie 3; regresję OIDC
sprawdzono kontrolowanym dostawcą. Nie wykonywano wdrożenia ani testów
publicznego HTTPS. Kontakty pozostają nieweryfikowane i dostępne wyłącznie
właścicielowi. Udostępnianie ich w ramach wypraw należy do przyszłych etapów.
