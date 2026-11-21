# Changelog

Write each entry the way you would advertise it to a viewer on the sofa:

- Lead with what the viewer gets, in their words. Say "Torrentio without Real-Debrid", not "P2P sources".
- Start each entry with a short **bold** headline, then one or two short sentences. Keep spotlight entries under 25 words.
- Use the names that Alder shows on the screen, in **bold**. Say where to find a setting.
- Put each entry under one heading: `### New`, `### Easier to use` or `### Fixes`, in that order, most important first.
- Group small fixes by where the viewer sees them: "Small fixes for Google TV: …".
- Put `<!-- Spotlight: whats-new/name.webp -->` before at most five entries to show them first in **What's new**, with a
  1920×1080 TV screenshot saved as a 960×540 WebP in `app/src/main/assets/whats-new/`. Remove the old pictures after a release.

Put changes that are not released under `## Unreleased`. Before each release, read all of `## Unreleased` again and rewrite
each entry that does not follow these rules. Then rename that heading to the version number.

## Unreleased

### New
<!-- Spotlight: whats-new/p2p.webp -->
- **Torrentio without Real-Debrid.** Torrent add-ons like Torrentio now play right in Alder. No debrid account needed. When nothing else plays, Alder tries a torrent for you.
<!-- Spotlight: whats-new/discover.webp -->
- **Discover anything.** Browse every movie, show and anime, not only what your add-ons list. Filter by genre, decade or the streaming services in your country.
<!-- Spotlight: whats-new/anime.webp -->
- **Anime gets a home.** Anime, video channels and other catalogs get their own tabs. Anime gets episode lists, **Next episode** and **Continue**, like any show.
<!-- Spotlight: whats-new/streams.webp -->
- **More streams show up.** Alder now asks every add-on that Stremio asks, and gives slow debrid add-ons 45 seconds to answer. Fewer "no streams found".
<!-- Spotlight: whats-new/speed.webp -->
- **Watch at your speed.** Play from 0.75× to 2× with the speed button next to Pause. Alder remembers the speed for each show, so the next episode keeps it.
- **Fill your screen.** Movies with black bars can fill the TV. In the player, select **Audio & subtitles → Sync & Style → Picture**, then **Zoom** or **Stretch**.
- **Big text mode.** Larger text, posters and buttons everywhere, for big rooms and far sofas. Select **Settings → Picture & Sound → Text and poster size → Large**.
- **Use VLC or Just Player.** Alder picks the stream, the subtitles and your resume point, and your favourite player plays it. Select **Settings → Streams → Video player**.
- **Full Dolby TrueHD and DTS-HD.** Lossless audio goes straight to your soundbar or receiver. If it fails, Alder plays the same track as PCM.
- **Letterboxd ratings.** See a film's Letterboxd score next to IMDb, or instead of it. Select **Settings → Profile → Ratings**. It needs a free MDBList key.
- **New episode badges.** Posters show **+1** when a new episode is out. To count the rest of the season instead, select **Settings → Profile → New episode badges**.
- **Alder speaks Arabic.** Select **Settings → Language → App language → العربية**.
- **Send a crash report.** If Alder stops, it offers to send a report the next time you open it. Reports never include your account.

### Easier to use
- **A fresh look.** Smooth motion, one poster style on every screen, and cleaner **Home**, **Search** and player screens.
- **My List is now My Alder.** A full poster grid with **Genre** and **Year** filters, and a check before you remove a title.
- **Choose your tabs.** Show, hide or reorder any tab, add-on tabs included. Select **Settings → Profile → Tabs**.
- **Switch streams without losing your spot.** **Change stream** keeps the video behind the list. The new stream starts at the same second.
- **A richer source list.** See size, audio (**DD+ 5.1**), seeders and debrid status. Or show each add-on's own text: **Settings → Streams → Source list**.
- **Smarter Search.** **The Matrix** comes before a short film called "Matrix", and Search shows all your tabs.
- **Subtitles that fit.** Subtitles for your exact release come first. New **Gray** subtitles are easier on OLED TVs in a dark room.
- **A faster Home.** Your rows show right away while fresh data loads, and they scroll smoothly even when you hold Right.
- **Each row keeps its place** on **Shows** and **Movies**.
- **A new What's new.** The biggest changes come first, each with a picture from the TV. Every other change is listed below them, by type.
- **Errors tell you what to do.** Messages name the add-on or service that failed and say what to try next.

### Fixes
- **Your place is safe.** Progress syncs correctly with your phone, survives going offline, and is saved the moment you stop.
- **Resume works everywhere.** **Continue Watching**, the title page and the player agree on where you stopped.
- **Skip intro shows up more often,** including on anime, on more MKV files, and when you go back into an intro.
- **One bad add-on can't empty Home anymore,** and adding an add-on can't wipe your add-on list.
- **4K remuxes buffer less.** Alder keeps up to 200 MB of video ready, so big files stop less often to load.
- **The source list says why.** It names the add-ons that found nothing, and the reason.
- **Fewer stream switches.** Short network blips no longer jump you to another stream.
- **Subtitles from every add-on.** SubDL, OpenSubtitles, zip files, Arabic, Polish and Russian all show correctly.
- **Leave and come back safely.** Paused stays paused, and nothing starts playing in the background.
- **Small fixes for Google TV:** a clear app icon, and a correct **Watch Next** row.
- **Small fixes for older TVs on Android 6 and 7:** Alder starts again, and uses less memory, so fewer crashes.
- **Small fixes for 4K TVs:** 4K films show **4K** and are picked first with **Best picture**.
- **Small fixes for profile PINs:** Alder asks for the PIN everywhere it should.
- **Sign-ins stay on this TV.** Android backups no longer copy profiles, sign-ins or PINs. After you move to a new TV, sign in again.
- **Small fix for downgrades:** Alder starts when you install an older version over a newer one.
- **Lots of small fixes** for focus, posters and layout on every screen.

## 1.3.1

### Fixes
- **Alder.apk download.** The release file uses the Alder.apk name.

## 1.3

### New
<!-- Spotlight -->
- **Alder in your language.** Alder shows menus, buttons and settings in Spanish, Portuguese, French, German, Hindi or Italian. Alder uses the TV language. To change it, select **Settings → Language → App language**.
<!-- Spotlight -->
- **Trailers on the title page.** Select **▷ Trailer** on a movie or show to play its trailer. Alder does not add trailers to your watch history.
- **Subtitle colors.** Choose White, Yellow, Cyan, Cream, or Warm white in **Settings → Subtitles** or while you watch in **Audio & subtitles**.
<!-- Spotlight -->
- **Live TV and sport.** If your add-ons have TV channels, sport or events, select **Live** at the top. Select a channel to play it. To choose a source, hold OK.
<!-- Spotlight -->
- **Directors and cast.** See directors and lead actors on the title screen. Select a person to see their other works. A label shows the title and year of the focused poster. Select **ⓘ** to see the full story and all cast members with portraits.
<!-- Spotlight -->
- **Chapter markers and chapter snap.** See chapter marks and intro marks on the progress bar. Press Up on the bar to jump between chapters.
<!-- Spotlight -->
- **Clear dialogue.** Turn on **Settings → Clear dialogue** to boost voices above music and loud effects on TV speakers. You can also turn it on while you watch.
- **Foreign audio subtitles.** Alder can start subtitles automatically only when the audio is in a different language. Select **Settings → Subtitles → Foreign audio only**.
- **Still watching?** If you do not press a remote button for 3 episodes, Alder stops before the next episode. Select **Play** or **Exit to Home**.
- **Sleep timer.** In the player, select **Audio & subtitles**, then select **Sleep**. Choose **After this episode** or **In 60 min**.
- **Mark episodes as watched.** Hold OK on an episode in the episode guide. Select **Mark as watched**, **Mark as unwatched**, or **Mark previous episodes watched**. Hold OK on a season button to mark all episodes of that season.
- **New episode tags.** A tungsten tag shows on Continue Watching when a new episode is available.
- **Protect unreleased episodes.** Alder shows the air date for future episodes and disables playback until they air.
- **Default tab.** Choose which tab opens when you start Alder. Select **Settings → Profile → Default tab**.
- **Show or hide tabs.** Turn tabs on or off in **Settings → Profile**.
- **Featured rows on Home.** Show top recommendations and popular titles below Continue Watching. Turn on **Settings → Profile → Featured on Home**.
- **Hide rows.** Hold OK on a title in a row, then select **Hide row**. To put it back, select **Settings → Profile → Hidden rows**.
- **30 GB file limit.** Select **30 GB** in **Settings → Largest file**.
- **Blur episode thumbnails.** Turn on **Settings → Profile → Blur episode thumbnails**. Alder blurs pictures of unwatched episodes to prevent spoilers.

### Easier to use
<!-- Spotlight -->
- **House Lights logos.** Alder uses the tungsten mark on the TV banner, transparent app icon, and small transparent logo. The profile picker and Settings headers use vector art.
- **Content ratings on the title page.** A badge shows the content rating, for example PG-13, R, or TV-MA, before the year.
- **Compact Up next prompt.** The Up next card uses a smaller width, smaller text, and a single Next episode button.
- **QWERTY Search keys.** The Search keyboard uses QWERTY letter rows. The microphone icon stays dim until voice search is available.
- **Audio and subtitle tabs.** Select **Subtitles**, **Audio**, or **Sync & Style**. Active, preferred, and recent tracks appear first. Adjust subtitle sync in the panel.
- **Clearer title page.** Cast names stay clear in dark glass capsules.
- **Clearer menus.** Add-on lists, Search, and choice menus use glass that is easy to read.
- **Clearer player controls.** The control bar shows more video. Player tags and panels use dark glass.
- **What's new showcase.** The update screen shows important changes first. Select **See all changes** to read the full list.
<!-- Spotlight -->
- **Direct play.** When you select Play, Alder starts the video directly. A progress indicator shows while Alder finds the best source. To see all sources, hold OK.
<!-- Spotlight -->
- **Cinematic video start.** When you select Play, Alder shows the title art and bottom controls immediately. The progress bar pulses with warm light while Alder connects to the source, and the video fades in smoothly.
- **Format and debrid badges in the player.** Open the player controls to see badges for resolution, Dolby Vision, HDR, Dolby Atmos, DTS, audio language, subtitles, and your debrid service below the title.
- **Faster access to Audio & subtitles.** Press Left once from Play / Pause in the player to open **Audio & subtitles**.
- **Live subtitle sync.** Adjust subtitle delay with Left and Right while the video plays. This works for embedded subtitles and add-on subtitles.
- **Close player controls.** Press Back to close the controls while the video plays. Press Back again to leave the player.
- **Exact subtitle remembered.** When you play a video again, Alder uses the exact subtitle track that you selected before.
- **Update notification dot.** A dot on your profile avatar shows when a new version of Alder is ready.
- **Install an update from the profile menu.** Select your profile avatar, then select **Install update**. Alder installs version 1.3 over version 1.2.
- **Faster update checks.** Alder checks for new releases every 4 hours instead of once a day.

### Fixes
- **Subtitle language list in Audio & subtitles.** All add-on subtitle languages appear under Subtitles. Quick sync sits below the list so remote focus moves smoothly.
- **Up next episode card.** When credit times are available, Alder shows Up next only after the credits start. The fallback starts in the final 15 to 25 seconds.
- **Skip intro button.** Alder reads MKV intro chapters first. Alder filters IntroDB segments with low confidence and stops false prompts at the start of a video.
- **Remote focus and scroll in Audio & subtitles.** When you select a track, remote focus stays on it and the list keeps its position. The list moves smoothly and stays in its panel.
- **Smoked glass overlay in Audio & subtitles.** The Audio & subtitles sheet uses the Alder smoked glass recipe for high contrast over video.
- **Cleaner title page.** The light around **Play** and **Resume** sits in the center. Cast names stay inside the screen margins. Action buttons align in the center.
- **Install on Fire TV.** Alder installs on Fire TV and Fire OS devices without a package parse error.
- **Support older TV devices.** Alder supports Android 6.0 and Fire OS 6 devices, such as Fire TV Stick 4K.
- **Cleaner player controls.** The audio, subtitle, format, and debrid badges sit in one row below the title, and the top progress bar no longer appears while you seek.
- **Back button on Android 16.** The Back button closes panels and player controls correctly on Android 16.

## 1.2

### New
- **Sources in your language.** Select a language above the source list. Alder shows only the sources in that language.
- **All audio and subtitle languages.** Select them in **Settings**. Each profile keeps its own languages.
- **Alder keeps your player choices.** The next episode starts with the audio and the subtitles that you selected.
- **Remove titles from Continue Watching.** Hold OK on a title. To put it back, select **Settings → Profile → Hidden from Continue Watching**.
- **See the debrid service.** The source list and the player show the service, for example **RD** for Real-Debrid.

### Easier to use
- **Help when there is no source.** Alder tells you why. Scan the QR code to set up the add-on on your phone. Then select **Try again**.
- **See your subtitles quickly.** The player shows the audio and the subtitles that play.
- **What's new after each update.** To read it again, select **Settings → About → What's new**.

### Fixes
- **Fixed sources that did not show.** Add-ons with an http:// address work. Some add-ons do not block Alder now. Dolby Vision sources show on all TVs.
- **Fixed skip forward.** Some movies, for example .ts files, do not start again from the beginning.
- **Fixed empty pages.** When an add-on gives no details, Alder uses the next add-on. All AIOMetadata catalogs show.
- **Fixed sign-in problems.** A profile stays after Stremio signs it out. Alder tells you when 2 profiles use the same account.
- **Fixed updates on Fire TV.** Alder does not stop during an update.
- **Fixed language problems.** Subtitles and audio tracks match in all add-ons.

## 1.1

- Each profile keeps its own streaming add-ons. Add-ons that you add or remove in Stremio show in Alder on the next page.
- Continue Watching shows correctly when Home opens for the first time.
- The **Audio & subtitles** panel in the player opens correctly.
- In **Search**, the selected poster is no longer cut off at the top.

## 1.0

- **Press Play and it plays.** Alder picks a good source and starts it. Hold OK on **Play** to see every source.
- **Automatic fallback.** When a stream fails, Alder tries the next one from the same second.
- **Only what your TV can play.** Alder checks your TV for 4K, HDR, Dolby Vision, HEVC, AV1 and DTS. It hides files that the TV cannot play.
- **Profiles.** Up to 5 profiles, each with its own Stremio account. A PIN is optional.
- **Stream preferences in plain words:** **Best picture**, **Balanced** or **Fast start**.
- **Series.** The next episode keeps the same release and quality. **Skip intro** is built in.
- **Continue Watching on the Google TV home screen.**
- **Frame-rate matching.** The TV changes to the film's frame rate.
- **Sync with Stremio.** Watch progress matches your phone and computer.
- **Updates.** Alder finds new versions on GitHub and installs them.
