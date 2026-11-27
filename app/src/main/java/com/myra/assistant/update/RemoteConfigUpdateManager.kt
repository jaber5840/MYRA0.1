package com.myra.assistant.update

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.myra.assistant.BuildConfig
import com.myra.assistant.R

object RemoteConfigUpdateManager {

    private const val TAG = "RemoteConfigUpdate"

    const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled"
    const val KEY_LATEST_APP_VERSION = "latest_app_version"
    const val KEY_MIN_SUPPORTED_VERSION = "min_supported_version"
    const val KEY_UPDATE_GITHUB_REPO = "update_github_repo"
    const val KEY_UPDATE_RELEASE_URL = "update_release_url"
    const val KEY_UPDATE_RELEASE_NOTES = "update_release_notes"
    const val KEY_FORCE_UPDATE_REQUIRED = "force_update_required"

    data class UpdateOutcome(
        val checkResult: GitHubUpdateChecker.CheckResult,
        val isMandatory: Boolean,
        val remoteConfigSynced: Boolean,
        val remoteLatestVersion: String,
        val effectiveRepo: String,
        val updateCheckEnabled: Boolean
    )

    private var defaultsInitialized = false

    private fun getRemoteConfigOrNull(): FirebaseRemoteConfig? {
        return try {
            val rc = Firebase.remoteConfig
            if (!defaultsInitialized) {
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 60L else 3600L
                }
                rc.setConfigSettingsAsync(configSettings)
                rc.setDefaultsAsync(R.xml.remote_config_defaults)
                defaultsInitialized = true
            }
            rc
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Remote Config unavailable, using local fallback: ${e.message}")
            null
        }
    }

    fun getCurrentRemoteSummary(): String {
        val rc = getRemoteConfigOrNull() ?: return "Update Service: Ready"
        val latest = rc.getString(KEY_LATEST_APP_VERSION).trim().ifBlank { BuildConfig.VERSION_NAME }
        val enabled = rc.getBoolean(KEY_UPDATE_CHECK_ENABLED)
        return if (enabled) {
            "Update Service: Active (Latest v${latest.removePrefix("v").removePrefix("V")})"
        } else {
            "Update Service: Paused"
        }
    }

    fun checkForUpdates(
        context: Context,
        forceRefresh: Boolean = false,
        repoOverride: String? = null,
        onResult: (UpdateOutcome) -> Unit
    ) {
        val rc = getRemoteConfigOrNull()
        if (rc == null) {
            val fallbackRepo = GitHubUpdateChecker.normalizeRepo(
                repoOverride ?: GitHubUpdateChecker.getConfiguredRepo(context)
            )
            checkGitHubWithRemoteFallback(
                context = context,
                repo = fallbackRepo,
                remoteConfigSynced = false,
                updateEnabled = true,
                rcLatestVersion = BuildConfig.VERSION_NAME,
                rcMinVersion = "0.1",
                rcReleaseUrl = "https://github.com/$fallbackRepo/releases/latest",
                rcReleaseNotes = "A new version of MYRA is ready.",
                rcForceUpdate = false,
                onResult = onResult
            )
            return
        }

        val fetchTask = if (forceRefresh) rc.fetch(0L) else rc.fetch()
        fetchTask.addOnCompleteListener { fTask ->
            if (fTask.isSuccessful) {
                rc.activate().addOnCompleteListener {
                    readRemoteConfigAndCheckGitHub(
                        context = context,
                        rc = rc,
                        synced = true,
                        repoOverride = repoOverride,
                        onResult = onResult
                    )
                }
            } else {
                Log.w(TAG, "Remote Config fetch failed, using cached/default values", fTask.exception)
                readRemoteConfigAndCheckGitHub(
                    context = context,
                    rc = rc,
                    synced = false,
                    repoOverride = repoOverride,
                    onResult = onResult
                )
            }
        }
    }

    private fun readRemoteConfigAndCheckGitHub(
        context: Context,
        rc: FirebaseRemoteConfig,
        synced: Boolean,
        repoOverride: String?,
        onResult: (UpdateOutcome) -> Unit
    ) {
        val updateEnabled = rc.getBoolean(KEY_UPDATE_CHECK_ENABLED)
        val rcLatestVersion = rc.getString(KEY_LATEST_APP_VERSION).trim().ifBlank { BuildConfig.VERSION_NAME }
        val rcMinVersion = rc.getString(KEY_MIN_SUPPORTED_VERSION).trim().ifBlank { "0.1" }
        val rcRepo = rc.getString(KEY_UPDATE_GITHUB_REPO).trim()
        val rcReleaseUrl = rc.getString(KEY_UPDATE_RELEASE_URL).trim()
        val rcReleaseNotes = rc.getString(KEY_UPDATE_RELEASE_NOTES).trim()
        val rcForceUpdate = rc.getBoolean(KEY_FORCE_UPDATE_REQUIRED)

        val storedRepo = GitHubUpdateChecker.getConfiguredRepo(context)
        val effectiveRepo = GitHubUpdateChecker.normalizeRepo(
            when {
                !repoOverride.isNullOrBlank() -> repoOverride
                storedRepo != GitHubUpdateChecker.DEFAULT_GITHUB_REPO -> storedRepo
                rcRepo.isNotBlank() -> rcRepo
                else -> storedRepo
            }
        )

        checkGitHubWithRemoteFallback(
            context = context,
            repo = effectiveRepo,
            remoteConfigSynced = synced,
            updateEnabled = updateEnabled,
            rcLatestVersion = rcLatestVersion,
            rcMinVersion = rcMinVersion,
            rcReleaseUrl = rcReleaseUrl.ifBlank { "https://github.com/$effectiveRepo/releases/latest" },
            rcReleaseNotes = rcReleaseNotes.ifBlank { "A new version of MYRA is ready." },
            rcForceUpdate = rcForceUpdate,
            onResult = onResult
        )
    }

    private fun checkGitHubWithRemoteFallback(
        context: Context,
        repo: String,
        remoteConfigSynced: Boolean,
        updateEnabled: Boolean,
        rcLatestVersion: String,
        rcMinVersion: String,
        rcReleaseUrl: String,
        rcReleaseNotes: String,
        rcForceUpdate: Boolean,
        onResult: (UpdateOutcome) -> Unit
    ) {
        val belowMinSupported = GitHubUpdateChecker.isNewerVersion(rcMinVersion, BuildConfig.VERSION_NAME)
        val isMandatory = rcForceUpdate || belowMinSupported

        if (!updateEnabled && !isMandatory) {
            onResult(
                UpdateOutcome(
                    checkResult = GitHubUpdateChecker.CheckResult.UpToDate(
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestTag = rcLatestVersion
                    ),
                    isMandatory = false,
                    remoteConfigSynced = remoteConfigSynced,
                    remoteLatestVersion = rcLatestVersion,
                    effectiveRepo = repo,
                    updateCheckEnabled = false
                )
            )
            return
        }

        GitHubUpdateChecker.checkLatestRelease(context, repo) { ghResult ->
            val rcHasNewer = GitHubUpdateChecker.isNewerVersion(rcLatestVersion, BuildConfig.VERSION_NAME)
            val normalizedRcTag = if (rcLatestVersion.startsWith("v", ignoreCase = true)) {
                rcLatestVersion
            } else {
                "v$rcLatestVersion"
            }

            val finalResult = when (ghResult) {
                is GitHubUpdateChecker.CheckResult.UpdateAvailable -> {
                    val ghRelease = ghResult.release
                    if (rcHasNewer && GitHubUpdateChecker.isNewerVersion(normalizedRcTag, ghRelease.tagName)) {
                        GitHubUpdateChecker.CheckResult.UpdateAvailable(
                            GitHubUpdateChecker.ReleaseInfo(
                                tagName = normalizedRcTag,
                                releaseName = "MYRA $normalizedRcTag",
                                releaseNotes = rcReleaseNotes,
                                htmlUrl = rcReleaseUrl,
                                apkDownloadUrl = ghRelease.apkDownloadUrl
                            )
                        )
                    } else {
                        ghResult
                    }
                }
                is GitHubUpdateChecker.CheckResult.UpToDate,
                is GitHubUpdateChecker.CheckResult.Error -> {
                    if (rcHasNewer) {
                        GitHubUpdateChecker.CheckResult.UpdateAvailable(
                            GitHubUpdateChecker.ReleaseInfo(
                                tagName = normalizedRcTag,
                                releaseName = "MYRA $normalizedRcTag",
                                releaseNotes = rcReleaseNotes,
                                htmlUrl = rcReleaseUrl,
                                apkDownloadUrl = null
                            )
                        )
                    } else {
                        ghResult
                    }
                }
            }

            onResult(
                UpdateOutcome(
                    checkResult = finalResult,
                    isMandatory = isMandatory,
                    remoteConfigSynced = remoteConfigSynced,
                    remoteLatestVersion = rcLatestVersion,
                    effectiveRepo = repo,
                    updateCheckEnabled = updateEnabled
                )
            )
        }
    }
}
