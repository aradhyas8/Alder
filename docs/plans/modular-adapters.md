# Modular adapter migration plan

## Goal

Keep the current Stremio experience. Let one profile choose one service for My List and watch progress, and several ordered services for catalogs, details, subtitles, and streams. A future service can use a different add-on protocol. The player receives a playable URL, regardless of where a stream starts.

Keep one Android build module. Register built-in adapters in one place. Do not load executable code from add-ons on the TV.

```text
TV screens
  -> profile (one library owner, ordered source connections)
  -> app services (library, content, source management)
  -> account and source adapters (Stremio first)
  -> stream candidates -> optional resolver -> playable URL -> Media3
  -> local store and cache (scoped to profile and source)
```

## Current state

The branch has `Accounts`, `Sources`, `AccountBackend`, `SourceBackend`, and `ContentSource`. Most screens now call those services. `Profiles.kt` can read old Stremio profile records. `Sources.forVideo` gives automatic play and the source list the same source order.

The work is not complete. `Profile.sourceAccounts` uses `AccountRef` for content connections. `AccountBackend` and `Library.kt` pass Stremio-shaped `JSONObject` items to screens. `Meta`, `Video`, settings, intents, and watch progress use plain IDs without a source scope. `Stream` requires a URL; `Addon.streams` counts hash-only answers but cannot play them. `AddonsActivity` and sign-in in `ProfilesActivity` are specific to Stremio. `Http.forget()` clears all saved answers when one profile leaves.

## Architecture rules

1. One account owns a profile's My List and watch progress. Do not merge two accounts' watch data by default.
2. A profile has ordered source connections. A source connection has its own provider ID, credential, and settings. It can be linked without changing the account that owns watch data.
3. Keep title and video IDs with their source. Use a verified common ID only when two sources refer to the same title. Do not merge titles by name.
4. Each adapter converts its wire data at its boundary. Screens use app models. Stremio JSON stays in the Stremio adapter.
5. A source can return a URL, a hash, or another candidate. A resolver produces a playable URL. The player never receives an unresolved hash.
6. Source order, partial failure, cache scope, and account expiry have one rule in app services. A failed source does not hide a healthy source.
7. Audio modes and decoder checks stay in playback. Lossless audio is a player capability, not a content plugin.

An account change does not copy watch history by default. A future import needs a separate flow with a clear choice about which account owns each item. A new add-on protocol needs a short compatibility check before work starts. The adapter can support only the capabilities that protocol supplies; this plan does not promise that every add-on will work.

## Work packages

### 1. Protect the current path

Add tests for old profile records, re-sign-in, source order, empty and failed add-ons, library changes, source selection, and player handoff. Use current Stremio behavior as the expected result. Keep the TV layout and navigation the same.

**Files:** `ArchitectureTest.kt`, `StreamsResolverTest.kt`, library tests, and focused tests for the changed services.

**Done when:** the tests cover both the old saved record and the new record, and the current test suite passes.

### 2. Separate account and source connections

Replace `sourceAccounts: List<AccountRef>` with a distinct `SourceRef` list. Add profile operations to link, remove, and order sources. Migrate both old `authKey` records and the branch's `sources` records. Preserve PINs, settings, Stremio keys, and shared-source choices. Re-sign-in updates the matching Stremio source credential, but an Alder account can keep a Stremio source.

**Files:** `Profiles.kt`, `Accounts.kt`, `Sources.kt`, `ProfilesActivity.kt`, `SettingsActivity.kt`.

**Done when:** tests prove that a profile can have an Alder account and a Stremio source, and can have two ordered sources with no shared credential field.

### 3. Give library data an app model

Define only the fields the app uses for My List, resume, watched episodes, and time stamps. Keep Stremio's extra fields inside its adapter so a save round trip does not erase them. Move Stremio's watched bitfield conversion next to the adapter. Route Home, Details, Player, Settings, profile previews, and Google TV Watch Next through the app model.

**Files:** `Library.kt`, `Accounts.kt`, `Stremio.kt`, `MainActivity.kt`, `DetailsActivity.kt`, `PlayerActivity.kt`, `SettingsActivity.kt`, `ProfilesActivity.kt`, `WatchNext.kt`.

**Done when:** no screen reads or edits Stremio library JSON, and tests prove that a Stremio save keeps unknown fields and watched episodes.

### 4. Give content stable identity and source management

Add a source-scoped title and video key to the app models and to screen intents, saved choices, and watch progress. Map Stremio IDs without changing current links. Keep a common IMDb or other verified ID as an optional match key. Move add-on list, install, remove, and order operations behind a source-management capability. Keep the current Stremio add-on page as its first UI. Do not make every source implement management if it has no add-ons.

**Files:** `ContentSource.kt`, `Addon.kt`, `Sources.kt`, `AddonsActivity.kt`, `MainActivity.kt`, `DetailsActivity.kt`, `SearchActivity.kt`, `SettingsActivity.kt`, `WatchNext.kt`, and intent helpers.

**Done when:** two fake sources can use the same raw ID without mixing titles, progress, or settings. A managed Stremio add-on still keeps its install and order behavior.

### 5. Resolve streams before playback

Separate a source's stream answer from a playable stream. Keep direct URLs and request headers working. Represent a hash as a candidate, not as a fake URL. Add a resolver capability that can turn a candidate into a playable URL. If no resolver supports it, show the current clear no-source reason. Keep ranking, device checks, retries, and fallback in the existing stream flow.

**Files:** `Addon.kt`, `ContentSource.kt`, `Sources.kt`, `StreamsResolver.kt`, `StreamsActivity.kt`, `PlayerActivity.kt`.

**Done when:** URL playback is unchanged, a fake hash resolver hands a URL to the player, and an unresolved hash never reaches Media3. This package does not add a torrent engine.

### 6. Isolate owned storage and cache scope

Put profile records and any Alder-owned watch data behind a small local store. Keep `SharedPreferences` as the first implementation. Keep HTTP response caching separate. Scope saved answers by provider, source connection, and profile where data is private. Remove only one owner's saved data when that owner leaves; keep other profiles' data. Preserve old records through a tested migration.

**Files:** `Profiles.kt`, `SettingsActivity.kt`, `Http.kt`, `Stremio.kt`, `Sources.kt`, and storage tests.

**Done when:** changing the local store implementation does not change screens or provider adapters, and removing one profile does not clear another profile's saved answers.

### 7. Prove the extension points

Add test adapters for a second account and a second content protocol. Keep them out of release UI. Test an Alder-style account with a Stremio source, two content sources with different outcomes, and a source that gives a hash. Add provider selection or link UI only when a real provider is ready.

**Done when:** the test adapters need no edits in `Stremio.kt`, `PlayerActivity.kt`, or the TV screens. Run unit tests and a debug build. Check sign-in, My List, Details, Search, sources, playback, and progress on a TV before merge.

## Delivery

Use small PRs in the order above. Each PR includes a changelog entry and its tests. Keep the old Stremio path working at every step. The current provider PR is the starting point; it does not satisfy the final checks above yet.
