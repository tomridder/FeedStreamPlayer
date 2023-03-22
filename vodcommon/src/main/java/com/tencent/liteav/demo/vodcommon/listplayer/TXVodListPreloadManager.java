package com.tencent.liteav.demo.vodcommon.listplayer;

import android.content.Context;
import android.util.Log;

import com.tencent.rtmp.downloader.ITXVodPreloadListener;
import com.tencent.rtmp.downloader.TXVodPreloadManager;

import java.util.ArrayList;
import java.util.List;

public class TXVodListPreloadManager implements ITXVodPreloadListener {

    public  static final String TAG = "TXVodListPlayer::TXVodListPreloadManager";

    private static volatile TXVodListPreloadManager mInstance;

    private TXVodPreloadManager preloadManager;

    private List<Integer> taskIDList;

    private List<String> urlList;

    private TXVodListPreloadManager(Context context) {
        preloadManager = TXVodPreloadManager.getInstance(context.getApplicationContext());
        taskIDList = new ArrayList<>();
        urlList = new ArrayList<>();
    }

    /**
     * Get TXVodListPreloadManager instance
     *
     * @param context
     * @return
     */
    public static TXVodListPreloadManager getInstance(Context context) {
        if (mInstance == null) {
            synchronized (TXVodListPreloadManager.class) {
                if (mInstance == null) {
                    mInstance = new TXVodListPreloadManager(context.getApplicationContext());
                }
            }
        }
        return mInstance;
    }

    /**
     * Start Preload Video
     *
     * @param url
     * @param preloadSizeMB
     * @param preferredResolution
     * @return
     */
    public int startPreload(String url,int preloadSizeMB,long preferredResolution) {
        if (!isPreLoad(url)) {
            urlList.add(url);
            int taskID = preloadManager.startPreload(url,preloadSizeMB,preferredResolution,this);
            Log.i(TAG,"[startPreload] " + url);
            taskIDList.add(taskID);
            return taskID;
        }
        return -1;
    }

    /**
     * Is video Preload Or not
     * @param taskId
     * @return
     */
    public boolean isPreLoad(int taskId) {
        return taskIDList.contains(taskId);
    }

    /**
     * Is video Preload Or not
     * @param url
     * @return
     */
    public boolean isPreLoad(String url) {
        return urlList.contains(url);
    }

    /**
     * Release TXVodListPreloadManager
     */
    public void release() {
        mInstance = null;
        for (int taskId : taskIDList) {
            preloadManager.stopPreload(taskId);
        }
        taskIDList.clear();;
        urlList.clear();
        preloadManager = null;
    }

    @Override
    public void onComplete(int taskId, String url) {
        Log.i(TAG, "[onComplete] "  + url);
        preloadManager.stopPreload(taskId);
    }

    @Override
    public void onError(int taskID, String url, int code, String msg) {
        Log.i(TAG, "[onError] url "  + url + " taskID " + taskID + " code " + code + " msg " + msg);
    }

}
