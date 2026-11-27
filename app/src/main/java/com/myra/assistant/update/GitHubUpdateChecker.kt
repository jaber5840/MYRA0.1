package com.myra.assistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.myra.assistant.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GitHubUpdateChecker {

    const val DEFAULT_GITHUB_REPO = "jaber5840/MYRA0.1"
    private const val PREFS_NAME = "myra_prefs"
    private const val KEY_GITHUB_REPO = "github_repo"

    data class ReleaseInfo(
        val tagName: String,
        val releaseName: String,
        val releaseNotes: String,
        val htmlUrl: String,
        val apkDownloadUrl: String?
    )

    sealed class CheckResult {
        data class UpdateAvailable(val release: ReleaseInfo) : CheckResult()
        data class UpToDate(val currentVersion: String, val latestTag: String?) : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun getConfiguredRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalizeRepo(prefs.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO))
    }

    fun saveConfiguredRepo(context: Context, rawRepo: String): String {
        val normalized = normalizeRepo(rawRepo)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GITHUB_REPO, normalized)
            .apply()
        return normalized
    }

    fun normalizeRepo(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_GITHUB_REPO
        val stripped = trimmed
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        return if (stripped.contains('/')) stripped else DEFAULT_GITHUB_REPO
    }

    fun checkLatestRelease(
        context: Context,
        repoOverride: String? = null,
        onResult: (CheckResult) -> Unit
    ) {
        val repo = normalizeRepo(repoOverride ?: getConfiguredRepo(context))
        val url = "https://api.github.com/repos/$repo/releases/latest"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MYRA-Android/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(CheckResult.Error("Connection failed: ${e.message ?: "Network error"}"))
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                if (response.code == 404) {
                    onResult(
                        CheckResult.Error("No new update is currently available. Please check again later.")
                    )
                    return
                }
                if (!response.isSuccessful) {
                    onResult(CheckResult.Error("Update server returned HTTP ${response.code}"))
                    return
                }

                try {
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "").trim()
                    val releaseName = json.optString("name", tagName).ifBlank { tagName }
                    val releaseNotes = json.optString("body", "").ifBlank {
                        "A new version of MYRA is ready."
                    }
                    val htmlUrl = json.optString("html_url", "https://github.com/$repo/releases")

                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                                break
                            }
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (tagName.isNotEmpty() && isNewerVersion(tagName, currentVersion)) {
                        onResult(
                            CheckResult.UpdateAvailable(
                                ReleaseInfo(
                                    tagName = tagName,
                                    releaseName = releaseName,
                                    releaseNotes = releaseNotes,
                                    htmlUrl = htmlUrl,
                                    apkDownloadUrl = apkUrl
                                )
                            )
                        )
                    } else {
                        onResult(CheckResult.UpToDate(currentVersion, tagName.ifBlank { null }))
                    }
                } catch (e: Exception) {
                    onResult(CheckResult.Error("Failed to parse release info: ${e.message}"))
                }
            }
        })
    }

    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val latestParts = parseVersionParts(latestTag)
        val currentParts = parseVersionParts(currentVersion)
        if (latestParts.isEmpty() || currentParts.isEmpty()) {
            return latestTag.removePrefix("v").removePrefix("V") !=
                currentVersion.removePrefix("v").removePrefix("V")
        }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        val cleaned = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return cleaned.split('.').mapNotNull { it.trim().toIntOrNull() }
    }

    fun openReleaseInBrowser(context: Context, release: ReleaseInfo, preferApkLink: Boolean = false) {
        val targetUrl = if (preferApkLink && !release.apkDownloadUrl.isNullOrBlank()) {
            release.apkDownloadUrl
        } else {
            release.htmlUrl
        }
        if (!targetUrl.startsWith("https://github.com/")) {
            Toast.makeText(context, "Invalid GitHub release URL", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
