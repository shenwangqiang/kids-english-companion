package com.kidsenglish.companion

import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class BackupPolicyTest {
    private val app = ApplicationProvider.getApplicationContext<KidsEnglishApplication>()

    @Test fun installedApplicationDisablesPlatformBackup() {
        assertFalse(app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test fun packagedBackupAndTransferRulesExcludeAllPrivateStorage() {
        val manifest = app.assets.openXmlResourceParser("AndroidManifest.xml")
        val rules = manifest.use {
            while (it.next() != XmlPullParser.END_DOCUMENT) {
                if (it.eventType == XmlPullParser.START_TAG && it.name == "application") break
            }
            listOf("fullBackupContent", "dataExtractionRules").map { name ->
                it.getAttributeResourceValue("http://schemas.android.com/apk/res/android", name, 0)
            }
        }
        assertTrue("Both platform backup formats must be bound in the installed manifest", rules.all { it != 0 })
        val expectedDomains = setOf("root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref")
        rules.forEach { resource ->
            val scopes = mutableMapOf<String, MutableSet<String>>()
            app.resources.getXml(resource).use { parser ->
                var scope = ""
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    if (parser.name in listOf("full-backup-content", "cloud-backup", "device-transfer")) {
                        scope = parser.name
                        scopes[scope] = mutableSetOf()
                    }
                    if (parser.name == "exclude") {
                        assertEquals(".", parser.getAttributeValue(null, "path"))
                        scopes.getValue(scope) += parser.getAttributeValue(null, "domain")
                    }
                }
            }
            assertTrue(scopes.isNotEmpty())
            scopes.values.forEach { assertEquals(expectedDomains, it) }
        }
    }
}
