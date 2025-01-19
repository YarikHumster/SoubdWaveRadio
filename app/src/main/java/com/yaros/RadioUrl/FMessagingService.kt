package com.yaros.RadioUrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

class FMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Проверка наличия уведомления
        remoteMessage.notification?.let { notification ->
            val title = notification.title
            val body = notification.body

            // Создание намерения для открытия приложения
            val resultIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val resultPendingIntent = PendingIntent.getActivity(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Проверка канала уведомлений (необходимо для Android 8.0 и выше)
            val channelId = "2038"
            val channelName = "SoundWave"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = R.string.descr_notification.toString()
                    enableLights(true)
                    lightColor = Color.BLUE
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Создание уведомления
            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher) // Значок уведомления
                .setContentTitle(title) // Заголовок
                .setContentText(body) // Текст уведомления
                .setContentIntent(resultPendingIntent) // Намерение на открытие приложения
                .setAutoCancel(true) // Удаление уведомления по нажатию
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Высокий приоритет
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // Для длинных сообщений
                .setLights(Color.BLUE, 1000, 300) // Установка цветового мигания

            // Отправка уведомления
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(0, notificationBuilder.build())

            Timber.tag(TAG).d("Сообщение получено: $body + $title")
        }
    }

    override fun onNewToken(token: String) {
        // Обработка обновления токена
        sendRegistrationToServer(token)
        Timber.tag(TAG).d("Новый токен: $token")
    }

    private fun sendRegistrationToServer(token: String) {
        // Логика для отправки токена на сервер
    }

    companion object {
        private const val TAG = "MyFMsgService"
    }
}

