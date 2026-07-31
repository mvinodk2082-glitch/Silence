package com.silence.blocker.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Lists launchable apps without QUERY_ALL_PACKAGES (launcher intent query).
 */
class InstalledAppsRepository(
    private val packageManager: PackageManager,
) {
    data class AppEntry(
        val packageName: String,
        val label: String,
    )

    fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        val seen = LinkedHashSet<String>()
        val out = ArrayList<AppEntry>(resolves.size)
        for (info in resolves) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val label = try {
                info.loadLabel(packageManager)?.toString() ?: pkg
            } catch (_: Exception) {
                pkg
            }
            out.add(AppEntry(packageName = pkg, label = label))
        }
        return out.sortedBy { it.label.lowercase() }
    }
}
