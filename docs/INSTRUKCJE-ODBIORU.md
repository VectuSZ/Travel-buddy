# Jak przygotować odbiór etapu Travel Buddy

Autor ocenia aplikację, wykonując zadania zwykłego użytkownika. Codex odpowiada za weryfikację techniczną oraz za opisanie jej wyników i luk. Obie części trafiają do docs/ACCEPTANCE.md pod właściwym etapem.

## Przygotowanie przez Codex

1. Sprawdź wymagania etapu, aktualny ekran, nazwy przycisków, dostępne role i wcześniejsze wyniki. Zaznacz miejsce wznowienia. Zachowaj identyfikatory i wykonane próby. Wróć do ukończonej próby wyłącznie wtedy, gdy poprawka wymaga ponownego sprawdzenia. Wyjaśnij, co się zmieniło.
2. Ułóż ścieżkę wokół celu osoby, np. „poprawiam błędnie wpisany kontakt”. Każdy ręczny odbiór rozpocznij w nowej lokalnej bazie PostgreSQL przeznaczonej dla danego etapu, osobnej od bazy developerskiej i automatycznych testów. Codex uruchamia migracje, backend i frontend, sprawdza działający adres oraz zapisuje nazwę bazy i adres w dokumentacji. Istniejące bazy zachowaj. Pierwszy scenariusz opisuje rejestrację nowych fikcyjnych kont, role, przykładowe loginy, potrzebne dane i sposób powrotu do sesji. Wszystkie warunki wstępne odtwórz w obrębie tego odbioru. Przy kontynuacji rozpoczętego odbioru zachowaj jego bazę, konta i postęp. Jeśli potrzebne jest drugie konto, podaj, kto ma być zalogowany w którym oknie. Wskaż osobno próby zależne od urządzenia, konfiguracji albo danych z późniejszego etapu.
3. Sprawdź po stronie Codex uprawnienia, prywatność publicznych odpowiedzi i HTML, walidację serwera, izolację danych, konflikty zapisu oraz przypadki wymagające narzędzi technicznych. W raporcie zapisz datę, środowisko, faktyczny wynik, błędy i pozostałe luki. Wcześniejszy wynik oznacz datą. Otwartą próbę techniczną zachowaj po stronie Codex.
4. Autorowi opisz widoczny skutek reguły, jeśli pomaga ocenić produkt. Przy prywatnym kontakcie może to być pytanie „Czy z formularza rozumiesz, kto zobaczy numer?”. Inspekcję HTML, API, tokenów i bazy wykonuje Codex.

## Próba funkcjonalna przed odbiorem autora

Codex przed przekazaniem etapu przechodzi w prawdziwej przeglądarce ścieżki opisane w instrukcji, korzystając z lokalnego frontendu, backendu i PostgreSQL. E2E uzupełnia testy API, walidacji, uprawnień i SSR. Zacznij od rejestracji przez formularz. Twórz i edytuj wyprawę przez ekran organizatora. Gdy etap obejmuje uczestników, otwórz drugą niezależną sesję, zgłoś się jako podróżnik, zaakceptuj zgłoszenie w sesji organizatora i sprawdź widoczny wynik po obu stronach. Odtwórz istotne odmowy, popraw dane i sprawdź, czy użytkownik może dokończyć zadanie.

Każdy niezależny test E2E, także osobny wariant językowy i powtórzenie po błędzie, uruchamiaj na nowej bazie z migracjami i przewidzianymi danymi demonstracyjnymi. Alternatywą jest wyczyszczenie dedykowanej bazy testowej za wyraźną zgodą autora. Kroki jednego scenariusza współdzielą jego dane. Baza ręcznego odbioru ma osobny cykl życia i zachowuje postęp autora.

W ACCEPTANCE.md przy ID scenariusza odnotuj, co przeszło przez UI, co sprawdzono wyłącznie technicznie oraz co pozostaje niesprawdzone. Zachowaj także nieudane wyniki. Autor dostaje krótką ścieżkę oceny UX: czy rozumie ekran i komunikat, czy znajduje właściwą akcję, co jest uciążliwe i jak chciałby to zmienić. Szczegółowe granice walidacji pozostają materiałem pomocniczym Codex.

## Wzór jednego scenariusza

- **ID i cel użytkownika.** Jedno zdanie, np. „Chcę poprawić zapisany numer telefonu”.
- **Początek.** Konto, stan danych, adres startowy lub droga od strony głównej. Podaj fikcyjne dane i wymagane przygotowanie.
- **Kroki.** Numerowana sekwencja. Wskaż ekran, pole, dokładny napis na przycisku, moment zmiany okna i czynność kończącą próbę. Przy rozgałęzieniu podaj obie drogi.
- **Oczekiwany wynik.** Napis lub zmiana widoczna dla użytkownika. Przy błędzie wyjaśnij, jak poprawić dane i sprawdzić odzyskanie poprawnego stanu.
- **Uwagi autora.** Wystarczy ID, czynność, zaobserwowany wynik i oczekiwanie. Przy braku warunku wstępnego odnotuj przyczynę pominięcia. Zrzut ekranu jest opcjonalny.

Zakończ instrukcję jasnym miejscem zatrzymania. Autor zgłasza uwagi lub potwierdza odbiór. Decyzja o przejściu do kolejnego etapu należy do autora.
