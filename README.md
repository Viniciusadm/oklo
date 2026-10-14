# Oklo

Oklo is an Android flashcards app with nested decks, FSRS scheduling, study statistics and AI-drafted cards. Cards live in a local Room database and sync with Manage API, a private Laravel backend that is not published; this README describes the part of its contract the app consumes. This repository holds the Android app.

Other clients of the same backend: [`calendar`](https://github.com/Viniciusadm/calendar), [`exercises`](https://github.com/Viniciusadm/exercises), [`files`](https://github.com/Viniciusadm/files) and [`finances`](https://github.com/Viniciusadm/finances).

## Requirements

- JDK 11+
- Android SDK with `compileSdk` 37; the app runs on Android 8.0+ (`minSdk` 26)
- Android Gradle Plugin 9.3.3 and Kotlin 2.2.10 (from [`gradle/libs.versions.toml`](gradle/libs.versions.toml))
- The sibling [`ribalta`](https://github.com/Viniciusadm/ribalta) repository checked out at `../../ribalta`, plus a Rust toolchain (`cargo`) with the `aarch64-linux-android` and `x86_64-linux-android` targets and the Android NDK, used to build its native lesson compiler

## Building

The backend address is read at build time from `BASE_URL` in `env.properties`, which is not versioned. Copy the example and set the address; without it the build points to `http://127.0.0.1:8001/`:

```bash
cp env.properties.example env.properties
```

Lessons are rendered by the Ribalta Android library, pulled in as a composite build from `../../ribalta/android` (see [`settings.gradle.kts`](settings.gradle.kts)). Gradle compiles its native library with the NDK found through `ANDROID_NDK_HOME` or the SDK, but the fonts it bundles are not versioned, so fetch them once before the first build:

```bash
../../ribalta/android/scripts/fetch-fonts.sh
```

Build and install the debug app:

```bash
./gradlew installDebug
```

## API contract

Every request sends `Accept: application/json`, and every response comes in an envelope with the payload in `body`. `POST api/auth/login` returns a token, sent afterwards as `Authorization: Bearer <token>`.

Sync is offline-first: the app writes to Room, pushes its changed rows and pulls the rows changed since its last cursor, with last-write-wins on each row.

| Endpoint | Use |
| --- | --- |
| `POST api/flashcards/sync/push` | Sends the changed decks, cards, reviews and settings. |
| `GET api/flashcards/sync/pull?since=&cursor=&limit=` | Pages through the rows changed since a timestamp. |
| `GET api/flashcards/sync/status` | Server sequence number, row counts and last sync time. |
| `GET api/flashcards/sync/snapshot?cursor=&limit=` | Full download, used to rebuild the local database. Requires the access code. |
| `GET api/flashcards/ai/status` | Whether AI drafting is configured on the server. |
| `POST api/flashcards/ai/draft` | Drafts cards from a text. Requires the access code. |

Access code: the routes marked above require a daily 8-digit access code that the user types on a shuffled keypad. The app keeps it in memory until the day ends in the server's time zone (`America/Sao_Paulo`), sends it in the `code` header and drops it when the server rejects it. The app never derives the code.

The request and response shapes are in [`app/src/main/java/com/archieapps/oklo/core/net`](app/src/main/java/com/archieapps/oklo/core/net), and the recorded responses that pin them are in [`app/src/test/resources/contract`](app/src/test/resources/contract).

## Tests

```bash
./gradlew test
```

The unit tests cover FSRS against the reference vectors of py-fsrs 4.1.2, the API contract, the sync engine, the access code gate, the deck tree, the study queue and statistics. `SyncRoundTripIT` runs against a real server only when `OKLO_IT_BASE_URL` and `OKLO_IT_TOKEN` are set.
