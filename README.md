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

Tylko odczyt demonstracyjnych wypraw. Bez kont, formularzy i filtrowania. Seedy są aktywne wyłącznie w dev/test; nie używaj tego wolumenu dla profilu produkcyjnego. Testowy schemat pozostaje po testach.

## Wyniki lokalnej weryfikacji

2026-11-06: dwa testy API przeszły na PostgreSQL; typecheck i build przeszły.
Skrypt SSR przeszedł dla listy i szczegółów PL/EN, z pominięciem skryptów
oraz head w HTML. Healthcheck UP, strona nieistniejącej wyprawy HTTP 404.
Po restarcie PostgreSQL oba rekordy zachowały UUID. Pierwsze żądanie po
restarcie zwróciło 500 przez zerwane połączenie z puli; kolejne działały.
Nie wykonano automatycznego testu kliknięć w przeglądarce ani oceny wizualnej
na telefonie. Te czynności pozostają do ręcznej weryfikacji.
