package de.letzgo.launcher.panels

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import de.letzgo.launcher.LetzgoApplication

/** Kategorien der App-Mediathek, angelehnt an iOS. */
enum class AppCategory(@StringRes val title: Int) {
    SUGGESTIONS(R.string.library_suggestions),
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
 * Ordnet Apps einer [AppCategory] zu, in dieser Reihenfolge:
 * 1. eigene Wahl im Kontextmenü („Kategorie ändern“, [setOverride])
 * 2. Liste bekannter Apps ([PACKAGES])
 * 3. Kategorie, die die App selbst angibt ([ApplicationInfo.category])
 * 4. Stichwörter in Paketname und Titel, sonst „Andere“
 */
object AppCategories {

    private const val PREFS = "letzgo_categories"

    /** Kategorien, die man einer App zuordnen kann (ohne Vorschläge und Zuletzt hinzugefügt). */
    val choosable = AppCategory.entries - AppCategory.SUGGESTIONS - AppCategory.RECENT

    private val PACKAGES: Map<String, AppCategory> =
        buildMap {
            fun put(category: AppCategory, vararg packages: String) = packages.forEach { put(it, category) }
            put(AppCategory.UTILITIES,
                "com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
                "com.android.settings", "com.google.android.apps.nbu.files", "com.google.android.documentsui",
                "com.google.android.deskclock", "com.google.android.calculator", "com.google.android.contacts",
                "com.google.android.dialer", "com.android.vending", "com.google.android.googlequicksearchbox",
                "com.google.android.apps.safetyhub", "com.google.android.apps.authenticator2",
                "com.azure.authenticator", "com.google.android.apps.wellbeing", "com.google.android.apps.tachyon",
                "com.samsung.android.dialer", "com.sec.android.app.myfiles", "com.google.android.inputmethod.latin",
                "com.google.android.apps.recorder", "com.google.android.apps.translate", "com.google.ar.lens")
            put(AppCategory.SOCIAL,
                "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
                "com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.twitter.android",
                "com.instagram.barcelona", "com.snapchat.android", "com.discord", "com.linkedin.android",
                "com.zhiliaoapp.musically", "com.reddit.frontpage", "com.google.android.apps.messaging",
                "com.samsung.android.messaging", "ch.threema.app", "com.bsky.app", "org.joinmastodon.android")
            put(AppCategory.PRODUCTIVITY,
                "com.google.android.gm", "com.google.android.calendar", "com.google.android.apps.docs",
                "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
                "com.google.android.apps.docs.editors.slides", "com.google.android.keep",
                "com.google.android.apps.tasks", "com.microsoft.office.outlook", "com.microsoft.teams",
                "com.microsoft.office.officehubrow", "com.slack", "com.notion.id", "net.thunderbird.android",
                "com.fsck.k9", "com.todoist", "com.paypal.android.p2pmobile", "de.dkb.portalapp",
                "com.db.pwcc.dbmobile", "de.number26.android", "de.comdirect.android", "com.starfinanz.smob.android.sfinanzstatus",
                "de.ingdiba.bankingapp", "com.google.android.apps.walletnfcrel", "com.anthropic.claude",
                "com.openai.chatgpt", "com.google.android.apps.bard", "com.zoom.videomeetings")
            put(AppCategory.ENTERTAINMENT,
                "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.spotify.music",
                "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient",
                "com.plexapp.android", "tv.twitch.android.app", "com.audible.application",
                "com.google.android.apps.podcasts", "de.zdf.android.mediathek", "de.swr.avp.ard.phone",
                "com.apple.android.music", "deezer.android.app", "com.joyn.android")
            put(AppCategory.CREATIVITY,
                "com.google.android.GoogleCamera", "com.android.camera", "com.sec.android.app.camera",
                "com.google.android.apps.photos", "com.canva.editor", "com.adobe.lrmobile",
                "com.niksoftware.snapseed", "com.picsart.studio", "com.instagram.layout",
                "com.google.android.apps.photosgo", "com.sec.android.gallery3d")
            put(AppCategory.INFORMATION,
                "com.google.android.apps.magazines", "de.tagesschau", "de.spiegel.android.app.spon",
                "de.heise.android.heiseonlineapp", "com.google.android.apps.weather", "de.wetteronline.wetterapp",
                "com.amazon.kindle", "com.google.android.apps.books", "org.wikipedia", "com.feedly.android")
            put(AppCategory.TRAVEL,
                "com.google.android.apps.maps", "de.hafas.android.db", "com.ubercab", "com.waze",
                "com.booking", "com.airbnb.android", "de.flixbus.app", "com.lufthansa.android.lufthansa",
                "de.bahn.dbnavigator", "com.tripadvisor.tripadvisor", "com.here.app.maps")
            put(AppCategory.SHOPPING,
                "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.lieferando.android",
                "de.zalando.mobile", "com.ikea.kompis", "com.kleinanzeigen", "de.kleinanzeigen.app",
                "com.ebay.kleinanzeigen", "com.vinted", "com.temu", "com.einnovation.temu",
                "com.alibaba.aliexpresshd", "de.otto.app", "com.wolt.android", "com.ubercab.eats")
            put(AppCategory.HEALTH,
                "com.google.android.apps.fitness", "com.strava", "com.sec.android.app.shealth",
                "com.fitbit.FitbitMobile", "com.calm.android", "com.getsomeheadspace.android",
                "com.withings.wiscale2", "com.garmin.android.apps.connectmobile", "de.rki.coronawarnapp")
        }

    /** Eigene Zuordnung (Paketname) speichern; null setzt sie zurück. */
    fun setOverride(context: Context, packageName: String, category: AppCategory?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (category == null) remove(packageName) else putString(packageName, category.name)
        }.apply()
    }

    fun override(context: Context, packageName: String): AppCategory? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(packageName, null)
            ?.let { name -> AppCategory.entries.firstOrNull { it.name == name } }

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
        val pkg = app.componentName?.packageName
        if (pkg != null) {
            override(LetzgoApplication.appContext, pkg)?.let { return it }
            PACKAGES[pkg]?.let { return it }
        }
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
