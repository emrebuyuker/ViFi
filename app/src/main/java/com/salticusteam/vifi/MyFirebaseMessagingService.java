package com.salticusteam.vifi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {

    private static final String CHANNEL_ID = "general";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        showNotification(remoteMessage.getData().get("message")); // Mesaj içeriği alınıp bildirim gösteren metod çağırılıyor
    }

    private void showNotification(String message) {

        Intent i = new Intent(this, MainActivity.class); // Bildirime basıldığında hangi aktiviteye gidilecekse
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // FLAG_IMMUTABLE is mandatory from Android 12 (API 31) on; minSdk 24 keeps it a no-op on older devices.
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        // Notifications without a channel are silently dropped on Android 8+ (API 26).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel_general_name), NotificationManager.IMPORTANCE_DEFAULT));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setAutoCancel(true) // Kullanıcı bildirime girdiğinde otomatik olarak silinsin. False derseniz bildirim kalıcı olur.
                .setContentTitle("FCM Test") // Bildirim başlığı
                .setContentText(message) // Bildirim mesajı
                .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark) // Bildirim simgesi
                .setContentIntent(pendingIntent);

        manager.notify(0, builder.build());
    }
}
