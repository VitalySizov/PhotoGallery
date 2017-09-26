package com.sizov.vitaly.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;

    private Handler mRequestHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadeListener;
    private LruCache<String, Bitmap> mLruCache;

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadeListener (ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadeListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
        mLruCache = new LruCache<String, Bitmap>(20480);
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_DOWNLOAD:
                        T target = (T) msg.obj;
                        Log.i(TAG, "Got a request for URL: " + mRequestMap.get(target));
                        handleRequest(target);
                        break;
                    case MESSAGE_PRELOAD:
                        String url = (String) msg.obj;
                        downLoadImage(url);
                        break;
                }
            }
        };
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void preLoadImage(String url) {
        mRequestHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget();
    }

    // Очистка очереди
    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    // Очистка кэша
    public void clearCache() {
        mLruCache.evictAll();
    }

    // Получение закэшированного изображения
    public Bitmap getCachedImage(String url) {
        return mLruCache.get(url);
    }

    private void handleRequest(final T target) {
        final String url = mRequestMap.get(target);
        final Bitmap bitmap;
        if (url == null) return;

        bitmap = downLoadImage(url);
        Log.i(TAG, "Bitmap created");

        // Загрузка и вывод
        mResponseHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mRequestMap.get(target) != url) {
                    return;
                }

                mRequestMap.remove(target);
                mThumbnailDownloadeListener.onThumbnailDownloaded(target, bitmap);
            }
        });
    }

    private Bitmap downLoadImage(String url) {
        Bitmap bitmap;

        if (url == null) return null;

        // Если картинка уже в кэше, не загружать ее
        bitmap = mLruCache.get(url);
        if (bitmap != null) return bitmap;

        // Загрузка и кэширование изображения
        try {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            mLruCache.put(url, bitmap);
            Log.i(TAG, "Download and cached image: " + url);
            return bitmap;
        } catch (IOException ex) {
            Log.e(TAG, "Error downloading image.", ex);
            return null;
        }
    }
}
