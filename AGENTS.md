# Instrukcje projektu

## Produkt
Platforma do publikowania wypraw i szukania współtowarzyszy.
Pełny kontekst: docs/PLAN.md.

## Aktualny etap
Pracujemy wyłącznie lokalnie na macOS.
Implementuj tylko zakres bieżącego zadania.
Nie wdrażaj aplikacji ani nie konfiguruj usług chmurowych.

## Stack
- Java 21, Quarkus 3.x, Maven Wrapper.
- PostgreSQL 17, Flyway, Hibernate ORM/Panache.
- Nuxt 4, Vue, TypeScript, npm.
- Interfejs PL/EN od początku.
- PostgreSQL w Docker Compose.
- Backend i frontend lokalnie, z hot reload.

## Organizacja
- backend/ — Quarkus
- frontend/ — Nuxt
- docs/ — decyzje i opis projektu
- compose.yaml — lokalne usługi pomocnicze

## Zasady implementacji
- Preferuj najprostsze rozwiązanie spełniające wymagania.
- Nie dodawaj funkcji z późniejszych etapów.
- Ustal konkretne stabilne wersje zależności; bez snapshotów.
- Commituj package-lock.json i Maven Wrapper.
- Zmiany schematu realizuj migracjami Flyway.
- Nie używaj Hibernate do automatycznej zmiany schematu.
- Nie zwracaj encji ORM bezpośrednio z API.
- Publiczne DTO nie mogą zawierać kontaktów ani danych logowania.
- Sekrety trzymaj poza repozytorium; przykłady w .env.example.
- Nie kasuj danych ani wolumenów bazy bez wyraźnej zgody.
- Nie modyfikuj globalnej konfiguracji komputera.
- Nie commituj ani nie pushuj zmian automatycznie.

## Weryfikacja
- Uruchamiaj sprawdzenia odpowiednie do zmiany.
- Nie deklaruj sukcesu testu, którego nie uruchomiono.
- Aktualizuj README o rzeczywiste polecenia uruchamiania.
- Zapisuj istotne decyzje w docs/DECISIONS.md.
- Na końcu podaj: zmiany, wyniki sprawdzeń, sposób ręcznej
  weryfikacji oraz pozostałe ograniczenia.
- Komunikuj się po polsku; identyfikatory kodu po angielsku.