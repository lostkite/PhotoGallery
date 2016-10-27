package info.kite.lost.android.photogallery.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created on 2016/10/18.
 */

public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    // message 的标志，what of message，一个应用中的what设置为不同为宜
    private static final int MESSAGE_DOWNLOAD = 0;
    // target of message，负责将 下载请求 封转成message有序地投入到下载进程中
    // 同时也负责消费掉looper从queue中取出的相关message
    private Handler mBitmapDownloadHandler;
    // 线程安全的HashMap，存放identity 和 urlString
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();

    // UI 进程中的handler，用来将bitmap传入holder
    private Handler mUIResponseHandler;
    // 回调接口,
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;

    /**
     * 监听回调接口，代表了处理下载完成的image的职责，提升代码复用性
     * 在bitmap下载完毕后被调用其中的onThumbnailDownloaded()方法
     * 由调用ThumbnailDownloader的类实现
     *
     * @param <T> 由downloader引用的UI Thread参数
     */
    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T identity, Bitmap bitmap);
    }

    /**
     * 设置回调借口
     * @param listener 实现了{@link ThumbnailDownloadListener}接口的类
     */
    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mUIResponseHandler = responseHandler;
    }

    /**
     * 在{@link android.os.Looper} 第一次检查queue之前被调用
     * 适合在此实现{@link HandlerThread} 中的handler
     */
    @Override
    protected void onLooperPrepared() {
        // 初始化mHandler
        mBitmapDownloadHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.i(TAG, "onLooperPrepared handleMessage: " + mRequestMap.get(target));
                    handleBitmapDownload(target);
                }
            }
        };
    }

    /**
     * 将identity 和 urlString存入线程安全的map中，再将identity作为message传给handler对应的Message Queue
     *
     * @param identity 专一的view与专一的URL对应，避免了photo显示错误的问题，同时也区分出不同的子线程
     * @param url 照片url
     */
    public void queueThumbnail(T identity, String url) {
        Log.i(TAG, "queueThumbnail: " + identity + url);

        if (url == null) {
            // url 为空表示ViewHolder被回收，故而remove
            mRequestMap.remove(identity);
        } else {
            mRequestMap.put(identity, url);
            // 此处obtainMessage 从一个recycler pool中生成message，其target就是mRequestHandler
            // 在这个app中，一个Message代表了T identity的一个下载请求
            // message.sentToTarget()发送msg 给目标Handler从属的Looper管理的MessageQueue
            // identity 在此是msg的obj，此处没有将urlString传过去因为可以从map中取出
            mBitmapDownloadHandler.obtainMessage(MESSAGE_DOWNLOAD, identity).sendToTarget();
        }
    }

    /**
     * MessageQueue清理，在Fragment或activity被重建时保证信息不紊乱
     */
    public void clearQueue() {
        // 清楚了queue，后续task会被终止
        mBitmapDownloadHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    /**
     * 根据identity 从map中取出urlString，再以url下载得到bitmap
     * @param identity T，key of mRequestMap{@link ConcurrentHashMap}
     */
    private void handleBitmapDownload(final T identity) {
        try {
            final String url = mRequestMap.get(identity);
            if (url == null) {
                return;
            }
            byte[] bitmapBytes = new PhotoFetcher().getUrlBytes(url);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.i(TAG, "handleRequest: bitmap created" );

            // Handler的另一种初始化方式，在run()方法中的所有代码都在主线程中运行
            mUIResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 由于PhotoHolder可能被回收复用，确认在下载图片期间url没有被改变
                    if (mRequestMap.get(identity) != url) {
                        return;
                    }
                    // 清除Map中的此次信息，从而不影响holder的复用
                    mRequestMap.remove(identity);

                    mThumbnailDownloadListener.onThumbnailDownloaded(identity, bitmap);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "handleRequest: ", e);
        }

    }
}
