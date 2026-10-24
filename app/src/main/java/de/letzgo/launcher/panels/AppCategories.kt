package de.letzgo.launcher.panels

import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo

/** Kategorien der App-Mediathek, angelehnt an iOS. */
enum class AppCategory(@StringRes val title: Int) {
    RECENT(R.string.library_recent),
    UTILITIES(R.string.library_utilities),
    PRODUCTIVITY(R.string.library_productivity),
    SOCIAL(R.string.library_social),
    CREATIVITY(R.string.library_creativity),
    ENTERTAINMENT(R.string.library_entertainment),
    INFORMATION(R.string.library_information),
    TRAVEL(R.string.library_travel),
    SHOPPING(R.string.library_shopping),
    HEALTH(R.string.library_health),
    GAMES(R.string.library_games),
    OTHER(R.string.library_other),
}

/**
 * Ordnet Apps einer [AppCategory] zu: zuerst über die Kategorie, die die App selbst angibt
 * ([ApplicationInfo.category]), sonst über Stichwörter in Paketname und Titel.
 */
object AppCategories {

    private val KEYWORDS =
        linkedMapOf(
            AppCategory.CREATIVITY to listOf("camera", "photo", "gallery", "lightroom", "snapseed", "canva"),
            AppCategory.SOCIAL to
                listOf("messag", "sms", "mms", "whatsapp", "telegram", "signal", "instagram",
                    "facebook", "twitter", "threads", "snapchat", "discord", "linkedin"),
            AppCategory.PRODUCTIVITY to
                listOf("mail", "calendar", "docs", "drive", "notes", "keep", "office", "word",
                    "excel", "sheets", "slides", "bank", "wallet", "finance", "paypal", "sparkasse",
                    "n26", "todo", "task"),
            AppCategory.ENTERTAINMENT to
                listOf("youtube", "music", "spotify", "netflix", "video", "podcast", "audible",
                    "twitch", "disney", "prime"),
            AppCategory.INFORMATION to
                listOf("news", "weather", "wetter", "wikipedia", "reader", "books", "kindle",
                    "tagesschau", "spiegel"),
            AppCategory.TRAVEL to
                listOf("maps", "uber", "bahn", ".db.", "flight", "booking", "airbnb", "waze",
                    "navigation", "transit"),
            AppCategory.SHOPPING to
                listOf("shop", "amazon", "ebay", "lieferando", "food", "zalando", "ikea"),
            AppCategory.HEALTH to listOf("health", "fit", "strava", "sleep", "medit"),
            AppCategory.UTILITIES to
                listOf("settings", "files", "documentsui", "clock", "calculator", "calc",
                    "contacts", "dialer", "phone", "chrome", "browser", "firefox", "vending",
                    "googlequicksearchbox", "safety", "security", "vpn", "authenticator"),
        )

    fun categorize(app: AppInfo, info: ApplicationInfo?): AppCategory {
        when (info?.category) {
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.CREATIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.INFORMATION
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppCategory.UTILITIES
        }
        val haystack = "${app.componentName?.packageName} ${app.title}".lowercase()
        return KEYWORDS.entries.firstOrNull { (_, words) -> words.any { it in haystack } }?.key
            ?: AppCategory.OTHER
    }
}
