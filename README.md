# TMDB Embed (Android)

Native Kotlin & Jetpack Compose rewrite of the **TMDB Embed API — VidZee fork**, preserving stream aggregation, segment playability probing, embedded `/m3u8-proxy` rewriting, Media3 ExoPlayer HLS playback, and the authenticated Admin Configuration Console.

## Features

- **Stream Explorer & In-App Player (`Streams`)**:
  - Resolve movie (`/streams/movie/<tmdbId>`) and TV episode (`/streams/tv/<tmdbId>/<season>/<episode>`) streams from **VidZee** (`dcloud`, `tik`, `ipcloud`, `v6:Hindi`) directly from the device (non-IP-locked) or via an optional remote `FREE_MOVIES_API_BASE_URL` backend.
  - **Segment Magic-Byte Playability Probe**: Follows master playlists to the first media segment and sniffs bytes `0..15` to detect and skip image-wrapped sources (such as `ipcloud`'s 70-byte PNG wrapper).
  - **Embedded Local `/m3u8-proxy` & `/ts-proxy` Server**: Runs on `127.0.0.1` inside the app, rewriting `.m3u8` playlists, `#EXT-X-KEY` entries, and media segment URLs to inject required upstream headers (`Referer: https://player.vidzee.wtf/`, `Origin`, `User-Agent`).
  - **Jetpack Media3 ExoPlayer**: Built-in HLS/MP4 player with `OkHttpDataSource.Factory` header injection, adaptive controls, and external player / clipboard actions.
- **Server Status & Diagnostics (`Status`)**:
  - Live runtime counters (`uptimeSeconds`, `requestsTotal`, `streamRequests`, `streamsReturned`, `tmdbToImdbLookups`, local proxy port).
  - Provider status matrix across all 7 catalogued providers (`4khdhub`, `moviesmod`, `mp4hydra`, `showbox`, `uhdmovies`, `vidzee`, `vixsrc`) with one-tap functional checks against TMDB `293660`.
- **Authenticated Admin Console (`Admin`)**:
  - Ported PBKDF2 password hashing (`admin` / `change-me` seeded on first launch), 5-attempt exponential lockout countdown, 12-hour session expiry, and password change dialog.
  - Full configuration panels: **Core** (remote `FREE_MOVIES_API_BASE_URL`, port, region, provider enable/default matrix), **Quality / Filters** (minimum quality presets & codec exclusions), **TMDB API Keys**, **FebBox / PStream Cookies**, **Advanced Flags**, and **Live Config JSON**.

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3, edge-to-edge, adaptive `NavigationBar` / `NavigationRail`)
- **Jetpack Media3 ExoPlayer** (`media3-exoplayer`, `media3-exoplayer-hls`, `media3-datasource-okhttp`)
- **OkHttp**, **Kotlinx Serialization**, **Jetpack DataStore**
- **Gradle Kotlin DSL** (AGP 9.1.1, Android SDK 36)
