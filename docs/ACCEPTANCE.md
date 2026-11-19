# Odbiór lokalnego MVP

## Etap 1 — 19 listopada 2026

**Decyzja autora oczekuje.** Audyt dotyczy szkieletu w aktualnym MVP, commit `b8095f7`. Ręczny odbiór i uwagi autora są otwarte. Etap 2A oczekuje na osobną decyzję.

### Wymagania i stan implementacji

Podstawą są PLAN §5, „Etap 1”, pierwszy wpis DECISIONS oraz bieżące przekazanie w STATUS. Kryterium końcowe obejmuje start w czystym katalogu, odczyt PostgreSQL przez API i SSR, oba języki oraz zachowanie danych po restarcie.

| Wymaganie i kryterium | Ocena | Dowód i odstępstwa |
| --- | --- | --- |
| Lokalny checkout z instrukcją, powtarzalne zależności i jawne źródło konfiguracji | Zaimplementowany | README prowadzi przez kopię .env.example, npm ci i trzy terminale. scripts/backend.sh eksportuje .env, wybiera JDK 21 i uruchamia dołączony mvnw. Lockfile pozwolił odtworzyć frontend. Początkowa sekcja „Ograniczenia” README opisuje historyczny produkt i mylnie sugeruje brak edycji i filtrów w obecnej wersji. |
| PostgreSQL 17 na loopback, nazwany wolumen, gotowość i brak drugiej bazy Dev Services | Zaimplementowany | compose.yaml przypina 17.6, port 5433 i pg_isready. application.properties wyłącza Dev Services. W próbie zmieniono port hosta na 25433. Pełny Compose uruchamia też Mailpit dodany po etapie 1. |
| Model, migracje, walidacja ORM i deterministyczne dane demonstracyjne | Zaimplementowany | V1 tworzy UUID, tytuł, kraj, opis, daty i język PL/EN, wymaga końca najwcześniej w dniu początku. V2 w db/demo wstawia dwie wyprawy. Późniejsze V7 i V9 wiążą je z organizatorem i publikują. Lokalizacja demo jest dodana w dev/test. Flyway migruje przy starcie, Hibernate ma strategię validate. |
| Publiczne GET listy i szczegółów, jawne DTO, 404 | Zaimplementowany | TripResourceTest potwierdza items, szczegóły i zestaw pól oraz 404 dla brakującego UUID. TripSearch ogranicza listę do otwartych, widocznych wypraw aktywnych autorów. Obecnie sortuje po publikacji i UUID malejąco. Lista jest stronicowana. Gość czyta bez konta. |
| Nuxt SSR z danymi z bazy, proxy, PL/EN i mobilna nawigacja | Zaimplementowany | PublicTripList.vue i strony szczegółów używają useFetch, frontend/server/api/v1/[...path].ts przekazuje GET do backendu. Treść autora zachowuje język. Dawny URL szczegółów prowadzi przez 301 do adresu ze slugiem. Cztery odpowiedzi SSR i atrybuty lang sprawdzone. Wygoda nawigacji na telefonie czeka na autora. |
| Lokalne serwery developerskie z hot reload, readiness i testy | Zaimplementowany | quarkus:dev zgłosił Live Coding, npm run dev uruchomił Vite. Readiness obejmuje pulę JDBC. Próbę zmiany źródła na żywo pozostawiono do ręcznego odbioru. |
| Zachowanie danych po restarcie PostgreSQL | Zaimplementowany | Po odzyskaniu połączenia pełna odpowiedź listy była identyczna bajt w bajt. Dostępność API zaraz po gotowości kontenera ma odrębny defekt opisany poniżej. |

Obecne konta i formularze wynikają z następnych etapów. Publiczny odczyt nadal nie wymaga roli ani danych kontaktowych. Na czystej bazie test listy zakłada dokładnie dwa rekordy. Na bazie z dodatkowymi wyprawami ten warunek nie jest miarodajny.

### Przebieg i wyniki

macOS 27.2.1 arm64, Temurin 21.0.13, Maven 3.9.11 z wrappera, Node v24.22.2, npm 11.19.1, Docker Engine 29.9.2, Compose v5.6.1, Quarkus 3.40.1, Nuxt 4.6.4. Wersje sprawdzono przez `./scripts/backend.sh -version`, `node -v`, `npm -v`, `docker compose version`, `docker info --format '{{.ServerVersion}}'` i logi startowe.

Polecenia odtwarzające izolację używaną w audycie (pierwszy blok wykonano jednorazowo z katalogu głównego projektu, po utworzeniu pustego katalogu tymczasowego). **Do ręcznego odbioru tej samej kopii przejdź od razu do drugiego bloku.**

```sh
git clone --no-hardlinks . /private/tmp/travel1.dus66e/repo
cd /private/tmp/travel1.dus66e/repo
cp .env.example .env
sed 's/5433:5432/25433:5432/' compose.yaml > compose.audit.yaml
echo 'QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:25433/travel_buddy' >> .env
npm ci --prefix frontend --offline
```

Użyto istniejących cache Maven i npm. Był to świeży checkout z nowym wolumenem, bez próby pobierania zależności na pustym komputerze. Osobny projekt Compose pozwolił zachować domyślną nazwę bazy we własnej instancji. Pominięto Mailpit, zbędny dla publicznego odczytu.

```sh
# Terminal 1
cd /private/tmp/travel1.dus66e/repo
docker compose -p travel1dus66e -f compose.audit.yaml up -d --wait postgres
./scripts/backend.sh test -Dtest=TripResourceTest -Dquarkus.http.test-port=28081
./scripts/backend.sh quarkus:dev -Dquarkus.http.port=28080 -Ddebug=false

# Terminal 2
cd /private/tmp/travel1.dus66e/repo/frontend
npm run typecheck && npm run build
NUXT_BACKEND_URL=http://127.0.0.1:28080 npm run dev

# Terminal 3
cd /private/tmp/travel1.dus66e/repo
curl -fsS http://127.0.0.1:28080/q/health/ready
node scripts/check-ssr.mjs
docker compose -p travel1dus66e -f compose.audit.yaml exec -T postgres psql -U travel_buddy_dev -d travel_buddy -c 'SELECT version,success FROM flyway_schema_history ORDER BY installed_rank'
```

- Instalacja z lockfile zakończona. Typecheck i build przeszły. Publiczne testy PostgreSQL przeszły 2/2, bez pominięć. Flyway zastosował V1–V13 w public i travel_buddy_test. Odczyt SQL z public potwierdził 13 sukcesów.
- Readiness backendu i Database connections health check dały UP. Porównano przez cmp odpowiedzi curl z /api/v1/trips na 3000 i 28080, były identyczne. Skrypt SSR znalazł „Weekend w Tatrach” poza head i script na liście i szczegółach w obu językach (4/4). Dodatkowy odczyt fetch bez automatycznych przekierowań wykazał 301 na obu dawnych adresach, końcowy HTML miał właściwy lang. Brakujący UUID przez Nuxt dał 404.
- Przy działającym Quarkusie wykonano polecenia stop i up z poniższego scenariusza 5. Dwa kolejne GET listy po gotowości kontenera dały 500, następny pomiar 200 z identycznym JSON jak przed restartem (`cmp ../before.json ../retry.json`). Readiness wrócił do UP bez restartu Quarkusa.
- Po zatrzymaniu backendu GET / i /en w Nuxt dały 502 i odpowiednio „Nie udało się pobrać danych.” oraz „Could not load data.” w HTML poza skryptami. Po audycie zatrzymano oba serwery oraz audytowy kontener. Wolumen, checkout i logi w /private/tmp/travel1.dus66e zachowano lokalnie.

### Defekt i decyzje do podjęcia

**E1-01.** Zdrowy PostgreSQL po restarcie nie gwarantuje powodzenia pierwszego odczytu przez działający backend. Log obu odpowiedzi 500 zawiera GenericJDBCException i „FATAL: terminating connection due to administrator command”. Prawdopodobna przyczyna to ponowne użycie połączeń z puli zerwanych podczas zatrzymania bazy. Propozycja do decyzji autora: sprawdzić walidację połączeń przed wydaniem z puli i powtórzyć pomiar pierwszych GET po restarcie. Osobna propozycja dokumentacyjna: oznaczyć historyczne ograniczenia w README. W fazie audytu nie wprowadzono poprawek.

### Scenariusze ręczne dla autora

Wszystkie próby wykonaj na kopii audytowej. Port 3000 musi być wolny. Przed startem kolejnych terminali sprawdź, że bieżący katalog odpowiada instrukcji. Dla szczegółów użyj UUID 11111111-1111-4111-8111-111111111111.

| Próba | Warunki początkowe | Kroki i dane | Oczekiwany rezultat |
| --- | --- | --- | --- |
| 1. Start i gotowość | Docker działa, kopia i wolumen audytu zachowane | Uruchom terminale 1–3 według drugiego bloku. | PostgreSQL healthy, Quarkus i Nuxt nasłuchują, readiness UP, historia Flyway bez nieudanych wpisów. Ponowny start nie duplikuje seedów. |
| 2. Gość, lista i szczegóły | Serwery działają, prywatne okno bez sesji | Otwórz http://localhost:3000, kliknij „Weekend w Tatrach”, wróć i wybierz „Discover Portugal”. W szczegółach przełącz PL/EN. | Dwie wyprawy, szczegóły odpowiedniej pozycji, dla Tatr daty 12–14 czerwca 2027. Etykiety zmieniają język, tytuły i opisy pozostają w języku autora. Brak wymogu logowania. |
| 3. SSR i nawigacja mobilna | Gość, szerokość 320 lub 390 px | Otwórz /en, przejdź do szczegółów, przełącz PL, wróć na listę. Wyświetl źródło strony. | Linki i przełącznik dostępne bez poziomego przewijania, poprawny język po nawigacji. Tytuł jest w treści HTML przed wykonaniem JS. |
| 4. Brakujący rekord i pusty wynik | Działające API i Nuxt | Otwórz /trips/99999999-9999-4999-8999-999999999999. Na liście wpisz w pole kraju JP i zastosuj. Następnie otwórz /?size=0. | Brakujące szczegóły dają HTTP 404. Dla JP na danych demo pojawia się pusta lista i komunikat. Dla size=0 API zwraca 400, a strona pokazuje błąd walidacji. Publiczny odczyt etapu 1 nie ma formularza zapisu. |
| 5. Trwałość i E1-01 | Działające serwery, katalog audytu, wykonany co najmniej jeden odczyt listy | Wykonaj blok restartu pod tabelą, zapisz kolejno statusy HTTP. | Dane i UUID przetrwają. Docelowo pierwszy GET po gotowości daje 200. W audycie dwa razy wystąpiło 500 przed odzyskaniem połączenia. |
| 6. Awaria backendu | Nuxt działa, lista wcześniej się wczytała | Zatrzymaj Quarkusa przez Ctrl+C w terminalu 1. Odśwież / i /en, następnie ponownie uruchom Quarkusa. | Lista pokazuje komunikat błędu w wybranym języku. Po powrocie backendu odświeżenie przywraca listę. |
| 7. Hot reload | Oba serwery dev działają w kopii | W kopii zmień na chwilę widoczny tekst w frontend/i18n/locales/pl.json. Na backendzie zmień tymczasowo tytuł w mapowaniu publicznej odpowiedzi i ponów GET. Cofnij obie własne edycje. | Zmieniony tekst widać bez ręcznego restartu procesu, po cofnięciu wraca poprzedni. Dane w PostgreSQL pozostają te same. |

```sh
cd /private/tmp/travel1.dus66e/repo
curl -fsS http://127.0.0.1:28080/api/v1/trips > ../manual-before.json
docker compose -p travel1dus66e -f compose.audit.yaml stop postgres
docker compose -p travel1dus66e -f compose.audit.yaml up -d --wait postgres
curl -sS -o ../manual-after.json -w '%{http_code}\n' http://127.0.0.1:28080/api/v1/trips
# Zapisz pierwszy kod. W przypadku 500 powtórz ostatni curl do pierwszego 200.
cmp ../manual-before.json ../manual-after.json
```

Po próbach zatrzymaj serwery przez Ctrl+C i wykonaj polecenie stop postgres z bloku restartu. Pozostawia ono wolumen do dalszego odbioru.

**Uwagi autora:** oczekujące. **Ostateczny status etapu:** otwarty, do decyzji autora po ręcznych próbach.

### Powtórka przed odbiorem autora — 19 listopada 2026

W izolowanej kopii wykonano ścieżkę gościa w lokalnym Chromium sterowanym przez Playwright (`node --input-type=module` z pakietem @playwright/test). Na każdej z szerokości 1280, 390 i 320 px sprawdzono dwie karty na liście, kliknięcia Weekend w Tatrach → EN → Travel Buddy → Discover Portugal → PL, tytuły szczegółów i zmianę lang. Po każdym kliknięciu szerokość dokumentu odpowiadała szerokości okna. Dla każdego rozmiaru przeszły też próby pustej listy JP, błędnego size=0 (400) i brakującego UUID (404).

Powtórzono stop/up z próby 5 na tym samym wolumenie. Pięć kolejnych odczytów GET po healthy dało **500, 500, 200, 200, 200**. Ostatnia lista była identyczna z zapisaną przed restartem (cmp). Ponowne `node scripts/check-ssr.mjs` przeszło 4/4. E1-01 jest powtarzalny. Pozostawiono działające audytowe UI na localhost:3000 i backend na 127.0.0.1:28080 do prób autora. Ręczna ocena autora i decyzja o poprawkach pozostają otwarte.

### Poprawki po uwagach autora — 19 listopada 2026

**Status:** autor zlecił poprawę E1-01 i pięciu elementów interfejsu. Zmiany są gotowe do ponownego odbioru etapu 1.

| Uwaga | Rozwiązanie | Wynik weryfikacji |
| --- | --- | --- |
| E1-01. Dwa błędy 500 po restarcie | Agroal sprawdza połączenie przy pobraniu z puli. | Trzy kolejne cykle stop/up. Pierwszy GET po healthy za każdym razem 200, bez prób ponawiania. Pełna lista zgodna z odpowiedzią sprzed restartu w każdym cyklu. |
| 1. Filtry przesłaniały wyprawy | Domyślnie zwinięty panel, licznik zastosowanych kryteriów, rozwijanie z zachowaniem wartości, zwinięcie po zastosowaniu lub wyczyszczeniu. Rozmiar strony i numer strony nie zwiększają licznika. | PL/EN, desktop/telefon: dwa kryteria, odtworzenie wartości, wyczyszczenie URL, zmiana strony, powrót w historii i SSR skopiowanego adresu. |
| 2. Kraje wymagały kodów ISO | Wspólny wybór kraju w filtrach i edytorze. Można wpisać fragment polskiej lub angielskiej nazwy, wybrać wynik dotykiem, myszą lub strzałkami i Enter. | Sprawdzone aliasy GB, filtr PT oraz tworzenie, zapis, ponowna edycja i publikacja trasy SK/PL. W URL i API pozostają kody ISO. |
| 3. Nieczytelne trasy | Karta pokazuje unikalne kraje i kontynenty, maksymalnie trzy wiersze. Szczegóły pokazują pełną trasę wraz z miastami. Reguły krajów transkontynentalnych opisano w DECISIONS. | Sprawdzone 249 kodów, nazwy PL/EN, karta SK/PL z jednym wystąpieniem Europy, a także trasa PL/GB/PL/EG z dwoma kontynentami. |
| 4. Słabo widoczny status | Etykieta przy tytule pokazuje stan otwarty, zamknięty lub pełny. Zielony wyróżnia otwartą wyprawę z miejscami. Backend oblicza dostępność wspólnie z walidacją zgłoszenia. | PL/EN: złożenie zgłoszenia na otwartą wyprawę, akceptacja ostatniej osoby, etykieta pełnej wyprawy, ukrycie formularza, zwolnienie miejsca po wycofaniu oraz zamknięcie przez autora. |
| 5. Przełącznik języków | Lista pobiera języki z konfiguracji i18n, oznacza aktualny język i zamyka się po wyborze, Escape lub kliknięciu poza nią. | PL→EN i EN→PL, ze sprawdzeniem bieżącego języka, zamknięcia menu i zachowania kraju oraz budżetu w URL. |

**Środowisko.** Weryfikację wykonano na zmienionych źródłach skopiowanych do istniejącego checkoutu audytowego. Publiczny serwer do odbioru działa na localhost:3000. Testy zapisujące dane korzystały z osobnego schematu e1_ui, backendu 28082 oraz zbudowanego frontendu 3105. Lokalny limit rejestracji zwiększono wyłącznie dla tego procesu. Przebiegi Chromium używały okien 1280×800 i 390×844 z emulacją urządzenia dotykowego. Fizyczny telefon pozostaje do oceny autora.

**Polecenia i dowody**

- W katalogu frontend przeszły `npm run typecheck` i `npm run build`.
- `./scripts/backend.sh test -Dtest=TripResourceTest,TripSearchTest,ModerationTest -Dquarkus.http.test-port=28081` — 11/11. Klasa ModerationTest dziedziczy pięć scenariuszy ApplicationServiceTest. Próba uruchomienia samej klasy bazowej obok testów API wykazała konflikt tworzenia instancji w CDI (pięć błędów inicjalizacji). Przypadki wykonano przez klasę potomną.
- `node scripts/check-ssr.mjs` — 4/4. W kopii audytowej użyto lokalnego acceptance.config.ts rozszerzającego konfigurację Playwright o projekty desktop i phone. `E2E_BASE_URL=http://localhost:3105 npx playwright test -c acceptance.config.ts search.spec.ts` — 6/6. Te same testy na developerskim porcie 3000 — 6/6.
- `E2E_BASE_URL=http://localhost:3105 npx playwright test -c acceptance.config.ts applications.spec.ts trips.spec.ts profiles.spec.ts` — 10/10 na finalnym buildzie. Łącznie 16/16 testów przeglądarkowych buildu.
- Podczas prób wykryto różnice w nazwach kontynentów z Intl między Node i Chromium oraz możliwość kliknięcia przycisku, zanim Nuxt podłączy obsługę zdarzeń. Kontynenty mają teraz jawne tłumaczenia, a przyciski filtrów i języka stają się aktywne po zamontowaniu komponentu.
- Do pomiaru E1-01 użyto bloku restartu z próby 5, z opcją `curl --retry 0`, bez kontroli readiness Quarkusa pomiędzy startem bazy a pierwszym GET. Baza i wolumen developerski autora pozostały poza próbą.

### Ponowny odbiór przez autora

Wykonaj próby na localhost:3000, najpierw w dużym oknie, potem w wąskim. Powtórz w EN. Próby 1–4 są dostępne dla gościa.

1. **Lista i filtry.** Wejdź na stronę główną. Powinny być od razu widoczne karty. Kliknij Filtry, wpisz „Polan”, wybierz Polskę, ustaw minimalny budżet 100 i zastosuj. Oczekuj zwinięcia panelu, „Filtry (2)”, parametru country=PL oraz wyprawy w Tatry. Otwórz panel ponownie, sprawdź wartości i użyj „Wyczyść filtry”. Obie wyprawy powinny wrócić.
2. **Aliasy.** Wpisz „Anglia” lub „England”, wybierz Wielką Brytanię i zastosuj. W URL powinno być country=GB. Pusty wynik na danych demonstracyjnych jest prawidłowy.
3. **Język.** Pozostaw aktywny filtr. Kliknij PL, sprawdź znacznik przy PL, zamknij Escape. Otwórz ponownie i kliknij tytuł strony. Za trzecim razem wybierz EN. Oczekuj zamkniętego menu, zachowanego filtra i angielskich etykiet. Na szczegółach zmiana języka powinna pozostawić tę samą wyprawę.
4. **Karta i szczegóły.** Wyczyść filtry. Na karcie Tatr porównaj „Polska · Europa” i „Poland · Europe”. Otwórz szczegóły. Sprawdź pełną nazwę kraju i zieloną etykietę otwartej wyprawy przy tytule.
5. **Edytor.** Jako organizator z gotowym profilem i kontaktem dodaj do nowej wyprawy Słowację i Polskę. Zapisz szkic, otwórz ponownie, a po uzupełnieniu wymaganych pól opublikuj. Oczekuj tych samych krajów w edytorze, obu nazw i jednej Europy na karcie. W szczegółach powinna pozostać kolejność lokalizacji.
6. **Ostatnie miejsce.** Dla powyższej wyprawy ustaw limit dwóch osób przed publikacją. Z drugiego gotowego konta wyślij zgłoszenie. Zaakceptuj jako organizator i odśwież szczegóły. Powinien pojawić się stan „Brak wolnych miejsc”. Po wycofaniu uczestnika wyprawa znów jest otwarta z miejscem. Po zamknięciu przez organizatora sprawdź szarą etykietę „Zamknięta” i brak formularza zgłoszenia.
7. **Opcjonalne własne potwierdzenie restartu.** W katalogu kopii audytowej wykonaj blok restartu z próby 5. Zapisz pierwszy kod bez odświeżania. Oczekiwany wynik to 200 i identyczne dane po cmp.

**Ostateczna decyzja autora po poprawkach:** oczekująca.
