package com.webrules.feedflow.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosParityInventoryTest {
    @Test
    fun everyCurrentIosXCTestHasAValidatedAndroidParityRule() {
        val names = classQualifiedIosXCTests()
        val rules = parityRules()
        val rulesByClass = rules.associateBy { it.iosClass }

        assertEquals(262, names.size)
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.all { "." in it })
        assertTrue(names.containsAll(requiredParityAnchors))
        assertEquals(rules.size, rulesByClass.size, "Each iOS XCTest class must have exactly one parity rule")
        assertEquals(
            names.map { it.substringBefore(".") }.toSet(),
            rulesByClass.keys,
            "Parity rules must cover exactly the current iOS XCTest classes",
        )

        names.forEach { iosTest ->
            assertNotNull(rulesByClass[iosTest.substringBefore(".")], "Missing parity rule for $iosTest")
        }
        rules.forEach(::validateRule)
    }

    private fun classQualifiedIosXCTests(): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("ios-xctest-manifest.txt")) {
            "Missing ios-xctest-manifest.txt"
        }.bufferedReader().readLines().filter { it.isNotBlank() }

    private fun parityRules(): List<ParityRule> {
        val lines = checkNotNull(javaClass.classLoader?.getResourceAsStream("ios-parity-rules-v1.tsv")) {
            "Missing ios-parity-rules-v1.tsv"
        }.bufferedReader().readLines()
        assertEquals("# version=1", lines.firstOrNull(), "Unsupported or missing parity-map version")
        return lines
            .filterNot { it.isBlank() || it.startsWith("#") }
            .map { line ->
                val columns = line.split('\t')
                assertEquals(5, columns.size, "Invalid parity rule: $line")
                ParityRule(
                    iosClass = columns[0],
                    featureArea = columns[1],
                    status = columns[2],
                    androidTest = columns[3],
                    testType = columns[4],
                )
            }
    }

    private fun validateRule(rule: ParityRule) {
        assertTrue(rule.featureArea.isNotBlank(), "Feature area is required for ${rule.iosClass}")
        assertTrue(rule.status in validStatuses, "Invalid status for ${rule.iosClass}: ${rule.status}")
        assertTrue(rule.testType in validTestTypes, "Invalid test type for ${rule.iosClass}: ${rule.testType}")
        val targetParts = rule.androidTest.split('#')
        assertEquals(2, targetParts.size, "Android target must be Class#method for ${rule.iosClass}")
        assertTrue(targetParts.all { it.isNotBlank() }, "Android target is incomplete for ${rule.iosClass}")

        if (rule.testType == "jvm") {
            val testClass = Class.forName(targetParts[0])
            val testMethod = testClass.declaredMethods.firstOrNull { it.name == targetParts[1] }
            assertNotNull(testMethod, "Missing Android test target ${rule.androidTest}")
            assertTrue(
                testMethod.annotations.any { it.annotationClass.qualifiedName == "org.junit.Test" },
                "Android target is not an executable JUnit test: ${rule.androidTest}",
            )
        } else {
            assertEquals(
                "android-specific-replacement",
                rule.status,
                "Instrumentation mappings must be explicit Android replacements",
            )
        }
    }

    private data class ParityRule(
        val iosClass: String,
        val featureArea: String,
        val status: String,
        val androidTest: String,
        val testType: String,
    )

    private val validStatuses = setOf(
        "mapped",
        "covered-by-contract",
        "android-specific-replacement",
        "live-opt-in",
        "obsolete-approved",
    )

    private val validTestTypes = setOf("jvm", "instrumentation", "live")

    private fun iosXCTestNames(): List<String> = """
testAllCasesCount
testAmpersandEntityDecodedInAvatarURL
testAvatarTemplateSizePlaceholderResolved
testAvatarURLForEmptyUID
testAvatarURLForInvalidUID
testAvatarURLForUIDLargeNumber
testAvatarURLForUIDPadding
testAvatarURLForUIDSimple
testBackgroundPrefetchGating
testBackgroundPrefetchToggle
testBlockquoteLinkStripping
testBlockquoteRegexMatches
testBlockquoteWithAttributesRegex
testBookmarkIdempotent
testBookmarkPersistence
testBookmarkToggle
testCachedThreadNonexistent
testCachedTopicsNonexistentKey
testCanCreateThreadDefault
testClampHonorsMaximum
testClampHonorsMinimum
testClampPassesThroughInRange
testClearCookies
testClearCookiesRemovesAll
testClearLoginRequest
testCommentAuthorDecodedFromFlatFallback
testCommentAuthorDecodedFromNestedMember
testCommentModel
testCommentModelWithReplies
testCommentRepliesOptional
testCommunityCache
testCommunityCacheEmpty
testCommunityCacheEmptyService
testCommunityCacheSaveAndLoad
testCommunityModel
testCommunityModelFull
testCommunitySettingsRSSAlwaysEnabled
testCommunitySettingsVisibleSites
testCookieEmptyArray
testCookieHeader
testCookieRoundTrip
testCookieSecureFlagPreserved
testCookieSerializationPreservesAllProperties
testDecimalEntityDecoding
testDecryptInvalidDataReturnsNil
testDecryptInvalidReturnsNil
testDefaultRequiresLoginFalse
testDefaultRestoreSessionTrue
testDiscourseProps
testEmptyAuthorNameBecomesAnonymous
testEmptyAvatarAndTemplateStaysEmpty
testEmptyAvatarFallsBackToTemplate
testEmptyCommentList
testEmptyRefreshPreservesThreads
testEncryptDecryptChinese
testEncryptDecryptEmpty
testEncryptDecryptRoundTrip
testEncryptDecryptSpecialChars
testEncryptEmptyString
testEncryptProducesDifferentOutput
testEncryptedSetting
testEncryptedSettingRoundTrip
testEncryptionDifferentOutputsForSameInput
testEncryptionRoundTrip
testExistingExpiresDateUnchanged
testExistingExpiryPreserved
testExtractUIDFromGenericUIDParam
testExtractUIDFromSpaceLink
testExtractUIDFromThreadListRowHTML
testExtractUIDNoUIDPresent
testFilteredPostLifecycle
testForceRefreshHook
testForumSiteOrder
testForumViewModelNeedsLogin
testForumViewModelRefreshResetsLogin
testFourD4YAuthCookieDetection
testFourD4YCanCreateThread
testFourD4YProps
testFourD4YSIDRegex
testFourD4YServiceID
testFromServiceIdInvalidReturnsNil
testFromServiceIdMapsCorrectly
testFullAvatarFlowFromRowHTML
testGeminiServiceSummaryDoesNotCrash
testGenericAvatarNotURL
testGetBookmarkedThreads
testGetWebURL
testHNProps
testHTMLEntityDecodingDecimal
testHTMLEntityDecodingEmpty
testHTMLEntityDecodingEntityInTitle
testHTMLEntityDecodingHex
testHTMLEntityDecodingMixed
testHTMLEntityDecodingNamed
testHTMLEntityDecodingNoEntities
testHTTPCookieMinimumProps
testHTTPCookieNilForBadProps
testHackerNewsServiceID
testHasCookies
testHasCookiesFalseForUnknownSite
testHexEntityDecoding
testInvalidEntityNoCrash
testIsLoginURLIdentifiesLoginPage
testIsPostLoginNavigation
testJustNow
testKeysHaveTranslations
testLanguageToggle
testLinkRegexBracketedTitle
testLinkRegexMultipleBracketedTitles
testLinkRegexNestedBrackets
testLinkRegexSimpleTitle
testLinkRegexWithSurroundingText
testLinuxDoAuthCookie
testLinuxDoOAuthOptionCount
testLinuxDoServiceID
testLocalizationAllKeysHaveValues
testLocalizedExtension
testMakeServiceReturnsCorrectType
testMixedEntityDecoding
testMultipleSiteCookiesIsolation
testNamedEntityDecoding
testNetworkMonitorAccessDoesNotCrash
testNetworkMonitorExists
testNoEntityReturnsUnchanged
testNonEmptyAuthorNameKept
testOPMLEmptyXML
testOPMLParserExists
testOPMLParsing
testOneDay
testOneHour
testOneMinute
testParseAtom
testParseEmptyXML
testParseNoItems
testParseRSS2
testPersistentCookieUpgrade
testPersistentCookieUpgradeTiming
testPlaintextMigration
testPopToRoot
testPrefetchFlagDefaults
testProtocolRelativeAvatarGetsHTTPS
testRSSAlwaysEnabled
testRSSNoLoginConfig
testRSSParserExists
testRSSProps
testRSSServiceProperties
testRemoveNonexistentNoCrash
testRemoveThread
testReplaceCookiesOverwrites
testSIDExtractionGuest
testSIDExtractionLoggedIn
testSaveAndLoadCachedThread
testSaveAndLoadCachedTopics
testSaveLoadSetting
testSchemaMigrationDecision
testServiceWebURL
testSettingsKeyValue
testShouldCheckCookies
testSiteCookiesFilterByDomain
testSiteLoginConfigFourD4YDomain
testSiteLoginConfigHackerNews
testSiteLoginConfigZhihuRequiredCookie
testSpeechInitialState
testSpeechServiceInitialState
testSpeechServiceSharedInstance
testSpeechServiceStopWhenNotSpeaking
testSummaryCache
testSummaryExpiresWithZeroMaxAge
testSummaryOverwrite
testSummaryPersistence
testSummaryWithinTTL
testThemeManagerDefault
testThemeManagerPersists
testThemeManagerToggle
testThirtyDays
testThreadEquality
testThreadLikeToggle
testThreadListViewModelEmptyLoad
testThreadListViewModelInitialState
testThreadModelWithTags
testThreadModelWithoutTags
testThreadTagsOptional
testTimeAgoDays
testTimeAgoFromISO8601String
testTimeAgoFromInvalidString
testTimeAgoHours
testTimeAgoJustNow
testTimeAgoMinutes
testTitleFullCleanFlowBracketedTag
testTitleStripLINKMarkerBracketed
testTitleStripLINKMarkerSimple
testTitleStripMultipleLINKMarkers
testToggleCommunityVisibility
testTwoMinutes
testURLBookmarkAddRemove
testURLBookmarkRoundTrip
testURLBookmarksList
testUpdateScrollPosition
testUpdateSetting
testUserModel
testUserModelWithRole
testUserModelWithoutRole
testV2EXOAuthOptionCount
testV2EXProps
testV2EXServiceID
testValidateSession
testVisibleSitesContainsRSS
testZhihuProps
testZhihuRequiredCookie
testZhihuServiceID
test_cacheKey_sameForAllAuthStates
test_canDeleteThread_othersThread_false
test_canDeleteThread_ownThread_true
test_cdbAuthCookie_passesAuthCheck
test_cdbLoginCookie_passesAuthCheck
test_cookieHeader_expiredCookie_excluded
test_cookieHeader_matchingDomain_included
test_cookieHeader_matchingPath_included
test_cookieHeader_nonMatchingPath_excluded
test_cookieHeader_sessionCookie_included
test_cookiePersistence_acrossAppRestart
test_differentValues_haveDifferentSignature
test_extractFirstTypeId_noSelect_returnsNil
test_extractFirstTypeId_returnsFirstNonZero
test_extractFormHash_fromURL
test_extractForumLinks_fromGuestHTML
test_extractForumLinks_fromHTML
test_extractSID_fromHTML
test_fetchFreshData_notAtTop_keepsOldThreads
test_forceRefresh_emptyResult_preservesOldFor4d4y
test_forceRefresh_fetchesNewData
test_forceRefresh_sessionExpired_setsNeedsLogin
test_hasLoginWithoutLogout_detected
test_hasLogout_detected
test_identicalCookies_haveSameSignature
test_isReturning_loadsStaleCacheFirst
test_logout_clearsDB
test_logout_clearsHTTPCookieStorage
test_noRelevantCookies_failsAuthCheck
test_non4d4YEmptyRefresh_clearsThreads
test_onlySIDCookie_failsAuthCheck
test_parseFirstPostId_fromAuthorPostMarker
test_parseFirstPostId_none_returnsNil
test_parseLoggedInUsername_fromBoldProfileLink
test_parseLoggedInUsername_guestReturnsNil
test_parseLoggedInUsername_welcomeBar
test_parseWAPThreadDetail_extractsThreadAndReplies
test_persistentCookie_preservesExistingExpiry
test_persistentCookie_sets30DayExpiry
test_restoreSession_bothFail_returnsFalse
test_restoreSession_validateFails_authCookiePresent_returnsFalse
test_restoreSession_validatePasses_returnsTrue
test_siteCookies_filtersByDomain
test_validateSession_cloudflareChallenge_fails
test_validateSession_guestHTML_fails
test_validateSession_loggedInHTML_succeeds
test_validateSession_logoutWithoutForumLinks_fails
    """.trimIndent().lines()

    private val requiredParityAnchors = listOf(
        "ForumSiteTests.testAllCasesCount",
        "ForumSiteTests.testMakeServiceReturnsCorrectType",
        "AuthenticationTests.testFourD4YAuthCookieDetection",
        "ContentBrowsingTests.testBookmarkPersistence",
        "RSSFeedTests.testParseRSS2",
        "RSSFeedTests.testOPMLParsing",
        "SettingsTests.testEncryptedSetting",
        "PerSiteTests.testZhihuProps",
        "FourD4YHTMLParsingTests.test_parseWAPThreadDetail_extractsThreadAndReplies",
    )
}
