package com.myra.assistant.update

import android.app.Activity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.myra.assistant.BuildConfig
import com.myra.assistant.R

object UpdateDialogHelper {

    fun showUpdateDialog(
        activity: Activity,
        release: GitHubUpdateChecker.ReleaseInfo,
        isMandatory: Boolean = false
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_available, null)
        val headerBadge = view.findViewById<TextView>(R.id.updateHeaderBadge)
        val closeBtn = view.findViewById<ImageButton>(R.id.closeUpdateDialogBtn)
        val titleText = view.findViewById<TextView>(R.id.updateTitleText)
        val versionPill = view.findViewById<TextView>(R.id.updateVersionPill)
        val notesText = view.findViewById<TextView>(R.id.updateNotesText)
        val downloadBtn = view.findViewById<Button>(R.id.downloadUpdateBtn)

        val currentVer = "v${BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V")}"
        val nextVer = "v${release.tagName.removePrefix("v").removePrefix("V")}"

        headerBadge.text = if (isMandatory) "REQUIRED UPDATE" else "MYRA UPDATE"
        titleText.text = if (isMandatory) "Update Required" else "New Version Ready"
        versionPill.text = "$currentVer  →  $nextVer"
        notesText.text = cleanReleaseNotes(release.releaseNotes)

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(!isMandatory)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        downloadBtn.setOnClickListener {
            dialog.dismiss()
            GitHubUpdateChecker.openReleaseInBrowser(
                activity,
                release,
                preferApkLink = release.apkDownloadUrl != null
            )
        }

        dialog.show()
    }

    private fun cleanReleaseNotes(rawNotes: String): String {
        val cleanedLines = rawNotes
            .lineSequence()
            .map { it.replace("**", "").replace("###", "").replace("##", "").trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.contains("http://", ignoreCase = true) &&
                    !line.contains("https://", ignoreCase = true) &&
                    !line.contains("github", ignoreCase = true) &&
                    !line.contains("full changelog", ignoreCase = true)
            }
            .toList()

        return if (cleanedLines.isEmpty()) {
            "Performance upgrades, bug fixes, and the latest MYRA improvements are ready."
        } else {
            cleanedLines.joinToString("\n").take(260)
        }
    }
}
