# App architecture

The Android app stays in one build module. The code has three boundaries:

```text
TV screens -> Accounts -> AccountBackend -> Stremio account API
           -> Sources  -> SourceBackend  -> ContentSource -> Stremio add-on protocol
           -> PlayerActivity -> Media3, Device, audio settings
```

`Profile.account` owns My List and watch progress. `Profile.sourceAccounts` names the services that supply catalogs, details, streams, and subtitles. A profile can use one account service and a different content service. Old profiles load their Stremio account as both references. The app writes the new form when it next saves a profile.

Screens use `Accounts` for library data and `Sources` for content. `Stremio` handles its account API. `Addon` handles the Stremio add-on protocol. `ContentSource` supplies only the content features that a source supports. `Sources.forVideo` keeps the same order for automatic play and the source list. The add-on setup screen stays specific to Stremio.

To add an account service, implement `AccountBackend`, register it in `Accounts`, and add its sign-in UI. Convert its library data to the current `Library` item schema before it reaches a screen. To add a content service, implement `SourceBackend` and its `ContentSource` objects, register the backend in `Sources`, and add its source reference to a profile. Give each source a stable, unique ID and cache key. Map proprietary title IDs to stable app IDs before sources are combined.

Playback features stay in `PlayerActivity` and the Media3 audio and video path. Check the TV and receiver before selecting a codec or audio mode. A content source supplies a playable `Stream`; it does not control the player.

The current app still has Stremio sign-in UI and a Stremio-shaped library schema. A new account service needs its own sign-in flow and library conversion. A new content service needs an install or link flow. The app does not load executable plugins at runtime.
