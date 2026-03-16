# Tour Guide Android App Spec

## Product Summary

An Android app that notices where the user is, infers what is interesting nearby, and delivers concise, useful context for travelers and urban explorers without aggressively draining battery or collecting more location data than necessary.

The app should feel like a quiet companion:
- aware of place changes
- selective about when it interrupts
- transparent about privacy
- configurable for users who want to bring their own AI provider key

## Product Goals

- Give users timely, contextually relevant information about their surroundings.
- Support low-friction use while walking around a city with the phone in a pocket.
- Favor battery efficiency over turn-by-turn precision.
- Keep AI integration provider-agnostic through user-supplied API keys.
- Use modern Android architecture and APIs without legacy compatibility driving the design.

## Non-Goals

- Full navigation or route guidance.
- Social features, accounts, or cloud sync in v1.
- Continuous high-accuracy tracking.
- A generic travel planner or booking app.
- Hardcoding the product to a single AI provider.

## Primary Use Cases

- A user arrives in a new city and wants lightweight, relevant facts about nearby landmarks, neighborhoods, architecture, and local culture.
- A user walks through an unfamiliar area and receives occasional insights when they appear to have meaningfully changed location.
- A user walks with earphones connected and listens to live spoken insights as they enter new areas.
- A user taps into the app to browse recent places, review past insights, and replay narration on demand.
- A privacy-conscious user supplies their own AI API key instead of relying on an app-managed backend key.

## Core Product Principles

- Relevance over volume: fewer, better insights.
- Background by default, but conservative: wake up sparingly and avoid chatty polling.
- User agency: API key, notification frequency, and background behavior should be configurable.
- Local-first privacy posture: keep as much raw context on-device as practical.
- Explainability: the user should understand why the app surfaced an insight.

## MVP Scope

### 1. Onboarding

- Explain the product clearly in 2 to 3 screens.
- Ask for location permission in stages:
  - foreground location first
  - background location only after user sees clear value
- Ask for notification permission when the app is ready to deliver insights.
- Let the user choose an AI provider and enter an API key.
- Validate the key with a lightweight test request when possible.

### 2. Background Location and Place Detection

The app must support semi-precise but battery-efficient background location.

Requirements:
- Default to coarse or balanced-power location behavior unless the user explicitly enables a higher precision mode later.
- Treat movement in terms of meaningful place changes, not continuous tracking.
- Prefer opportunistic signals over frequent active polling.

Intended approach:
- Use Fused Location Provider with balanced power accuracy.
- Request updates with wide intervals and meaningful displacement thresholds.
- Use passive location updates when available.
- Use geofencing for dwell/exit around inferred or saved places when that reduces polling.
- Use WorkManager for deferred background processing.
- Avoid long-running foreground services for normal operation.
- Only escalate to foreground service behavior for user-visible, explicitly enabled modes if ever added later.

Behavioral rules:
- Do not generate a new insight for small jitter within the same area.
- Detect probable place visits using a combination of:
  - distance threshold
  - dwell time
  - activity confidence if useful
- Apply cooldown windows to prevent repeated insights in the same zone.
- Suspend or greatly reduce background work when battery saver is on.

Success criteria:
- The app should work well while walking in a city.
- The app does not need exact doorway-level accuracy.
- Background operation should be defensible to the user in battery settings and permissions rationale.

### 3. Nearby Context Gathering

For each meaningful place visit, gather context from on-device and network sources.

Potential inputs:
- current coarse location
- reverse geocoded area name
- nearby points of interest
- local categories such as museum, square, historic district, waterfront, transit hub
- time of day
- optional visit history for deduplication

Requirements:
- Cache recent place lookups locally.
- Deduplicate similar insights for the same area.
- Gracefully degrade when network access is unavailable.
- Distinguish location permission failures from "no location fix yet" failures so manual testing on cold-start devices and emulators is understandable.
- Use Google Places nearby search when a Google Maps Platform key is configured, with Android geocoder fallback if the Google request is unavailable or not configured yet.

Architectural rule:
- Coordinates alone are not enough input for AI insight generation.
- The app should first resolve coordinates into grounded place context such as area labels, nearby POIs, and lightweight metadata.
- AI should operate on curated place/context inputs, not act as the primary source of truth for "what is nearby."

### 4. AI-Generated Insights

The app uses AI to turn nearby context into short, interesting, useful information.

Requirements:
- User provides their own API key.
- App stores the key securely on-device.
- App can support multiple AI providers behind a common interface.
- App should also support a separately configured Google Maps Platform key for nearby place enrichment.
- App should support an app-owned Google Maps SDK key for the embedded home-screen map, separate from any user-supplied Google Places key.
- Prompts should request concise, factual, non-hallucinatory outputs with uncertainty acknowledged when needed.
- Outputs should be structured enough for UI rendering, narration, and local history review.
- The first native OpenAI integration should use the modern Responses API rather than legacy chat completions.

Initial output format:
- title
- short insight summary
- why this matters here
- confidence or certainty note

Guardrails:
- Prefer grounded prompts with explicit place/context inputs.
- Avoid presenting invented facts as certain.
- Do not reference raw coordinates or house numbers directly in generated user-facing insight text.
- Street or place names without house numbers are acceptable when they help orientation.
- If source context is too weak, the app should skip the notification rather than fabricate value.

AI responsibility boundary:
- AI is responsible for interpretation, summarization, and style adaptation.
- The deterministic app layer is responsible for sensing, candidate place gathering, cooldowns, deduplication, and surfacing decisions.
- The app may ask AI to choose among a short list of candidate places, but should not rely on AI alone to discover nearby places from raw coordinates.

### 5. User Experience

Primary surfaces:
- Home screen built around a full-screen map, a top-bar mode toggle, a primary on-demand insight action, and the latest insight in a bottom sheet.
- Home screen diagnostics should be hidden by default and exposed through an overflow action rather than occupying the main surface.
- Feed/history screen showing recent place-based insights.
- Detail screen for one insight, including replayable narration and generation metadata.
- Detail screen should also expose a metadata view showing the generation-time tone, interests, custom prompt, and confidence note used for that insight.
- Mode setup and playback controls for live audio behavior.
- Settings screen for provider setup, permissions, frequency, and privacy controls.

Operating modes:
- `Live` mode:
  - delivers spoken insights as the user reaches meaningful new places
  - should avoid rapid back-to-back narration
  - should favor shorter, more conversational insight formats
- `Popups` mode:
  - delivers asynchronous notifications when the user reaches an interesting place
  - user opens the notification to read or listen later
  - should work well even when no audio device is connected

Home screen rules:
- The embedded map should be the primary full-screen canvas of Home.
- The map should center on the latest resolved/current place and show a single "you are here" marker.
- If no map key or current place is available, Home should show a graceful fallback state instead of a broken map.
- Home should persist the last resolved place locally and use it as the startup placeholder while a fresh location lookup is in progress.
- Manual insight should be the primary explicit action on Home and should be presented as a floating action button over the map.
- The current mode should be switchable from the Home top bar without leaving the screen.
- The latest generated insight should appear in a draggable bottom sheet that opens partially expanded by default, can expand until its top reaches the system safe area, exposes its primary actions near the top for quick access, and can be dismissed and reopened.
- Diagnostics should include coordinates, resolved place label, full address, last updated time, and place source/provider when available.

Notification behavior:
- Occasional, high-signal notifications only.
- Notification copy should explain what triggered it, for example "Near the old town" rather than raw coordinates.
- Respect quiet hours if configured.

Live audio behavior:
- Prefer text-to-speech for generated insights in MVP.
- Only speak when the app has enough confidence that the user changed to a genuinely new area.
- Respect audio focus and avoid interrupting active calls or other critical audio sessions.
- The app should not model specific output devices like headphones versus speaker as a product concept.
- In live mode, audio just plays through the device's current Android-managed audio route.

User controls:
- operating mode selection: live or popups
- enable/disable background insights
- notification frequency: low, medium, high
- live narration frequency: sparse, normal, frequent
- areas of interest selection, for example history, architecture, food, culture, nature, hidden gems, trivia
- output tone selection, for example factual, playful, guide-like, academic, concise
- custom prompt field for advanced users
- preferred output language
- preferred TTS locale
- AI provider selection
- API key entry and validation
- clear history
- delete locally stored data

Personalization rules:
- Frequency settings must influence both place-detection cooldowns and how often insights are surfaced.
- Areas of interest should shape both nearby-context filtering and the AI prompt.
- Tone should affect wording style, not factual grounding requirements.
- A custom prompt must be treated as an additive user preference layered on top of app safety and structure constraints, not a full override of core guardrails.

### 6. Privacy and Security

- Store API keys using Android Keystore-backed encryption.
- Store only the minimum location history needed for product behavior.
- Retain local place history and generated insights until the user deletes them.
- Make it clear what data is sent to the selected AI provider.
- Never log raw API keys.
- Provide clear in-app history review and deletion controls.

## Functional Requirements

### FR-1 Place Awareness

- The app shall infer when the user has entered or dwelled in a meaningfully different area.
- The app shall avoid producing repeated events caused by GPS drift.
- The app shall continue basic place awareness when the app is not open, subject to OS constraints and granted permissions.

### FR-2 Insight Generation

- The app shall generate an insight only when enough context is available.
- The app shall associate each insight with a place label and timestamp.
- The app shall allow the user to reopen recent insights.
- The app shall support distinct delivery behavior for live spoken insights and popup notifications.
- The app shall tailor generated output according to user-selected interests, tone, and optional custom prompt instructions.
- The app shall expose AI generation through an internal provider interface so native provider adapters and OpenRouter-style adapters can coexist.

### FR-3 AI Key Management

- The app shall allow manual entry of one or more provider API keys.
- The app shall securely persist keys on-device.
- The app shall allow the user to replace or remove keys at any time.
- The app shall avoid bundling secret provider keys in the client app.
- The app shall support both native provider keys and, later, aggregator keys through the same settings model.

### FR-4 Settings and Transparency

- The app shall explain active permissions and why they are needed.
- The app shall expose whether background insights are active.
- The app shall let the user pause background behavior without uninstalling.
- The app shall let the user choose between live and popup operating modes.
- The app shall let the user configure insight frequency, interest categories, tone, and custom prompt preferences.
- The app shall support an output language preference.
- The app shall support a separate TTS locale preference for narration.
- The app shall let the user review stored place visits and generated insights.
- The app shall let the user delete individual history items and clear all local history.

## Non-Functional Requirements

### Battery

- Low-power operation is a first-class requirement.
- Background sensing should bias toward coarse, infrequent, opportunistic updates.
- Insight generation should be rate-limited.

### Performance

- Home screen should open quickly from cached local state.
- Background work should be short-lived and cancellable.

### Reliability

- App should tolerate missing network, missing provider key, and revoked permissions without crashing.
- Background behavior should degrade gracefully under OS restrictions.

### Maintainability

- Codebase should be modular from the start.
- Business logic should be testable without Android framework dependencies.

## Technical Direction

### Getting Started Decisions

These defaults should be treated as settled enough to scaffold the project unless implementation reveals a strong reason to change them.

### Product Identity

- App name: `Tournarrat`
- Package/application id: `net.sagberg.tournarrat`
- Initial device focus: Android phones first

### Android Baseline

- Kotlin-only codebase
- Jetpack Compose UI
- Material 3
- Minimum SDK: API 33
- Target SDK: latest stable available at implementation time
- Compile SDK: latest stable available at implementation time

Rationale:
- A modern minimum SDK reduces compatibility drag around permissions, background work, and UI.
- This app depends on modern location behavior and does not benefit enough from supporting old Android versions.

### Initial Architecture Defaults

- Modular project from day one
- MVVM plus unidirectional state flow
- Koin for dependency injection
- DataStore-backed local persistence for preferences and history in the first version
- DataStore for preferences
- WorkManager for deferred/background processing
- Fused Location Provider for sensing
- Native Android TextToSpeech behind a narrator interface for the first live-mode implementation

### Initial Integration Strategy

- Use a hybrid AI integration approach:
  - implement OpenAI as the first native provider adapter
  - keep the interface compatible with adding an OpenRouter adapter next
- Use Google Places nearby search as the first network place-context provider implementation, with Android geocoder fallback and room to add Google reverse geocoding next.
- Place and reverse-geocoding providers should also sit behind an abstraction boundary.
- Provider-specific request and response details must stay out of UI code.
- Do not couple app logic to provider SDKs unless a specific provider later justifies it.

### Initial Product Defaults

- Default mode: `Popups`
- Default insight frequency: `Low`
- Default tone: `Factual`
- Default interests: history, architecture, culture, hidden gems
- Default output language: English
- Live mode should start conservative and remain mostly quiet.
- Local history retention default: keep until user deletes

### Bootstrap Deliverables

The first coding pass should produce:
- Android project scaffold
- modular Gradle setup
- Compose app shell and navigation
- onboarding flow
- settings screen with provider key input and preference controls
- foreground location permission flow
- manual "what is interesting here?" action
- storage foundations for preferences, secure key handling, and cached insights

Settings screen rules:
- Stored provider API keys should remain visible and editable after they have been saved locally.
- Frequency controls should keep their labels on a single line when standard phone width allows it.
- Speech locale selection should use a compact dropdown rather than a chip grid.

### Decisions Still Needed Before Coding Starts In Earnest

- none

### Platform

- Android only.
- Kotlin only.
- Jetpack Compose for UI.
- Coroutines and Flow for async and state.
- Modern AndroidX libraries and current stable tooling.

### Architecture

Recommended architecture:
- unidirectional data flow
- MVVM at screen boundaries
- repository pattern for data access
- use-case layer for domain logic where it adds clarity

Key interfaces:
- `AiClient` for provider-specific text generation adapters
- `Narrator` for spoken delivery, with native Android TTS as the first implementation
- `PlaceContextProvider` for nearby place and reverse-geocoding data

Responsibility split:
- deterministic services should handle location change detection, place lookup, deduplication, and delivery gating
- AI services should handle ranking assistance, insight generation, and wording

Recommended module split:
- `app`
- `core:model`
- `core:data`
- `core:location`
- `core:ai`
- `core:database`
- `feature:onboarding`
- `feature:home`
- `feature:history`
- `feature:settings`

This can be simplified early if needed, but the boundaries should remain clear.

### Data Storage

- Room for local structured storage such as places, insights, and history metadata.
- DataStore for user preferences and lightweight settings.
- Encrypted local storage for API keys and sensitive configuration.

### Background Execution

- WorkManager for scheduled and deferred work.
- Fused Location Provider and related Google Play Services location APIs for sensing.
- Geofencing or passive updates where they materially reduce battery cost.

### Dependency Injection

- Use Koin for dependency injection to keep wiring straightforward under the current AGP 9 setup.

## Domain Model

Initial entities:
- `PlaceVisit`
  - id
  - timestamp range
  - coarse latitude/longitude
  - area label
  - confidence
- `NearbyContext`
  - place visit id
  - poi summaries
  - area metadata
  - time context
- `GeneratedInsight`
  - id
  - place visit id
  - delivery mode
  - title
  - summary
  - rationale
  - confidence note
  - created at
- `UserExperiencePreferences`
  - operating mode
  - insight frequency
  - interest categories
  - tone
  - custom prompt
  - output language
  - TTS locale
  - live audio fallback behavior
- `AiProviderConfig`
  - provider id
  - key alias or secure reference
  - validation state
  - provider type

## Insight Pipeline

1. Detect probable meaningful location change.
2. Resolve area and nearby context.
3. Check cooldowns, history, and deduplication rules.
4. Build grounded AI prompt from structured context plus user preferences.
5. Request concise insight from selected provider.
6. Persist result locally.
7. Surface via narration or notification and in-app feed if quality threshold is met.

## UX Rules

- Do not overwhelm the user during the first day of use.
- Notifications should be sparse by default.
- Live mode should be quieter than a human tour guide and should allow long silent stretches.
- If background permission is denied, foreground/manual use should still be useful.
- The app should still provide value when opened manually, even without background permission.
- Home should prioritize place awareness and on-demand insight generation over diagnostics or configuration details.

## Failure Handling

- No provider key:
  - show setup state and allow manual exploration without AI output where possible
- No network:
  - show cached insights and skip generation
- Permission revoked:
  - downgrade to manual/foreground mode and explain the limitation
- Live mode unavailable:
  - fall back to popup mode or pause insight delivery according to user preference
- Low confidence context:
  - skip notification

## Data Retention

- Keep local location-derived history and generated insights until the user deletes them.
- Users must be able to browse previously detected place visits and produced information.
- Users must be able to delete individual items as well as clear all retained local history.
- Retention is local-device only by default unless a future sync feature is explicitly added.

## Out of Scope for MVP

- Wear OS support
- iOS app
- user accounts
- cloud backup
- collaborative or social features
- continuous route-based audio tours
- AR overlays
- offline vector maps

## Open Questions

- How much control should advanced users have over prompt templates before the UX becomes too complex?

## Localization Direction

- The first release targets English as the default and only fully validated language.
- The architecture should support expanding to any language supported by the configured AI provider and available TTS engine voices.
- Language should remain a user preference rather than a hardcoded product assumption.
- Narration locale should be independently configurable from AI output language.
- If the chosen language is unsupported by the active TTS engine or provider combination, the app should fall back gracefully and explain the limitation.

## Audio Routing Principle

- The app should rely on standard Android audio routing rather than treating headphones and speaker as separate primary modes.
- Product settings should not expose low-level route selection or public/private playback toggles in v1.

## Initial Delivery Phases

### Phase 1

- project scaffolding
- onboarding
- provider key setup
- foreground location
- manual "what is interesting here?" flow
- user preference model for mode, frequency, interests, tone, and custom prompt

### Phase 2

- battery-aware background place detection
- local history
- notifications
- popup mode tuning
- insight deduplication and cooldowns

### Phase 3

- OpenRouter adapter
- additional native provider adapters if needed
- richer nearby context
- live spoken mode with text-to-speech
- tuning based on real battery and relevance observations
