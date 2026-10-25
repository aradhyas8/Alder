# Roadmap

What Alder is still missing compared to Stremio and to the paid services people add to it, and how each one should
work. Stremio itself is free: what people pay for sits around it (debrid, Trakt VIP, RPDB, MDBList, OpenSubtitles VIP).
Debrid and Usenet already work through stream add-ons, and subtitles through subtitle add-ons, so they aren't here.

Not planned: torrents without debrid (Alder has no streaming server, on purpose) and trailers (not for now).

## 1. Open in another player

The most important item on this list. ExoPlayer can't play everything, and some people simply prefer MX Player, VLC or
Just Player.

- **Settings → Player**: `Alder` (default), `Ask every time`, or one installed player picked from the list of apps
  that answer `ACTION_VIEW` for `video/*`.
- **Source list**: holding OK on a source adds "Play in…" next to the existing actions, for a one-off.
- **Hand-off**: `ACTION_VIEW` with the stream URL and `video/*`, plus the extras the big players read:
  - title: `title` (MX, Just Player, VLC)
  - start position: `position` in ms (MX, Just Player), `from_start=false` + `position` (VLC)
  - request headers from the stream's `behaviorHints.proxyHeaders`: `headers` as a flat `[key, value, …]` string array (MX)
  - subtitles Alder already found: `subs` / `subs.name` (MX), `subtitles_location` (VLC)
  - `return_result=true` so the player answers when it closes
- **Progress**: read the position back from the result (`position` from MX and Just Player, `extra_position` from VLC)
  and save it to the library the way `PlayerActivity` does, so Continue Watching, Watch Next and the other Stremio
  devices stay right. When a player returns nothing, keep the old position rather than marking it watched.
- What's lost in another player: skip intro, Up next, failing over to the next source. The setting's hint should say so.

## 2. Ratings and badges

Ratings come from add-ons, not from a service of Alder's own.

- **What the protocol gives us**: Stremio's meta format has one rating field, `imdbRating`, which Alder already shows.
  Today Alder ignores `links` (Cinemeta puts the IMDb link and rating there) and any vote count.
- **Default: show everything an add-on gives**, in a fixed order: IMDb (rating · votes), Rotten Tomatoes (critics,
  audience), Metacritic, TMDB, Letterboxd. One row of small badges with the source's mark, never wrapped.
- **Settings → Ratings**: one switch per source, separately for **posters** (a small corner badge) and the **details
  page** (the full row). Sources no add-on provides are shown disabled with "Needs an add-on that provides it".
- **When a source is missing**: on the details page, a quiet "More ratings" link that opens Add-ons with a
  suggestion, instead of empty badges.
- **To check before building**: which meta add-ons (AIOMetadata and others) send Rotten Tomatoes, Metacritic or vote
  counts as data, and in which fields. Many put them in the description text or bake them into the poster (RPDB), and
  neither can be shown as badges. If no common add-on sends them as data, decide between showing IMDb only and an
  optional MDBList key in Settings.

## 3. Cast and crew on the details page

- Read `cast`, `director` and `writer` (older string arrays) and `links` with category `Cast`, `Directors`,
  `Writers` (current format) from the meta. Alder's `Meta` reads none of these today.
- A row under the description: director first, then up to ~10 cast names. Where the meta add-on gives photos, round
  portraits; otherwise text chips.
- OK on a person opens Search with their name, which most catalog add-ons match. A page per person needs an add-on
  that offers it, so it isn't part of this.

## 4. Notifications in the profile avatar

New episodes of shows in your library, shown where the profile already sits instead of a separate bell.

- **Avatar**: a small dot on Home's avatar when there's anything unseen. The profile menu (now "Switch profile",
  "Settings") gets **New episodes** at the top, with a count.
- **New episodes screen**: one row per episode, newest first: show logo or poster, "S2 E5 · Title", "Out today" or
  the date. OK plays it (through the usual Play path), holding OK offers **Dismiss** and **Turn off for this show**.
- **How Stremio does it** (stremio-core `update_notifications.rs`), which Alder should match so both apps agree:
  - Which titles: library items with `state.noNotif` false, not movies, not `other`, no
    `behaviorHints.defaultVideoId`, not `removed`, not `temp`, newest `mtime` first.
  - Where the episodes come from: catalogs that support the `lastVideosIds` extra (Cinemeta does), asked with
    `lastVideosIds=<id>,<id>,…` (up to 100 ids, split by type). The answer is metas with `videos`.
  - When: at most every 6 hours.
  - What counts: an episode whose `released` is after the item's `state.lastWatched` and not in the future (or any
    released episode if it was never watched).
  - Dismiss: sets the item's `state.lastWatched` to now and saves it, so the notification is gone on every device.
  - "Turn off for this show" sets `state.noNotif = true`, which Stremio respects too.
- **Google TV**: optionally the newest unseen episode also goes to Watch Next as a "new episode" card, using the same
  code that publishes Continue Watching.
- PIN-protected profiles: nothing outside the app (Watch Next, system notifications) before the PIN is entered, as
  Watch Next does today.

## 5. Airing this week (future)

A Home row of episodes from library shows that come out in the next 7 days, and maybe a calendar later.

- **How Stremio does it** (stremio-core `models/calendar.rs`): it asks catalogs that support the `calendarVideosIds`
  extra (Cinemeta does) with the ids of every library item that isn't `removed` or `temp` (up to 100, newest
  `mtime` first). It then groups the returned metas' `videos` by the day of their `released` date. Stremio's calendar
  shows a month; Alder only needs today to +7 days.
- **Row**: "Airing this week" on Home and the Shows page, under Continue Watching. Cards show the episode still or the
  show's backdrop with "Today", "Tomorrow" or the weekday, and "S2 E5". It's hidden when empty.
- Episodes already out but unwatched belong in notifications (section 4), not here, so the two don't repeat each other.
- Both features can share one request: a catalog that answers `calendarVideosIds` gives the full episode list with
  dates, which covers notifications too.

## 6. Trakt scrobbling

Trakt add-ons (MyTrakt, Trakt Lists, Syncribullet) only give catalogs, plus "Mark watched" placeholders in the
source list. The Stremio add-on protocol has no playback events, so no add-on can know what you're watching.

Stremio scrobbles from the app itself. In stremio-core `models/player.rs` the player emits `TraktPlaying` and
`TraktPaused` on play, pause, seek and stop. `stremio-core-web` sends them as `traktPlaying` / `traktPaused`
analytics events to Stremio's API (`events`, with the profile's auth key), and Stremio's servers pass them on to
Trakt for accounts that connected Trakt in Stremio's settings.

That means **today, what you watch in Alder never reaches Trakt**, even when the account has Trakt connected.
Only the library progress syncs. To match Stremio:

- When the account has Trakt (the user object's `trakt` with an unexpired token), send the same events from
  `PlayerActivity`: playing on start, resume and after a seek; paused on pause, seek and leaving. The payload is
  `player`: `libItemID`, `libItemType`, `libItemName`, `libItemVideoID`, `libItemTimeOffset`,
  `libItemTimeDuration`, `playerDuration`, `playerVideoWidth`, `playerVideoHeight`, `hasTrakt`.
- Check this end to end on a test account before relying on it: watch in Alder and see whether it shows up on Trakt.
