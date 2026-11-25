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

**Ręczny odbiór przez autora: oczekujący.** Etap 2A odebrany. Podstawa analizy: PLAN, etap 2; DECISIONS, etap 2B; otwarty test rzeczywistego Google w STATUS. Na wejściu HEAD cb7b265 i lokalne zmiany odbioru 2A w dokumentacji, formularzu, tłumaczeniach i teście auth. W tym odbiorze zmieniono tylko dokumentację.

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

**Uwagi autora i ostateczna decyzja 2B:** oczekujące.

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
