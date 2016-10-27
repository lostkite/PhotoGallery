package info.kite.lost.android.photogallery;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.List;

import info.kite.lost.android.photogallery.model.GalleryItem;
import info.kite.lost.android.photogallery.net.PhotoFetcher;
import info.kite.lost.android.photogallery.storage.QueryPreferences;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class PollService extends IntentService {
    private static final String TAG = "PollService";
    private static final long POLL_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    public static final String ACTION_SHOW_NOTIFICATION = "info.kite.lost.android" +
            ".photogallery.SHOW_NOTIFICATION";
    public static final String PERM_PRIVATE = "info.kite.lost.android.photogallery.PRIVATE";

    public static final String REQUEST_CODE = "REQUEST_CODE";
    public static final String NOTIFICATION = "NOTIFICATION";

    /**
     * 构造一个启动service的intent
     * @param context context
     * @return Intent
     */
    public static Intent newIntent(Context context) {
        return new Intent(context, PollService.class);
    }

    public PollService() {
        super(TAG);
    }

    /**
     * 静态方法需要外部传入context
     * @param context {@link Context}
     * @param isOn 定时服务开关
     */
    public static void setServiceAlarm(Context context, boolean isOn) {
        // 启动PollService的intent
        Intent i = PollService.newIntent(context);
        // 封装进PendingIntent，getService封装了startService()方法
        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);
        // AlarmManager 是一个系统service
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (isOn) {
            alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime(),
                    POLL_INTERVAL,
                    pi
            );
        } else {
            alarmManager.cancel(pi);
            pi.cancel();
        }
        QueryPreferences.setAlarmOn(context, isOn);
    }

    /**
     * 如果PendingIntent没有被创建，则会返回null而不是创建一个PendingIntent
     * @param context {@link Context}
     * @return Service定时是否设置
     */
    public static boolean isServiceAlarmOn(Context context) {
        Intent i = PollService.newIntent(context);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_NO_CREATE);
        return pi != null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!isNetworkAvailableAndConnected()) {
            return;
        }
        Log.i(TAG, "onHandleIntent: received an intent " + intent);
        String query = QueryPreferences.getStoredQuery(this);
        String lastResultId = QueryPreferences.getPrefLastResultId(this);
        List<GalleryItem> itemsOfService;

        if (query == null) {
            itemsOfService = new PhotoFetcher().fetchPopularPhotos();
        } else {
            itemsOfService = new PhotoFetcher().searchPhotos(query);
        }

        if (itemsOfService.size() == 0) {
            return;
        }

        String resultId = itemsOfService.get(0).getId();
        if (resultId.equals(lastResultId)) {
            Log.i(TAG, "onHandleIntent: got an old result" + resultId);
        } else {
            Log.i(TAG, "got an new result: " + resultId);
        }

        Resources resources = getResources();
        Intent i = PhotoGalleryActivity.newIntent(this);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        // 构建一个notification
        Notification notification = new NotificationCompat.Builder(this)
                .setTicker(resources.getString(R.string.new_pictures_title))
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setContentTitle(resources.getString(R.string.new_pictures_title))
                .setContentText(resources.getString(R.string.new_pictures_text))
                .setContentIntent(pi) // 相应notification点击事件
                .setAutoCancel(true) // 设置点击后消失
                .build();
//        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
//        // post notification
//        notificationManager.notify(0, notification);
//        // 发送广播，接收方是VisibleFragment
//        sendBroadcast(new Intent(ACTION_SHOW_NOTIFICATION), PERM_PRIVATE);
        showBackgroundNotification(0, notification);

        QueryPreferences.setPrefLastResultId(this, resultId);
    }

    private void showBackgroundNotification(int requestCode, Notification notification) {
        Intent i = new Intent(ACTION_SHOW_NOTIFICATION);
        i.putExtra(REQUEST_CODE, requestCode);
        i.putExtra(NOTIFICATION, notification);
        sendOrderedBroadcast(i, PERM_PRIVATE, null, null, Activity.RESULT_OK, null, null);
    }

    /**
     * 网络是否可用 && 网络是否能连接上
     *
     * @return true or false
     */
    private boolean isNetworkAvailableAndConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        // 如果禁用了后台网络，返回空
        boolean isNetworkAvailable = cm.getActiveNetworkInfo() != null;
        boolean isNetworkConnected = isNetworkAvailable && cm.getActiveNetworkInfo().isConnected();

        return isNetworkConnected;
    }
}
