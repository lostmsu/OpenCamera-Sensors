package net.sourceforge.opencamera;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/** Foreground service that keeps the process alive whilst video recording continues in the
 *  background (activity paused, screen turned off). The camera and MediaRecorder are still owned
 *  by MainActivity/Preview - this service just raises the process priority (so the OS doesn't
 *  revoke camera access or kill us), shows the required foreground notification, and holds a
 *  partial wakelock so the CPU keeps running whilst the screen is off.
 */
public class RecordingService extends Service {
    private static final String TAG = "RecordingService";

    private static final String CHANNEL_ID = "open_camera_video_recording_channel";
    private static final int NOTIFICATION_ID = 2; // n.b., MainActivity uses 1 for the image saving notification

    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate();
        PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        if( powerManager != null ) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenCamera:RecordingService");
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if( MyDebug.LOG )
            Log.d(TAG, "onStartCommand");
        Notification notification = createNotification();
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        }
        else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        int pending_intent_flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, pending_intent_flags);
        Notification.Builder builder;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.video_recording_notification_channel), NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        }
        else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.ic_videocam_white_48dp)
                .setContentTitle(getString(R.string.video_recording_notification_title))
                .setContentText(getString(R.string.video_recording_notification_text))
                .setOngoing(true)
                .setContentIntent(pendingIntent);
        return builder.build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if( MyDebug.LOG )
            Log.d(TAG, "onTaskRemoved");
        // the activity (and hence the recording) is gone, so no point keeping the process in the foreground
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        if( wakeLock != null ) {
            if( wakeLock.isHeld() )
                wakeLock.release();
            wakeLock = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
