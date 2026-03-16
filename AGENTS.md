# Repository Rules

- `SPEC.md` is the authoritative product and feature specification. Keep it updated whenever scope, behavior, architecture, or priorities change.
- Put feature-specific requirements, tradeoffs, open questions, and acceptance criteria in `SPEC.md`, not in `AGENTS.md`.
- Keep `AGENTS.md` limited to durable repo workflow rules that are broadly useful across features.
- If something unexpected is learned during implementation and it would be generally useful for future contributors, add that rule to `AGENTS.md`.
- Use Java 21 for this repo. Prefer `sdkman` with `.sdkmanrc`, and avoid Java 25 for Android builds because it has already caused toolchain issues here.
- Prefer formal Android smoke coverage in `app/src/androidTest` and keep shell-based `adb` scripts as thin wrappers around instrumentation runs instead of UI-scraping logic.
- Treat the D8 `Companion could not be found in class com.google.android.gms.internal.location.zze` warning from `com.google.android.gms:play-services-location` as a known upstream artifact issue unless it starts failing builds or causing runtime behavior changes.
