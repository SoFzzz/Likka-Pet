package com.likkapet.data.remote

import com.likkapet.domain.EscalationConfig
import com.likkapet.domain.model.TargetApp
import com.likkapet.domain.model.TriggerReason
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privacy contract of documentación §8 / RF-I02: the body sent to the Worker is exactly
 * `{app, minutes, angle, level, reason, lang}`. These tests fail on any extra field, and on any app name
 * or package name anywhere in the body, whatever app the request is for.
 */
class RoastRequestPrivacyTest {
    private val allowedFields = setOf("app", "minutes", "angle", "level", "reason", "lang")

    private fun requests() =
        TargetApp.entries.flatMap { app ->
            TriggerReason.entries.flatMap { reason ->
                (1..3).map { level -> RoastRequest(app, minutes = 20, angle = 40, level = level, reason = reason) }
            }
        }

    @Test
    fun `the body has exactly the six documented fields and nothing else`() {
        requests().forEach { request ->
            val json = JSONObject(request.toJson())

            assertEquals(allowedFields, json.keys().asSequence().toSet())
        }
    }

    @Test
    fun `the request model cannot carry another field`() {
        // A new property on RoastRequest would be serialized by someone sooner or later: make it a conscious change.
        val fields =
            RoastRequest::class.java.declaredFields.filterNot {
                it.isSynthetic ||
                    java.lang.reflect.Modifier.isStatic(
                        it.modifiers,
                    )
            }

        assertEquals(allowedFields, fields.map { it.name }.toSet())
    }

    @Test
    fun `an added app goes out as OTHER`() {
        val json =
            JSONObject(RoastRequest(TargetApp.OTHER, minutes = 15, angle = 55, level = 1, reason = TriggerReason.USAGE_TIME).toJson())

        assertEquals("OTHER", json.getString("app"))
    }

    @Test
    fun `app is always one of the five enum names`() {
        val allowedApps = setOf("TIKTOK", "INSTAGRAM", "YOUTUBE", "FACEBOOK", "OTHER")

        requests().forEach { assertTrue(JSONObject(it.toJson()).getString("app") in allowedApps) }
    }

    @Test
    fun `no body contains a package name or an app display name`() {
        val forbidden =
            EscalationConfig.DEFAULT_TARGET_PACKAGES.keys +
                TargetApp.entries.map { it.displayName } +
                listOf("com.", "org.", "android", "Otra app")

        requests().forEach { request ->
            val body = request.toJson()
            forbidden.forEach { assertFalse("'$it' found in $body", body.contains(it)) }
        }
    }

    @Test
    fun `values are plain numbers and enum names, never free text`() {
        requests().forEach { request ->
            val json = JSONObject(request.toJson())

            assertTrue(json.get("minutes") is Int)
            assertTrue(json.get("angle") is Int)
            assertTrue(json.get("level") is Int)
            assertTrue(json.getString("reason") in TriggerReason.entries.map { it.name })
        }
    }

    @Test
    fun `lang is always es or en`() {
        requests().forEach { request ->
            val lang = JSONObject(request.toJson()).getString("lang")
            assertTrue(lang == "es" || lang == "en")
        }
    }
}
