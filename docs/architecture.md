# App architecture

The Android app stays in one build module. Built-in adapters connect at these boundaries:

```text
TV screens
  -> Profile: one library account + ordered content connections
  -> Accounts -> AccountBackend -> Stremio account API
  -> Sources  -> SourceBackend  -> ContentSource -> Stremio add-on protocol
  -> StreamCandidate -> StreamResolution -> playable URL -> PlayerActivity -> Media3
  -> ProfileStore + owner-scoped HTTP cache
```

`Profile.account` owns My List and watch progress. `Profile.sources` holds separate `SourceRef` connections. Old Stremio profiles load as one account and one source connection. A profile can keep a Stremio source when its account changes. Source order is explicit; a failed source does not hide a healthy source.

Screens use `LibraryItem` and `LibraryProgress`. Each account adapter converts its own wire data and preserves fields that the app does not model. Stremio JSON and watched-episode bitfields stay inside `Stremio`. `Meta` and `Video` keep raw protocol IDs for requests and a `ContentIdentity` for app matching. The Stremio adapter marks exact IMDb IDs as shared; other IDs keep their provider and source scope.

`LibraryProgress` also keeps `videoIdentity`, so the resume point follows the source and not only the raw video ID. An account adapter that stores only the raw ID writes an identity with an empty `sourceId`. `ContentIdentity.sameContent` then compares the provider and the ID. A watched list that the adapter cannot decode stays `null`, so a later save cannot remove the stored history.

Each `ContentSource` compares with `fromSource(provider, sourceId)`. `List<ContentSource>.preferred` is the only source-order rule. Screens, `DetailsActivity`, `PlayerActivity`, and `MainActivity` call it; none keep a copy.

`ContentSource` can return direct URLs or candidates that need a resolver. Resolution runs before player handoff. A hash without a registered resolver stays unplayable. A resolver result must use `http` or `https` and must have a host. Stream order, device checks, retries, and audio handling remain in the existing playback flow. Lossless audio support belongs to the player and device checks, not a content source.

`ProfileStore` owns profile records; `SharedPreferencesProfileStore` is the current implementation. HTTP answers use separate account or profile/source cache scopes. Removing a profile clears its scoped answers without clearing another profile's. Cache deletes run on one background thread, so a large cache does not stop the screen. Old flat cache files have no owner marker and remain until normal cache trim removes them.

Stremio add-on install, remove, and order all go through `StremioAddonManagement`. A screen passes the typed address and the cache owner; the adapter reads the manifest and returns the installed add-on.

To add an account provider, implement `AccountBackend`, register it in `Accounts`, and add its sign-in UI. To add a content provider, implement `SourceBackend` and `ContentSource`, register the backend in `Sources`, and add a way to link a `SourceRef`. New provider management UI can use its own model. Stremio add-on management is specific to Stremio. An optional resolver can turn supported stream candidates into playable URLs. The app does not load executable add-on code on the TV.

The branch adds the extension points. It does not include a second real provider, a torrent engine, a new sign-in page, or a TV playback check. A provider still needs its protocol adapter, account or source setup, and focused compatibility tests.
