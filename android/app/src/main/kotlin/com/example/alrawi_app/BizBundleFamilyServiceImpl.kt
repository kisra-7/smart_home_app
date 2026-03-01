package com.example.alrawi_app

import android.util.Log

/**
 * Compile-safe helper (NO direct AbsBizBundleFamilyService import).
 * Tries to shift BizBundle "current home/family" via reflection when possible.
 */
object BizBundleFamilyServiceImpl {

    private const val TAG = "BizFamilyService"

    @Volatile private var currentHomeId: Long = 0L
    @Volatile private var currentHomeName: String = ""

    fun setHome(homeId: Long, homeName: String) {
        currentHomeId = homeId
        currentHomeName = homeName
        Log.d(TAG, "➡️ setHome(homeId=$homeId, name=$homeName)")
        shiftCurrentFamilyReflectively(homeId, homeName)
    }

    fun getHomeId(): Long = currentHomeId
    fun getHomeName(): String = currentHomeName

    private fun shiftCurrentFamilyReflectively(homeId: Long, homeName: String) {
        val microCandidates = listOf(
            "com.thingclips.smart.android.common.utils.MicroServiceManager",
            "com.tuya.smart.android.common.utils.MicroServiceManager",
            "com.thingclips.smart.android.module.MicroServiceManager",
            "com.tuya.smart.android.module.MicroServiceManager",
            "com.thingclips.smart.android.common.manager.MicroServiceManager",
            "com.tuya.smart.android.common.manager.MicroServiceManager"
        )

        val absFamilyCandidates = listOf(
            "com.thingclips.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.thingclips.smart.android.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.bizbundle.family.api.AbsBizBundleFamilyService",
            "com.tuya.smart.android.bizbundle.family.api.AbsBizBundleFamilyService"
        )

        try {
            val microClz = microCandidates.firstNotNullOfOrNull {
                try { Class.forName(it) } catch (_: Throwable) { null }
            } ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager not found in app classpath.")
                return
            }

            val absFamilyName = absFamilyCandidates.firstOrNull { name ->
                try { Class.forName(name); true } catch (_: Throwable) { false }
            } ?: run {
                Log.w(TAG, "⚠️ AbsBizBundleFamilyService not found in app classpath.")
                return
            }

            val mgr = microClz.getMethod("getInstance").invoke(null) ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager.getInstance() returned null")
                return
            }

            val service = microClz.getMethod("findServiceByInterface", String::class.java)
                .invoke(mgr, absFamilyName) ?: run {
                Log.w(TAG, "⚠️ No family service registered for: $absFamilyName")
                return
            }

            val shift = service.javaClass.methods.firstOrNull { m ->
                m.name == "shiftCurrentFamily" && m.parameterTypes.size == 2
            }
            if (shift != null) {
                shift.invoke(service, homeId, homeName)
                Log.d(TAG, "✅ shiftCurrentFamily applied (reflect) homeId=$homeId name=$homeName")
                return
            }

            val setId = service.javaClass.methods.firstOrNull { m ->
                (m.name == "setCurrentHomeId" || m.name == "setCurrentFamilyId") && m.parameterTypes.size == 1
            }
            if (setId != null) {
                setId.invoke(service, homeId)
                Log.d(TAG, "✅ setCurrentHomeId applied (reflect) homeId=$homeId")
                return
            }

            Log.w(TAG, "⚠️ Family service found but no shift/setCurrent methods found.")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ shiftCurrentFamilyReflectively failed: ${t.message}")
        }
    }
}