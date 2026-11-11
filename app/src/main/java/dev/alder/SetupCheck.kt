package dev.alder

// The setup check: before anyone presses Play, each stream add-on is asked for a well-known title, and the screen says
// what stops it from playing (no stream add-on, a refused key, torrents without a debrid service). It answers the
// "I see titles but nothing plays" reports from new users. The screen itself is ProfilesActivity.showCheck.

/** Titles every general stream add-on knows: a film, then an episode for add-ons that have only series. */
internal val SETUP_PROBES = listOf("movie" to "tt0111161", "series" to "tt0903747:1:1")

/** What the setup check asks an add-on for, given what it [serves], or null when it knows neither title (anime only). */
internal fun setupProbe(serves: (type: String, id: String) -> Boolean) = SETUP_PROBES.firstOrNull { (type, id) -> serves(type, id) }

/** The first add-on a phone can fix: it found only torrents (no debrid service yet) or refused its key. */
internal fun fixableAddon(configurable: List<Boolean>, failures: Array<Throwable?>, torrents: IntArray): Int? =
    configurable.indices.firstOrNull { i -> configurable[i] && (failures[i]?.let(::refusedKey) ?: (torrents[i] > 0)) }

/** Sign in with Apple can hide the address behind a relay, which tells the viewer how this account signs in. */
internal fun appleRelay(email: String) = email.endsWith("@privaterelay.appleid.com", ignoreCase = true)
