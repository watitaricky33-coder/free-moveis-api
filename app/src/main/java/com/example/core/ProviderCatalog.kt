package com.example.core

import com.example.model.AppConfig

data class ProviderSpec(
    val name: String,
    val displayName: String,
    val cookieRequired: Boolean = false,
    val defaultStatusNote: String = ""
)

object ProviderCatalog {
    val PROVIDERS: List<ProviderSpec> = listOf(
        ProviderSpec("4khdhub", "4KHDHub", defaultStatusNote = "Inherited upstream scraper"),
        ProviderSpec("moviesmod", "MoviesMod", defaultStatusNote = "Inherited upstream scraper"),
        ProviderSpec("mp4hydra", "MP4Hydra", defaultStatusNote = "Inherited upstream scraper"),
        ProviderSpec("showbox", "Showbox", cookieRequired = true, defaultStatusNote = "Requires FebBox ui cookie & TMDB key"),
        ProviderSpec("uhdmovies", "UHDMovies", defaultStatusNote = "Inherited upstream scraper"),
        ProviderSpec("vidzee", "VidZee", defaultStatusNote = "Active primary HLS resolver"),
        ProviderSpec("vixsrc", "Vixsrc", defaultStatusNote = "Inherited upstream scraper")
    )

    val PROVIDER_NAMES: List<String> = PROVIDERS.map { it.name }

    fun enableFlagFor(name: String): String {
        val clean = name.lowercase()
        return "enable${clean.replaceFirstChar { it.uppercase() }}Provider"
    }

    fun enableEnvFor(name: String): String = "ENABLE_${name.uppercase()}_PROVIDER"

    fun isProviderEnabled(config: AppConfig, name: String): Boolean = when (name.lowercase()) {
        "4khdhub" -> config.enable4khdhubProvider
        "moviesmod" -> config.enableMoviesmodProvider
        "mp4hydra" -> config.enableMp4hydraProvider
        "showbox" -> config.enableShowboxProvider
        "uhdmovies" -> config.enableUhdmoviesProvider
        "vidzee" -> config.enableVidzeeProvider
        "vixsrc" -> config.enableVixsrcProvider
        else -> false
    }

    fun withProviderEnabled(config: AppConfig, name: String, enabled: Boolean): AppConfig = when (name.lowercase()) {
        "4khdhub" -> config.copy(enable4khdhubProvider = enabled)
        "moviesmod" -> config.copy(enableMoviesmodProvider = enabled)
        "mp4hydra" -> config.copy(enableMp4hydraProvider = enabled)
        "showbox" -> config.copy(enableShowboxProvider = enabled)
        "uhdmovies" -> config.copy(enableUhdmoviesProvider = enabled)
        "vidzee" -> config.copy(enableVidzeeProvider = enabled)
        "vixsrc" -> config.copy(enableVixsrcProvider = enabled)
        else -> config
    }
}
