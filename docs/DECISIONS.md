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

# Decyzje — etap 2B (2026-11-11)

- quarkus-oidc z istniejącego BOM 3.40.1 obsługuje Authorization Code Flow.
  Jawnie wymagamy nonce i PKCE S256 oraz weryfikujemy issuer Google,
  audience/client ID i ważność ID tokenu. Podpis sprawdza biblioteka przez
  JWKS. Zakresy: openid, email, profile; bez dodatkowych Google API.
- Migracja V4 dodaje auth_identities z UNIQUE(issuer, subject),
  UNIQUE(user_id, provider) i FK ON DELETE CASCADE. Nie modyfikuje kont
  lokalnych ani wypraw. Adres e-mail nie służy do wiązania tożsamości.
- POST google/start wymaga istniejącej ochrony CSRF/Origin. Ma dwa tryby:
  register wymaga local-draft-1 i akceptacji, login nie daje zgody na
  utworzenie nowego konta. Język, wersja i czas akceptacji są zapamiętane
  w zaufanym magazynie po stronie serwera, nigdy odczytywane z callbacku.
- GoogleAttempts przechowuje maksymalnie 1000 prób, przez 10 minut,
  w pamięci pojedynczego procesu. Zapamiętuje hash losowego powiązania
  z HttpOnly cookie, język, zgodę i state wygenerowany przez Quarkusa.
  Usuwa stare wpisy co minutę oraz przed operacjami. Restart unieważnia
  rozpoczęte przepływy, nie istniejące sesje aplikacji.
- OidcRedirectFilter wiąże wygenerowany state z próbą; jest jawnie
  Unremovable (inaczej optymalizacja CDI usuwała filtr). Callback musi
  przejść weryfikację Quarkusa i mieć zgodne powiązanie cookie/state.
  Najnowszy start w tej samej przeglądarce zastępuje cookie wcześniejszej
  próby; stary callback jest odrzucany, zamiast korzystać z nowej zgody.
- Nie utrzymujemy sesji OIDC. Własny TokenStateManager odrzuca ID/access/
  refresh tokeny i zwraca wyłącznie stały, nieautoryzujący placeholder.
  Quarkus weryfikuje callback w tym samym żądaniu, bez dodatkowego redirectu
  usuwającego parametry. Endpoint usuwa cookie q_session i tworzy zwykłą
  sesję przez SessionService. Tokeny Google nie trafiają do bazy,
  localStorage ani odpowiedzi dla frontendu.
- Callback i ścieżka błędu są pod origin frontendu. Nitro zachowuje query
  (code/state), Cookie, wszystkie Set-Cookie i Location. Fetch w proxy ma
  redirect: manual — przekierowanie do Google ma wykonać przeglądarka.
  Test check-google-proxy.mjs potwierdza te właściwości na zbudowanym Nuxt.
- Cel sukcesu jest stały: /account albo /en/account. returnUrl w body lub
  query startu jest odrzucany. Wynik błędu zawiera wyłącznie kod z aplikacji;
  UI nie renderuje surowych parametrów dostawcy. Filtr HTTP dodaje no-store
  również dla błędów uwierzytelniania powstających przed REST i przekierowuje
  je do stałej strony błędu. Anulowanie ma osobny komunikat.
- Istniejąca tożsamość zawsze wskazuje dotychczasowe konto, nawet gdy Google
  zmieni e-mail. Nowa wymaga email_verified=true, akceptacji i wolnego e-maila.
  Konflikt nigdy nie łączy kont, nie zmienia hasła ani tożsamości. Konta
  BLOCKED/DELETION_PENDING nie dostają sesji. Konto Google ma NULL hasła.
- Transakcja obejmuje konto, tożsamość i sesję. PostgreSQL advisory lock
  na issuer+subject szereguje równoległe tworzenie tej samej tożsamości;
  indeksy UNIQUE pozostają ostatecznym zabezpieczeniem także konfliktów
  między różnymi subject i rejestracją hasłem.
- Google jest domyślnie wyłączone; nie są potrzebne credentials ani
  połączenie z Google, aby uruchomić konta lokalne. GET google/config steruje
  widocznością przycisków. Nie zmieniamy TLS, certyfikatów ani CORS.
- Logi io.quarkus.oidc są wyłączone, aby błędy dostawcy nie ujawniły tokenów
  ani parametrów callbacku. Wyłączono też komunikaty błędów SQL Hibernate,
  które przy konflikcie UNIQUE mogłyby zawierać subject. Publiczne błędy
  pozostają kodami aplikacji. HTTP access log nie jest włączony.
- OidcProvider w testach udostępnia lokalne discovery, JWKS i token endpoint.
  Wydaje podpisane tokeny testowe, sprawdza PKCE i nie wymaga Google.
  Testujemy działającą integrację Quarkusa, a nie własną walidację podpisów.
  Prawdziwy Google wymaga ręcznego sprawdzenia przez właściciela credentials.

Źródła:
- https://quarkus.io/version/3.40/guides/security-oidc-code-flow-authentication/
- https://developers.google.com/identity/openid-connect/openid-connect
- https://developers.google.com/identity/protocols/oauth2/web-server

Weryfikacja: 29 testów backendu (9 OIDC) oraz 2 E2E PL/EN zaliczone;
proxy OIDC, typecheck/build i 4 ścieżki SSR również zaliczone. Brak
credentials oznacza, że nie potwierdzono prawdziwego logowania Google.
Start Google jest ograniczony do 10 prób na 15 minut na adres proxy/IP.

## Etap 3 — profil i prywatne kontakty (2026-11-13)

- Migracja V5 dodaje dwie tabele 1:1 z users i ON DELETE CASCADE. Konto
  może nie mieć profilu ani kontaktów; brak fikcyjnych danych i kopiowania
  e-maila logowania. Dotychczasowe migracje i dane pozostają zachowane.
- PublicProfile to osobne DTO z dokładnie sześcioma polami: userId,
  displayName, age, bio, languages, travelStyles. OwnProfile zawiera też
  ageUpdatedAt i version. Me zawiera własny e-mail i kontakty. Encje nie
  są serializowane. Nieaktywne konto ma publiczne 404 i prywatne 401.
- PATCH stosuje jawną listę pól i rozróżnia brak od null. Pierwszy zapis
  wymaga nazwy 2–60 znaków po strip, wieku 18–120, języków i stylów.
  Bio do 1000 znaków można usunąć przez null/pusty tekst. Teksty nie
  dopuszczają znaków < ani >; interfejs renderuje tekst bez v-html.
- Wiek jest deklarowany, bez daty urodzenia. age_updated_at to data UTC
  serwera przy pierwszym zapisie lub zmianie wartości wieku. Zapis tego
  samego wieku albo samego bio nie odświeża daty.
- Lokalny słownik 22 języków: pl, en, de, fr, es, it, pt, uk, cs, sk, nl,
  sv, no, da, fi, el, tr, ar, zh, ja, ko, hi. Maksymalnie 10 unikalnych.
  Style: backpacking, comfort, trekking, city_break. Słowniki backendu
  i frontend/shared/profile.ts używają tych samych kodów; etykiety PL/EN
  są w i18n/locales. Bez API zewnętrznego. Tablice wejściowe do 32 pozycji,
  duplikaty usuwane z zachowaniem kolejności, nieznane kody odrzucane.
- profileComplete oznacza istnienie poprawnie zapisanego profilu;
  hasContact co najmniej jeden kontakt; readyToParticipate jest ich
  koniunkcją. Flagi wyliczamy, nie zapisujemy. Nie blokują przeglądania.
- Kontakty zastępuje PUT: brak pola, null i pusty tekst usuwają wartość.
  E-mail jest niezależny, przycinany i walidowany przez Bean Validation.
  Instagram to 1–30 znaków ASCII (litery, cyfry, kropka, podkreślenie),
  opcjonalne pojedyncze początkowe @ usuwamy. WhatsApp: + i 2–15 cyfr,
  pierwsza niezerowa. To jedynie sprawdzenie formatu, bez weryfikacji.
- Wersja 0 oznacza utworzenie; pierwszy zapis zwraca 1. Kolejne wymagają
  aktualnej wersji i korzystają z Hibernate @Version. Krótka blokada
  wiersza właściciela serializuje również równoległe tworzenie profilu
  i kontaktów; nie zastępuje sprawdzenia wersji. Stare żądanie dostaje
  409 PROFILE_VERSION_CONFLICT. UI zachowuje szkic do jawnego przeładowania.
- Właściciel pochodzi wyłącznie z aktywnej sesji. Zapis korzysta z istniejącego
  CSRF oraz kontroli Origin, przez ten sam proxy Nuxta. Nieznane pola
  (w tym userId/role/status/email) są odrzucane. Kontakty nie mają publicznego API.
- Prywatny edytor działa dopiero po mounted w ClientOnly. Brak danych konta
  w SSR/hydratacji. Publiczny profil SSR ma tylko publiczne DTO, noindex,
  bez katalogu i sitemap. API profili, /me oraz strony profili mają
  Cache-Control: private, no-store, także na odpowiedziach błędów API.
- Specyfikacja nowych endpointów: docs/openapi-profiles.yaml (OpenAPI 3.0.3).
  Dotychczas projekt nie udostępniał generatora OpenAPI; specyfikacja jest
  utrzymywana w repozytorium bez dodawania zależności runtime.
- Testy zwykłego auth wymuszają wyłączenie Google w profilu test, niezależnie
  od lokalnego .env. Osobny kontrolowany dostawca OIDC włącza integrację
  tylko na czas swoich testów. Nie wymaga prawdziwych credentials.

## Organizacja weryfikacji i statusu (2026-11-14)

- Bieżące ukończone zakresy, datowane wyniki testów, otwarte sprawdzenia
  i następny krok zapisujemy w docs/STATUS.md. PLAN opisuje dalszy zakres,
  README polecenia i scenariusze ręczne, DECISIONS decyzje techniczne.
- Testy Maven uruchamiamy przy zatrzymanym backendzie dev: oba procesy
  korzystają z backend/target, a równoległa przebudowa wywołała konflikt
  classloaderów hot reload. Dopiero po testach uruchamiamy dev dla E2E.
- Wynik testów kontrolowanego OIDC nie potwierdza ręcznego logowania
  przez Google. Status rozróżnia te sprawdzenia i podaje datę wyników.

## 2026-11-14 — początek migracji etapu 4 (implementacja nieukończona)

V6 addytywnie zachowuje kolumny etapu 1, UUID i treść; kopiuje opis i daty
 do nowych pól. Starsze rekordy bez właściciela pozostają DRAFT.
V7 znajduje się wyłącznie w db/demo (dev/test): przypisuje dwa znane UUID
konta demonstracyjnego bez hasła i tożsamości logowania. Nie przypisuje
wypraw pierwszemu zalogowanemu użytkownikowi. Nieznane starsze rekordy
wymagają osobnej, jawnej decyzji o właścicielu.
Migracje są początkiem pracy: dotychczasowy model ORM i API nadal używają
kolumn etapu 1. Przed ukończeniem etapu trzeba zmienić ograniczenia starych
kolumn umożliwiając niekompletne szkice oraz wdrożyć walidację publikacji.

## 2026-11-14 — backend cyklu życia wypraw

- Zachowano V6/V7 bez przepisywania. V8 usuwa NOT NULL ze starych pól
  i poszerza język treści; stare kolumny/dane pozostają. V9 tylko dev/test
  publikuje demonstracyjne rekordy z dodatkowymi warunkami, aby stare
  publiczne strony działały. Nie tworzy hasła ani tożsamości logowania.
- Nowe zapisy używają nowych pól; publiczne aliasy description,
  startDate/endDate zachowują kompatybilność. organizer jest minimalnym
  publicznym DTO albo null dla demonstracyjnego konta bez profilu.
- Jeden serwis transakcyjny zapisuje wyprawę i lokalizacje. Blokada
  użytkownika serializuje zapisy, kontrolę kontaktów i limitów;
  @Version i obowiązkowe version odrzucają stary formularz przez 409.
  Aktualizacja samej trasy też aktualizuje znacznik wyprawy i wersję.
- Publikacja korzysta z rzeczywistych flag ProfileService; edycja OPEN
  sprawdza kompletność ogłoszenia. CLOSED nie jest edytowalne ani otwierane
  ponownie. Limity 3/dzień UTC i 5 OPEN są konfigurowalne.
- Publiczna lista: OPEN/VISIBLE/aktywny organizator. Szczegóły dopuszczają
  CLOSED. Szkice, HIDDEN i nieaktywne konta dają 404. Jawne DTO nie
  zawierają encji, kontaktów ani e-maila logowania.
- Test migracji używa dwóch osobnych baz PostgreSQL i Flyway
  (od zera oraz target V2 → latest), bez clean i bez usuwania baz.
  Nazwy pozostawionych baz są wypisywane w logu testu.

## 2026-11-14 — prywatny edytor wypraw

- /my-trips oraz /my-trips/edit?id=UUID (i warianty /en) mają ClientOnly,
  noindex i no-store. Dane są lokalnym stanem komponentu, bez SSR payloadu.
  Pusty edytor nie tworzy rekordu. Pierwszy zapis zamienia URL na UUID;
  kolejne zapisy używają PATCH i wersji z odpowiedzi.
- Publikacja to zapis, następnie POST publish. Po odmowie publikacji
  zapisany szkic pozostaje pod tym samym UUID. Bez automatycznych retry.
  Pola kompletności są wymagane dopiero przy publikacji lub edycji OPEN.
- Konflikt nie nadpisuje formularza. Jawne przeładowanie ostrzega, że
  zastąpi wpisane dane. Zamknięcie działa na zapisanej wyprawie; potwierdzenie
  informuje również o niezapisanych zmianach. CLOSED jest tylko do odczytu.
- Kody krajów ISO są lokalną listą; nazwy PL/EN pochodzą z Intl.DisplayNames.
  Języki i style współdzielą istniejący słownik profilu.
- Publiczne szczegóły pokazują status, aby link do CLOSED był czytelny.
  Nie zmieniano publicznych filtrów, paginacji, adresów ani SEO.

## 2026-11-14 — wyszukiwanie i adresy etapu 4

- GET /trips zwraca kopertę items,total,page,size. Wcześniej nie było
  konwencji paginacji; wybieramy numerację od 1. Rozmiar poza 1–50,
  niepoprawny kraj/styl i odwrócony zakres dają 400 INVALID_SEARCH.
- Parametryzowany SQL: EXISTS dla kraju, ANY dla tablicy stylów,
  wspólne warunki licznika i wyników. Przedziały domknięte; paginacja
  oraz sortowanie published_at DESC,id DESC są wykonywane w bazie.
- Slug wyliczamy deterministycznie z aktualnego tytułu w DTO, bez
  przebudowy starych danych. UUID identyfikuje rekord. Zmiana tytułu
  automatycznie zmienia slug, stary URL przekierowuje po kontroli widoczności.
- Kanoniczna wersja treści EN: /en/trips, pozostałe języki: /pl/wyprawy.
  Drugi wariant UI ma ten sam canonical; nie dodajemy hreflang do treści
  autora. Origin canonical: runtimeConfig.public.siteUrl, lokalnie localhost.
- Stare / i /en nadal udostępniają listę dla zgodności; nowe lokalizowane
  listy są pod /pl/wyprawy i /en/trips. Pełna sitemap i końcowe SEO: etap 7.

## 2026-11-14 — zgłoszenia i kontakty (etap 5)

- V10 dodaje applications z UNIQUE(trip_id,applicant_id). Zgłoszenie
  można wysłać raz, także po odrzuceniu/wycofaniu nie jest tworzone ponownie.
  Wiadomość to prywatny plain text, 1–2000 znaków. Limit: 20/dzień UTC.
- Wszystkie zmiany zgłoszenia blokują rekord wyprawy. Akceptacja po
  blokadzie odświeża zgłoszenie i liczy ACCEPTED + organizator. Ta sama
  blokada obejmuje edycję limitu miejsc i zamknięcie. Zamykanie odrzuca
  PENDING w tej samej transakcji; ACCEPTED pozostają.
- first_accepted_at w trips trwale oznacza pierwszą akceptację. Samo
  decided_at nie wystarcza, ponieważ występuje także przy odrzuceniu.
  Pole nie jest usuwane po wycofaniu; kraje, daty i budżet pozostają
  zablokowane, liczba miejsc może jedynie wzrosnąć.
- Kontakty są wyliczane z aktualnej relacji dla konkretnej wyprawy,
  bez kopiowania do applications. Odczyt transakcyjny sprawdza VISIBLE,
  opublikowany stan i aktywność obu kont. CLOSED zachowuje relację.
  Administrator nie dostaje wyjątku. Wyłącznie organizer ↔ ACCEPTED.
- Prywatne DTO zgłoszenia zawiera wiadomość; publiczni uczestnicy
  otrzymują wyłącznie istniejący PublicProfile. ClientOnly dla wiadomości
  i kontaktów; publiczna lista uczestników korzysta z SSR. Proxy
  przepuszcza tylko jawne endpointy/metody. Prywatne odpowiedzi no-store.

## 2026-11-15 — zgłoszenia nadużyć i moderacja

- V11 przechowuje reports i nieusuwalny przez API audyt moderation_actions.
  Dokładnie jeden publiczny cel jest walidowany w serwisie; URL musi być
  wewnętrzną ścieżką do tego UUID, bez query/fragmentów/obcego hosta.
  Kategorie: SPAM, HARASSMENT, FRAUD, UNSAFE, OTHER. Opis 1–4000 znaków,
  imię anonimowego zgłaszającego 1–120 i poprawny e-mail do 254 znaków,
  potwierdzenie dobrej wiary. Reporter zalogowany zawsze pochodzi z sesji.
- Wszystkie zapisy wymagają CSRF/Origin. Limit raportów 5/h/IP, osobny
  limit wydania lokalnego tokenu 20/h/IP. Stosujemy dotychczasowy limiter
  procesu i zaufany adres połączenia, bez akceptowania X-Forwarded-For.
  Lokalny proxy powoduje współdzielenie limitu IP; restart resetuje licznik.
- Brak wcześniejszej integracji Turnstile. Adapter używa oficjalnego
  Siteverify i sprawdza success, hostname z auth.frontend-url oraz action
  report. Błędy sieci/konfiguracji/tokenów nie przepuszczają raportu.
  https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
- Dev/test domyślnie używa jawnego symulatora: losowy token wydany przez
  serwer, jeden odczyt, ważność 5 min, maks. 1000 wpisów. Nie jest to
  zabezpieczenie antybot dla internetu. LaunchMode.NORMAL odrzuca lokalny
  tryb przy inicjalizacji. Poza dev/test brak kluczy nie pozwala zgłaszać.
- ADMIN jest sprawdzany w backendzie przy każdej operacji; UI nie stanowi
  zabezpieczenia. Panel pokazuje cel (tytuł/plan lub publiczny profil),
  opis raportu i status, ale nie profile_contacts, e-mail logowania ani
  dane kontaktowe anonimowego zgłaszającego. Dane anonimowego raportu
  pozostają w tabeli i służą wysłaniu informacji o rozstrzygnięciu.
- Hide/unhide zmienia tylko moderation_status; CLOSED pozostaje CLOSED.
  Block/unblock używa users.status. Block usuwa wszystkie sesje, unblock
  ich nie odtwarza. DELETION_PENDING nie może zostać przypadkiem odblokowane.
  Administrator nie moderuje własnego konta.
- Każda decyzja zapisuje aktora, cel, akcję, uzasadnienie i czas.
  Rozstrzygnięcie raportu jest odrębne od ukrycia/blokady; brak automatycznego
  kasowania treści. Powiadomienie e-mail po commit: właściciel moderowanego
  celu lub zgłaszający po rozstrzygnięciu. Lokalnie Mailpit; awaria wysyłki
  nie cofa decyzji, nie ma jeszcze trwałej kolejki ponowień.
- Roli ADMIN nie można uzyskać przez publiczne API. Właściciel nadaje ją
  lokalnie konkretnemu UUID SQL-em. E2E wymaga E2E_SETUP_ADMIN=1, tworzy
  nowe konto i nadaje rolę tylko jemu; finally przywraca USER, bez usuwania
  kont, raportów i audytu. Brak konta admina z hasłem w seedzie.

## 2026-11-18 — początek etapu 7: fizyczne usunięcie konta

Usunięcie na żądanie zalogowanego użytkownika odbywa się od razu, w jednej
transakcji, przez POST /api/v1/auth/delete-account chroniony Origin/CSRF.
UI wymaga zaznaczenia potwierdzenia. Usuwamy własne wyprawy, aplikacje
własne i do własnych wypraw oraz dane kaskadowo zależne od konta.
Raporty powiązane z kontem (także decyzją moderatora) i audyt są usuwane,
ponieważ mogą zawierać dane osobowe w tekście; nie ustanowiono wyjątku
retencji dowodów nadużyć. Nie jest to mechanizm wyszukiwania wzmianek
w dowolnym tekście innych użytkowników. Retencja okresowa pozostaje do realizacji.

## 2026-11-18 — etap 7A: zadania usuwania i retencja

Ta decyzja zastępuje wcześniejszy wariant natychmiastowego kasowania oraz
usuwania całych powiązanych raportów. DELETE /api/v1/me wymaga aktywnej
sesji, Origin/CSRF oraz potwierdzenia ważnego 300 sekund w tej samej sesji.
Hasło sprawdzamy ponownie, z limitem prób. OIDC sprawdza issuer/subject
istniejącej tożsamości, powiązanie przepływu z sesją i świeże auth_time.
Brak auth_time lub inna tożsamość oznacza odmowę. max_age=0 żąda świeżego
uwierzytelnienia u dostawcy; rzeczywisty Google wymaga osobnego odbioru.

Żądanie atomowo ustawia DELETION_PENDING, usuwa wszystkie sesje i tworzy
trwały deletion_jobs. Uproszczony model zadania: obecność oznacza pending,
blokada użytkownika zastępuje RUNNING; po udanym fizycznym kasowaniu FK
usuwa zadanie. Błąd wycofuje całą transakcję konta; next_attempt_at odsuwa
ponowienie o godzinę. Restart nie usuwa kolejki. Worker przetwarza do 50
kont na przebieg, co godzinę, w osobnej transakcji per konto. Wymagane
jest działanie lokalnej aplikacji; przed produkcją potrzebny jest alarm
na zadania starsze niż 7 dni. Nie zapisujemy danych osobowych w logu błędu.

Fizycznie usuwane: użytkownik (email i hash), profil, kontakty, tożsamości,
sesje, tokeny, jego aplikacje z wiadomościami, własne wyprawy, lokalizacje
oraz wszystkie aplikacje do tych wypraw. FK i transakcja chronią spójność.
Blokady i ponowny odczyt ACTIVE serializują zapis profilu/wyprawy,
akceptację zgłoszenia oraz żądanie kasowania. Zgłoszenia do usuniętej
wyprawy znikają z listy; istniejący otwarty widok obsługuje odpowiedź 404.

Powiązane raporty (reporter/target/resolver, własna wyprawa lub zgodny email
anonimowego reportera) tracą wszystkie identyfikatory użytkowników i celu,
nazwę/email reportera oraz URL. Opis i uzasadnienie zastępuje [removed].
Powiązany audyt traci identyfikatory osób/celu i całe uzasadnienie.
Pozostają UUID sprawy/akcji, powiązanie akcji ze sprawą, kategoria, status,
good_faith, typ akcji i daty — minimalny przebieg obsługi moderacji.
Nie próbujemy automatycznie rozpoznawać osoby w niepowiązanych cudzych
tekstach: takie żądanie wymaga wskazania treści i ręcznej minimalizacji.
Zakończone raporty: 12 miesięcy od resolved_at. Audyt: 12 miesięcy od
utworzenia decyzji/akcji. OPEN wymaga rozpatrzenia przez administratora;
nie ustanawiamy automatycznego przedłużenia zakończonych spraw.

Daty podróży interpretujemy w Europe/Warsaw. OPEN z date_to < dzisiaj
zamyka wspólna reguła z zamykaniem ręcznym, odrzucając PENDING.
CLOSED usuwamy dopiero gdy date_to < dzisiaj minus 12 miesięcy.
Każda grupa porządkowania ma limit 50 rekordów. Sesje/tokeny wygasłe
oraz tokeny zużyte sprzątane są co godzinę (częściej niż minimum w planie).
Konfiguracja: privacy.batch-size, privacy.trip-months,
privacy.moderation-months, privacy.cleanup-interval,
privacy.reauthentication-seconds. Wartości domyślne: 50, 12, 12, 1h, 300.

Logi: aplikacja używa konsoli, brak zarządzanego pliku i gwarancji 14 dni
dla zapisu terminala/IDE. Etap 8 musi skonfigurować 14-dniowe wygaszanie
logów wszystkich warstw, z wyłączeniem haseł, tokenów, kontaktów i wiadomości.
Backupy: lokalny wolumen pozostaje; brak produkcyjnych kopii w tym etapie.
Etap 8: 7 kopii dziennych + 4 tygodniowe (maks. ok. 35 dni), osobny minimalny
rejestr wykonanych usunięć przez czas życia kopii i obowiązkowe ponowienie
usunięć po restore, przed otwarciem ruchu. Bieżąca kolejka nie zastępuje
tego rejestru. Test odtworzenia i kontroli usunięć jest bramką wdrożenia.

## 2026-11-19 — lokalne dokumenty, cookies i indeksowanie

Rozszerzonemu regulaminowi nadano wersję local-draft-2. Nowe rejestracje
hasłem i Google wymagają tej wersji. Historyczne akceptacje zachowują
dotychczasową wartość. Link do polityki jest osobny od checkboxa regulaminu.
Lista opcjonalnych integracji jest pusta. Ustawienia stają się aktywne
po dodaniu integracji, której loader musi respektować zgodę i jej cofnięcie.

Mapa witryny pobiera wyłącznie ID, slug, język treści i datę aktualizacji
widocznych OPEN/CLOSED aktywnych autorów. CLOSED pozostaje kanonicznym
publicznym zasobem do usunięcia przez retencję. Odpowiedź mapy ma no-store.
Listy PL/EN i strony informacyjne mają własne canonical i hreflang.

## Odbiór etapu 1 — odporność na restart i prezentacja wypraw

- Pula Agroal sprawdza połączenie JDBC przy każdym wypożyczeniu. Oznacza to koszt walidacji również przy zdrowej bazie. W lokalnym MVP priorytet ma poprawny pierwszy odczyt po restarcie PostgreSQL.
- Wybór, filtr i zapis kraju mają wspólny identyfikator ISO 3166-1 alpha-2. Lokalny słownik zawiera 249 kodów, grupy geograficzne i aliasy wyszukiwania. Nazwy krajów pobieramy z lokalnego Intl.DisplayNames dla PL i EN. Nazwy kontynentów są zapisane jawnie, ponieważ obsługa numerycznych kodów regionów różni się między przeglądarką a Node.
- Na potrzeby kart przypisujemy jeden kontynent do kraju lub terytorium. Rosję pokazujemy w Europie, Turcję, Kazachstan, Gruzję, Armenię, Azerbejdżan i Cypr w Azji, Egipt w Afryce. Terytoria mają własne wpisy, np. Grenlandia należy na karcie do Ameryki Północnej, a Gujana Francuska do Południowej. To konwencja prezentacji. Powtarzające się kraje i kontynenty pokazujemy raz, w kolejności pierwszego wystąpienia.
- Pole remainingPlaces w publicznych szczegółach korzysta z tej samej reguły co zapis zgłoszenia. Organizator i każda zaakceptowana osoba zajmują po jednym miejscu. Ostateczna kontrola dostępności przy zapisie nadal odbywa się w transakcji backendu.
