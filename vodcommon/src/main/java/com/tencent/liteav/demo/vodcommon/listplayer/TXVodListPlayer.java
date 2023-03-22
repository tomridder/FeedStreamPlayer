package com.tencent.liteav.demo.vodcommon.listplayer;

import android.content.Context;
import android.nfc.Tag;
import android.util.Log;

import com.tencent.liteav.demo.vodcommon.entity.VideoModel;
import com.tencent.rtmp.ITXVodPlayListener;
import com.tencent.rtmp.TXPlayerGlobalSetting;
import com.tencent.rtmp.TXVodPlayConfig;
import com.tencent.rtmp.TXVodPlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TXVodListPlayer {

    public  static final String TAG = "TXVodListPlayer::TXVodListPlayer";

    private TXVodPlayer mVodPlayer;

    private TXVodListPreloadManager mPreloadManager;

    private CopyOnWriteArrayList<VideoModel> mList;

    private int mCurrentIndex = 0;

    private int mPreloadSizeMB = 5;

    private int mPreferResolution = 720 * 1280;

    public TXVodListPlayer(Context context) {
        mVodPlayer = new TXVodPlayer(context);
        mPreloadManager = TXVodListPreloadManager.getInstance(context);
        mList = new CopyOnWriteArrayList<>();
    }

    private int findCurrentIndex(VideoModel model) {
        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).videoURL == model.videoURL) {
                return i;
            }
        }
        return -1;
    }

    public void setSingleVideoPreloadSizeMB(int preLoadSize) {
        mPreloadSizeMB = preLoadSize;
    }

    public void setPreferResolution(int preferResolution) {
        mPreferResolution = preferResolution;
    }

    /**
     * Set Player's Config
     * @param config
     */
    public void setConfig(TXVodPlayConfig config) {
        mVodPlayer.setConfig(config);
    }

    /**
     * Set player's cache folder path
     * @param path the directory of cache folder
     */
    public void setCacheFolderPath(String path) {
        TXPlayerGlobalSetting.setCacheFolderPath(path);
    }

    /**
     * AddVideo To TxListPlayer
     *
     * @param model
     */
    public void addVideo(VideoModel model) {
        mList.add(model);
        Log.i(TAG, "url " + model.videoURL);
        mPreloadManager.startPreload(model.videoURL, mPreloadSizeMB, mPreferResolution);
    }

    /**
     * Play Current VideoModel
     *
     * @param model
     */
    public boolean moveTo(VideoModel model) {
        mCurrentIndex = findCurrentIndex(model);
        if (mCurrentIndex == -1) {
            return false;
        }
        mVodPlayer.startVodPlay(mList.get(mCurrentIndex).videoURL);
        return true;
    }

    /**
     * Clear txVodListPlayer data
     */
    public void clear() {
        mList.clear();
    }

    /**
     * Play next VideoModel
     */
    public boolean moveToNext() {
        mCurrentIndex = mCurrentIndex + 1;
        if (mCurrentIndex > mList.size() - 1) {
            return false;
        } else {
            mVodPlayer.startVodPlay(mList.get(mCurrentIndex).videoURL);
            return true;
        }
    }

    /**
     * Play pre VideoModel
     */
    public boolean moveToPrev() {
        mCurrentIndex = mCurrentIndex - 1;
        if (mCurrentIndex >= 0) {
            mVodPlayer.startVodPlay(mList.get(mCurrentIndex).videoURL);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Remove selected video Source
     * @param model
     */
    public boolean removeSource(VideoModel model) {
        return mList.remove(model);
    }

    /**
     * SetVodListener
     * @param vodListener
     */
    public void setVodListener(ITXVodPlayListener vodListener) {
        mVodPlayer.setVodListener(vodListener);
    }

    /**
     * Set isLoop
     * @param isLoop
     */
    public void setLoop(boolean isLoop) {
        mVodPlayer.setLoop(isLoop);
    }

    /**
     * Stop Play
     */
    public void stop() {
        mVodPlayer.stopPlay(true);
    }

    /**
     * Release txVodListPlayer
     */
    public void release() {
        mVodPlayer.stopPlay(true);
        mList.clear();
        mPreloadManager.release();
    }

    /**
     * Play first videoModel
     */
    public void start() {
        if (mList.size() != 0) {
            moveTo(mList.get(0));
        }
    }

    /**
     * Pause play
     */
    public void pause() {
        mVodPlayer.pause();
    }

    /**
     * SetPlayerView for TxVodListPlayer
     *
     * @param view
     */
    public void setPlayerView(TXCloudVideoView view) {
        mVodPlayer.setPlayerView(view);
    }

    /**
     * Resume play
     */
    public void resume() {
        mVodPlayer.resume();
    }

    /**
     * IsPlaying or not
     * @return
     */
    public boolean isPlaying() {
        return mVodPlayer.isPlaying();
    }

    /**
     * SeekTo position
     * @param progress
     */
    public void seekTo(int progress) {
        mVodPlayer.seek(progress);
    }

    /**
     * Return current VodPlayer
     * @return
     */
    public TXVodPlayer getVodPlayer() {
        return mVodPlayer;
    }
}
