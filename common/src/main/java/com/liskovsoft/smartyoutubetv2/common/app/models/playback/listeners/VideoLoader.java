package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listeners;

import android.annotation.SuppressLint;
import com.liskovsoft.mediaserviceinterfaces.MediaItemManager;
import com.liskovsoft.mediaserviceinterfaces.MediaService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import java.io.InputStream;

public class VideoLoader extends PlayerEventListenerHelper {
    private static final String TAG = VideoLoader.class.getSimpleName();
    private final Playlist mPlaylistManager;
    private Video mLastVideo;

    public VideoLoader() {
        mPlaylistManager = Playlist.instance();
    }

    @Override
    public void setVideo(Video item) {
        mLastVideo = item;
        mPlaylistManager.add(item);
    }

    @Override
    public void onEngineInitialized() {
        loadVideo(mLastVideo);
    }

    @Override
    public void onPreviousClicked() {
        loadVideo(mPlaylistManager.previous());
    }

    @Override
    public void onNextClicked() {
        Video next = mPlaylistManager.next();

        if (next == null) {
            loadNextVideo(mPlaylistManager.getCurrent());
        } else {
            loadVideo(next);
        }
    }

    @Override
    public void onPlayEnd() {
        onNextClicked();
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        mPlaylistManager.add(item);
        loadVideo(item);
    }

    private void loadVideo(Video item) {
        if (item != null) {
            mLastVideo = item;
            mController.setVideo(item);
            loadFormatInfo(item);
        }
    }

    private void loadNextVideo(MediaItemMetadata metadata) {
        mController.getVideo().mediaItemMetadata = metadata;

        MediaItem nextVideo = metadata.getNextVideo();
        Video item = Video.from(nextVideo);
        mPlaylistManager.add(item);
        loadVideo(item);
    }

    @SuppressLint("CheckResult")
    private void loadNextVideo(Video current) {
        if (current == null) {
            return;
        }

        MediaItemMetadata mediaItemMetadata = mController.getVideo().mediaItemMetadata;
        if (mediaItemMetadata != null && mediaItemMetadata.getNextVideo() != null) {
            loadNextVideo(mediaItemMetadata);
            return;
        }

        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getMetadataObserve(current.mediaItem)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadNextVideo, error -> Log.e(TAG, "loadNextVideo: " + error));
    }

    @SuppressLint("CheckResult")
    private void loadFormatInfo(Video video) {
        String videoTitle = video.title;
        MediaService service = YouTubeMediaService.instance();
        MediaItemManager mediaItemManager = service.getMediaItemManager();
        mediaItemManager.getFormatInfoObserve(video.videoId)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(formatInfo -> {
                    loadFormatInfo(formatInfo, videoTitle);
                }, error -> Log.e(TAG, "loadFormatInfo: " + error));
    }

    private void loadFormatInfo(MediaItemFormatInfo formatInfo, String videoTitle) {
        if (formatInfo == null) {
            Log.e(TAG, "loadFormatInfo: Can't obtains format info: " + videoTitle);
            return;
        }

        InputStream dashStream = formatInfo.getMpdStream();
        String hlsManifestUrl = formatInfo.getHlsManifestUrl();

        if (hlsManifestUrl != null) {
            mController.openHls(hlsManifestUrl);
        } else {
            mController.openDash(dashStream);
        }
    }
}