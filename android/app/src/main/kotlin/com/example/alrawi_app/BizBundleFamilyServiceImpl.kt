package com.example.alrawi_app

import android.util.Log

/**
 * Compile-safe family context helper (NO direct AbsBizBundleFamilyService import).
 *
 * Your SDK build does NOT expose AbsBizBundleFamilyService in the app classpath,
 * so we do the official "shift current family/home" step via reflection only.
 */
object BizBundleFamilyServiceImpl {

    private const val TAG = "BizFamilyService"

    @Volatile private var currentHomeId: Long = 0L
    @Volatile private var currentHomeName: String = ""

    fun setHome(homeId: Long, homeName: String) {
        currentHomeId = homeId
        currentHomeName = homeName
        Log.d(TAG, "➡️ setHome(homeId=$homeId, name=$homeName)")

        // Try to shift BizBundle "current family" using reflection.
        shiftCurrentFamilyReflectively(homeId, homeName)
    }

    fun getHomeId(): Long = currentHomeId
    fun getHomeName(): String = currentHomeName

    /**
     * Official docs concept:
     * MicroServiceManager.findServiceByInterface(AbsBizBundleFamilyService) then shiftCurrentFamily(homeId, homeName)
     *
     * We do it reflection-only because AbsBizBundleFamilyService is not visible at compile time in your build.
     */
    private fun shiftCurrentFamilyReflectively(homeId: Long, homeName: String) {
        val microCandidates = listOf(
            // Add more candidates because your previous log said "MicroServiceManager not found"
            // from app side, even though SDK logs show it exists internally.
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
                Log.w(TAG, "⚠️ MicroServiceManager not found in app classpath (will rely on SDK internal state).")
                return
            }

            val absFamilyName = absFamilyCandidates.firstOrNull { name ->
                try { Class.forName(name); true } catch (_: Throwable) { false }
            } ?: run {
                Log.w(TAG, "⚠️ AbsBizBundleFamilyService not found in app classpath.")
                return
            }

            val getInstance = microClz.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.isEmpty() }
                ?: run {
                    Log.w(TAG, "⚠️ MicroServiceManager.getInstance() not found")
                    return
                }

            val mgr = getInstance.invoke(null) ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager.getInstance() returned null")
                return
            }

            val find = microClz.methods.firstOrNull {
                it.name == "findServiceByInterface" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            } ?: run {
                Log.w(TAG, "⚠️ MicroServiceManager.findServiceByInterface(String) not found")
                return
            }

            val service = find.invoke(mgr, absFamilyName) ?: run {
                Log.w(TAG, "⚠️ No family service registered for: $absFamilyName")
                return
            }

            // Prefer shiftCurrentFamily(long, String)
            val shift = service.javaClass.methods.firstOrNull { m ->
                m.name == "shiftCurrentFamily" && m.parameterTypes.size == 2
            }

            if (shift != null) {
                shift.invoke(service, homeId, homeName)
                Log.d(TAG, "✅ shiftCurrentFamily applied via MicroServiceManager homeId=$homeId name=$homeName")
                return
            }

            // Fallback: setCurrentHomeId(long) / setCurrentFamilyId(long)
            val setId = service.javaClass.methods.firstOrNull { m ->
                (m.name == "setCurrentHomeId" || m.name == "setCurrentFamilyId") && m.parameterTypes.size == 1
            }

            if (setId != null) {
                setId.invoke(service, homeId)
                Log.d(TAG, "✅ setCurrentHomeId applied via MicroServiceManager homeId=$homeId")
                return
            }

            Log.w(TAG, "⚠️ Family service found but no shiftCurrentFamily/setCurrentHomeId methods found.")
        } catch (t: Throwable) {
            Log.w(TAG, "⚠️ shiftCurrentFamilyReflectively failed: ${t.message}")
        }
    }
}