package com.swara.nativeapp.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val durationMs: Long = 0L,
    val browseId: String = "",
)

data class Shelf(
    val title: String,
    val tracks: List<Track>,
    val isList: Boolean,
)

data class UserPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
)

data class LyricLine(
    val startMs: Long,
    val text: String,
)

data class LyricsData(
    val synced: Boolean,
    val lines: List<LyricLine>,
)

data class EqPreset(
    val id: String,
    val name: String,
    val bandsDb: List<Float>,
)

data class ThemeAccent(
    val id: String,
    val name: String,
    val primaryHex: Long,
    val glowBgHex: Long,
)

val BUILTIN_ACCENTS = listOf(
    ThemeAccent("cobalt", "Electric Cobalt", 0xFF2563EBL, 0xFF101C36L),
    ThemeAccent("sakura", "Sakura Neon", 0xFFFF3B6BL, 0xFF2B1324L),
    ThemeAccent("matcha", "Matcha Mint", 0xFF10B981L, 0xFF0E2620L),
    ThemeAccent("tangerine", "Sunset Amber", 0xFFF97316L, 0xFF2B180EL),
)

val ENGLISH_COUNTRY_NAMES = mapOf(
    "ID" to "Indonesia",
    "US" to "United States",
    "GB" to "United Kingdom",
    "JP" to "Japan",
    "KR" to "South Korea",
    "MY" to "Malaysia",
    "SG" to "Singapore",
    "AU" to "Australia",
    "CA" to "Canada",
    "DE" to "Germany",
    "FR" to "France",
    "ES" to "Spain",
    "IT" to "Italy",
    "BR" to "Brazil",
    "MX" to "Mexico",
    "IN" to "India",
    "TH" to "Thailand",
    "VN" to "Vietnam",
    "PH" to "Philippines",
    "TW" to "Taiwan",
    "HK" to "Hong Kong",
    "NL" to "Netherlands",
    "SE" to "Sweden",
    "TR" to "Turkey",
    "SA" to "Saudi Arabia",
    "AR" to "Argentina",
    "PL" to "Poland",
    "ZA" to "South Africa",
    "NG" to "Nigeria",
    "KE" to "Kenya",
    "EG" to "Egypt",
    "MA" to "Morocco",
)

val BUILTIN_EQ_PRESETS = listOf(
    EqPreset("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    EqPreset("bass", "Bass Boost", listOf(6f, 4f, 0f, 1f, 2f)),
    EqPreset("vocal", "Vocal", listOf(-2f, 1f, 5f, 4f, 1f)),
    EqPreset("pop", "Pop", listOf(2f, 4f, 1f, 3f, 4f)),
    EqPreset("rock", "Rock", listOf(5f, 2f, -1f, 3f, 5f)),
    EqPreset("acoustic", "Acoustic", listOf(3f, 2f, 1f, 3f, 2f)),
    EqPreset("electronic", "Electronic", listOf(5f, 3f, 0f, 2f, 5f)),
    EqPreset("latenight", "Late Night", listOf(3f, 1f, 2f, -1f, -2f)),
)

val SUPPORTED_COUNTRIES = listOf(
    "ID" to "Indonesia",
    "US" to "Amerika Serikat",
    "GB" to "Inggris",
    "JP" to "Jepang",
    "KR" to "Korea Selatan",
    "MY" to "Malaysia",
    "SG" to "Singapura",
    "AU" to "Australia",
    "CA" to "Kanada",
    "DE" to "Jerman",
    "FR" to "Prancis",
    "ES" to "Spanyol",
    "IT" to "Italia",
    "BR" to "Brasil",
    "MX" to "Meksiko",
    "IN" to "India",
    "TH" to "Thailand",
    "VN" to "Vietnam",
    "PH" to "Filipina",
    "TW" to "Taiwan",
    "HK" to "Hong Kong",
    "NL" to "Belanda",
    "SE" to "Swedia",
    "TR" to "Turki",
    "SA" to "Arab Saudi",
    "AR" to "Argentina",
    "PL" to "Polandia",
    "ZA" to "Afrika Selatan",
    "NG" to "Nigeria",
    "KE" to "Kenya",
    "EG" to "Mesir",
    "MA" to "Maroko",
)

val SUPPORTED_LANGUAGES = listOf(
    "id" to "Bahasa Indonesia",
    "en" to "English",
    "ja" to "日本語 (Jepang)",
    "ko" to "한국어 (Korea)",
    "ms" to "Bahasa Melayu",
    "zh-CN" to "中文 (Mandarin)",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "pt" to "Português",
    "ar" to "العربية",
    "hi" to "हिन्दी",
    "th" to "ไทย",
    "vi" to "Tiếng Việt",
    "tr" to "Türkçe",
)
