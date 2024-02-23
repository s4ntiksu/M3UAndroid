package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.data.R
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.PlaylistRepository
import com.m3u.i18n.R.string
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val manager: NotificationManager
) : CoroutineWorker(context, params) {
    private val dataSource = inputData
        .getString(INPUT_STRING_DATA_SOURCE)
        ?.let { DataSource.of(it) }
    private val title = inputData.getString(INPUT_STRING_TITLE)
    private val address = inputData.getString(INPUT_STRING_ADDRESS)
    private val username = inputData.getString(INPUT_STRING_USERNAME)
    private val password = inputData.getString(INPUT_STRING_PASSWORD)
    private val url = inputData.getString(INPUT_STRING_URL)
    private val strategy = inputData.getInt(INPUT_INT_STRATEGY, PlaylistStrategy.SKIP_FAVORITE)
    override suspend fun doWork(): Result = coroutineScope {
        dataSource ?: return@coroutineScope Result.failure()
        createChannel()
        when (dataSource) {
            DataSource.M3U -> {
                title ?: return@coroutineScope Result.failure()
                url ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    val message = context.getString(string.data_error_empty_title)
                    val data = workDataOf("message" to message)
                    Result.failure(data)
                } else {
                    try {
                        playlistRepository
                            .m3u(title, url, strategy)
                            .launchIn(this)
                        Result.success()
                    } catch (e: Exception) {
                        Result.failure()
                    }
                }
            }

            DataSource.Xtream -> {
                title ?: return@coroutineScope Result.failure()
                address ?: return@coroutineScope Result.failure()
                username ?: return@coroutineScope Result.failure()
                password ?: return@coroutineScope Result.failure()
                if (title.isEmpty()) {
                    val message = context.getString(string.data_error_empty_title)
                    val data = workDataOf("message" to message)
                    Result.failure(data)
                } else {
                    try {
                        playlistRepository.xtream(title, address, username, password, strategy)
                        Result.success()
                    } catch (e: Exception) {
                        Result.failure()
                    }
                }
            }

            else -> Result.failure()
        }
    }

    private val builder = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.round_file_download_24)
        .setContentTitle(title.orEmpty())
        .setContentText(url.orEmpty())
        .setOngoing(true)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "display subscribe task progress"
        manager.createNotificationChannel(channel)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle(title)
            .setContentText(url)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "subscribe task"
        private const val NOTIFICATION_ID = 1224
        const val INPUT_STRING_TITLE = "title"
        const val INPUT_STRING_URL = "url"
        const val INPUT_STRING_ADDRESS = "address"
        const val INPUT_STRING_USERNAME = "username"
        const val INPUT_STRING_PASSWORD = "password"
        const val INPUT_STRING_DATA_SOURCE = "data-source"
        const val INPUT_INT_STRATEGY = "strategy"
    }
}