# Odbiór lokalnego MVP

## Etap 1 — 19 listopada 2026

**Etap 1 odebrany 24 listopada 2026.** Autor potwierdził poprawne działanie po pięciu poprawkach. Poniższe wpisy z 19 listopada dokumentują przebieg audytu.

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

**Stan pierwszej próby z 19 listopada:** oczekiwanie na uwagi autora.

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

**Ostateczna decyzja autora po poprawkach:** etap 1 odebrany.

## Etap 2A — lokalne konta, 24 listopada 2026

**Ręczny odbiór: odebrany.** Autor potwierdził działanie po obu poprawkach formularza rejestracji. Badana rewizja cb7b265, początkowy git status --short pusty. Zakres wynika z PLAN „Etap 2”, podziału 2A/2B w DECISIONS i otwartej próby restartu w STATUS. Google pozostaje w 2B.

### Wymagania i wyniki

Każde wymaganie w poniższej tabeli oceniono jako **zaimplementowane**.

| Wymaganie | Implementacja i dowód |
|---|---|
| Rejestracja, walidacja, unikalność e-maila | AuthDtos, AuthService i migracja V3. Poprawny e-mail do 254 znaków, hasło i potwierdzenie po 12–1024 jednostki UTF-16. Zapis małymi literami, unikalny indeks lower(email). AuthResourceTest sprawdza 400, duplikat 409 oraz równoczesną rejestrację. |
| Akceptacja regulaminu i automatyczna sesja | Bieżąca wersja local-draft-2; wymagane zaznaczenie zgody. Backend zapisuje wersję i czas, tworzy konto ACTIVE/USER i zwraca 201 z cookie. E2E w PL/EN potwierdził przejście do własnego konta bez potwierdzania poczty. |
| Poprawne i błędne logowanie, logout | Login akceptuje inną wielkość liter e-maila. Złe hasło i nieznany użytkownik dają ten sam 401. Logout unieważnia bieżący token i usuwa cookie. Testy API i E2E. |
| Sesja po odświeżeniu, restarcie i wygaśnięciu | SessionService zapisuje sesję w PostgreSQL. Próba w Chromium z tym samym cookie po pełnym restarcie przeszła. Po 7 dniach token jest odrzucany, konto nieaktywne także traci dostęp. Test API wymusił datę wygaśnięcia w przeszłości. |
| Reset przez Mailpit i neutralna odpowiedź | Link wysyłany na adres aktywnego konta w języku rejestracji. Dla nieznanego adresu również 202, bez wiadomości. Dwa rzeczywiste przepływy przez lokalny SMTP. |
| Czas i jednorazowość resetu, unieważnienie sesji | Token ważny 30 minut. Testy odrzuciły wygasły i wykorzystany token; przy równoczesnym potwierdzeniu tylko jedna operacja wygrywa. Po zmianie hasła obie wcześniejsze sesje dały 401, stare hasło odrzucone. |
| Publiczne wyprawy oraz prywatny dostęp | Listy i szczegóły dostępne anonimowo (odbiór 1). Prywatny GET auth/session wymaga sesji, odpowiedź no-store zawiera własny e-mail. Próba gościa na /account przekierowała do /login; dane konta są pobierane po stronie klienta. Publiczny HTML sprawdzony pod kątem braku własnego e-maila. |
| Formularze PL/EN i błędy | AuthForm i tłumaczenia obsługują rejestrację, login, odzyskanie i reset. E2E przeszedł w obu językach. Przy różnym potwierdzeniu Chromium pokazał alert, zachował e-mail i pozwolił poprawić dane. HTML używa też natywnej walidacji wymaganych pól, długości i typu email; język natywnego komunikatu zależy od przeglądarki. |
| Ochrona hasła, sesji i CSRF | Password4j Argon2id: 19456 KiB, 2 iteracje, równoległość 1, losowa sól 16 bajtów. Sesje i tokeny resetu mają 32 losowe bajty; w bazie SHA-256. Cookie sesji HttpOnly, SameSite=Lax, Path=/; w lokalnym HTTP bez Secure, poza dev/test z Secure. Zapisy wymagają tokenu CSRF i zgodnego Origin. Brak sesji w localStorage. Testy API i kontraktu cookie. |
| Ograniczanie prób | Login 10/15 min na IP oraz na konto, rejestracja 5/h na IP, żądanie resetu 10/h na IP i 3/h na adres. Po przekroczeniu 429. Testy limitów i wygasania okna przeszły. Liczniki są lokalne dla procesu; za lokalnym proxy użytkownicy współdzielą IP. |

**Wyniki automatyczne.** W osobnym projekcie Compose tb2a1124 utworzono nowy wolumen PostgreSQL i osobny Mailpit. Testy API używały schematu travel_buddy_test, a przeglądarka profilu dev na tej samej izolowanej instancji. Zwykłe środowisko autora pozostało uruchomione.

- `./scripts/backend.sh test -Dtest=AuthResourceTest,RateLimiterTest -Dquarkus.http.test-port=28085` — **19/19**, 0 błędów i pominięć (18 metod API obejmuje także istniejące przypadki regresyjne, 1 test magazynu limitów).
- `npm ci --offline --prefix frontend`, w frontend `npm run typecheck && npm run build` — zakończone poprawnie. `E2E_BASE_URL=http://localhost:3106 npx playwright test tests/auth.spec.ts` — **2/2**, 0 niezaliczonych. Wyłącznie w tymczasowej kopii istniejącego testu podstawiono porty 3106 i 28025 zamiast zaszytych 3000 i 8025. Asercje zachowano.
- Dodatkowa próba w kopii: w frontend `node restart.mjs`, zatrzymanie Quarkusa przez Ctrl+C, potwierdzenie braku nasłuchu na 28084, ten sam start Quarkusa, `node restart.mjs --after`. Konto restart-probe@example.test dało **200 przed i 200 po restarcie**. Chromium odtworzył zapisane cookie, odświeżył /account i wyświetlił ten sam e-mail bez logowania. Po sprawdzeniu wykonano logout i usunięto tymczasowy plik sesji. **1/1 przebieg**, 0 niezaliczonych.
- Brak stwierdzonego defektu blokującego 2A. Czas ważności sprawdzono przez kontrolowaną zmianę daty w testowej bazie. Reset działa z lokalnym SMTP; rzeczywista dostawa internetowa jest poza zakresem. Po otwarciu linku UI usuwa fragment tokenu z adresu. Po odświeżeniu formularza trzeba ponownie otworzyć oryginalny link.

### Uruchomienie do ręcznego odbioru

Przygotowana kopia działa na **http://localhost:3106**, skrzynka na **http://localhost:28025**, SMTP 21025, API 28084, PostgreSQL 25434. Użyj osobnego profilu przeglądarki i jednego adresu hosta localhost przez cały test. Cookies dla localhost są współdzielone między portami.

Do wznowienia zatrzymanej kopii wykonaj poniższe polecenia w osobnych terminalach. W trakcie próby restartu zatrzymuj wyłącznie proces backendu audytowego. Jeśli prowadzi go asystent, pozostaw otwartą kartę konta i poproś o restart portu 28084.

```sh
# Terminal 1 — PostgreSQL i Mailpit
cd /private/tmp/tb2a.5iq9Cz/repo
docker compose -p tb2a1124 -f compose.audit.yaml up -d --wait

# Terminal 2 — backend
cd /private/tmp/tb2a.5iq9Cz/repo
./scripts/backend.sh quarkus:dev -Dquarkus.http.port=28084 -Dauth.frontend-url=http://localhost:3106 -Dquarkus.mailer.port=21025 -Ddebug=false

# Terminal 3 — zbudowany frontend
cd /private/tmp/tb2a.5iq9Cz/repo
HOST=127.0.0.1 PORT=3106 NUXT_BACKEND_URL=http://127.0.0.1:28084 node frontend/.output/server/index.mjs
```

Standardowe uruchomienie repozytorium po przygotowaniu .env według README: z katalogu głównego `docker compose up -d --wait`, osobno `./scripts/backend.sh quarkus:dev`, w frontend `npm ci && npm run dev`. Standardowe adresy to UI http://localhost:3000, Mailpit http://localhost:8025 i SMTP 127.0.0.1:1025.

### Checklista przeglądarkowa autora

Do prób użyj świeżego adresu, np. odbior-2411-a@example.test, hasła początkowego „Wyprawa listopad 123!” i nowego „Nowa wyprawa 456!”. Dla drugiego języka użyj innego adresu i odpowiednio /en/register, /en/login, /en/account oraz /en/forgot-password. Okna A i B muszą mieć oddzielne magazyny cookies (np. dwa profile).

| Próba i warunek początkowy | Dane i kroki | Oczekiwany rezultat |
|---|---|---|
| 1. Gość, pusty profil A | Otwórz listę, kliknij Tatry, potem wpisz /account. | Lista i szczegóły są publiczne; prywatne dane pozostają niedostępne, ekran konta prowadzi do logowania. |
| 2. Błędy formularza, A bez sesji | Na /register próbuj kolejno: pusty e-mail, „abc”, hasło „krótko”, różne długie hasła, brak zgody. Po każdym błędzie popraw pole. | Formularz pozostaje otwarty, wskazuje problem, dane można poprawić, konto nie powstaje. |
| 3. Rejestracja, nowy adres | Wpisz poprawne dane, otwórz regulamin, zaznacz zgodę i zarejestruj. Odśwież stronę. | Natychmiast pojawia się własne konto z właściwym e-mailem, sesja przeżywa odświeżenie. |
| 4. Duplikat, niezależne okno B | Zarejestruj ponownie ten sam adres zapisany wielkimi literami. | Komunikat o niedostępnym adresie, brak nowej sesji w B; konto A nadal działa. |
| 5. Restart, A zalogowane | Zachowaj kartę i cookies. Zatrzymaj backend audytu w terminalu 2, ponów jego polecenie, poczekaj na start i odśwież /account. | Adres konta wciąż widoczny, aplikacja nie wymaga hasła. |
| 6. Logout i login, A zalogowane | Wyloguj się, otwórz /account, spróbuj błędnego długiego hasła, potem poprawnego z e-mailem wielkimi literami. | Po logout prywatny widok niedostępny; błędne dane dają neutralny komunikat, poprawne logowanie przywraca konto. |
| 7. Reset, konta A i B zalogowane na ten sam adres | Z /forgot-password wyślij żądanie najpierw dla brak@example.test, potem dla swojego konta. Otwórz Mailpit, wybierz wiadomość do swojego adresu. Zachowaj oryginalny link. Otwórz go i dwukrotnie wpisz nowe hasło. | Oba żądania dają neutralne potwierdzenie. Wiadomość dla istniejącego konta ma język rejestracji. Fragment tokenu znika z paska. Formularz potwierdza zmianę. |
| 8. Unieważnienie, po próbie 7 | Odśwież /account w obu profilach. Ponownie użyj oryginalnego linku i spróbuj zatwierdzić reset. Na /login sprawdź stare i nowe hasło. | Obie wcześniejsze sesje odrzucone. Zużyty link i stare hasło odrzucone; nowe hasło działa. |
| 9. Wygasły reset, nowy nieużyty link | Poproś o kolejny e-mail, odczekaj ponad 30 min, otwórz jego link i zatwierdź nowe hasło. | Komunikat o nieważnym linku; dotychczasowe hasło działa. |
| 10. Limity, na końcu prób | W osobnym oknie wykonaj 11 prób błędnego logowania tym samym poprawnym adresem i długim błędnym hasłem w 15 min. | Po wyczerpaniu limitu pojawia się komunikat o zbyt wielu próbach, API daje 429. Wcześniejsze próby mogą skrócić drogę do limitu. |

**Uwagi autora:** obie poprawki opisano poniżej. **Decyzja:** etap 2A odebrany.

### Poprawki rejestracji po uwagach autora — 2A

1. Usunięto stałą podpowiedź o długości hasła na stronach rejestracji PL/EN. Przy próbie wysłania zbyt krótkiego hasła przeglądarka wskazuje pole i pokazuje zakres 12–1024 w języku formularza. Wymagane pola, minlength, maxlength i walidacja backendu pozostają aktywne.
2. Różne hasła zgłasza teraz natywna walidacja pola potwierdzenia (setCustomValidity). Poprawienie wartości usuwa błąd. Puste pole nadal podlega walidacji required.

**Weryfikacja:** w audytowej kopii frontend przeszły `npm run typecheck`, `npm run build` i `E2E_BASE_URL=http://localhost:3106 npx playwright test tests/auth.spec.ts` (Chromium, 2/2, PL i EN). W każdej wersji sprawdzono brak stałej podpowiedzi, fokus i treść komunikatu dla krótkiego hasła oraz dla niezgodnego potwierdzenia, brak żądań rejestracji w obu błędnych próbach, wyczyszczenie błędu po korekcie i udaną rejestrację. Przeszła także dalsza część scenariuszy z logowaniem i resetem przez Mailpit.

**Do odbioru:** otwórz /register lub /en/register na porcie 3106, podaj nowy e-mail i zaznacz zgodę. Spróbuj wysłać formularz kolejno z hasłami „krótko” w obu polach, „Wyprawa listopad 123!” i innym długim potwierdzeniem, a na końcu z identycznym poprawnym hasłem. Oczekiwane kolejno: komunikat przy haśle, komunikat przy potwierdzeniu, przejście do konta. Autor potwierdził wynik ponownego odbioru etapu 2A.

## Etap 2B — Google OIDC

**Odebrany przez autora 26 listopada 2026** po poprawkach Google OIDC, łączenia metod logowania i resetu hasła. Poniżej przebieg wcześniejszych prób. Etap 2A odebrany. Podstawa analizy: PLAN, etap 2; DECISIONS, etap 2B; otwarty test rzeczywistego Google w STATUS. Na wejściu HEAD cb7b265 i lokalne zmiany odbioru 2A w dokumentacji, formularzu, tłumaczeniach i teście auth. W tym odbiorze zmieniono tylko dokumentację.

### Wymagania i ocena

| Wymaganie | Ocena | Działanie i dowód |
|---|---|---|
| Przyciski Google podczas logowania i rejestracji | Zaimplementowane | AuthForm pobiera google/config po zamontowaniu. Chromium sprawdził cztery formularze w działającym UI 3000. |
| Rejestracja nowego użytkownika Google | Zaimplementowane | Kontrolowany callback utworzył konto ACTIVE/USER bez lokalnego hasła, wpis tożsamości i sesję. Nowa tożsamość wymaga zweryfikowanego, wolnego adresu. Sam tryb login nie tworzy konta. |
| Zgoda na regulamin | Zaimplementowane | W PL i EN przycisk rejestracji aktywuje się po zaznaczeniu zgody. GoogleResource sprawdza zgodę i aktualne local-draft-2. Wersja i czas pochodzą z próby zapisanej po stronie serwera. |
| Ponowne logowanie i identyfikacja | Zaimplementowane | GoogleAccounts wyszukuje parę issuer i subject. Ten sam subject powrócił do konta nawet po zmianie e-maila u dostawcy. Migracja V4 wymusza unikalność, a transakcja szereguje równoczesne tworzenie tożsamości. |
| Konflikt z kontem na hasło | Zaimplementowane | Nowa tożsamość przy zajętym adresie dostała GOOGLE_EMAIL_CONFLICT. Test potwierdził zachowanie hasła i brak dopisanej tożsamości. |
| Utworzenie i unieważnienie sesji | Zaimplementowane | Callback tworzy zwykłą sesję przez SessionService; odczyt konta zwrócił własny e-mail. Kolejne logowanie tworzy nowy token. Wspólną obsługę logout, odrzucenie wylogowanej i wygasłej sesji sprawdził AuthResourceTest. Wylogowanie dotyczy aplikacji; przeglądarka może nadal pamiętać konto Google. |
| Anulowanie | Zaimplementowane | Test lokalnego dostawcy sprawdza obsługę odmowy. Strona wyniku wyświetla komunikat anulowania; osobno sprawdzono jej treść PL/EN w Chromium. |
| Konta nieaktywne i zablokowane | Zaimplementowane | Istniejąca tożsamość w stanach BLOCKED i DELETION_PENDING dostała GOOGLE_ACCOUNT_BLOCKED. |
| Wyłączone OIDC | Zaimplementowane | Na 3106 integracja wyłączona. Oba E2E przeszły bez przycisku Google i z działającym logowaniem na hasło. |
| Komunikaty i język | Zaimplementowane | Strona google-result tłumaczy dozwolone kody, w tym konflikt, anulowanie i wymóg rejestracji; inne mapuje na ogólny błąd. Ograniczenie: błąd samego POST start w AuthForm ma komunikat ogólny, np. przy limicie prób. |

**Ochrona poświadczeń.** Quarkus realizuje Authorization Code z PKCE S256, state i nonce. Weryfikuje podpis przez JWKS, issuer, audience i ważność ID tokenu. Żądanie startu wymaga CSRF i zgodnego Origin. Powiązanie próby z cookie HttpOnly i state obowiązuje przez 10 minut. Restart procesu przerywa rozpoczętą próbę. Ograniczenie startu to 10 prób na 15 minut na IP. DiscardOidcTokens nie utrwala tokenów dostawcy; aplikacja używa własnego cookie sesji. Proxy przekazuje przekierowania i cookies, a odpowiedzi uwierzytelniania mają no-store. Główny .env jest ignorowany przez Git. Potwierdzono obecność ustawień bez wypisywania poświadczeń.

### Sprawdzenia

- Osobna instancja PostgreSQL projektu tb2a1124 na 25434, schemat travel_buddy_test. W kopii audytowej wykonano `./scripts/backend.sh test -Dtest=GoogleOidcTest,AuthResourceTest,RateLimiterTest -Dquarkus.http.test-port=28085`. **29/29**, zero błędów i pominięć: 10 OIDC z testowym OidcProvider, 18 API kont i sesji, 1 limiter. Testowy dostawca uruchamia lokalne discovery, JWKS i wymianę kodu. Objęto także ponowne uwierzytelnienie i równoległe callbacki.
- `node scripts/check-google-proxy.mjs` — zaliczony. Kontrolowany upstream potwierdził przekazanie query, Cookie, Set-Cookie, Origin, Location oraz no-store przez zbudowanego Nuxta.
- W frontend kopii audytowej `E2E_BASE_URL=http://localhost:3106 npx playwright test tests/auth.spec.ts` — **2/2**, PL i EN, przy wyłączonym OIDC, z lokalnym Mailpit. Kopia ma podstawienia portów 3106/28025 opisane przy 2A.
- Odczyt `GET /api/v1/auth/google/config` — na 3000 enabled=true, na 3106 enabled=false. Dodatkowy przebieg Chromium na 3000 sprawdził widoczność i stan przycisku na login/register w PL i EN oraz tekst anulowania na obu stronach wyniku. Nie wysyłano formularza do Google. **Rzeczywista autoryzacja przez Google: do wykonania przez autora.**

### Konfiguracja i start

**Do ręcznego odbioru użyj http://localhost:3000.** W głównym lokalnym .env włączono Google i ustawiono Client ID oraz Client secret. Redirect URI w pliku jest zgodny z poniższym adresem. Stan procesu potwierdza publiczny endpoint config. Ważność sekretu, konfiguracja klienta w konsoli Google i uprawnienie wybranego konta testowego wymagają rzeczywistego powrotu z Google. Na porcie 3106 działa wariant bez Google, z osobną bazą.

Jeśli właściciel musi uzupełnić lub odtworzyć konfigurację:

1. W swoim projekcie w Google Cloud Console otwórz Google Auth Platform → Branding. Uzupełnij nazwę aplikacji, adres wsparcia i kontakt. W Audience dla zewnętrznych użytkowników pozostaw Testing i dodaj uprawnione adresy testowe. W Data Access sprawdź zakresy openid, email i profile. [Konfiguracja ekranu zgody Google](https://developers.google.com/workspace/guides/configure-oauth-consent)
2. W Clients wybierz klienta typu Web application lub utwórz taki klient. Do Authorized redirect URIs wpisz dokładnie `http://localhost:3000/api/v1/auth/google/callback`. [Instrukcja klienta webowego Google](https://developers.google.com/identity/protocols/oauth2/web-server)
3. W głównym lokalnym .env ustaw `GOOGLE_OIDC_ENABLED=true`, `GOOGLE_OIDC_CLIENT_ID`, `GOOGLE_OIDC_CLIENT_SECRET` z tego samego klienta oraz `GOOGLE_OIDC_REDIRECT_URI=http://localhost:3000/api/v1/auth/google/callback`. Zachowaj istniejące ustawienia bazy. Poświadczenia uzupełnij lokalnie.
4. Po zmianie konfiguracji zatrzymaj główny Quarkus przez Ctrl+C w jego terminalu i z katalogu głównego uruchom `./scripts/backend.sh quarkus:dev`. Przy starcie od zera najpierw `docker compose up -d --wait`, a w osobnym terminalu w frontend/ `npm run dev`. Sprawdź `curl -fsS http://localhost:3000/api/v1/auth/google/config` i odśwież formularz.

### Checklista rzeczywistego Google

Użyj osobnego profilu przeglądarki dla portu 3000. Przygotuj uprawnione konto Google A z wolnym adresem w tej bazie oraz drugie konto Google B, którego adres posłuży do kontrolowanego konfliktu. Konto A po rejestracji pozostanie w lokalnej bazie.

| Warunki początkowe | Kroki | Oczekiwany rezultat |
|---|---|---|
| Gość, nowe A | Na /register i /en/register sprawdź przycisk przed i po zaznaczeniu regulaminu. Na wybranej stronie kliknij przycisk po zgodzie, wybierz A u Google i zaakceptuj. | Etykieta w języku strony; start rejestracji dopiero po zgodzie. Powrót do /account lub /en/account z adresem A. |
| A zalogowane | Odśwież konto, otwórz je w drugiej karcie tego profilu, po czym wyloguj w pierwszej i odśwież drugą. | Przed logout oba widoki mają ten sam e-mail. Po logout prywatny ekran wymaga logowania. |
| A zarejestrowane, sesja aplikacji zakończona | Na /en/login wybierz Continue with Google i to samo A. Powtórz w PL po wylogowaniu. | Powrót do tego samego konta w języku formularza, bez ponownej rejestracji. |
| Gość, ekran Google umożliwia odmowę | Rozpocznij logowanie, anuluj na ekranie dostawcy. Powtórz w drugim języku, w razie potrzeby użyj świeżego profilu i konta bez wcześniejszej zgody. | Po powrocie z odmową komunikat „Logowanie anulowane.” lub „Sign-in cancelled.”, brak sesji. Samo zamknięcie karty nie wysyła callbacku. |
| B ma konto lokalne założone hasłem, bez powiązania Google | Wyloguj B. Na ekranie login wybierz Google B; sprawdź też próbę przez register ze zgodą. Następnie zaloguj się dotychczasowym hasłem. | Komunikat o zajętym adresie, brak sesji Google w aplikacji; lokalne hasło nadal działa. |
| Kopia 3106, Google wyłączone | Otwórz /login i /register oraz warianty /en. | Widoczne formularze lokalne, przycisk Google ukryty. |

**Ostateczna decyzja autora 2B:** odebrany po poprawkach.

### Ponowny odbiór na nowym Macu — 25 listopada 2026

Bieżący checkout to b4aef87. Etapy 1 i 2A odebrane przez autora, 2B oczekuje na próbę rzeczywistego Google. Wpisy o procesach na portach 3106/28084 oraz o aktywnym kliencie Google powyżej dotyczą poprzedniego komputera. Tutaj używamy localhost:3000, backendu 8080, PostgreSQL 5433 i Mailpit 8025 (SMTP 1025). Utworzono lokalny ignorowany plik .env z przykładu. Dane klienta Google trzeba przenieść osobno.

**Konfiguracja właściciela.** W istniejącym projekcie Google Auth Platform → Clients wybierz dotychczasowego klienta Web application. W Authorized redirect URIs zapisz dokładnie `http://localhost:3000/api/v1/auth/google/callback`. Dla External w trybie Testing dodaj uprawnione adresy A i B w Audience → Test users. W lokalnym .env ustaw GOOGLE_OIDC_CLIENT_ID i GOOGLE_OIDC_CLIENT_SECRET z bezpiecznie zachowanej konfiguracji klienta. Jeśli stary sekret jest zamaskowany i nie masz kopii, w szczegółach tego klienta dodaj nowy sekret i zapisz go lokalnie. Pozostaw dotychczasowy sekret używany przez inne instalacje. Ustaw GOOGLE_OIDC_ENABLED=true, uruchom ponownie backend i sprawdź na porcie 3000, że GET /api/v1/auth/google/config daje enabled=true.

**Próby na tej instalacji** (osobny profil przeglądarki, localhost w całym przebiegu):

1. Przy wyłączonym Google otwórz /login i /register w PL i EN. Sprawdź brak przycisku Google i dostępność formularza hasła. Utwórz konto lokalne B przez /register, używając adresu drugiej kontrolowanej tożsamości Google. Wyloguj się.
2. Po włączeniu integracji spróbuj logowania nowym A na /login. Oczekuj wskazania rejestracji. Na /register sprawdź blokadę przycisku przed zgodą. Zaakceptuj regulamin, wybierz A u Google i sprawdź adres na /account.
3. Odśwież konto i zrestartuj backend. Sprawdź utrzymanie sesji. Wyloguj się i odśwież drugą kartę konta. Dostęp powinien ponownie wymagać logowania. Zaloguj A przez /en/login, sprawdź powrót na /en/account z tym samym adresem.
4. Wyloguj się. W PL i EN odmów zgody na ekranie Google, jeżeli oferuje odmowę. Sprawdź komunikat anulowania oraz brak dostępu do konta. Zamknięcie karty pomija callback.
5. Spróbuj zalogować i zarejestrować B przez Google. Sprawdź komunikat o konflikcie oraz możliwość dalszego logowania hasłem. Powtórz w drugim języku.
6. Jeżeli dysponujesz uprawnionym moderatorem i przeznaczonym do tej próby kontem Google, zablokuj to konto w panelu moderacji. Sprawdź utratę dostępu oraz komunikat nieaktywnego konta przy ponownym logowaniu Google w PL i EN. Odblokuj konto po próbie. Scenariusz automatyczny inactiveAccountCannotSignIn obejmuje odmowę nowej sesji.

**Wyniki z tego komputera.** Java 21.0.13.1, Maven Wrapper 3.9.11, Node 24.22.3, npm 11.19.1, Docker Engine 29.9.3 i Compose 5.7.1. Początkowe listy kontenerów i wolumenów były puste. Obie usługi healthy. Na bazie travel_buddy sprawdzono 13 udanych migracji i dwie wyprawy demo, readiness JDBC UP oraz Mailpit HTTP 200. Tryb dev używa lokalnego symulatora Turnstile, sesji w PostgreSQL z cookie session na HTTP i SMTP 127.0.0.1:1025. Sprzątanie prób Google działa co minutę, sesji i danych prywatności co godzinę. W repozytorium brak osobnej konfiguracji uruchomieniowej IntelliJ.

Na osobnej bazie travel_buddy_acceptance_2b uruchomiono:

```sh
./scripts/backend.sh test -Dtest=GoogleOidcTest,AuthResourceTest,RateLimiterTest -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_acceptance_2b
```

Wynik 29/29, w tym 10 Google, 18 kont i sesji, 1 limitera. Zero błędów i pominięć. npm ci, build Nuxta oraz node scripts/check-google-proxy.mjs przeszły. Playwright tests/auth.spec.ts wykonano w obu językach na tej samej osobnej bazie, z pomocniczym backendem na 28084 i zbudowanym Nuxtem na 3000. Pierwsza próba 0/2 przekroczyła pięciosekundowy czas asercji przejścia na konto (PL) i komunikatu resetu (EN). Powtórzenie po zakończeniu startu usług, bez zmiany testu, dało 2/2 w 19 sekund. Przyczyna pierwszych opóźnień nieustalona. Próba z rzeczywistymi poświadczeniami Google pozostaje otwarta.

Na docelowym Nuxt dev na localhost:3000 odczyt google/config zwrócił enabled=false. Skrypt node scripts/check-ssr.mjs potwierdził listy i szczegóły w PL i EN (4/4).

**26 listopada 2026.** Po lokalnym uzupełnieniu danych klienta i restarcie Quarkusa endpoint konfiguracji przez Nuxt potwierdził aktywne OIDC. W Chromium sprawdzono przycisk Google na login/register w PL i EN oraz blokadę rejestracji przed zgodą. Start przez frontend wygenerował przekierowanie do accounts.google.com z redirect_uri równym http://localhost:3000/api/v1/auth/google/callback. Próbę zatrzymano przed ekranem dostawcy. Rzeczywisty callback, sesja i decyzja autora pozostają do ręcznego odbioru.

### Poprawki po ręcznym odbiorze 2B — 26 listopada 2026

Obie uwagi autora mają wysoki priorytet. Poniższa checklista zastępuje wcześniejsze próby wymagające wyboru osobnej rejestracji Google i zgody przed przekierowaniem.

1. **Wspólne wejście i zgoda po callbacku.** Na obu formularzach przycisk Google rozpoczyna ten sam proces. Nowa, zweryfikowana tożsamość z wolnym adresem dostaje osobny ekran zgody PL/EN z linkami do dokumentów. Rezygnacja zużywa próbę i prowadzi do logowania. Akceptacja zapisuje zgodę i tworzy konto, tożsamość i sesję. Powracająca aktywna tożsamość przechodzi do konta. Zajęty adres daje komunikat z drogą do logowania hasłem. Konto zablokowane nie otrzymuje sesji.
2. **Wybór konta po wylogowaniu.** Zwykła autoryzacja wysyła `prompt=select_account` bez login_hint. W ścieżce usuwania konta aplikacja nadal wymaga świeżego uwierzytelnienia tej samej tożsamości i zgodności sesji.

**Ręczny odbiór prawdziwego Google — oczekuje na autora.** Użyj tego samego profilu przeglądarki i localhost:3000 przez całą próbę. Przygotuj uprawnione konta Google A i B, w tym jedno z wolnym adresem. Dla konfliktu użyj osobnego adresu Google, który rejestrujesz lokalnie hasłem.

| Próba | Kroki i oczekiwany rezultat |
|---|---|
| Rezygnacja | Na /login kliknij Google i wybierz nową tożsamość. Sprawdź oba linki prawne i nieaktywny przycisk przed zgodą. Zrezygnuj. Otwiera się logowanie, /account pozostaje niedostępne. Kolejna próba tą samą tożsamością ponownie wyświetla regulamin. |
| Akceptacja | Wróć przez /en/register bez wypełniania pól lokalnych. Wybierz tę samą tożsamość. Sprawdź język i oba linki, zaznacz zgodę i potwierdź. Oczekuj /en/account z właściwym adresem bez drugiego przejścia przez Google. |
| A → B → A | Wyloguj A. Na /login kliknij Google, sprawdź selektor i zaloguj B. Jeśli to nowe konto, zaakceptuj regulamin. Sprawdź adres B i wyloguj. Wróć przez /register do A. Oczekuj wyboru konta oraz bezpośredniego przejścia na konto A. |
| Konflikt, blokada i odmowa dostawcy | Dla lokalnego adresu z hasłem sprawdź konflikt i link do logowania, a następnie dotychczasowe hasło. Dla zablokowanej testowej tożsamości sprawdź komunikat i brak sesji. Jeśli Google oferuje anulowanie, odmów i wróć linkiem do logowania. Zamknięcie karty nie wywołuje callbacku. |

**Testy automatyczne z 26 listopada.** Użyto osobnej bazy travel_buddy_acceptance_2b. Zestaw GoogleOidcTest, AuthResourceTest i AccountDeletionTest przeszedł 40/40 (11 + 18 + 11). Po rozdzieleniu parametrów żądania autoryzacji ponownie wykonano GoogleOidcTest, wynik 11/11. Pokrycie obejmuje pojedyncze zużycie decyzji, CSRF, ponowny callback istniejącej tożsamości, konflikt i wyścig adresów, blokadę oraz zabezpieczenia ponownego uwierzytelnienia. Pierwszy przebieg sześciu scenariuszy z nowym klientem testowym odrzucił podwójne cookie CSRF. Poprawiono składanie żądania w teście.

**Przeglądarka z lokalnym dostawcą.** W jednym kontekście Chromium sprawdzono rezygnację A na polskim ekranie, ponowną próbę i akceptację A, logout z odmową dostępu do sesji, wybór i akceptację B przez /en/register oraz powrót do A przez /en/login bez kolejnej zgody. Oba formularze zgody miały linki do odpowiednich wersji dokumentów. W pierwszej próbie dopasowanie adresu uwzględniało wielką literę mimo normalizacji przez aplikację. Po korekcie asercji pełny przebieg ze świeżymi tożsamościami przeszedł. Typecheck, build i check-google-proxy przeszły. Regresja Playwright tests/auth.spec.ts z wyłączonym Google i Mailpit przeszła 2/2 po ponowieniu bez zmiany testów. Przy pierwszym wykonaniu obie pięciosekundowe asercje (PL przejście do konta, EN status resetu) przekroczyły limit czasu.

**Konfiguracja prawdziwego Google.** Na docelowym localhost:3000 potwierdzono enabled=true oraz wygenerowanie żądania do Google z prompt=select_account, właściwym redirect URI i bez login_hint ani max_age w zwykłym logowaniu. Zatrzymano próbę przed interakcją z dostawcą. Widok rzeczywistego selektora Google, końcowy callback oraz decyzja odbiorowa należą do autora.

## Pełny ponowny odbiór Google OIDC po uwadze 3

**Decyzja autora po wykonaniu poprawek — odebrany.** Ta instrukcja zastępuje wcześniejsze checklisty 2B. Uwaga 3 dotyczyła konfliktu e-maila bez możliwości potwierdzenia istniejącego konta oraz braku tej operacji w ustawieniach.

### Przygotowanie i kolejność

Na prośbę autora 26 listopada przygotowano oddzielną bazę ręcznego odbioru `travel_buddy_manual_2b_20261126`. Stan początkowy po 13 migracjach to 0 tożsamości Google, 1 użytkownik demonstracyjny i 2 wyprawy. Backend działa na standardowym porcie 8080. Przy każdym restarcie podczas odbioru (również w 2B-15) użyj z katalogu projektu:

```sh
./scripts/backend.sh quarkus:dev -Ddebug=false -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_manual_2b_20261126
```

Po odbiorze zatrzymaj ten backend. Zwykłe `./scripts/backend.sh quarkus:dev` ponownie wybierze dotychczasową bazę `travel_buddy` z konfiguracji lokalnej.

Używaj http://localhost:3000 w jednym profilu przeglądarki. Callback klienta Web application w istniejącym projekcie Google to dokładnie `http://localhost:3000/api/v1/auth/google/callback`. W trybie External/Testing dodaj używane adresy do Test users. Endpoint /api/v1/auth/google/config ma zwracać enabled=true. Używaj kont przeznaczonych do prób. Udane powiązanie pozostaje w bazie.

- **A i B** — dwie tożsamości Google z wolnymi adresami, przeznaczone do utworzenia kont przez Google.
- **C** — adres trzeciej tożsamości Google. Jeszcze przed użyciem Google zarejestruj ten adres przez /register z hasłem Travel Buddy. Zapisz w profilu rozpoznawalny pseudonim i kontakt. W /my-trips utwórz szkic z rozpoznawalnym tytułem. Zanotuj adres edycji szkicu. Jeśli masz testowe zgłoszenie, zapisz jego status i stan w /my-applications. Wyloguj C.
- **D** — odrębne konto lokalne z hasłem i wolna tożsamość Google do połączenia w ustawieniach. Jej zweryfikowany e-mail może być inny niż podstawowy adres D. **M** — uprawniony moderator do próby blokady, w osobnym profilu przeglądarki. Test blokady wykonaj na końcu.
- Przed 06 zarejestruj C lokalnie przez /register, używając adresu Google C i własnego hasła testowego. Uzupełnij profil i prywatny kontakt, utwórz szkic w /my-trips. Jeśli sprawdzasz historię, zgłoś się na dostępną wyprawę innego organizatora i zanotuj stan w /my-applications. W tej samej przeglądarce otwórz /api/v1/me i zanotuj userId oraz adres edycji szkicu, następnie wyloguj. Przed 11 utwórz D przez lokalną rejestrację i zaloguj się hasłem D.
- Dla każdego ID zanotuj PL/EN, wynik i zaobserwowaną różnicę. Do raportu nie kopiuj haseł, tokenów, wartości cookies ani pełnych URL callbacku. Jeśli ponawiasz odbiór wcześniej połączonego konta, dla prób pierwszego połączenia przygotuj nowe konto testowe.

| ID | Warunek i konta | Kroki | Oczekiwany rezultat | Jak sprawdzić |
|---|---|---|---|---|
| 2B-01 | Gość, nowe A | Na /login wybierz Google, następnie A. Otwórz regulamin i politykę prywatności. Bez zaznaczania zgody wybierz powrót. | Dokumenty PL, potwierdzenie nieaktywne, powrót do logowania bez utworzenia konta. | /account wymaga logowania. Ponowny wybór A w 02 ponownie pokazuje zgodę. |
| 2B-02 | Gość, A po 01 | Ponów przez /login, zaznacz zgodę, zaakceptuj jeden raz. | Konto A i sesja powstają po zgodzie, bez drugiego logowania Google. | Na /account odczytaj adres A, w metodach Google połączone i hasło niedostępne. |
| 2B-03 | Zalogowane A | Odśwież konto, otwórz je w drugiej karcie. Wyloguj w pierwszej i odśwież drugą. | Odświeżenie zachowuje aktywną sesję, wylogowanie kończy dostęp w obu kartach. | Druga karta przekierowuje na logowanie. |
| 2B-04 | Ten sam profil po 03, nowe B | Na /en/register pozostaw puste pola i odznaczoną zgodę lokalną. Kliknij Continue with Google. W selektorze wybierz B, sprawdź oba linki EN, zaznacz zgodę i potwierdź. | Wybór innego konta i pierwsza rejestracja B z ekranu rejestracji. | /en/account pokazuje adres B i angielskie etykiety. |
| 2B-05 | B zalogowane, A istnieje | Wyloguj B. Na /en/login wybierz Google A. | Selektor jest dostępny, A nie powtarza zgody. | Bezpośredni powrót na /en/account z adresem A. Wyloguj A. |
| 2B-06 | Gość, lokalne C | Na /login wybierz Google C. Sprawdź propozycję „Połącz konto z Google”, wybrany adres, kliknij akcję, wpisz błędne hasło Travel Buddy. | Komunikat walidacji i możliwość ponowienia. | Pozostajesz na ekranie łączenia. Otwórz /account w innej karcie — brak sesji. |
| 2B-07 | C po błędzie | Wybierz powrót do logowania. Wejdź hasłem C. | Próba anulowana, stara metoda nadal działa. | W metodach Google niepołączone. Wyloguj C. |
| 2B-08 | Gość, C niepołączone | Zacznij ponownie przez /en/login. Na ekranie łączenia wybierz odzyskiwanie hasła. Podaj adres C, otwórz mail w localhost:8025 i ustaw nowe hasło. | Standardowy reset i konieczność nowego startu Google. | Powrót na /en/google-link bez nowego OIDC pokazuje komunikat nieważnej próby. |
| 2B-09 | C z nowym hasłem | Wybierz Google C przez /en/login, kliknij Link account with Google, podaj aktualne hasło i potwierdź. | Powiązanie z istniejącym kontem, automatyczna sesja, brak rejestracji i zgody od nowa. | Na /en/account widzisz adres C i obie dostępne metody. Powrót na ekran łączenia nie daje kolejnego potwierdzenia. |
| 2B-10 | C po 09 | Porównaj pseudonim, kontakt, listę /my-trips i zapisany adres edycji. Otwórz /my-applications, jeśli przygotowano zgłoszenie. Wyloguj, zaloguj hasłem, wyloguj i zaloguj Google C. | Obie metody prowadzą do istniejących danych i tych samych uprawnień. | Porównaj userId w /api/v1/me z wartością sprzed 06, szkic pod wcześniejszym adresem, pola profilu i status zgłoszenia po obu logowaniach. Wyloguj. |
| 2B-11 | Lokalne D, A już używane | Zaloguj D hasłem. W /account wybierz połączenie, u Google wskaż A. | Odmowa zmiany właściciela A. | Wróć do /account. D nadal Google niepołączone, sesja D pozostaje aktywna. |
| 2B-12 | D w ustawieniach, wolne Google D | Ponów z ustawień, wybierz wolną tożsamość D, sprawdź wyświetlony adres. Wpisz błędne, następnie poprawne hasło lokalne D. | Świeże potwierdzenie wymagane mimo aktywnej sesji. Obie metody dostępne po sukcesie. | Odśwież /account i sprawdź podstawowy adres D. Po wylogowaniu wybierz Google D i porównaj adres oraz profil. |
| 2B-13 | Gość, A lub B | Na /login lub /en/login rozpocznij Google i wybierz odmowę, jeśli dostawca ją oferuje. | Po callbacku komunikat anulowania i droga do logowania w języku startu. | Sprawdź komunikat i brak dostępu do /account. Gdy Google nie oferuje odmowy, zapisz „niedostępne u dostawcy”. Zamknięcie karty nie wysyła callbacku. |
| 2B-14 | C aktywne, M ma dostęp do moderacji C | Zaloguj C. Z drugiego profilu otwórz /profiles/{userId} dla C, zgłoś jego profil testowy. Jako M otwórz /moderation, wybierz to zgłoszenie, podaj uzasadnienie i zablokuj C. Odśwież kartę C i spróbuj obu metod ponownie. Po sprawdzeniu odblokuj C. | Blokada odbiera dostęp i uniemożliwia utworzenie nowej sesji. | Google pokazuje komunikat o nieaktywnym koncie. /account pozostaje niedostępne. Po odblokowaniu C znowu może się zalogować. |
| 2B-15 | Ostatnia próba, konto lokalne C lub D | Ustaw GOOGLE_OIDC_ENABLED=false w lokalnym .env i zrestartuj backend. Sprawdź /login, /register i wersje /en. Zaloguj C hasłem i odwiedź ustawienia. Przywróć wcześniejszą wartość i zrestartuj backend. | Formularze hasła działają, przyciski startu Google ukryte, zapisane powiązania zachowane. | Porównaj /api/v1/auth/google/config przed i po przywróceniu; sprawdź obie metody C po ponownym włączeniu. |

### Dodatkowe próby graniczne

Wykonuj poniższe próby na **C przed 09 lub D przed 12**, zanim powiązanie zostanie zapisane.

| ID | Warunek i konta | Kroki | Oczekiwany rezultat | Jak sprawdzić |
|---|---|---|---|---|
| 2B-16 | C niepołączone | Rozpocznij łączenie, odczekaj ponad 10 minut od rozpoczęcia autoryzacji, wyślij aktualne hasło. | Stan wygasł. | Brak nowej sesji; po logowaniu hasłem sprawdź Google niepołączone. |
| 2B-17 | Lokalne D zalogowane | Rozpocznij z ustawień. Przed wysłaniem hasła wyloguj D w drugiej karcie; wróć do pierwszej i potwierdź. | Utrata sesji inicjującej blokuje zapis. | Zaloguj ponownie D hasłem i sprawdź metody. |
| 2B-18 | C niepołączone, dwie karty | Rozpocznij próbę C, otwórz formularz hasła. W drugiej karcie rozpocznij kolejną próbę C. Wyślij formularz ze starej karty. | Stare potwierdzenie nie pasuje do nowej próby. | Brak nowej sesji. W razie potrzeby zacznij 09 od nowa. |
| 2B-19 | Gość, kilka wcześniejszych prób | Jeśli pojawi się limit, zanotuj operację i czas, poczekaj 15 minut i rozpocznij od nowa. | Start ma limit 10 na IP w oknie 15 minut. Potwierdzenia hasłem mają osobne limity po 10 na IP i konto. | Po upływie okna ponów z poprawnymi danymi. Nie usuwaj danych w celu wyzerowania próby. |
| 2B-20 | C przed 09, działający Mailpit | Po konflikcie przejdź do odzyskiwania hasła, podaj adres C, otwórz wiadomość w localhost:8025 i ustaw nowe hasło. Wróć na /google-link. Następnie rozpocznij od nowa przez Google C i potwierdź nowym hasłem (zalicza 09). | Stary proces nie umożliwia potwierdzenia. Nowa próba przyjmuje aktualne hasło. | Stara strona informuje o nieważnej próbie. Po nowym potwierdzeniu /account pokazuje obie metody, a nowe hasło działa w 10. |

### Weryfikacja automatyczna uwagi 3

- Na osobnej bazie travel_buddy_acceptance_2b i w izolowanym katalogu kompilacji uruchomiono zestaw GoogleOidcTest, AuthResourceTest i AccountDeletionTest. Wynik **43/43** (14 + 18 + 11), bez pominięć. Sprawdzono m.in. jedno powiązanie i obie metody logowania, zachowanie UUID, profilu, kontaktu, szkicu wyprawy i historii wycofanego zgłoszenia, błędne hasło, CSRF, anulowanie, wygaśnięcie, ponowienie potwierdzenia, blokadę i próbę zajęcia cudzej tożsamości. Wygaśnięcie odtworzono przez uruchomienie sprzątania ze znacznikiem czasu po końcu ważności próby.
- Wcześniejsze przebiegi rozszerzonego testu wymagały korekty przygotowania i sprzątania danych: użyto encji ORM ze schematu testowego i jawnej kolejności usuwania własnych rekordów. Jedno uruchomienie ze współdzielonym katalogiem kompilacji przerwało się podczas wyszukiwania testów. Powyższy wynik pochodzi z ponownego pełnego przebiegu aktualnych źródeł.
- Chromium z lokalnym dostawcą podpisującym tokeny: PL — konflikt, anulowanie, ponowienie, błędne i poprawne hasło oraz ponowne logowanie Google; EN — start z ustawień, błędne i poprawne hasło, obie metody widoczne, wylogowanie i ponowne logowanie Google. Obie ścieżki przeszły.
- Przy wyłączonym OIDC Playwright tests/auth.spec.ts przeszedł **2/2** z Mailpit. Pierwszy przebieg dał 1/2 — polska próba przekroczyła limit 5 sekund przy komunikacie resetu. Powtórzono bez zmiany asercji. Typecheck, build, test proxy i git diff --check zakończyły się powodzeniem.
- Środowisko autora przywrócone na localhost:3000 z enabled=true. Rzeczywiste połączenie kontrolowanych przez autora kont Google oraz jego decyzja odbiorowa pozostają do wykonania.

### Uwaga 4 — walidacja nowego hasła podczas resetu (2A/2B)

Formularze resetu i rejestracji korzystają z tej samej walidacji pól. Usunięto stałą podpowiedź PL/EN. Puste pole obsługuje natywne required, a komunikaty o długości (12–1024) i niezgodności haseł są przypisane do odpowiednich pól i znikają po poprawieniu wartości. Backend nadal sprawdza oba limity i zgodność haseł.

**Weryfikacja.** Typecheck i build Nuxta przeszły. W izolacji na porcie 3106 i bazie travel_buddy_acceptance_2b wykonano `E2E_BASE_URL=http://localhost:3106 ./node_modules/.bin/playwright test tests/auth.spec.ts` z katalogu frontend. Wynik 2/2 w Chromium, PL i EN. W obu językach otwarto rzeczywisty link z Mailpit. Sprawdzono pusty formularz, 5 znaków, 1025 znaków, różne poprawnej długości hasła i poprawną zgodną parę. Atrybut maxlength ogranicza wpisywanie do 1024 znaków, więc wartość 1025 ustawiono programowo ze zdarzeniem input. Przy błędach nie wysłano żądania potwierdzenia resetu. Poprawna para pozwoliła zakończyć reset i zalogować się nowym hasłem.

**Próba autora.** W scenariuszu 2B-20 otwórz link z Mailpit, wyślij pusty formularz, wpisz krótkie hasło, następnie dwa różne hasła długości co najmniej 12 znaków. Sprawdź fokus i komunikat przy odpowiednim polu. Wyrównaj wartości i wyślij ponownie. Oczekuj potwierdzenia resetu. Kontynuuj łączenie konta nowym hasłem zgodnie z 2B-09. Autor następnie potwierdził poprawne działanie i zakończył odbiór 2B.

## Etap 3 — profil i prywatne kontakty, 26 listopada 2026

**Odebrany przez autora bez uwag wymagających poprawek.** Autor sprawdził własny profil, prywatne kontakty i publiczny widok profilu. Ręczna weryfikacja udostępnienia kontaktów innemu użytkownikowi po akceptacji zgłoszenia należy do etapu 5. Poniżej zachowano przebieg odbioru. Podstawą oceny są etap 3 PLAN, decyzje etapów 3 i 5, migracja V5, ProfileResource, ProfileService, ProfileDtos, ApplicationService.contacts, ProfileEditor.vue, strony account i profiles/[id] oraz oba słowniki i18n. Oceny opisują implementację. Wyniki uruchomień podano osobno.

| Wymagania | Ocena | Zachowanie |
| --- | --- | --- |
| Nowe konto, zapis bez kontaktów, kompletność | zaimplementowane | /api/v1/me zwraca profile=null i puste kontakty. profileComplete oznacza zapisany profil, hasContact przynajmniej jeden kontakt, readyToParticipate wymaga obu. UI pokazuje najpierw brak profilu, następnie brak kontaktu lub gotowość. |
| Nazwa, wiek, języki, style, walidacja wymaganych pól | zaimplementowane | Wymagane są wszystkie cztery. Nazwa po usunięciu skrajnych białych znaków 2–60 jednostek UTF-16, wiek całkowity 18–120. Od 1 do 10 z 22 języków i od 1 do 4 stylów (backpacking, comfort, trekking, city_break). API odrzuca obce kody, usuwa powtórzenia w kolejności wejścia i przyjmuje najwyżej 32 elementy wejściowe na listę. |
| Opis, edycja, odświeżenie, konflikt wersji | zaimplementowane | Bio opcjonalne do 1000 jednostek UTF-16 po przycięciu. Pusty tekst lub null usuwa opis. Nazwa i bio nie przyjmują znaków < ani >. PATCH sprawdza wersję, pominięte pola zachowują wartość. Data wieku to dzień UTC pierwszego zapisu lub zmiany wieku. |
| Każdy rodzaj kontaktu, walidacja, dodanie, edycja, usunięcie | zaimplementowane | Wszystkie opcjonalne. E-mail do 254 i walidacja adresu. Instagram 1–30 liter ASCII, cyfr, kropek lub podkreśleń po usunięciu jednego początkowego @. WhatsApp wymaga + i 2–15 cyfr, pierwsza 1–9. API przycina wartości. PUT zastępuje zestaw, brak pola, null lub same odstępy usuwają wartość. Sprawdzany jest format. |
| Oddzielny e-mail logowania, własna edycja i odczyt | zaimplementowane | Właściciel widzi login, własne kontakty i stan profilu. Właściciela wyznacza aktywna sesja. Kontakt nie jest kopiowany z loginu. Zapis wymaga CSRF i dozwolonego Origin. |
| Profil publiczny, API, SSR i konta nieaktywne | zaimplementowane | Gość i inne konto widzą tę samą nazwę, wiek, opis, języki i style. Publiczne DTO zawiera dodatkowo userId. Publiczny HTML ma noindex. Prywatny edytor pobiera dane po stronie klienta. Brak profilu, BLOCKED i DELETION_PENDING dają publiczne 404. |
| Dostęp do kontaktów na wyprawie | zaimplementowane | Osobny odczyt konkretnej widocznej opublikowanej wyprawy tylko między aktywnym organizatorem i aktywnym zaakceptowanym uczestnikiem, w obu kierunkach. Zamknięcie zachowuje dostęp, wycofanie go odbiera. Inny uczestnik i administrator bez relacji nie mają wyjątku. |
| Formularze, komunikaty, PL/EN | częściowo zaimplementowane | Etykiety, słowniki, błędy i statusy mają tłumaczenia. Błąd serwerowy pola może być wyświetlony dwukrotnie (pole i alert). Natywne komunikaty zależą od języka przeglądarki. Opis prywatności w obu językach zapowiada udostępnianie kontaktów w przyszłości mimo działającego etapu 5. |
| Obsługa telefonu | częściowo zaimplementowane | Formularz ma etykiety, zawijane opcje i textarea o szerokości kontenera. Czytelność, fokus, klawiatura i wygoda obsługi wymagają próby na telefonie. |

### Wyniki automatyczne z 26 listopada

Testy korzystały z nowej lokalnej bazy PostgreSQL 17.6 `travel_buddy_acceptance_3_1126`. Backend skompilowano z kopii ówczesnego drzewa roboczego. Playwright korzystał z osobnego schematu `profile_ui`, frontendu na 3107 i backendu na 8087.

| Zestaw | Wynik | Zakres potwierdzony wykonaniem |
| --- | --- | --- |
| ProfileResourceTest | 6/6 | Pusty profil, zapis częściowy, granice wieku i długości, niedozwolone pola, normalizacja, data wieku, flagi przed i po podaniu kontaktów, zastąpienie i usunięcie kontaktów, publiczna lista pól, CSRF, nieaktywne konta, konflikt wersji i wyścig pierwszego zapisu. |
| GoogleOidcTest | 14/14 | Kontrolowany dostawca OIDC, nowe konto Google, ponowne logowanie, zachowanie UUID, profilu i kontaktu podczas łączenia kont. Pełna edycja profilu konta wyłącznie Google oraz rzeczywiste przełączanie metod w przeglądarce pozostają w 3-R i 3-R2. |
| ApplicationServiceTest | 0/5 wprost, 5/5 odziedziczonych metod przez ModerationTest | Pierwszy przebieg zakończył się pięcioma błędami inicjalizacji CDI (niejednoznaczny wybór klasy testowej). Te same metody uruchomiono przez klasę potomną. Potwierdziły m.in. wzajemny dostęp po akceptacji, odmowę między uczestnikami, cofnięcie dostępu, zachowanie dostępu po zamknięciu oraz prywatny kontrakt HTTP. |
| Playwright tests/profiles.spec.ts | 1/1, Chromium, świeży build Nuxta | Dwa konta lokalne, walidacja, trwałość po odświeżeniu, szkic po 409, odrzucenie cudzych pól, reset kontaktów, publiczne DTO, brak testowych sekretów w publicznym HTML PL/EN i źródle /account. Czas końcowego przebiegu 16,3 s. |

Pierwsze dwie próby Chromium zatrzymały się na oczekiwaniu na /account podczas rejestracji. Po rozgrzaniu serwera ten sam test przeszedł, a po osobnym buildzie z aktualnych wtedy źródeł przeszedł ponownie. Wynik dodatkowej próby mobilnego Chromium nie zachował się po wznowieniu sesji. Próba 3-Q pozostaje otwarta. Wyniki powyżej pochodzą z wykonanych przebiegów z 26 listopada.

### Wykonane przez autora — 3-A do 3-N

Poniższa tabela zachowuje przebieg wcześniejszych prób. **Dalszy odbiór zaczyna się w sekcji [Od 3-O — odbiór użytkownika](#odbior-uzytkownika-3o).**

| ID | Stan i konta | Dane i czynności | Oczekiwany rezultat |
| --- | --- | --- | --- |
| 3-A | Nowe A | Otwórz /account i /api/v1/me. Zanotuj userId, wejdź na /profiles/{userId}. | Pola puste, brak linku publicznego, trzy flagi false, publiczny adres 404. Login wyłącznie w prywatnym koncie. |
| 3-A2 | A bez profilu | Zapisz sam Instagram @solo. Odczytaj flagi, wyczyść kontakt i zapisz kontakty. | Kolejno false/true/false i false/false/false (profil/kontakt/gotowość). |
| 3-B | A puste | Wpisz „  Mira  ”, 29, wybierz pl, en i trekking. Pozostaw opis i kontakty puste, zapisz profil. | Mira, wersja 1, data wieku z dzisiaj UTC, link publiczny, true/false/false. |
| 3-C | A zapisane | Odśwież, wyloguj się i zaloguj hasłem. Wróć do konta. | Ten sam UUID i wszystkie zapisane wartości. |
| 3-D | A | Zmień nazwę na Mira2, wiek na 30, pl na de, trekking na comfort, dopisz dwuwierszowe bio. Zapisz, odśwież i otwórz link publiczny. Potem usuń sam opis. | Każda zmiana trwała, podziały wierszy widoczne. Usunięty opis ma null. Zmiana samego opisu zachowuje datę wieku. |
| 3-E | A, poprawne pozostałe pola | Osobno usuń nazwę, wiek, wszystkie języki i wszystkie style. Za każdym razem zapisz i przywróć poprawną wartość. Wybierz 10, potem 11 języków. | Braki blokują zapis, 10 przechodzi, 11 daje błąd języków. Po odświeżeniu ostatni poprawny zapis. |
| 3-F | A | Zapisuj kolejno 17, 18, 120, 121 i 18.5, pozostałe pola poprawne. | Tylko 18 i 120 przechodzą. Po ostatniej próbie odświeżenie pokazuje 120. |
| 3-G | A | Nazwa kolejno 1, 2, 60 i 61 liter a, bio 1000 i 1001 liter b. Wklej także „<Mira>” do nazwy, potem do opisu przy poprawnej nazwie. | 2 i 60 oraz 1000 przechodzą. maxlength ogranicza wklejanie, sprawdź faktyczną długość. API odrzuca nadmiar, nawiasy i za krótką nazwę. Przywróć Mira2. |
| 3-H | A bez kontaktów | Wpisz tylko kontakt@example.test, zapisz kontakty i odśwież. Zastąp go samym @mira_3, następnie samym +48123456789. | Za każdym razem jeden kontakt, login bez zmian, gotowość true, nazwa Instagram bez @. |
| 3-I | A | Osobno próbuj adresu bez @, Instagramu @@mira, adresu URL i 31 liter, WhatsApp 48123, +01, +1 i numeru ze spacją. Spróbuj też @a, 30 liter, +12 i +123456789012345. | Pierwsza grupa odrzucona lub ograniczona przez pole. Poprawne granice przechodzą. Przy błędzie serwera szkic pozostaje, po odświeżeniu wraca ostatni zapis. |
| 3-J | A | Wypełnij wszystkie trzy jak w H, zapisz. Zmień telefon, zapisz. Usuń Instagram i zapisz. | Nowy telefon i ten sam e-mail zachowane, Instagram pusty. |
| 3-K | A | Wyczyść pozostałe kontakty, zapisz, odśwież. Potem ponownie zapisz wszystkie trzy do prób prywatności. | Po usunięciu true/false/false, profil publiczny dostępny. Po dodaniu true/true/true. |
| 3-L | A, dwie karty konta z tą samą wersją | W pierwszej zapisz bio „Pierwsza”, w drugiej szkic „Druga”. Zapisz w drugiej, potem kliknij przeładowanie przy błędzie. | 409 PROFILE_VERSION_CONFLICT, szkic widoczny do kliknięcia. Potem „Pierwsza” i możliwość ponownej edycji. |
| 3-L2 | A, ponownie otwarte dwie karty | W pierwszej zmień Instagram, w drugiej zmień tylko telefon i zapisz kontakty. Odśwież pierwszą. | Ostatni PUT wygrywa dla całego zestawu, także starsza wartość Instagramu. Brak kontroli wersji kontaktów. |
| 3-M | B, adres publiczny A | Wejdź na profil A, potem /account i /api/v1/me. Edytuj własny profil B i ponownie obejrzyj A. | B widzi publiczne pola A, edytor oraz prywatna odpowiedź dotyczą B, dane A pozostają takie same. |
| 3-N | G | Otwórz adres A w PL i z prefiksem /en, potem /api/v1/me. | Oba profile dostępne bez logowania. Prywatny endpoint 401. |

<a id="odbior-uzytkownika-3o"></a>

### Od 3-O — odbiór użytkownika

Kontynuuj na swoim dotychczasowym koncie testowym A. Masz już zapisany profil, kontakty oraz sprawdzony widok innego użytkownika i gościa. Do dalszej części wystarczy zwykła przeglądarka. Zacznij na [stronie konta](http://localhost:3000/account). Jeśli pojawi się ekran logowania, zaloguj się dotychczasową metodą.

#### 3-O. Chcę wiedzieć, komu podaję swój kontakt

1. Na stronie konta znajdź nagłówki **Profil publiczny** i **Dane kontaktowe**. Przeczytaj krótkie opisy pod nimi. Wyobraź sobie, że chcesz zostawić numer dla przyszłego współtowarzysza. Czy z tego ekranu umiesz powiedzieć, gdzie go wpisać i kto go zobaczy?
2. W części **Dane kontaktowe** odszukaj zapisany wcześniej WhatsApp i Instagram. Sprawdź, czy potrafisz rozpoznać numer z prefiksem kraju i nazwę użytkownika Instagrama. Ilekroć chcesz zmienić tylko kontakt, służy do tego przycisk **Zapisz kontakty**.
3. Jeśli masz już testową wyprawę i zaakceptowane zgłoszenie z udziałem dwóch Twoich kont, możesz od razu zobaczyć kontakt w użyciu. Zaloguj się jako zaakceptowany uczestnik, otwórz [Moje zgłoszenia](http://localhost:3000/my-applications), znajdź odpowiednią wyprawę i kliknij **Pokaż kontakt**. Porównaj wyświetlone dane z kontaktami zapisanymi przez organizatora. Kliknij **Ukryj kontakt**. Bez gotowej wyprawy przejdź od razu do 3-P. Pełny przebieg zgłoszenia należy do odbioru etapu 5.

**Oczekiwany efekt:** potrafisz wskazać pola publiczne i miejsce na prywatny kontakt. Z opisu powinno wynikać, że kontakt służy organizatorowi i zaakceptowanej osobie na wspólnej wyprawie. W szczegółach zgłoszenia aplikacja pokazuje zapisane dane jako tekst do odczytania lub skopiowania. Jeśli np. słowo „docelowo” pozostawia wątpliwość, zapisz ją jako uwagę do 3-O.

#### 3-P. Chcę korzystać z tego samego profilu po angielsku

1. Wróć na stronę konta A. Zapamiętaj zapisany Instagram. W górnym menu kliknij **PL**, potem **EN**.
2. Znajdź **Contact details**. W polu **Instagram username** wpisz na próbę `@@mira` i kliknij **Save contacts**. Przeczytaj komunikat. Oceń, czy po angielsku rozumiesz, które pole poprawić.
3. Przywróć wcześniej zapisany Instagram i kliknij **Save contacts**. Oczekuj **Saved.** Kliknij **View public profile**. Sprawdź, czy widzisz swój pseudonim i opis oraz angielskie nazwy języków i stylów.
4. Kliknij **EN**, potem **PL**. Pseudonim i własny opis powinny pozostać takie same, a etykiety wrócić do polskiego.

#### 3-Q. Chcę wygodnie poprawić opis na małym ekranie

1. Jeśli masz już dostęp do lokalnej aplikacji na telefonie, użyj go. W przeciwnym razie otwórz stronę konta A na Macu i zwęź okno, przeciągając jego prawą krawędź w lewo. W uwagach podaj „telefon” lub „wąskie okno na Macu”.
2. Przewiń formularz do **Bio**. Dopisz w nowym wierszu „Lubię krótkie wyprawy”. Kliknij **Zapisz profil**. Sprawdź, czy widzisz potwierdzenie i czy po odświeżeniu zdanie pozostało w polu.
3. Przewiń do ostatniego pola kontaktowego i dotknij go lub kliknij. Sprawdź, czy możesz przeczytać cały numer, dotrzeć do **Zapisz kontakty** i wrócić do góry strony. Na telefonie sprawdź to także po otwarciu klawiatury.

**Oczekiwany efekt:** pola i przyciski mieszczą się na ekranie, etykiety da się odczytać, a oba przyciski zapisu łatwo odróżnić. Jeśli coś znika lub wymaga przewijania w bok, podaj nazwę pola i urządzenie.

#### 3-R. Chcę wrócić do swojego profilu inną metodą logowania

1. Użyj testowego konta z hasłem i połączonym Google z odbioru 2B. Otwórz stronę konta, zapamiętaj pseudonim i jeden kontakt. Jeśli konto ma dopiero zostać połączone, w **Metody logowania** wybierz **Połącz konto z Google** i wskaż swoje testowe konto Google. Jeśli Google jest lokalnie niedostępne, zapisz „3-R pominięty – brak Google”.
2. Kliknij **Wyloguj**, przejdź do logowania i wybierz **Kontynuuj z Google**. Wybierz dokładnie to samo konto Google. Na stronie konta odszukaj zapamiętany pseudonim i kontakt.
3. Dopisz w bio słowo „Weekend”, kliknij **Zapisz profil** i poczekaj na potwierdzenie. Wyloguj się. Tym razem wpisz e-mail logowania i hasło tego konta. Otwórz profil.

**Oczekiwany efekt:** po obu sposobach wejścia rozpoznajesz ten sam profil i kontakt. Po wejściu hasłem widzisz również słowo „Weekend”.

**3-R2 — jeśli masz osobne testowe konto wyłącznie Google.** Wejdź przez Google tym drugim kontem. Jeśli to jego pierwsza wizyta, zaakceptuj regulamin. Na stronie konta oczekuj pustego formularza przy pierwszej wizycie albo wcześniej zapisanych danych przy kolejnej. Uzupełnij brakujące pola profilu (np. Mira Google, 29, polski, trekking), kliknij **Zapisz profil**, a w kontaktach zapisz Instagram `mira_google_3`. Wyloguj się i ponownie wybierz to samo Google. Oczekuj własnego pseudonimu i zapisanego Instagramu. Bez osobnego konta Google odnotuj pominięcie tej próby.

**Tu kończy się odbiór autora.** Przekaż uwagi, np. „3-Q, wąskie okno, po wpisaniu bio nie widzę przycisku zapisu”. Jeśli wszystkie wykonane próby odpowiadają oczekiwaniom, możesz potwierdzić odbiór i wymienić pominięte próby.

### Weryfikacja techniczna po stronie Codex

Zakres obejmuje publiczne API i HTML, uprawnienia do kontaktów, nieaktywne konta, ochronę zapisu i walidację żądań. Wyniki wykonanych sprawdzeń zawiera sekcja „Wyniki automatyczne z 26 listopada”. Zmiana instrukcji nie stanowi nowego przebiegu testów. Dotychczasowe techniczne scenariusze 3-S–3-W są wyłączone z listy czynności autora.

## Etap 4 — publikacja i wyszukiwanie wypraw

**Oczekuje na ręczny odbiór autora.** Podstawa oceny: PLAN, etap 4, decyzje o migracji, cyklu życia, edytorze i wyszukiwaniu oraz poprawki prezentacji z odbioru etapu 1. Poniższe oceny dotyczą aktualnego kodu. Wyniki wykonanych prób technicznych zapisano osobno.

| Wymaganie | Ocena | Ustalenie |
| --- | --- | --- |
| Szkic, pola, zapis i ponowne otwarcie | Zaimplementowane | Samo otwarcie edytora nie tworzy wpisu. Pierwszy zapis nadaje identyfikator. Szkic może być pusty, lecz podane wartości podlegają walidacji. Reguły poszczególnych pól poniżej. |
| Kraje, miasta i kolejność | Zaimplementowane | Do 50 przystanków, każdy z krajem wybranym ze słownika; miasto opcjonalne. Można powtarzać kraje, usuwać i przesuwać wiersze. Słownik PL/EN obsługuje nazwy, kody i aliasy oraz klawiaturę. |
| Terminy, budżet i liczba uczestników | Zaimplementowane | Daty włącznie, dodatkowa długość dla elastycznego okna, PLN na osobę, cel łącznie z organizatorem. Brak reguły zabraniającej dat przeszłych. |
| Publikacja, kompletność konta i edycja otwartej wyprawy | Zaimplementowane | Wymagane kompletne ogłoszenie, zapisany profil i dowolny kontakt. Odmowa z powodu konta zachowuje szkic. Domyślne limity to 3 publikacje na dzień UTC i 5 otwartych wypraw. Po pierwszej akceptacji dodatkowe ograniczenia krajów, dat, budżetu i liczby osób należą do etapu 5. |
| Konflikt wersji i zamknięcie | Zaimplementowane | Starszy zapis daje 409, formularz zachowuje wpisy do jawnego wczytania. Potwierdzenie zamknięcia dotyczy zapisanej wersji, pomija niezapisane zmiany. Brak ponownego otwarcia. |
| Moje wyprawy, działania, trwałość, właściciel | Zaimplementowane | Jedna lista własnych wpisów z etykietą stanu. Szkic ma edycję, otwarta dodatkowo szczegóły i zamknięcie, zamknięta podgląd i szczegóły. Odczyt po odświeżeniu pochodzi z bazy. |
| Lista publiczna, widoczność i prywatność | Zaimplementowane | Na liście tylko OPEN/VISIBLE z aktywnym organizatorem. Szczegóły także dla CLOSED. Publiczny DTO zawiera publiczny profil organizatora, prywatne pola kontaktów i login są wyłączone. Prywatny edytor ładuje się w przeglądarce. |
| Filtry pojedyncze i łączone, granice | Zaimplementowane | Kraj wystarczy na jednym przystanku, styl na liście stylów. Wszystkie aktywne warunki muszą być spełnione. Daty i kwoty dopasowuje przecięcie przedziałów łącznie z końcami. Dla elastycznych dat przeszukiwane jest całe okno. |
| Wiele lokalizacji, brak duplikatów, kolejność i strony | Zaimplementowane | Wyszukiwanie kraju przez EXISTS daje jeden wpis na wyprawę. Od najnowszej publikacji; przy remisie malejący UUID. Strony od 1, domyślnie po 20, API dopuszcza 1–50. |
| Panel, adres wyników i czyszczenie | Zaimplementowane | Panel startuje zwinięty, po zastosowaniu zwija się i pokazuje liczbę aktywnych pól bez rozmiaru strony. Parametry i strona są w adresie. Ponowne zastosowanie wraca na pierwszą stronę. |
| Kraje i kontynenty | Częściowo zaimplementowane na szczegółach | Karta grupuje kraje i kontynenty w kolejności pierwszego wystąpienia. Szczegóły pokazują kraje i miasta po kolei, bez kontynentów. |
| Kompletność szczegółów, dostępne miejsca i status | Częściowo zaimplementowane | Widoczne tytuł, plan, trasa, daty, informacja o elastyczności, budżet, stan i liczba zajętych miejsc względem celu. Brak osobnej liczby wolnych miejsc, „Kogo szukam”, stylów, nazwy języka treści i liczby dni elastycznego wyjazdu. Pełna otwarta wyprawa ma specjalną etykietę. |
| Przejście ze szczegółów do publicznego profilu organizatora | Niezaimplementowane w interfejsie szczegółów | API podaje publiczny profil, jeśli istnieje. Lista uczestników wypisuje nazwy bez linków i wyróżnienia roli organizatora. Profil podróżnika jako osobna strona działa od etapu 3. |
| Adresy, przekierowania, SSR i SEO | Zaimplementowane | Publiczne szczegóły z tytułem w adresie; stary adres przekierowuje po sprawdzeniu widoczności. SSR, title, description, canonical i Open Graph. Listy z filtrami mają noindex,follow. |

### Sprawdzenia techniczne — 28 listopada 2026

Uruchomiono aktualne źródła w kopii tymczasowej, na lokalnym PostgreSQL 17.6. Nowa baza `travel_buddy_acceptance4_1128` służyła backendowi testowemu i osobnemu schematowi `ui4` dla testów przeglądarkowych. Nuxt działał na 3197, Quarkus na 8097. Test migracji dodatkowo utworzył i zachował dwie własne bazy.

| Sprawdzenie | Wynik | Co potwierdzono |
| --- | --- | --- |
| Maven, TripResourceTest, TripSearchTest, TripLifecycleTest, TripMigrationTest | 6/6 (2 + 1 + 2 + 1), zero pominięć | Widoczność i własność, cykl życia, konflikt wersji, odmowy publikacji, zakresy i kolejność wyników bez duplikatów, niepoprawne filtry, migracja od zera i z V2 z zachowaniem demonstracyjnej wyprawy. |
| Nuxt | typecheck i build poprawne | Sprawdzenie typów i kompilacja bieżących źródeł. |
| Playwright, search.spec.ts i trips.spec.ts | Pierwszy przebieg 4/5, powtórka 5/5 w 53,4 s | Pierwsza rejestracja PL przekroczyła 5 s oczekiwania na formularz profilu. Przy powtórzeniu obu zestawów bez zmian asercji przeszły PL i EN, szkice, publikacja, konflikt, filtry, aliasy kraju, menu języka, adresy, zamknięcie i dostęp do zamkniętej strony. |
| SSR | 4/4 | Skrypt check-ssr z podmienionym w kopii portem potwierdził widoczny tekst stron listy i szczegółów PL/EN po usunięciu skryptów. Test wyszukiwarki osobno sprawdził metadane i przekierowania. |
| Dodatkowa próba edycji OPEN i prywatności | Poprawna | Po zmianie tytułu dawny adres dał 301 do nowego. W anonimowych odpowiedziach listy i szczegółów, zarówno HTML jak i JSON, był nowy tytuł, a nie było żadnej z czterech zapisanych wartości kontrolnych (login, e-mail kontaktowy, Instagram, WhatsApp). |
| Chromium, wąski ekran | 4/4 (320 i 390 px × PL i EN) | Sprawdzono widoczność nazw trasy, otwarcie i zwinięcie panelu, przejście do szczegółów i szerokość dokumentu. Wygoda edytora i klawiatury na fizycznym telefonie pozostaje do oceny użytkownika. |

Odtworzone poprawki UI etapu 1 obejmują kraje, kontynenty, aliasy, filtry i język. Historyczny test pierwszego odczytu po restarcie PostgreSQL jest w wynikach etapu 1 z 19 listopada. W tym odbiorze nie powtarzano restartu wspólnego serwera bazy. Oceny limitów tekstu, 50 przystanków i limitów publikacji wynikają z kodu; zestaw E2E nie wyczerpuje wszystkich kombinacji tych granic.

### Instrukcja dla autora — etap 4

#### 4-00. Zaczynam z nowymi kontami

Ten odbiór działa pod **http://127.0.0.1:3104**. Zaczynasz od nowej lokalnej bazy `travel_buddy_manual_4_20261128` z dwiema przykładowymi publicznymi wyprawami. Wszystkie kolejne kroki wykonaj pod tym samym adresem.

1. **Organizator A — zwykłe okno Chrome.** Otwórz [rejestrację](http://127.0.0.1:3104/register). Wpisz `etap4-organizator@example.test`, wybierz lokalne hasło testowe (co najmniej 12 znaków), powtórz je i zaznacz akceptację regulaminu. Wyślij formularz. Po rejestracji zobaczysz stronę konta z tym adresem e-mail. Zapisz login i hasło w swojej prywatnej notatce na czas tego odbioru.
2. W części **Profil publiczny** wpisz „Mira Etap 4”, wiek 30, wybierz polski i trekking. Kliknij **Zapisz profil**. W części **Dane kontaktowe** wpisz e-mail kontaktowy `mira-kontakt4@example.test` i kliknij **Zapisz kontakty**. Odśwież stronę i sprawdź, czy widzisz nazwę i kontakt.
3. **Drugi użytkownik B — osobna przeglądarka, np. Safari.** Otwórz [rejestrację](http://127.0.0.1:3104/register) i wykonaj krok 1 z adresem `etap4-podroznik@example.test` i własnym hasłem testowym. Zapisz je w notatce. Zostaw profil i kontakty B puste. Przydadzą się w 4-05. Pozostaw tę przeglądarkę otwartą.
4. **Gość — okno incognito Chrome.** Otwórz [publiczną listę](http://127.0.0.1:3104/pl/wyprawy). Oczekuj opcji logowania i dwóch wypraw demonstracyjnych. Pozostaw to okno bez logowania.
5. Wróć do zwykłego okna A i otwórz [Moje wyprawy](http://127.0.0.1:3104/my-trips). Oczekuj „Nie masz jeszcze wypraw”. Zacznij 4-01. Przy wznowieniu po przerwie otwórz [logowanie](http://127.0.0.1:3104/login) w odpowiednim oknie i użyj danych zapisanych w tej próbie.

#### Twój odbiór UX — jedna podróż od szkicu do zamknięcia

Po 4-00 możesz wykonać poniższą ścieżkę w swoim tempie. Szczegółowa lista pod spodem służy do odtworzenia konkretnego przypadku, gdy zechcesz mu się przyjrzeć.

1. **4-01–4-03, tworzę plan.** Jako A wybierz **Moje wyprawy → Utwórz wyprawę**. Nazwij ją „Tatry z Mirą”, kliknij **Zapisz szkic**, wróć do listy i otwórz **Edytuj wyprawę**. Wpisz plan „Spacery po Tatrach” i „Szukam osób lubiących spokojne tempo”. Dodaj Polskę z miastem Zakopane oraz Słowację bez miasta, zmień ich kolejność. Wybierz polski, trekking, termin elastyczny 10–14 czerwca 2027, długość 3 dni, 4 osoby i budżet 100–500 PLN. Zapisz szkic. Czy pola pojawiały się w kolejności, w której naturalnie planujesz wyjazd? Czy było jasne, że 4 osoby obejmują Ciebie?
2. **4-04–4-05, publikuję.** Kliknij **Opublikuj**, otwórz **Publiczne szczegóły** i skopiuj adres. Wróć do **Moje wyprawy → Edytuj wyprawę**, zmień tytuł na „Tatry z Mirą – wspólne szlaki” i kliknij **Zapisz zmiany**. Po czym poznajesz różnicę między zapisanym szkicem a ogłoszeniem widocznym dla innych?
3. **4-06–4-08, szukam jako podróżnik.** W oknie gościa wejdź na [listę wypraw](http://127.0.0.1:3104/pl/wyprawy). Kliknij **Filtry**, wybierz Słowację, wpisz budżet minimum 500 i zastosuj. Otwórz swoją wyprawę. Czy rozumiesz, dlaczego wynik pasuje? Czy szczegóły wystarczają do decyzji o wyjeździe? Jeśli chcesz, obejrzyj tę samą stronę po angielsku lub zwęź okno i zanotuj, co trudniej znaleźć.
4. **4-09, kończę zapisy.** Jako A kliknij **Zamknij wyprawę**, przeczytaj komunikat, anuluj, a następnie zamknij swoją wyprawę testową. Otwórz zapisany adres jako gość. Czy przed potwierdzeniem było jasne, co stanie się z ogłoszeniem i linkiem?

**Tu możesz zakończyć.** Przy uwadze wystarczy np. „4-03, przy wyborze elastycznego terminu nie rozumiem różnicy między końcem okna a długością wyjazdu”. Możesz też wskazać rozwiązanie, które Ci odpowiada i warto zachować.

<details>
<summary>Szczegółowe kroki i przypadki graniczne 4-01–4-09 (do wykorzystania w razie potrzeby)</summary>

#### 4-01. Zostawiam pomysł na później

1. Kliknij **Utwórz wyprawę**, wróć do „Moje wyprawy” bez zapisu. Oczekuj dotychczasowej listy. Otwórz ponownie edytor i kliknij **Zapisz szkic** bez wypełniania pól.
2. Na liście odszukaj **Szkic bez tytułu** ze statusem **Szkic**. Kliknij **Edytuj wyprawę**, wpisz tytuł „Tatry z Mirą – próba 4”, zapisz i odśwież. Zapamiętaj adres edytora. Oczekuj jednego szkicu z nową nazwą.
3. Kliknij **Opublikuj** z samym tytułem. Sprawdź, czy formularz wskazuje brakujące pola. Wróć do listy i otwórz szkic. Nadal powinien być prywatny i zachować tytuł.

#### 4-02. Układam trasę i sprawdzam tekst ogłoszenia

1. Wpisz w **Plan podróży** „Dwa spacery po obu stronach Tatr”, w **Kogo szukam** „Osoby lubiące spokojne tempo”. Wybierz **Język treści** polski i styl trekkingowy. Do każdego z trzech pól tekstowych kolejno dopisz znak <, kliknij **Zapisz szkic**, odczytaj błąd i usuń znak. Wróć do poprawnych wartości.
2. Kliknij **Dodaj lokalizację**. Spróbuj zapisać pusty przystanek – powinien pojawić się błąd. W polu kraju wpisz „Pols” i kliknij **Polska** na liście. Wpisz miasto „Zakopane”. Dodaj „Polska — Kraków” i „Słowacja” z pustym miastem. W każdym wierszu zatwierdź kraj kliknięciem sugestii. Dowolny tekst bez wyboru kraju nie wystarcza.
3. Przy Słowacji kliknij **W górę**. Dodaj czwarty przystanek, usuń go przez **Usuń lokalizację**, zapisz i odśwież. Oczekuj trasy Polska — Zakopane, Słowacja, Polska — Kraków. Pierwszy wiersz nie może iść wyżej, ostatni niżej. Powtarzający się kraj i puste miasto są poprawne.
4. **Granice pól.** Przy zapisie szkicu tekst z samych spacji ma zachować się jak pusty. Pola przy wklejaniu ograniczają tytuł do 120, plan do 10 000, „Kogo szukam” do 2000 i miasto do 120 znaków. Możesz przygotować dłuższy tekst przez kopiowanie w edytorze tekstowym, wkleić go i sprawdzić, czy nadmiar został obcięty. W mieście sprawdź też „<Kraków>” i przywróć „Kraków”. Jeden znak zwykłego tekstu jest dopuszczalny. Maksymalnie 50 przystanków, potem **Dodaj lokalizację** jest nieaktywne. Do dalszych prób pozostaw trzy przystanki i początkowe teksty.

#### 4-03. Ustalam daty, koszty i wielkość grupy

1. Wybierz **Dokładny**, ustaw obie daty na 10 czerwca 2027. Zapisz szkic – jeden dzień jest poprawny. Zmień koniec na 9 czerwca, zapisz – oczekuj błędu. Ustaw koniec na 14 czerwca.
2. Wybierz **Elastyczny — okno dat**. Dla 10–14 czerwca wpisuj w **Długość wyjazdu (dni)** kolejno 0, 1, 5 i 6. Po każdej zmianie kliknij **Zapisz szkic**. 1 i 5 przechodzą, 0 i 6 wymagają poprawy. Ułamek jest błędny. Zostaw 3. Pole długości jest obowiązkowe przy publikacji elastycznego terminu, znika przy dokładnym. Serwerowy limit to 32 767 dni, także w szerokim oknie.
3. W **Docelowa liczba osób** wypróbuj kolejno 1, 2, 50, 51 i 2,5. Po każdej próbie zapisz szkic. Poprawne skrajne wartości to 2 i 50. Zostaw 4, wliczając siebie.
4. W obu polach budżetu wpisz 0 i zapisz. Potem ustaw od 500 do 100, spróbuj zapisać. Oczekuj błędu kolejności. Spróbuj ujemnej kwoty, trzech miejsc po przecinku i 100 000 000. Maksimum to 99 999 999,99, można podać tę samą wartość w obu polach. Wpisuj bez odstępów, zgodnie z separatorem obsługiwanym przez przeglądarkę. Na końcu zapisz **100–500 PLN na osobę**.
5. Mając cały szkic, usuwaj po jednym: tytuł, plan, „Kogo szukam”, język, wszystkie style, rodzaj terminu, początek, koniec, długość elastyczną, liczbę osób i każdą z kwot. Za każdym razem kliknij **Opublikuj** i przywróć pole przed następną próbą. Oczekuj wskazania braku i statusu szkicu. Brak całej trasy był sygnalizowany w 4-01. Zakończ przyciskiem **Zapisz szkic**.

#### 4-04. Rozpoznaję własny szkic i rozstrzygam równoczesną edycję

1. W oknie B wejdź do „Moje wyprawy”. Szkicu A nie powinno tu być. Otwórz w B zapisany adres edytora A. Oczekuj komunikatu o braku wyprawy i braku możliwości zmiany jej pól. W oknie gościa otwórz [listę publiczną](http://127.0.0.1:3104/pl/wyprawy) – szkicu tam nie ma.
2. W A otwórz ten sam szkic w dwóch kartach. W pierwszej zapisz plan „Wersja poranna”. W drugiej wpisz „Wersja wieczorna” i kliknij **Zapisz szkic**. Oczekuj informacji o nowszej wersji i zachowanego tekstu „Wersja wieczorna”.
3. W drugiej karcie kliknij **Wczytaj aktualną wersję**. Powinien wrócić plan „Wersja poranna”. Wpisz ponownie początkowy plan z 4-02 i zapisz. Zamknij pierwszą kartę.

#### 4-05. Publikuję i aktualizuję ogłoszenie

1. Z kompletnym profilem i kontaktem A kliknij **Opublikuj**. W „Moje wyprawy” oczekuj jednej **Otwartej** wyprawy, akcji **Edytuj wyprawę**, **Publiczne szczegóły** i **Zamknij wyprawę**. Kliknij szczegóły i skopiuj adres na później.
2. Wróć do edycji. Usuń „Kogo szukam”, kliknij **Zapisz zmiany**. Otwarta wyprawa wymaga kompletu. Przywróć tekst, zmień tytuł na „Tatry z Mirą – wspólne szlaki” i kliknij **Zapisz zmiany**. Otwórz wcześniejszy publiczny adres w oknie gościa. Oczekuj nowego tytułu i odpowiadającej mu końcówki adresu.
3. **Wariant z kontem testowym bez profilu lub kontaktu.** W przeglądarce B, na pustym koncie przygotowanym w 4-00, utwórz jego własny kompletny szkic według 4-02 i 4-03. Kliknij **Opublikuj**. Bez profilu oczekuj prośby o uzupełnienie profilu. Skorzystaj z linku do konta, zapisz profil, wróć do tego samego szkicu. Bez kontaktu oczekuj prośby o prywatny kontakt. Dodaj kontakt na koncie i wróć przez „Moje wyprawy”. Szkic powinien być jeden i gotowy do publikacji. Pozostaw wyprawę B jako szkic i wróć do okna A. Limit publikacji jest powodem do odnotowania komunikatu, bez zamykania istniejących wypraw autora.

#### 4-06. Czy jako gość wiem, dokąd jadę i ile jest miejsc?

1. W oknie gościa otwórz listę i kliknij nowy tytuł A. Sprawdź tytuł, **Otwarta**, kolejność trzech miejsc, obie daty, informację o elastyczności, budżet na osobę i plan. W uczestnikach oczekuj **1 / 4** dla wyprawy bez zgłoszeń (trzy miejsca wolne).
2. Porównaj kartę listy ze szczegółami. Na karcie powinno być **Polska, Słowacja · Europa**, na szczegółach ponownie Polska przy Krakowie. Spróbuj dowiedzieć się, kogo szuka autor, ile dni potrwa wyjazd i jak obejrzeć jego profil. Obecny ekran pomija te informacje lub przejście do profilu. Zapisz, czego jako podróżnik potrzebujesz. Wariant historycznej wyprawy demonstracyjnej może nie mieć profilu organizatora.

#### 4-07. Szukam wyprawy po kraju, terminie, cenie i stylu

1. W oknie gościa otwórz /pl/wyprawy, kliknij **Filtry**, zwiń i otwórz ponownie. Wybierz Polskę z podpowiedzi i kliknij **Zastosuj filtry**. Panel się zwija. Wyprawa A powinna wystąpić raz mimo dwóch polskich przystanków. Powtórz ze Słowacją, potem z Portugalią – tylko w ostatniej próbie wyprawa A ma zniknąć.
2. Po każdej kolejnej próbie klikaj **Filtry → Wyczyść filtry**. Sprawdź pojedynczo: termin od 14 czerwca 2027 (jest) i od 15 czerwca (brak); budżet minimum 500 (jest) i 500,01 (brak); maksimum 100 (jest) i 99,99 (brak); styl trekkingowy (jest) i inny niezaznaczony styl (brak). W próbie dat ustaw też sam termin do 10 czerwca (jest), następnie do 9 czerwca (brak). „Brak” dotyczy tytułu A, inne wyniki zależą od lokalnych danych.
3. Po wyczyszczeniu ustaw jednocześnie Słowację, 14–16 czerwca, budżet 500–600 i trekking. Zastosuj. Oczekuj tytułu A i licznika sześciu użytych pól na przycisku. Skopiuj adres do nowej karty, odśwież i rozwiń panel. Oczekuj tych samych wyników i ustawień. Zmień tylko styl na inny – A znika. Spróbuj odwróconego budżetu lub dat, zastosuj, następnie popraw lub wyczyść filtry. Oczekuj błędu i możliwości powrotu do wyników.
4. Wyczyść filtry. Przy co najmniej dwóch otwartych wyprawach ustaw **Wyników na stronie → 1**, zastosuj. Pierwsza strona powinna pokazać najnowszą publikację. Kliknij **Następna**, potem **Poprzednia**. Tytuły powinny się zmienić i wrócić. Na drugiej stronie zastosuj nowy filtr – numer powinien wrócić do 1. Wyczyść filtry i sprawdź powrót do domyślnych 20 wyników na stronę.

#### 4-08. Używam listy po angielsku i na małym ekranie

1. Na polskiej liście wybierz kraj, wpisując „Anglia”. Kliknij sugestię **Wielka Brytania**, zastosuj. Kliknij **PL → EN**. Otwórz **Filters** i sprawdź **United Kingdom**. Wyszukaj także „England” i zatwierdź sugestię strzałką w dół oraz Enter. Wyczyść filtry. Otwórz wyprawę A. Etykiety i nazwy krajów mają być angielskie, własny tytuł i plan mają pozostać po polsku.
2. Na telefonie z dostępem do lokalnej aplikacji lub w zwężonym oknie Maca otwórz listę. Rozwiń filtry, wybierz kraj, przewiń do zastosowania, otwórz wynik i przejrzyj trasę. Na koncie A otwórz też edytor i przejdź do ostatniego pola budżetu. Sprawdź, czy przyciski, menu języka, komunikaty i selektor kraju mieszczą się na ekranie i pozostają dostępne. Zapisz urządzenie lub „wąskie okno”. Wróć do PL przed kolejną próbą.

#### 4-09. Kończę zapisy, zachowując publiczny link

1. W A otwórz edytor testowej wyprawy, zapamiętaj zapisany plan, dopisz do niego „niezapisane”. Kliknij **Zamknij wyprawę**, najpierw anuluj. Wyprawa ma pozostać otwarta, wpis nadal w formularzu.
2. Kliknij ponownie i potwierdź **wyłącznie dla tej testowej wyprawy**. Odśwież „Moje wyprawy”. Oczekuj **Zamknięta**, **Zobacz wyprawę** i **Publiczne szczegóły**. W podglądzie pola są zablokowane, plan zawiera ostatnią zapisaną wersję. Brak operacji ponownego otwarcia.
3. W oknie gościa odśwież listę bez filtrów, a potem otwórz skopiowany wcześniej adres szczegółów. Wyprawa znika z listy, pod adresem pozostają tytuł i oznaczenie **Zamknięta**.

**Tutaj zatrzymaj odbiór.** Podaj ID próby, wykonaną czynność, widoczny wynik i oczekiwanie albo potwierdź odbiór bez uwag. Zgłoszenia i udostępnianie kontaktów sprawdzimy po osobnej decyzji o rozpoczęciu etapu 5.

</details>
