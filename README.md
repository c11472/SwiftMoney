# SwiftMoney

CLI-based Java banking application with PostgreSQL persistence.

## Features

- Open an account with an optional opening deposit
- Deposit, withdraw, and transfer funds
- View account balance
- View transaction history
- Use PostgreSQL in real environments or in-memory storage for local runs/tests

## Run

```bash
mvn package
java -jar target/swiftmoney-1.0-SNAPSHOT.jar help
```

### PostgreSQL mode

```bash
export SWIFTMONEY_STORAGE=postgres
export SWIFTMONEY_DB_URL=jdbc:postgresql://localhost:5432/swiftmoney
export SWIFTMONEY_DB_USER=postgres
export SWIFTMONEY_DB_PASSWORD=postgres
java -jar target/swiftmoney-1.0-SNAPSHOT.jar open-account Alice 1000.00
```

The PostgreSQL schema is stored at `src/main/resources/schema.sql` and is initialized automatically when the app starts in PostgreSQL mode.

### Example commands

```bash
java -jar target/swiftmoney-1.0-SNAPSHOT.jar open-account Alice 500.00
java -jar target/swiftmoney-1.0-SNAPSHOT.jar deposit ACC-12345678 50.00
java -jar target/swiftmoney-1.0-SNAPSHOT.jar withdraw ACC-12345678 25.00
java -jar target/swiftmoney-1.0-SNAPSHOT.jar transfer ACC-12345678 ACC-87654321 100.00
java -jar target/swiftmoney-1.0-SNAPSHOT.jar balance ACC-12345678
java -jar target/swiftmoney-1.0-SNAPSHOT.jar history ACC-12345678
```
