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
   potwierdź je i zaakceptuj lokalny regulamin roboczy local-draft-2.
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
Konto lokalne można powiązać z Google po potwierdzeniu aktualnego hasła przy konflikcie adresów lub z ustawień konta.

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

- Na ekranie logowania lub rejestracji wybierz „Kontynuuj z Google” i wskaż konto w selektorze Google.
- Nowa tożsamość z wolnym, zweryfikowanym adresem wraca na ekran regulaminu. Sprawdź linki do obu dokumentów. Rezygnacja wraca do logowania. Akceptacja tworzy konto i przenosi na ekran konta.
- Wyloguj się, wybierz inne konto w tej samej przeglądarce, a następnie wróć do pierwszego. Istniejące konto przechodzi bezpośrednio na ekran konta. Powtórz w PL i EN.
- Anuluj proces na ekranie dostawcy, jeśli udostępnia odmowę. Po powrocie sprawdź komunikat i link logowania.
- Użyj innego konta Google, którego e-mail wcześniej zarejestrujesz hasłem:
  powinna pojawić się propozycja połączenia z potwierdzeniem hasłem. Logowanie dotychczasowym hasłem nadal
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
| POST /api/v1/auth/google/start | Wspólny start z polem locale (pl/en). Opcjonalne link=true rozpoczyna łączenie z ustawień, a reauthenticate=true służy potwierdzeniu usuwania konta. Wymaga CSRF i Origin |
| GET /api/v1/auth/google/authorize | Przekierowanie do dostawcy dla wcześniej rozpoczętej próby |
| GET /api/v1/auth/google/callback | Callback obsługiwany i weryfikowany przez Quarkus OIDC |
| GET /api/v1/auth/google/methods | Dostępne metody logowania właściciela bieżącej sesji |
| GET /api/v1/auth/google/link | Dane ekranu oczekującego powiązania dla bieżącej przeglądarki |
| POST /api/v1/auth/google/link | Potwierdzenie aktualnym hasłem albo anulowanie próby. Wymaga Origin i CSRF |
| GET /api/v1/auth/google/consent | Potwierdzenie oczekującej próby dla bieżącej przeglądarki |
| POST /api/v1/auth/google/consent | Jednorazowa akceptacja aktualnej wersji lub rezygnacja. Wymaga CSRF i Origin |
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

## Backend etapu 4 — cykl życia wypraw

Backend obsługuje szkic → publikację → zamknięcie. Formularze, filtry
i nowe widoki SSR pozostają następnym zadaniem. Stare pola publicznego
DTO pozostają dla zgodności stron; nowe zawierają trasę, warunki, status
i minimalny profil organizatora (null dla demonstracyjnego konta bez profilu).

| Endpoint pod /api/v1 | Działanie |
| --- | --- |
| POST /trips | Szkic, także z pustym obiektem; HTTP 201 |
| GET /me/trips | Własne wyprawy |
| GET /me/trips/{id} | Własny model edycyjny |
| PATCH /trips/{id} | Częściowa edycja, wymagane version |
| POST /trips/{id}/publish | Publikacja, body: version |
| POST /trips/{id}/close | Zamknięcie, body: version |

Zapisy wymagają sesji, Origin i CSRF jak endpointy profilu. Nieznane pola
są odrzucane. Brak pola PATCH zachowuje wartość, null usuwa wartość;
locations zastępuje całą uporządkowaną trasę. Pusta tablica usuwa trasę
szkicu. Nie można edytować CLOSED ani ponownie go otworzyć.
Konflikt: 409 TRIP_VERSION_CONFLICT. Cudza wyprawa: 404.

Przykładowy komplet do PATCH (version pobierz z odczytu autora):

    {
      "version": 0,
      "title": "Weekend w górach",
      "itinerary": "Wspólne spokojne wędrówki.",
      "lookingFor": "Osoby lubiące piesze wycieczki.",
      "contentLanguage": "pl",
      "dateMode": "FLEXIBLE",
      "dateFrom": "2027-06-01",
      "dateTo": "2027-06-10",
      "durationDays": 3,
      "targetPeople": 4,
      "budgetMin": 300,
      "budgetMax": 800,
      "styles": ["trekking"],
      "locations": [{"countryCode": "PL", "city": "Zakopane"}]
    }

Limity: trips.publish-per-day=3 (dzień UTC), trips.max-open=5.
Blokada właściciela serializuje publikacje. Dzienny limit obejmuje też
zamknięte publikacje z tego dnia.

### Testy na osobnej bazie

Przy zatrzymanym backendzie dev:

    docker compose exec -T postgres sh -c 'createdb -U "$POSTGRES_USER" travel_buddy_stage4_test_20261114'
    ./scripts/backend.sh test -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114

Pierwsze polecenie wykonaj tylko raz — baza utworzona w tej sesji już
istnieje. TripMigrationTest wymaga uprawnienia CREATEDB lokalnej roli
Compose. Tworzy dwie bazy tb_migration_<uuid>, uruchamia prawdziwy Flyway
od zera i od V2, sprawdza dane oraz validate. Bazy pozostają do inspekcji;
ich nazwy są w logu. Nie usuwa lokalnej bazy ani wolumenu.

### Ręczny odbiór backendu na dwóch kontach

1. Na koncie A uzupełnij profil i kontakt w /account. W narzędziu API
   z cookies sesji A pobierz CSRF, utwórz POST /trips z {}.
   Odczytaj zapisany UUID przez /me/trips/{id}.
2. PATCH z przykładową treścią i aktualną version. Powtórz PATCH ze starą
   wersją — oczekuj 409 i zachowania nowszej treści.
3. Z sesją B spróbuj odczytu autora i PATCH tego UUID — oczekuj 404.
   Anonimowy GET /trips/{id} także ma zwrócić 404.
4. Z sesją A wywołaj publish z aktualną version. Anonimowy czytelnik i B
   zobaczą wyprawę na liście oraz szczegóły, bez kontaktu i e-maila A.
5. Z sesją A wywołaj close z wersją zwróconą przez publish. Wyprawa znika
   z listy, a szczegóły mają CLOSED. Dla osobnego szkicu usuń kontakt A:
   publikacja ma zwrócić 400 INVALID_CONTACT_REQUIRED.

Nie ma jeszcze interfejsu tworzenia wypraw; odbiór dotyczy API.

## Formularze wypraw — odbiór w przeglądarce

Interfejs autora etapu 4 jest już dostępny (zastępuje wcześniejszą
informację o obsłudze wyłącznie przez API). Uruchom Compose, backend
i frontend jak na początku README, używając http://localhost:3000.

1. Zaloguj konto A. Uzupełnij profil w „Moje konto”, początkowo bez kontaktu.
   W „Moje wyprawy” wybierz „Utwórz wyprawę”, wpisz tylko tytuł i zapisz
   szkic. Odśwież stronę, wróć na listę autora i otwórz ten sam szkic.
2. Uzupełnij trasę, plan, kogo szukasz, język, styl, daty, liczbę osób
   i budżet. Przestaw lokalizacje. Kliknij „Opublikuj”: komunikat o braku
   kontaktu ma prowadzić do odpowiedniej sekcji konta. Ponowienie nie
   powinno tworzyć drugiego szkicu.
3. Dodaj kontakt, wróć do szkicu i opublikuj. Otwórz „Publiczne szczegóły”
   na koncie B w osobnym profilu przeglądarki. Kontakt A pozostaje prywatny.
4. Edycję sprawdź w dwóch kartach A: zapisz zmianę w jednej, potem drugiej.
   Druga powinna zachować wpisany tekst i pokazać konflikt. Dopiero
   „Wczytaj aktualną wersję” zastępuje tekst nowszą treścią.
5. Zamknij wyprawę jako A i potwierdź. Na liście autora jest „Zamknięta”;
   na publicznej liście wyprawy nie ma, ale zapisany link nadal działa.
6. Powtórz w EN i sprawdź układ na wąskim ekranie. Wyprawa zamknięta
   pozostaje tylko do odczytu.

Typecheck/build: dotychczasowe polecenia. Pełny zestaw E2E tworzy 8 kont,
więc dla jednego przebiegu uruchom backend testowo:

    ./scripts/backend.sh quarkus:dev -Dauth.rate.register-ip=30 -Dauth.google.enabled=false

Następnie przy uruchomionym Nuxt i Mailpit:

    cd frontend
    npm run test:e2e

Sam nowy zestaw: npx playwright test tests/trips.spec.ts (PL i EN).
Ustawienia powyżej dotyczą wyłącznie bieżącego procesu; zwykłe uruchomienie
przywraca konfigurację lokalną. Nie zmieniaj .env dla tych testów.
E2E pozostawiają konta i wyprawy; nie usuwają wolumenu.

## Publiczne wyszukiwanie — ukończony etap 4

Listy: http://localhost:3000/pl/wyprawy oraz /en/trips.
Stare / i /en nadal działają. API GET /api/v1/trips przyjmuje:
country (ISO, np. PT), from/to (YYYY-MM-DD), budgetMin/budgetMax (PLN/os.),
style (kod profilu), page (od 1), size (1–50, domyślnie 20).
Odpowiedź: {items: [...], total, page, size}. Zastępuje dawną tablicę.
Granice dat i budżetu są włączne; można podać jedną granicę.
Błędne filtry: 400 INVALID_SEARCH. Kolejność published_at DESC,id DESC.

Przykład skopiowanego URL:
http://localhost:3000/pl/wyprawy?country=PT&budgetMin=2200&budgetMax=2500

Ręcznie: opublikuj jako A trasę Porto → Lizbona za 1500–2200 PLN.
Jako B ustaw PT i budżet 2200–2500: wyprawa pojawia się raz.
Minimum 2200.01 ją wyklucza. Sprawdź daty, styl, rozmiar strony,
Wstecz i skopiowanie adresu do okna prywatnego. Po zamknięciu przez A
znika z wyników, ale zapisany link szczegółów nadal pokazuje Zamknięta.
Stary URL bez sluga i nieaktualny slug przekierowują 301. Źródło HTML
zawiera wyniki/metadane bez potrzeby JS i bez kontaktów.

Dodatkowy test: cd frontend && npx playwright test tests/search.spec.ts.
Test cyklu życia trips.spec.ts obejmuje teraz także wyszukanie i SSR.
Canonical używa lokalnego http://localhost:3000, konfigurowalnego przez
NUXT_PUBLIC_SITE_URL. Nie jest to konfiguracja wdrożenia.

## Etap 5 — zgłoszenia, uczestnicy i prywatne kontakty

Po zwykłym uruchomieniu backendu Flyway zastosuje V10 bez kasowania
istniejących danych. Na stronie wyprawy zalogowany użytkownik może wysłać
wiadomość po potwierdzeniu zasad udostępnienia kontaktów. „Moje zgłoszenia”
pokazuje własne wiadomości/statusy i wycofanie. Panel zgłoszeń organizatora
znajduje się pod formularzem edycji w „Moje wyprawy”.

Ręczny odbiór na A, B i C:

1. Uzupełnij profile i prywatne kontakty trzech kont. A publikuje wyprawę
   z limitem 3 osób (łącznie z A). B wysyła wiadomość ze szczegółów.
2. A otwiera edycję wyprawy, odczytuje wiadomość, akceptuje po przeczytaniu
   informacji o publicznym uczestnictwie i wzajemnym kontakcie.
3. Odśwież szczegóły jako B/anonimowo: lista pokazuje A i B, ale nie
   wiadomość ani kontakt. A i B wybierają „Pokaż kontakt” w swoim panelu.
   E-mail logowania nie jest używany jako kontakt.
4. C zgłasza się, A odrzuca. C widzi Odrzucone w „Moje zgłoszenia” i nie
   otrzymuje kontaktu. Osobno sprawdź zaakceptowanie C: B nadal nie może
   uzyskać kontaktu C przez endpoint wyprawy.
5. B wycofuje zaakceptowane zgłoszenie: znika z listy po odświeżeniu,
   zwalnia miejsce i traci dostęp do kontaktu A przy następnym odczycie.
   Kraje, daty i budżet pozostają zablokowane nawet po wycofaniu wszystkich.
6. Zamknięcie przez A odrzuca oczekujące zgłoszenia, zachowuje zaakceptowanych
   i ich kontakty. Powtórz PL/EN. Kontakt wyświetlony wcześniej można ukryć
   przyciskiem; aplikacja nie może odebrać wcześniej skopiowanych danych.

API pod /api/v1:
POST /trips/{id}/applications (JSON message), GET /me/applications,
GET /trips/{id}/applications, POST /applications/{id}/accept|reject|withdraw,
GET /trips/{id}/participants, GET /trips/{id}/contacts/{userId}.
Zapisy wymagają sesji/CSRF/Origin. Kontakty: private, no-store.
Błędy m.in. 409 TRIP_FULL, ALREADY_APPLIED, APPLICATION_STATE,
TRIP_TERMS_LOCKED; 403 CONTACT_FORBIDDEN. Wycofane zgłoszenie nie jest
ponawiane. Uczestnik nie otrzymuje kontaktów innych uczestników.

Sprawdzenia:

    ./scripts/backend.sh test -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114
    cd frontend
    npm run typecheck
    npm run build
    npx playwright test tests/applications.spec.ts

Test E2E ma warianty PL/EN i tworzy 6 kont. W razie zajętych portów można
uruchomić osobne procesy (bez zmiany .env), z katalogu głównego:

    ./scripts/backend.sh quarkus:dev -Dquarkus.http.port=8082 -Dauth.frontend-url=http://localhost:3100 -Dauth.rate.register-ip=40 -Dauth.google.enabled=false -Ddebug=false
    PORT=3100 NUXT_BACKEND_URL=http://127.0.0.1:8082 NUXT_PUBLIC_SITE_URL=http://localhost:3100 node frontend/.output/server/index.mjs

W frontend: E2E_BASE_URL=http://localhost:3100 npx playwright test tests/applications.spec.ts.
Dane testowe pozostają lokalnie. Nie usuwaj wolumenu. Testy Maven wykonuj
przed uruchomieniem dev ze względu na wspólny katalog target.

## Etap 6 — zgłoszenia nadużyć i ADMIN

Uruchomienie pozostaje jak wcześniej; Flyway zastosuje V11 bez usuwania
danych. „Zgłoś” na wyprawie/profilu otwiera formularz. Anonimowy reporter
podaje nazwę i e-mail; dla zalogowanego tożsamość pochodzi z sesji.
Kategorie: spam, nękanie, oszustwo, niebezpieczna treść, inne. Raport
wymaga opisu i potwierdzenia dobrej wiary. Limit: 5 prób/h/IP.

Lokalnie przycisk „Weryfikuj lokalnie” uzyskuje jednorazowy token, który
backend sprawdza. To jawny symulator dev/test, bez komunikacji z Cloudflare.
Nie wystarcza do publicznego wdrożenia i nie uruchomi się w profilu NORMAL.
Rzeczywisty adapter wymaga TURNSTILE_SITE_KEY i TURNSTILE_SECRET w .env
oraz uruchomienia backendu z -Dreports.challenge.local=false. Serwer
sprawdza Siteverify, hostname i action=report; frontend nie zatwierdza
samodzielnie tokenu. Nie skonfigurowano usług chmurowych w tej sesji.

### Lokalny administrator

Zarejestruj oddzielne konto przez UI. Zalogowany GET /api/v1/me zwraca
jego userId. Właściciel projektu może nadać ADMIN wyłącznie temu UUID:

    docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

W konsoli SQL (zastąp UUID rzeczywistym identyfikatorem):

    UPDATE users SET role='ADMIN' WHERE id='TU-UUID-KONTA';
    \q

Nie ma endpointu samodzielnego nadawania uprawnień. Po odświeżeniu
nawigacja pokazuje „Moderacja”; adres /moderation (EN: /en/moderation).
Zwykłe konto dostaje 403 z endpointów admina, nawet przy bezpośrednim URL.

### Odbiór ręczny

1. A lub osoba anonimowa zgłasza publiczną wyprawę. Po błędzie zachowaj
   opis, popraw dane i ponów weryfikację. Potwierdzenie nie obiecuje wyniku.
2. ADMIN otwiera raport, sprawdza cel i wpisuje uzasadnienie.
   „Ukryj wyprawę” usuwa ją z listy/filtrów i daje 404 szczegółów.
   „Przywróć wyprawę” nie otwiera zamkniętej wyprawy.
3. Osobno „Rozstrzygnij” lub „Oddal” zamyka raport; filtr statusów
   pozwala go ponownie zobaczyć. Powstaje audyt. Mailpit: localhost:8025.
4. Zgłoś profil użytkownika; ADMIN może zablokować konto. Stara sesja
   przestaje działać, publiczny profil/wyprawy znikają i kontakty przez
   wyprawę nie są dostępne. Odblokowanie nie przywraca starych sesji.
5. Sprawdź PL/EN, pustą listę i nieudany zapis. Panel nie pokazuje
   prywatnych kontaktów ani e-maila logowania celu.

API: POST /api/v1/reports; GET /reports/config, POST /reports/challenge
(lokalny adapter); GET /admin/access, GET /admin/reports?status=OPEN;
POST /admin/reports/{id}/resolve z {status,reason};
POST /admin/trips/{id}/hide lub unhide oraz
POST /admin/users/{id}/block lub unblock z {reason}.
Prefiks wszystkich endpointów: /api/v1. Zapisy: CSRF + Origin,
admin dodatkowo aktywna sesja ADMIN. Reporter nie otrzymuje endpointu
odczytu cudzych raportów. Audit pozostaje w moderation_actions.

### Testy moderacji

Backend (z katalogu projektu, przed startem dev):

    ./scripts/backend.sh test -Dtest=ModerationTest,TripResourceTest,ProfileResourceTest -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114

Frontend:

    cd frontend
    npm run typecheck
    npm run build

Backend i zbudowany Nuxt na osobnych portach uruchom według instrukcji
etapu 5 (8082/3100). Następnie z katalogu frontend:

    E2E_BASE_URL=http://localhost:3100 E2E_SETUP_ADMIN=1 ./node_modules/.bin/playwright test tests/moderation.spec.ts --headed

Flaga jawnie zezwala testowi na nadanie ADMIN nowo utworzonemu kontu
przez lokalny Docker/psql. Test przywraca USER w finally; dane i audyt
zostają. Bez flagi test jest pomijany, co nie oznacza zaliczenia.
Nie uruchamiaj go przeciw innemu środowisku. Użycie lokalnego pliku
Playwright zapobiega przypadkowemu użyciu innej wersji przez npx.

Usuwanie konta (początek etapu 7): na stronie konta zaznacz potwierdzenie
trwałego usunięcia i wybierz „Usuń konto” / „Delete account”. Sprawdzaj
wyłącznie na koncie testowym: operacja fizycznie usuwa dane bez możliwości
cofnięcia. Po operacji dotychczasowa sesja i logowanie przestają działać.
Test API: `./scripts/backend.sh test -Dtest=AuthResourceTest`
z parametrem `-Dquarkus.datasource.jdbc.url=...` wskazującym osobną bazę testową.

### Etap 7A — odbiór usuwania i retencji

Poprzedni opis natychmiastowego kasowania zastępuje przepływ:
ustawienia konta → aktualne hasło (lub ponowne logowanie tą samą tożsamością
Google) → zaznaczenie potwierdzenia → „Usuń konto”. Potwierdzenie wygasa
po 5 minutach i działa tylko w bieżącej sesji. DELETE /api/v1/me zwraca 202;
konto natychmiast traci wszystkie sesje, publiczny profil/wyprawy i dostęp
do kontaktów. Zadanie fizycznego usunięcia jest trwałe i działa co godzinę.

Sprawdzaj wyłącznie na nowych kontach testowych A/B: utwórz profile,
wyprawę A, zgłoszenie B i zaakceptuj je. Usunięcie B odbiera kontakty,
ale zachowuje wyprawę A. W osobnym przebiegu usunięcie A usuwa wyprawę
i zgłoszenia do niej. Po odświeżeniu panelu B zgłoszenia nie ma.
Sprawdź też drugą otwartą sesję usuwanego konta oraz publiczne URL-e (404).
Nie używaj rzeczywistego konta Google do próby fizycznego kasowania.
Ręczny odbiór Google wymaga zgody właściciela testowej tożsamości:
inna tożsamość oraz brak świeżego auth_time muszą zostać odrzucone.
Testowy OIDC nie potwierdza zachowania rzeczywistego Google.

Weryfikacja na osobnej bazie (schemat privacy_test izoluje destrukcyjne testy retencji):

```sh
./scripts/backend.sh test -Dtest=AuthResourceTest,AccountDeletionTest,GoogleOidcTest,TripResourceTest,ProfileResourceTest,ModerationTest -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114 -Dquarkus.flyway.schemas=stage7a_suite -Dquarkus.hibernate-orm.database.default-schema=stage7a_suite
cd frontend
npm run typecheck
npm run build
E2E_BASE_URL=http://localhost:3100 ./node_modules/.bin/playwright test tests/deletion.spec.ts
```

Dla E2E uruchom backend 8082 i frontend 3100 według wcześniejszej instrukcji,
ale dopisz backendowi URL powyższej osobnej bazy, `-Dquarkus.flyway.schemas=stage7a_e2e`
i `-Dquarkus.hibernate-orm.database.default-schema=stage7a_e2e`.
Opcjonalne `-Dprivacy.cleanup-interval=5s` przyspiesza worker tylko na czas
odbioru w izolowanym schemacie. Nie używaj tej konfiguracji na danych autora.
Zakres minimalizacji raportów, okresy i zależności od działania aplikacji
opisano w docs/DECISIONS.md. Logi terminala i backupy nie mają automatycznej
retencji w tym lokalnym środowisku; konkretne wymagania zapisano do etapu 9.

Do powtórzenia pełnej próby A/B/B2/C użyj schematu `stage7a_trial`
zamiast `stage7a_e2e` w obu parametrach backendu. Worker co 5 s ułatwia
obserwację fizycznego usunięcia. Widoczny Chromium uruchom z katalogu frontend:

```sh
E2E_BASE_URL=http://localhost:3100 ./node_modules/.bin/playwright test tests/deletion-relations.spec.ts --headed
```

Test tworzy trzy konta i drugą sesję B. Wypisuje publiczne adresy profili
w kolejności A, B, C oraz adres i UUID wyprawy. Po zakończeniu workera
uruchom z głównego katalogu (podstaw UUID z własnej próby):

```sh
docker compose exec -T postgres sh -c 'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d travel_buddy_stage4_test_20261114 "$@"' sh -v a=UUID_A -v b=UUID_B -v c=UUID_C -v trip=UUID_WYPRAWY < docs/VERIFY-7A.sql
```

Wszystkie liczniki pierwszego wyniku powinny wynosić 0, a active_c_should_be_one
powinien wynosić 1. Raporty i audyt sprawdza osobny AccountDeletionTest
z kontrolowanym zegarem. W ręcznym odbiorze najpierw zapisz publiczne URL-e
i upewnij się, że otwierają się anonimowo. Ustaw limit dwóch osób łącznie
z organizatorem, aby zgłoszenie C po usunięciu B sprawdzało odzyskanie miejsca.

### Lokalny odbiór etapu 7B

W katalogu projektu uruchom PostgreSQL i backend. Poniższe polecenia
korzystają z istniejącej osobnej bazy testowej etapu 7A.

```sh
docker compose up -d
./scripts/backend.sh quarkus:dev -Dquarkus.http.port=8084 -Dauth.frontend-url=http://localhost:3104 -Dauth.google.enabled=false -Dauth.rate.register-ip=40 -Ddebug=false -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114 -Dquarkus.flyway.schemas=stage7b_e2e -Dquarkus.hibernate-orm.database.default-schema=stage7b_e2e -Dprivacy.cleanup-interval=5s
```

W drugim terminalu uruchom zbudowaną aplikację:

```sh
cd frontend
npm run typecheck && npm run build
PORT=3104 NUXT_BACKEND_URL=http://127.0.0.1:8084 NUXT_PUBLIC_SITE_URL=http://localhost:3104 node .output/server/index.mjs
```

1. Otwórz http://localhost:3104/register i oba dokumenty. Na telefonie sprawdź czytelność tekstu, stopki i przełącznika PL/EN. Wersja przy checkboxie to local-draft-2.
2. Ze stopki przejdź do ustawień cookies i przełącz język. Przy samych mechanizmach niezbędnych strona nie wymaga decyzji.
3. Otwórz /sitemap.xml i /robots.txt. W źródle szczegółów wyprawy porównaj canonical po zmianie języka interfejsu. Dla filtrowanej listy sprawdź noindex,follow.
4. Automatyczny odbiór ze świeżym procesem backendu (limit raportów 5/h), w katalogu frontend:

```sh
E2E_BASE_URL=http://localhost:3104 E2E_SETUP_ADMIN=1 E2E_STAGE7B=1 ./node_modules/.bin/playwright test tests/legal.spec.ts tests/search.spec.ts tests/deletion-relations.spec.ts tests/moderation.spec.ts tests/deletion.spec.ts
```
