package com.deniscerri.ytdlnis.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.DownloadDao
import com.deniscerri.ytdlnis.database.dao.ResultDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


class MoveCacheFilesWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val notificationUtil = NotificationUtil(context)
        val id = System.currentTimeMillis().toInt()

        val downloadFolders = File(context.cacheDir.absolutePath + "/downloads")
        val allContent = downloadFolders.walk()
        allContent.drop(1)
        val totalFiles = allContent.count()
        val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + File.separator + "YTDLnis/CACHE_IMPORT")

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createMoveCacheFilesNotification(pendingIntent, NotificationUtil.DOWNLOAD_MISC_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(id, notification)
        setForegroundAsync(foregroundInfo)

        var progress = 0
        allContent.forEach {
            progress++
            notificationUtil.updateCacheMovingNotification(id, progress, totalFiles)
            val destFile = File(destination.absolutePath + "/${it.absolutePath.removePrefix(context.cacheDir.absolutePath + "/downloads")}")
            if (it.isDirectory) {
                destFile.mkdirs()
                return@forEach
            }

            if (Build.VERSION.SDK_INT >= 26 ){
                Files.move(it.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }else{
                it.renameTo(destFile)
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            Toast.makeText(context, context.getString(R.string.ok), Toast.LENGTH_SHORT).show()
        }
        return Result.success()
    }

    companion object {
        const val TAG = "MoveCacheFilesWorker"
    }

}