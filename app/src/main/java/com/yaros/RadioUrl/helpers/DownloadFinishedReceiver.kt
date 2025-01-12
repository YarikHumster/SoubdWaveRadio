package com.yaros.RadioUrl.helpers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadFinishedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        DownloadHelper.processDownload(
            context,
            intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        )
    }
}
