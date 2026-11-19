# Status projektu

## Aktualny stan — 2026-11-15

Praca wyłącznie lokalna. Poniższe podsumowanie zastępuje starsze wpisy
„w toku” i „następny krok”; historia wyników pozostaje poniżej.

**Ukończone implementacje i zapisane dowody:**

| Zakres | Ostatnie potwierdzenie |
| --- | --- |
| Etapy 1, 2A, 2B, 3: SSR PL/EN, konta, OIDC, profile i kontakty | Regresja w 39/39 backendu etapu 4; w tym 9 testów kontrolowanego OIDC i 6 profili. SSR 4/4 i E2E auth/profili zaliczone. |
| Etap 4: publikacja, wyszukiwanie, paginacja, slugi i podstawowe SEO | 2026-11-14: backend 39/39, pełne ówczesne E2E 6/6, SSR 4/4, proxy, typecheck i build. |
| Etap 5: zgłoszenia, decyzje, uczestnicy i kontakty po akceptacji | 2026-11-14: backend 44/44, nowe E2E PL/EN 2/2, typecheck i build. |
| Etap 6: raporty, ADMIN, audyt, ukrywanie i blokowanie | 2026-11-15: 16/16 testów dotkniętych modułów, E2E moderacji 1/1 (także anonimowy formularz EN), typecheck i build. |

Wyniki powyżej pochodzą z wcześniejszych przebiegów. Przy tej aktualizacji
dokumentacji nie uruchamiano testów aplikacji ponownie. Etap 6 nadal
zawiera zmiany staged i unstaged; zachowano je bez commita/pushu.

**Niezakończone i miejsce wznowienia:**

- Nie ma przerwanej implementacji etapów 4–6. Etap 7 nie został rozpoczęty.
- Ujawniona luka UX: brak klikalnych profili przy uczestnikach i zgłoszeniach.
  Ewentualną poprawkę zacząć od frontend/app/components/ParticipantsList.vue
  (person.userId) oraz ApplicationsPanel.vue (applicantId/organizerId).
  Profil istnieje pod /profiles/{userId}; publiczne szczegóły wyprawy są
  w frontend/app/pages/trip-detail/[id]/[[slug]].vue.
- Organizator nie może usunąć zaakceptowanego uczestnika; wycofuje się
  sam uczestnik. Panel ADMIN blokuje konto z raportu profilu, bez katalogu
  kont. Rozszerzenia tych zachowań wymagają osobnego ustalenia zakresu.
- Integracja rzeczywistego Turnstile istnieje, lecz testowano tylko lokalny
  symulator. Powiadomienia moderacji nie mają trwałego retry po awarii SMTP.

**Pozostałe ręczne sprawdzenia:**

- Prawdziwe Google OIDC, start ze świeżego checkoutu i utrzymanie aktywnej
  sesji po restarcie backendu — nadal brak zapisanego potwierdzenia.
- Odbiór UI PL/EN i na telefonie: publikacja, zgłoszenia A/B/C, kontakty,
  moderacja i blokada sesji; sprawdzenie wiadomości decyzji w Mailpit.
  Samych pytań o obsługę panelu nie traktujemy jako potwierdzonego odbioru.
- Rzeczywiste klucze Turnstile i Siteverify — brak testu z Cloudflare.

**Następne zadanie:** po ręcznym odbiorze etap 7. Przed nim warto uzgodnić
małą poprawkę linków do profili, aby zgłoszenie użytkownika nie wymagało
ręcznego składania URL. Nie dodawać samodzielnie usuwania uczestników.

Własne serwery testowe 8082/3100 zatrzymano po testach etapu 6; to zapis
historyczny, nie bieżący monitoring. Baza i wolumen zostały zachowane.

## Historia prac i wyników

Aktualizacja: 2026-11-14. Praca wyłącznie lokalna, bez wdrożenia.

## Ukończone

- Etap 1: Quarkus, PostgreSQL i Nuxt; publiczna lista i szczegóły wypraw
  z bazy, SSR oraz interfejs PL/EN.
- Etap 2A: lokalne konta, sesje, CSRF, limity prób i reset hasła przez
  lokalny Mailpit.
- Etap 2B: Google OIDC, tworzenie sesji aplikacji, kontrola konfliktów
  kont i akceptacji regulaminu. Integracja sprawdzona testowym dostawcą;
  prawdziwe logowanie Google pozostaje osobnym sprawdzeniem ręcznym.
- Etap 3: migracja V5 dla profili i kontaktów, API `/me`, edycja profilu
  i kontaktów oraz publiczny profil. Walidacja, wersjonowanie i konflikt
  409, wyliczane flagi kompletności, osobne DTO publiczne i prywatne.
  Formularze PL/EN, publiczny SSR z noindex, brak kontaktów i e-maila
  konta w publicznym DTO i HTML. Dokumentacja API w
  [openapi-profiles.yaml](openapi-profiles.yaml).

## Wykonane sprawdzenia

Wyniki z 2026-11-13 po implementacji etapu 3; podczas aktualizacji
niniejszego dokumentu nie uruchamiano ponownie testów aplikacji.

| Sprawdzenie | Wynik |
| --- | --- |
| `./scripts/backend.sh test` — PostgreSQL 17.6 | 35/35, bez błędów i pominięć |
| Testy profili w powyższym zestawie | 6/6, w tym walidacja, prywatność DTO, CSRF, status konta i równoległy zapis |
| Kontrolowany OIDC w powyższym zestawie | 9/9, bez prawdziwego konta Google |
| `npm run typecheck` | zaliczone |
| `npm run build` | zaliczone |
| `npm run test:e2e` na zbudowanym Nuxtcie | 3/3 |
| `node scripts/check-ssr.mjs` | 4/4: lista i szczegóły wypraw PL/EN |
| `node scripts/check-google-proxy.mjs` | zaliczone: redirect, query, Cookie, Set-Cookie, Origin, no-store |
| `git diff --check` | bez błędów |

E2E obejmują auth i reset z Mailpit w PL/EN oraz profil i kontakty:
zapis, odświeżenie, usunięcie kontaktów, walidację, zachowanie szkicu po
409, dwa konta, anonimowy profil, przełącznik języka oraz kontrolę
publicznego HTML z payloadem Nuxta i jawnej listy pól API.

W trakcie prac poprawiono błędy pobierania użytkownika do blokady
transakcyjnej oraz kolejności ustawienia pól przed zapisem kontaktów.
Końcowe testy przechodzą. Równoległe uruchomienie Maven test i backendu
w trybie dev powodowało konflikt klas hot reload; README opisuje
uruchamianie tych sprawdzeń kolejno.

## Pozostałe sprawdzenia i ograniczenia

- Właściciel powinien wykonać checklistę prawdziwego Google OIDC z
  [README](../README.md). Nie potwierdzono jej wykonania w tej sesji.
- Ręczna akceptacja etapu 3: przejść scenariusz dwóch kont z README,
  szczególnie konflikt w dwóch kartach i publiczny widok bez kontaktów.
  Scenariusze te mają automatyczne pokrycie E2E, ale nie oznacza to
  ręcznej akceptacji przez właściciela.
- Kontakty nie są weryfikowane ani udostępniane innym użytkownikom.
  Regulamin pozostaje lokalnym szkicem. Brak testów wdrożenia/publicznego HTTPS.
- E2E pozostawiają developerskie konta i wiadomości Mailpit. Przed kolejnym
  pełnym przebiegiem należy zrestartować backend z uwagi na limiter w pamięci.
- Po weryfikacji 2026-11-13 zatrzymano uruchomiony backend i frontend;
  PostgreSQL i Mailpit pozostawiono uruchomione. To zapis stanu z końca
  testów, nie bieżący monitoring procesów.
- Nie wykonano commita ani pushu; dane i wolumeny nie były kasowane.

## Następny krok

Ręczna akceptacja etapu 3 według README, następnie osobne zadanie dla
etapu 4 z [PLAN.md](PLAN.md): publikacja i wyszukiwanie wypraw.
Zakres kolejnego zadania należy ustalić przed implementacją; przyszłe
etapy planu nie zostały oznaczone jako wykonane.

## Rozpoczęcie etapu 4 — 2026-11-14

Etap 4 NIE jest ukończony. Dodano addytywne migracje V6 (pola cyklu życia,
trasy i indeksy) oraz V7 w dev/test (jawny właściciel znanych demonstracyjnych
UUID bez możliwości logowania). Zachowano wcześniejsze zmiany dokumentacji.

Wykonano próbę SQL V1 → seed V2 → V3 → V6 w izolowanym schemacie PostgreSQL
w transakcji zakończonej ROLLBACK: zaliczona, w tym asercje zachowania UUID,
opisów i dat. To nie jest pełny test Flyway ani osobna baza. V7 nie zostało
jeszcze sprawdzone. `git diff --check` zaliczone przed dodaniem V7 i dokumentacji.
Nie zmieniano schematu aplikacji ani danych i wolumenu developerskiego.

Pozostaje cały cykl życia API, formularze, filtry, publiczna widoczność,
wersjonowanie zapisów, SSR ze slugami oraz wymagane testy integracyjne i E2E.
Nie uruchomiono w tej sesji Maven test, typecheck, build ani testów SSR.
Obecne API nadal pokazuje dane demonstracyjne według zasad etapu 1;
nowe pola statusu nie są jeszcze przez nie interpretowane.

## Backend etapu 4 ukończony — 2026-11-14

Ta sekcja zastępuje wcześniejszą informację o niewykonanym backendzie.
Cały etap 4 nadal czeka na formularze, filtry i zmiany SSR.

Zaimplementowano POST /trips, GET /me/trips, GET /me/trips/{id},
PATCH /trips/{id}, POST /trips/{id}/publish oraz /close. Sesje i CSRF,
właściciel z sesji, jawne DTO, walidacja i niekompletne szkice, atomowe
trasy, wersjonowanie i 409, rzeczywisty profil/kontakt, limity publikacji.
Publiczna widoczność uwzględnia status wyprawy, moderacji i organizatora.
V8 umożliwia szkice, V9 publikuje seed wyłącznie dev/test.

### Weryfikacja

Polecenie:

    ./scripts/backend.sh test -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114

**38/38 testów zaliczonych, 0 błędów, 0 pominięć, PostgreSQL 17.6.**
Obejmuje dotychczasowe 35 testów backendu i 3 nowe:
- pełny proces szkic → odczyt → edycja → publikacja → zamknięcie,
  cudzy dostęp, brak profilu/kontaktu, CSRF, 409, prywatność oraz
  widoczność CLOSED/HIDDEN/nieaktywnego organizatora;
- atomowe wycofanie błędnej trasy, granicę długości FLEXIBLE i
  odrzucanie pól właściciela/stanu/daty publikacji;
- Flyway od zera i ze schematu V2 na dwóch osobnych bazach,
  validate, zachowanie UUID, opisu i dat, właściciel demo bez loginu.

git diff --check: zaliczone. Nie uruchamiano frontendu, E2E ani SSR:
w tej sesji nie zmieniano formularzy ani stron.

Pierwsze nieudane próby i rozwiązania:
- ./scripts/backend.sh test: SocketException: Operation not permitted
  przy połączeniu z PostgreSQL. Powtórzono z dostępem do lokalnej sieci
  i jawnym URL osobnej bazy.
- Powyższe polecenie z URL: Schema validation: wrong column type
  [country_code], CHAR vs VARCHAR. Dodano jawne JdbcTypeCode CHAR.
- Kolejny przebieg: NoSuchFieldException: title w TripService.
  Usunięto refleksję niezgodną z przekształceniem pól Panache;
  przypisania są jawne. Końcowy pełny przebieg przeszedł.

### Pozostałe ograniczenia

Ręczny odbiór API na dwóch kontach opisano w README; nie wykonano go
ręcznie. Nie ma jeszcze formularzy wypraw, filtrów/paginacji ani nowych
slugów/SSR. Publiczny organizer demo jest null, ponieważ konto seed nie
ma profilu. Bazy testów migracji pozostają lokalnie do inspekcji.
Nie modyfikowano danych developerskich ani wolumenu. Bez commita/pushu.

## Interfejs autora etapu 4 — 2026-11-14

Ukończono „Moje wyprawy” i jeden formularz tworzenia/edycji w PL/EN:
- prywatna lista i edytor pobierają dane wyłącznie po stronie klienta;
  nawigacja zalogowanego użytkownika, statusy, pusta lista, wczytywanie,
  błędy i wygasła sesja;
- pełny zestaw pól API, kraje z lokalizowanymi nazwami, opcjonalne miasta,
  dodawanie/usuwanie/przestawianie trasy, daty EXACT/FLEXIBLE, liczba osób
  wraz z organizatorem, budżet PLN i style;
- POST dopiero przy zapisie, UUID w URL edytora, odświeżanie i dalszy PATCH;
  zapis niekompletnego szkicu, odrębna walidacja publikacji przy polach;
- publikacja zapisuje aktualny formularz i używa zwróconej wersji.
  Odmowa bez kontaktu zachowuje zapisany UUID i dane, bez duplikatu;
- 409 zachowuje wpisaną treść i pozwala jawnie wczytać nowszą wersję.
  Zamknięcie wymaga potwierdzenia; właściciel widzi aktualny status.
  Publiczne szczegóły dostały wyłącznie etykietę statusu, w tym CLOSED.
- proxy Nuxta dopuszcza nowe endpointy; sesja, Origin i CSRF używają
  dotychczasowego mechanizmu. Dodano kotwice sekcji profilu/kontaktów.

### Sprawdzenia wykonane w tej sesji

- npm run typecheck: zaliczone.
- npm run build: zaliczone.
- npx playwright test: **5/5**, na zbudowanym Nuxt, PostgreSQL i Mailpit.
  Dwa nowe scenariusze PL/EN obejmują zapis samego tytułu, ponowny odczyt
  po odświeżeniu/listę autora, trasę i jej kolejność, odmowę bez kontaktu
  i brak duplikatu, konflikt dwóch kart, publikację z aktualnymi zmianami,
  publiczne szczegóły na koncie B i zamknięcie. Sprawdzają też brak treści
  prywatnego szkicu w HTML edytora i kontaktu w publicznym HTML.
  Trzy dotychczasowe scenariusze kont/resetu/profili również przeszły.
- node scripts/check-ssr.mjs: 4/4 PL/EN.
- node scripts/check-google-proxy.mjs: zaliczone.
- git diff --check: zaliczone.

Pierwsza próba E2E wykryła brak endpointów autora w allowliście proxy
(404, zamiast listy obiekt błędu); dodano tylko wymagane metody/ścieżki.
Pierwsza regresja auth zatrzymała się na oczekiwaniu Google enabled=false,
gdy lokalne ustawienie wynosiło true. Ponowiono z
-Dauth.google.enabled=false, bez edycji .env. Dla pełnego zestawu E2E
proces backendu miał -Dauth.rate.register-ip=30, bo testy tworzą 8 kont.
Końcowy zestaw przeszedł w całości.

**38/38 testów backendu to wynik wcześniejszego zadania** — nie
uruchamiano ich ponownie i nie zmieniano implementacji backendu.
Użytkownik potwierdził ręczny odbiór API na dwóch kontach przed tą sesją.
Przy starcie dev Flyway zastosował V6–V9 do lokalnej bazy, zachowując dane.
Testowe konta i wyprawy pozostają lokalnie; wolumenu nie usuwano.

Pozostaje ręczny odbiór nowego UI w przeglądarce i na telefonie według
README. Filtry publiczne, paginacja, slugi i rozbudowa SEO/SSR są następnym
zadaniem. Nie dodawano etapu 5. Nie wykonano commita ani pushu.

Po testach zatrzymano uruchomione w tej sesji backend i Nuxt, aby nie
pozostawić tymczasowego limitu rejestracji. PostgreSQL i Mailpit pozostają
uruchomione. Standardowe polecenia README uruchomią aplikację ponownie.

## Wyszukiwanie etapu 4 — wznowienie 2026-11-14

Kod był nowszy niż poprzedni wpis. Potwierdzony log zakończonego przebiegu
przed wznowieniem: **39/39 backend**, PostgreSQL 17.6, bez pominięć.
TripSearchTest obejmuje PT z dwiema lokalizacjami, przecięcie budżetu
1500–2200 z 2200–2500, odpadnięcie przy 2200.01, granice/jednostronne
daty i budżety, łączenie stylu/kraju, brak duplikatów, kolejność UUID przy
równych published_at, strony i błędne parametry. Zapytania i count działają
w PostgreSQL, z jednym zestawem warunków. API zwraca items/total/page/size,
strony od 1, domyślnie 20, maks. 50. Klienci/testy zostały dostosowane.

Wznowienie: naprawiono import stałych profilu w SSR (Rollup nie rozwiązywał
ścieżki shared/profile.ts) oraz odczyt runtimeConfig podczas tworzenia
canonical (NUXT_E1001, HTTP 500). Stałe są w app/utils/profileCodes.ts;
konfiguracja pobierana w setup. Końcowe typecheck i build: zaliczone.

Lista PL/EN ma formularz, czyszczenie, paginację i stan pusty. Query URL
odtwarza wyniki w SSR i po Wstecz; zastosowanie filtrów usuwa page.
FLEXIBLE ma etykietę dopasowania okna. Filtrowane listy: noindex,follow.
Adresy /pl/wyprawy/{id}/{slug} i /en/trips/{id}/{slug}, stare adresy i
nieaktualny slug → 301 dopiero po kontroli widoczności. Szczegóły mają
title, description, canonical i Open Graph, język treści autora zachowany.
Canonical obu wariantów UI wskazuje język treści (EN albo PL dla innych).
CLOSED pozostaje publiczne. Sitemap pozostaje w etapie 7.

Wykonane po wznowieniu: E2E search.spec.ts i trips.spec.ts **3/3**:
paginacja, Wstecz, skopiowany SSR, 400, 301, canonical, draft 404 bez
Location, publikacja → wyszukanie → anonimowy odczyt → zamknięcie →
brak w wynikach i dostępne szczegóły, w PL/EN.
check-ssr.mjs **4/4**, check-google-proxy.mjs zaliczony.
Regresje auth.spec.ts i profiles.spec.ts **3/3** również przeszły teraz.
Nie wykonywano wdrożenia ani zmian wolumenu.

Końcowy pełny przebieg po poprawce SSR: **npx playwright test — 6/6**,
bez pominięć (auth PL/EN, profile, wyszukiwanie, wyprawy PL/EN).
Polecenia wykonane po wznowieniu:
- cd frontend && npm run typecheck
- cd frontend && npm run build
- cd frontend && npx playwright test
- node scripts/check-ssr.mjs
- node scripts/check-google-proxy.mjs
- git diff --check

Backend 39/39 pochodzi z zakończonego przebiegu bezpośrednio przed
przerwaniem tego samego zadania:
./scripts/backend.sh test -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114
Po wznowieniu kod backendu nie zmienił się. Nie przedstawiamy tego wyniku
jako nowego uruchomienia. Testy HIDDEN i nieaktywnego organizatora są
w TripLifecycleTest (404 API); nowy E2E potwierdza 404 bez przekierowania
dla szkicu w obu formatach adresu. Wszystkie te stany używają tej samej
kontroli GET przed generowaniem redirectu.

## Audyt etapów 1–4 — 2026-11-14

„Potwierdzone” odnosi się do wymienionego dowodu, nie do publicznego
wdrożenia. Dawne wyniki nie są oznaczane jako testy wykonane teraz.

| Etap | Kryterium | Status | Dowód | Ewentualny następny krok |
| --- | --- | --- | --- | --- |
| 1 | Uruchomienie lokalne zgodnie z README, PostgreSQL i SSR PL/EN | potwierdzone | Aktualny backend dev, zbudowany Nuxt; check-ssr 4/4 teraz, E2E 6/6 | Brak |
| 1 | Świeży checkout | zaimplementowane, brak weryfikacji | Wrapper i lockfile są w repo; obecna sesja używa istniejącego środowiska | Osobny odbiór czystego checkoutu, bez naruszania obecnej pracy |
| 1 | Trwałość danych po restarcie bazy | potwierdzone | README, wynik 2026-11-06: oba UUID zachowane po restarcie | Nie ponawiano restartu teraz |
| 2A | Rejestracja, login/logout, CSRF, błędne tokeny | potwierdzone | AuthResourceTest 17/17 w przebiegu 39/39; auth.spec PL/EN teraz | Brak |
| 2A | Jednorazowy reset i unieważnienie wszystkich sesji | potwierdzone | resetInvalidatesAllSessionsAndCannotBeReused; concurrentResetHasExactlyOneWinner; E2E Mailpit teraz | Brak |
| 2A | Sesja działa po restarcie procesu | zaimplementowane, brak weryfikacji | Sesje przechowywane w PostgreSQL; brak osobnego zapisanego testu restartu z aktywną sesją | Ręczny test tej samej sesji przed/po restarcie |
| 2B | OIDC, powrót tej samej tożsamości | potwierdzone | GoogleOidcTest 9/9 w 39/39; newAccountAndExistingSubjectDespiteChangedEmail | Dowód dotyczy kontrolowanego dostawcy |
| 2B | Brak łączenia kont po e-mailu | potwierdzone | businessRejectionsAndNoLinking oraz differentSubjectsRacingForSameEmailNeverLink | Brak |
| 2B | Ręczne logowanie do rzeczywistego Google | zaimplementowane, brak weryfikacji | README nadal wskazuje niewykonany ręczny test; brak zapisanego odbioru autora | Właściciel wykonuje checklistę Google z README |
| 3 | Własny profil, kontakty, usuwanie kontaktów i konflikt | potwierdzone | ProfileResourceTest 6/6 w 39/39; profiles.spec teraz | Brak |
| 3 | Kontakty/e-mail niewidoczne w publicznym DTO i HTML | potwierdzone | contactsReplacementFlagsAndPublicWhitelist; profiles.spec kontroluje HTML i payload PL/EN; E2E wypraw teraz | Brak |
| 4 | Cykl życia, autoryzacja, wersja, publikacja z profilem/kontaktem | potwierdzone | TripLifecycleTest, wcześniejszy odbiór API przez autora, trips.spec PL/EN teraz | Ręczny przegląd użyteczności na telefonie |
| 4 | Łączone filtry, granice, brak duplikatów, paginacja w bazie | potwierdzone | TripSearchTest w 39/39, search.spec oraz trips.spec teraz | Brak |
| 4 | SSR query URL, Wstecz, lokalizowane slugi, 301/404, metadane | potwierdzone | search.spec, trips.spec PL/EN, check-ssr 4/4 teraz | Sitemap i pełne SEO w etapie 7 |

### Braki z etapów 1–3 wymagające decyzji autora

Nie znaleziono brakującej implementacji w objętych audytem kryteriach.
Brak potwierdzenia dotyczy: czystego checkoutu, zachowania aktywnej sesji
po restarcie backendu oraz prawdziwego Google. Nie zastępujemy tych
odbiorów testem kontrolowanego OIDC ani założeniem na podstawie kodu.

Etap 4 jest ukończony w zakresie lokalnej publikacji i wyszukiwania.
Następny zakres produktowy to etap 5; sitemap/końcowe SEO pozostają w 7.
Commit, push i wdrożenie pozostawiono autorowi.

Po końcowych testach zatrzymano uruchomione przez agenta serwery Nuxt
oraz backend (tymczasowo Google wyłączone, limit rejestracji 30).
PostgreSQL/Mailpit i lokalny wolumen pozostawiono. Konta E2E i dane
wypraw pozostają lokalnie; nie wykonywano automatycznego czyszczenia.

## Etap 5 — implementacja w toku (2026-11-14)

Dodano V10 applications, trwały first_accepted_at, serwis zgłoszeń,
publicznych uczestników i prywatnych kontaktów. Zapisy decyzji, zamknięcie
oraz edycja wyprawy używają blokady jej rekordu. UI PL/EN ma zgłoszenie,
panel autora, decyzje, wycofanie i prywatne odczyty kontaktów.
Dotychczasowa regresja backendu 39/39 oraz typecheck/build przeszły.
W toku: nowe testy PostgreSQL wyścigu/uprawnień i E2E procesu zgłoszenia.

Etap 5 — backend zweryfikowany: **44/44 testów**, PostgreSQL 17.6,
0 błędów i pominięć. ApplicationServiceTest (5 testów) potwierdza ostatnie
miejsce (dokładnie jeden sukces, drugi 409 TRIP_FULL), A/B/C i brak B→C,
wycofanie, HIDDEN/BLOCKED, zamknięcie z odrzuceniem PENDING, zachowanie
kontaktów ACCEPTED, trwałą blokadę warunków po wycofaniu, własność,
unikalność, CSRF i private,no-store. Cała wcześniejsza regresja przeszła.
Początkowa próba wykazała odczyt nieaktualnego stanu w teście serwisu;
prywatne odczyty otrzymały granicę transakcji i test przeszedł.
Następny krok: końcowy E2E na ostatnim buildzie, dokumentacja odbioru.

Etap 5 — UI i odbiór automatyczny ukończone:
- typecheck i build ostatniej wersji: zaliczone;
- applications.spec.ts: **2/2 PL/EN**, rzeczywisty PostgreSQL i zbudowany
  Nuxt; B wysyła wiadomość przez UI, A czyta i akceptuje, B pojawia się
  w publicznym SSR, A/B odczytują kontakty, C zostaje odrzucone;
  wycofanie usuwa prywatny widok kontaktu; publiczny HTML nie zawiera
  wiadomości, kontaktów ani adresu logowania;
- git diff --check: bez błędów.
Pełny backend w tej sesji: 44/44. Dotychczasowego pełnego E2E etapów 1–4
nie ponawiano; nowy proces E2E obejmuje publiczne szczegóły i panel autora.

Pierwszy E2E PL przeszedł, EN dostał 429 przy zakładaniu konta. Przyczyna:
port 8080 zajęty przez istniejący backend autora (nowy proces zgłosił
„Port 8080 seems to be in use”). Testy przeniesiono na 8082/3100 z
lokalnym limitem rejestracji 40; oba języki przeszły. Nie zmieniano .env
ani nie zatrzymywano istniejącego serwera autora.

Polecenia: Maven test z URL osobnej bazy jak wyżej; npm run typecheck;
npm run build; E2E_BASE_URL=http://localhost:3100 npx playwright test
tests/applications.spec.ts. Szczegóły uruchomienia i odbiór A/B/C w README.
Pozostaje ręczny odbiór interfejsu i układu mobilnego. Brak czatu,
powiadomień i nowych funkcji moderacji (zgodnie z zakresem).
Następne zadanie produktowe: etap 6. Bez commita, pushu ani wdrożenia.

Po testach zatrzymano własne serwery testowe 8082/3100 i dodatkowy Nuxt.
Automatyczna ocena początkowo wstrzymała kill z powodu niepotwierdzonego
PID; po odczytowym sprawdzeniu ścieżki i czasu startu zatrzymanie przeszło.
Procesy autora, PostgreSQL/Mailpit oraz wolumen pozostały bez usuwania danych.

## Etap 6 — moderacja, 2026-11-15 (w toku)

V11 dodaje reports i moderation_actions. Raport anonimowy/z sesji,
walidacja dokładnie jednego publicznego celu, lokalny jednorazowy challenge,
limit 5/h/IP, admin API, audyt, hide/unhide i block/unblock są zaimplementowane.
Blokada usuwa sesje; istniejące reguły odbierają widoczność i kontakty.
Powiadomienia po commit trafiają lokalnie do Mailpit (w testach MockMailbox).

Wykonano testy dotkniętych modułów: 16/16 (ModerationTest wraz z testami
relacji uczestników, TripResourceTest, ProfileResourceTest). Typecheck/build
przeszły po poprawieniu jawnego typu config i brakującego localePath.
W toku: ostatnie uzupełnienie szczegółów celu w panelu i E2E.

Etap 6 — ukończono implementację i lokalną weryfikację (2026-11-15).
Panel ADMIN filtruje OPEN/RESOLVED/DISMISSED, pokazuje bezpieczne szczegóły
celu i umożliwia decyzję z uzasadnieniem oraz hide/unhide/block/unblock.
Formularze PL/EN zachowują dane po błędzie. Link „Zgłoś” jest na wyprawie
i profilu. Raporty i panel są klientowe, no-store/noindex.

Końcowe sprawdzenia:
- ./scripts/backend.sh test -Dtest=ModerationTest,TripResourceTest,ProfileResourceTest
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5433/travel_buddy_stage4_test_20261114
  — **16/16**, PostgreSQL, zero błędów i pominięć. W tym anonimowy
  i zalogowany reporter, błędny/brakujący cel, długość/kategoria/token,
  ponowne użycie tokenu, limit 5/h, odmowa USER, audyt, Mailpit mock,
  hide/unhide CLOSED, blokada/sesje/kontakty i prywatność odpowiedzi.
  ModerationTest dziedziczy też pięć scenariuszy relacji uczestników.
- cd frontend && npm run typecheck — zaliczone.
- cd frontend && npm run build — zaliczone.
- E2E_BASE_URL=http://localhost:3100 E2E_SETUP_ADMIN=1
  ./node_modules/.bin/playwright test tests/moderation.spec.ts
  — **1/1 rozbudowany scenariusz**, bez pominięcia: zgłoszenie A, ADMIN,
  uzasadnienie/ukrycie/rozstrzygnięcie, brak na anonimowej liście i 404
  szczegółów; anonimowy raport EN, odrzucony token i zachowanie tekstu,
  ponowienie zakończone sukcesem; panel EN.
- git diff --check — bez błędów.
Nie uruchamiano pełnego niezwiązanego zestawu testów. Dwa początkowe
błędy typecheck (inferencja typu config i localePath) zostały poprawione.

Prawdziwe Cloudflare Siteverify jest zaimplementowane, ale nie wykonano
testu z rzeczywistymi kluczami/siecią Cloudflare. Testy używają lokalnego
adaptera z jednorazowymi tokenami; nie przedstawiamy go jako ochrony
produkcyjnej. Ręczny odbiór UI/mobilny pozostaje dla autora.
Wysyłka decyzji: potwierdzona MockMailbox, dev kieruje ją do Mailpit;
brak trwałego retry przy awarii SMTP jest ograniczeniem.
Wolumenu nie usuwano. Dane syntetyczne E2E pozostają, tymczasową rolę
ADMIN odebrano również dwóm kontom z wcześniejszych przebiegów.
Następny etap: 7. Bez commita, pushu i wdrożenia.

Po weryfikacji zatrzymano własne serwery testowe 8082/3100. PostgreSQL,
Mailpit i istniejące procesy autora pozostały bez zmian. Wszystkie raporty,
uzasadnienia i audyt testów pozostają lokalnie; wolumenu nie usuwano.

## Etap 7 — rozpoczęty, 2026-11-18

Początkowy git status --short był pusty. Dodano fizyczne usuwanie konta
w transakcji, endpoint POST /api/v1/auth/delete-account oraz potwierdzenie
w panelu konta PL/EN. Zakres i decyzję dotyczącą raportów zapisano w DECISIONS.
README zawiera ręczną weryfikację na koncie syntetycznym.

Sprawdzenia: kompilacja Maven z pominięciem testów, frontend typecheck
oraz git diff --check zaliczone. AuthResourceTest: 18/18 zaliczone na
osobnej bazie travel_buddy_stage4_test_20261114. Pierwszą próbę blokował
sandbox; po eskalacji poprawiono Content-Type testu i obsługę schematu
w zapytaniach natywnych ({h-schema}). Nowy test sprawdza sesję, Origin,
fizyczne usunięcie użytkownika i odmowę ponownego logowania.

Etap 7 NIE jest ukończony. Następne zadanie: rozszerzyć test usuwania
na pełne zależności (wyprawy, uczestnicy, kontakty, raporty), następnie
retencja, dokumenty PL/EN, cookies, SEO i kontrola wycieków oraz pełny E2E
z usunięciem konta i przegląd mobilny. Nie wykonano builda ani E2E UI.
Bez commita, pushu i wdrożenia; nie usuwano wolumenów.

## Etap 7A — ukończony lokalnie, 2026-11-18

Zachowano istniejące niecommitowane zmiany, rozbudowując ich implementację.
DELETE /api/v1/me wymaga sesji, CSRF/Origin i świeżego potwierdzenia hasłem
lub tą samą tożsamością OIDC (issuer/subject/auth_time i ta sama sesja).
Potwierdzenie ważne 300 s. UI PL/EN opisuje wpływ na uczestników.
V12/V13: potwierdzenie w sesji i trwała kolejka z terminem ponowienia.
DELETION_PENDING + unieważnienie sesji + zadanie zapisują się atomowo.
Worker kasuje konto i zależności transakcyjnie, ponawia po błędzie/restartach.
Raporty/audyt zachowują zminimalizowany przebieg sprawy; szczegóły w DECISIONS.
Retencja ograniczona partiami, 12 miesięcy, wspólne zamykanie ręczne/automatyczne,
Europe/Warsaw, testowy zegar CDI. Dodano odświeżanie ACTIVE po blokadach.

Końcowe sprawdzenia:
- AuthResourceTest, AccountDeletionTest, GoogleOidcTest, TripResourceTest,
  ProfileResourceTest, ModerationTest: **55/55**, 0 błędów/pominięć;
  osobna baza travel_buddy_stage4_test_20261114, schemat stage7a_suite;
  destrukcyjna retencja odseparowana w privacy_test.
- Zakres: odmowy sesji/CSRF/braku potwierdzenia, wygasanie i powiązanie sesji,
  testowy OIDC/inny subject/brak auth_time, unieważnienie sesji/kontaktów,
  uczestnik vs organizator, zależności w bazie, minimalizacja moderacji,
  rollback i ponowienie, wyścig akceptacji/usunięcia, granice czasu.
- npm run typecheck i npm run build: zaliczone po ostatniej zmianie frontendu.
- Playwright tests/deletion.spec.ts: **2/2 PL/EN**, zbudowany Nuxt 3100,
  backend 8082 i osobny schemat stage7a_e2e. Rejestracja/profil/wyprawa
  przygotowane przez API; potwierdzenie hasła i usunięcie przez UI;
  sesja 401, profil/wyprawa 404. Odczyt SQL po workerze: 0 zadań i 0 pending users.
- git diff --check: zaliczone.

W trakcie poprawiono konflikt routingu /me (DELETE umieszczony w istniejącym
ProfileResource), whitelist proxy, izolację retencji od demo innych testów
oraz zawodny mock zegara zastąpiony alternatywą CDI. Początkowe błędne
przebiegi nie są zaliczone. Konta z nieudanych E2E pozostają syntetyczne
w izolowanym schemacie; nie wykonywano masowego czyszczenia bazy.

Ograniczenia: nie wykonano rzeczywistego Google — test OIDC jest lokalny;
Google bez świeżego auth_time zostanie bezpiecznie odrzucone. Kolejka
wznawia się z bazy; test symuluje błąd przed commit, nie zabija procesu OS.
Retencja działa podczas pracy aplikacji. Brak produkcyjnego alarmu 7 dni,
retencji konsoli/IDE i obsługi backupów/restore — konkretne punkty etapu 8
w DECISIONS. Niepowiązane wzmianki w cudzych tekstach wymagają ręcznej oceny.

Z etapu 6 pozostaje rzeczywisty test Cloudflare, ręczny odbiór mobilny i brak
trwałego retry SMTP; nie blokują kasowania. Nie poszerzano tego zakresu.
Pozostałe zadania etapu 7: dokumenty prawne PL/EN, preferencje cookies,
końcowy przegląd SEO/i18n/wycieków i odbiór mobilny. Tylko 7A ukończony.
Bez commita, pushu, wdrożenia i usuwania wolumenu.

Próba A/B/B2/C w widocznym Chromium, schemat stage7a_trial. Pierwszy
przebieg wykazał zajęte miejsce do czasu pracy workera. Żądanie usunięcia
wycofuje teraz aktywne zgłoszenia uczestnika w tej samej transakcji.
Powtórka deletion-relations.spec.ts przeszła, podobnie AccountDeletionTest.
SQL po workerze: 0 pozostałości A/B we wszystkich dziesięciu licznikach.
Polecenia powtórzenia oraz odczytu SQL w README. Utworzenie kont, profili
i publikacja były przygotowaniem przez API, akceptacja, kontakty i kasowanie przez UI.

## Etap 7 — ukończony i zweryfikowany lokalnie, 2026-11-19

Odbiór techniczny 7A opisano wyżej. W 7B dodano roboczy regulamin
local-draft-2, politykę prywatności i ustawienia cookies w PL/EN,
stopkę, linki przy rejestracji, metadane stron informacyjnych, sitemap
i robots. Wersja przy rejestracji odpowiada wersji walidowanej i zapisywanej
przez backend także przy rejestracji Google. Dodano tłumaczenia błędów
ponownego uwierzytelniania. Słowniki mają po 279 kluczy, z identycznym
zestawem ścieżek. Strona cookies nie wymaga decyzji. Czysty Chromium
po zmianie języka na tej stronie nie miał zapisanych cookies.

Weryfikacja 7B: backend 38/38 (AuthResourceTest, GoogleOidcTest,
TripResourceTest, ProfileResourceTest, TripLifecycleTest), npm run typecheck,
npm run build i git diff HEAD --check zaliczone. Końcowy wspólny przebieg
Playwright 6/6 bez pominięć na osobnym schemacie stage7b_e2e.
Sprawdzono dokumenty bez JS przy szerokości 375 px, SSR, canonical,
hreflang, opis, przekierowania, filtry i błędne daty/budżety w testowanym
zakresie wyszukiwarki. Szkic poza mapą, zamknięta wyprawa 200 z etykietą,
po ukryciu 404 i usunięcie z mapy. Próba A/B/C objęła rejestrację
i publikację przez API, wyszukanie przez B, zgłoszenie, akceptację, kontakty
i usunięcie. W anonimowym HTML razem z osadzonym payloadem oraz w publicznym
API i mapie sprawdzono brak znaczników prywatnych e-maili i wiadomości
z próby. Odpowiedź kontaktów miała no-store. Instrukcja odbioru w README.
W trakcie powtórek zadziałał limit raportów, a asercja komunikatu zapisu
wymagała odróżnienia go od równoległego komunikatu ładowania.

### Do uzupełnienia przez autora przed publikacją

- Nazwa i adres administratora oraz działający kontakt do spraw danych,
  skarg, moderacji i odwołań. Ustalić procedurę odpowiedzi i zweryfikować
  dokumenty prawnie dla rzeczywistego modelu usługi.
- Wskazać faktycznych dostawców hostingu, bazy, poczty i weryfikacji,
  ich role, lokalizacje, ewentualne transfery i zabezpieczenia.
- Potwierdzić produkcyjną retencję logów, backupów i procedurę po restore
  zgodnie z decyzjami 7A. Prawdziwe Google i Cloudflare nadal wymagają
  odbioru ze skonfigurowanymi usługami.

Autor może teraz ocenić czytelność formularzy i dokumentów na telefonie,
język PL/EN oraz nawigację ze stopki. Lokalnie dostępne na porcie 3104.

Odbiór mobilny 7B w Chromium z JavaScriptem i emulacją dotyku: 4/4
kombinacje szerokości 320 i 390 px oraz PL/EN przeszły. Z formularza
rejestracji otwarto regulamin w nowej karcie (wersja local-draft-2,
checkbox pozostał niezaznaczony) i politykę prywatności. Sprawdzono
wszystkie trzy linki stopki, treść dokumentów, informację o niezbędnych
cookies i przełączenie języka na stronie ustawień. Brak poziomego
przewijania, wyjątków JS i zapisanych cookies w tej anonimowej próbie.
Na zrzucie PL przy 320 px nagłówek i stopka zawijają się czytelnie.
Próba obejmowała emulację Chromium. Natywne Safari i fizyczny ekran
telefonu nie były przedmiotem tego sprawdzenia.
