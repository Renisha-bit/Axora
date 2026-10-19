# Axora 💜

> **Value in every choice. Balance in every step.**

A polished, modern, local-first personal finance tracker built with JavaFX and SQLite. All your financial data stays on your machine — no cloud, no accounts, no subscriptions.

---

## Screenshots

> *(Add screenshots here after first run)*

---

## Features — Phase 1 MVP

| Feature | Status |
|---|---|
| Dashboard with KPI cards (income, expenses, net cash flow) | ✅ |
| Category expense pie chart | ✅ |
| Monthly income vs. expense bar chart | ✅ |
| Period selector (This Month / Last Month / This Year / All Time) | ✅ |
| Add / edit / delete transactions | ✅ |
| Transaction history with table | ✅ |
| Filters: date range, type, category, keyword, amount range | ✅ |
| Quick date range presets | ✅ |
| CSV export (UTF-8, RFC 4180 compliant) | ✅ |
| SQLite persistence (`%APPDATA%\Axora\axora.db`) | ✅ |
| Schema migrations | ✅ |
| Input validation with clear error messages | ✅ |

**Coming in Phase 2:** Budgets, recurring transactions, financial accounts, savings goals  
**Coming in Phase 3:** PDF reports, dark/light themes, custom categories, calendar view

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 LTS |
| UI | JavaFX 21 + FXML + CSS |
| Build | Apache Maven 3.9 |
| Database | SQLite via `sqlite-jdbc` |
| JSON | Jackson |
| Testing | JUnit 5 |

---

## Project Structure

```
src/main/java/com/axora/
├── app/          — Application entry point
├── config/       — AppConfig (data directory, DB path)
├── model/        — Domain entities (Transaction, Category)
├── dao/          — DAO interfaces + filter/result types
│   └── sqlite/   — SQLite implementations + DatabaseManager
├── service/      — Business logic (TransactionService)
├── export/       — CsvExporter
├── validation/   — TransactionValidator
├── util/         — CurrencyUtil (INR formatting)
└── ui/
    ├── controller/ — JavaFX controllers
    ├── view/       — FXML layouts
    └── css/        — axora.css
```

---

## Getting Started

### Prerequisites

- **JDK 21+** (tested with Oracle JDK 21 and 27)
- **Maven 3.8+** — or use the included `run.bat` which uses the bundled Maven at `C:\tools\apache-maven-3.9.6`

### Build & Run

```bash
# Clone the repo
git clone <repo-url>
cd axora

# Compile
mvn compile

# Run tests
mvn test

# Launch the application
mvn javafx:run
```

### Windows Quick Launch

Double-click **`run.bat`** in the project root.

---

## Data Storage

All data is stored in:
```
%APPDATA%\Axora\axora.db
```
Typically: `C:\Users\<YourName>\AppData\Roaming\Axora\axora.db`

This file is a standard SQLite database. You can open it with [DB Browser for SQLite](https://sqlitebrowser.org/) for inspection or manual backup.

---

## Architecture Decisions

- **Integer cents** (`amount_cents INTEGER`) — no floating-point money arithmetic anywhere. `BigDecimal` used at boundaries.
- **Schema migrations** — append-only versioned migrations in `DatabaseManager`. Safe to add new migrations without breaking existing data.
- **Layered architecture** — controllers call only services; services call only DAOs; no SQL in controllers, no JavaFX in services.
- **Off-thread DB** — all database operations run in background `Task` threads; UI is never blocked.
- **Prepared statements** — all user-supplied values use parameterized queries.
- **Foreign keys ON** — enforced on every connection via `PRAGMA foreign_keys = ON`.

---

## Contributing / Development

1. Fork & clone
2. Run `mvn test` before submitting changes — all 6 tests must pass
3. Add tests for new validation or calculation logic
4. Follow the existing package structure

---

## License

MIT — see [LICENSE](LICENSE)

---

## Roadmap

- [ ] Phase 2: Monthly budgets, category budgets, recurring transactions
- [ ] Phase 2: Financial accounts (cash, bank, digital wallet) and explicit transfers
- [ ] Phase 2: Savings goals with progress tracking
- [ ] Phase 3: PDF monthly reports
- [ ] Phase 3: Dark/light theme toggle
- [ ] Phase 3: Custom categories
- [ ] Phase 4: CSV bank-statement import
- [ ] Phase 4: Multiple user profiles
