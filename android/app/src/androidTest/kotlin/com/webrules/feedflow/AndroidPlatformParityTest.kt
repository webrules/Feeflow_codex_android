package com.webrules.feedflow

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.webrules.feedflow.auth.AndroidWebLoginCookieBridge
import com.webrules.feedflow.core.data.SiteLoginConfig
import com.webrules.feedflow.core.database.FeedflowDatabaseContract
import com.webrules.feedflow.core.model.ForumSite
import com.webrules.feedflow.core.network.FeedflowCookie
import com.webrules.feedflow.persistence.AndroidSqliteFeedflowStore
import com.webrules.feedflow.persistence.KeystoreSecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlatformParityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext

    @Test
    fun keystoreCiphertextSurvivesSecretStoreRecreation() {
        val alias = "feedflow.instrumentation.secret.v1"
        val firstStore = KeystoreSecretStore(context, alias)
        val ciphertext = firstStore.encrypt("session-cookie-密钥")

        assertNotEquals("session-cookie-密钥", ciphertext)
        assertEquals(
            "session-cookie-密钥",
            KeystoreSecretStore(context, alias).decrypt(ciphertext),
        )
    }

    @Test
    fun sqliteV1UpgradePreservesRowsAndScopesFilteredPostsByService() {
        context.deleteDatabase(FeedflowDatabaseContract.databaseName)
        context.openOrCreateDatabase(FeedflowDatabaseContract.databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE filtered_posts(
                    postId TEXT PRIMARY KEY,
                    serviceId TEXT,
                    filteredAt INTEGER
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO filtered_posts(postId, serviceId, filteredAt) VALUES(?, ?, ?)",
                arrayOf("shared-id", "zhihu", 100L),
            )
            database.version = 1
        }

        val store = AndroidSqliteFeedflowStore(context)
        store.addFilteredPost("shared-id", "hackernews")

        assertTrue(store.isPostFiltered("shared-id", "zhihu"))
        assertTrue(store.isPostFiltered("shared-id", "hackernews"))
        assertEquals(setOf("shared-id"), store.getFilteredPostIds("zhihu"))
        assertEquals(setOf("shared-id"), store.getFilteredPostIds("hackernews"))
    }

    @Test
    fun webViewCookieBridgeInstallsAndRecapturesSiteSessionCookie() {
        val config = checkNotNull(SiteLoginConfig.forSite(ForumSite.V2ex))
        val cookieManager = CookieManager.getInstance()
        val bridge = AndroidWebLoginCookieBridge(cookieManager)
        lateinit var captured: List<FeedflowCookie>

        instrumentation.runOnMainSync {
            val webView = WebView(context)
            bridge.configure(webView)
            bridge.installCookies(
                config,
                listOf(
                    FeedflowCookie(
                        name = "a2_test",
                        value = "instrumentation-token",
                        domain = "v2ex.com",
                        expiresAtMillis = System.currentTimeMillis() + 60_000,
                        secure = true,
                    ),
                ),
            )
            captured = bridge.cookiesFor(config, "https://www.v2ex.com/")
            bridge.clearSiteCookies(config)
            webView.destroy()
        }

        assertTrue(
            "CookieManager session was not recaptured by AndroidWebLoginCookieBridge",
            captured.any { it.name == "a2_test" && it.value == "instrumentation-token" },
        )
    }
}
