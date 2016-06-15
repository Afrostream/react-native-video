package com.brentvatne.react;

import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.net.Uri;
import android.webkit.CookieManager;
import android.support.annotation.IntRange;
import android.content.Intent;
import android.content.Context;

import java.util.Map;
import java.util.HashMap;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import com.devbrackets.android.exomedia.BuildConfig;
import com.devbrackets.android.exomedia.listener.OnBufferUpdateListener;
import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.ui.widget.EMVideoView;
import com.devbrackets.android.exomedia.core.video.scale.ScaleType;

import com.devbrackets.android.exomedia.core.builder.DashRenderBuilder;
import com.devbrackets.android.exomedia.core.builder.HlsRenderBuilder;
import com.devbrackets.android.exomedia.core.builder.RenderBuilder;
import com.devbrackets.android.exomedia.core.builder.SmoothStreamRenderBuilder;
import com.devbrackets.android.exomedia.type.MediaSourceType;
import com.devbrackets.android.exomedia.util.MediaSourceUtil;

import com.google.android.exoplayer.util.Util;


public class ReactVideoView extends EMVideoView implements
        OnPreparedListener, OnErrorListener, OnBufferUpdateListener, OnCompletionListener {

    protected static final String USER_AGENT_FORMAT = "EMVideoView %s / Android %s / %s";
    // For use within demo app code.
    public static final String CONTENT_ID_EXTRA = "content_id";
    public static final String CONTENT_TYPE_EXTRA = "content_type";
    public static final String PROVIDER_EXTRA = "provider";
    // For use when launching the demo app using adb.
    private static final String CONTENT_EXT_EXTRA = "type";

    private String contentId;
    private String provider;

    public enum Events

    {
        EVENT_LOAD_START("onVideoLoadStart"),
                EVENT_LOAD("onVideoLoad"),
                EVENT_ERROR("onVideoError"),
                EVENT_PROGRESS("onVideoProgress"),
                EVENT_SEEK("onVideoSeek"),
                EVENT_END("onVideoEnd");

        private final String mName;

        Events( final String name){
        mName = name;
    }

        @Override
        public String toString () {
        return mName;
    }
    }

    public static final String EVENT_PROP_FAST_FORWARD = "canPlayFastForward";
    public static final String EVENT_PROP_SLOW_FORWARD = "canPlaySlowForward";
    public static final String EVENT_PROP_SLOW_REVERSE = "canPlaySlowReverse";
    public static final String EVENT_PROP_REVERSE = "canPlayReverse";
    public static final String EVENT_PROP_STEP_FORWARD = "canStepForward";
    public static final String EVENT_PROP_STEP_BACKWARD = "canStepBackward";

    public static final String EVENT_PROP_DURATION = "duration";
    public static final String EVENT_PROP_PLAYABLE_DURATION = "playableDuration";
    public static final String EVENT_PROP_CURRENT_TIME = "currentTime";
    public static final String EVENT_PROP_SEEK_TIME = "seekTime";
    public static final String EVENT_PROP_NATURALSIZE = "naturalSize";
    public static final String EVENT_PROP_WIDTH = "width";
    public static final String EVENT_PROP_HEIGHT = "height";
    public static final String EVENT_PROP_ORIENTATION = "orientation";

    public static final String EVENT_PROP_ERROR = "error";
    public static final String EVENT_PROP_WHAT = "what";
    public static final String EVENT_PROP_EXTRA = "extra";

    private ThemedReactContext mThemedReactContext;
    private RCTEventEmitter mEventEmitter;

    private Handler mProgressUpdateHandler = new Handler();
    private Runnable mProgressUpdateRunnable = null;

    private String mSrcUriString = null;
    private String mSrcType = "mp4";
    private boolean mSrcIsNetwork = false;
    private boolean mSrcIsAsset = false;
    private ScaleType mResizeMode = ScaleType.CENTER_INSIDE;
    private boolean mRepeat = false;
    private boolean mPaused = false;
    private boolean mMuted = false;
    private float mVolume = 1.0f;
    private float mRate = 1.0f;
    private boolean mPlayInBackground = false;

    private boolean mMediaPlayerValid = false; // True if mMediaPlayer is in prepared, started, or paused state.
    private int mVideoDuration = 0;
    private int mVideoBufferedDuration = 0;

    public ReactVideoView(ThemedReactContext themedReactContext) {
        super(themedReactContext);

        mThemedReactContext = themedReactContext;
        mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);

        setupPlayerListeners();

        mProgressUpdateRunnable = new Runnable() {
            @Override
            public void run() {

                if (mMediaPlayerValid) {
                    WritableMap event = Arguments.createMap();
                    event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
                    event.putDouble(EVENT_PROP_PLAYABLE_DURATION, mVideoBufferedDuration / 1000.0); //TODO:mBufferUpdateRunnable
                    mEventEmitter.receiveEvent(getId(), Events.EVENT_PROGRESS.toString(), event);
                }
                mProgressUpdateHandler.postDelayed(mProgressUpdateRunnable, 250);
            }
        };
        mProgressUpdateHandler.post(mProgressUpdateRunnable);
    }

    private void setupPlayerListeners() {
        this.setOnErrorListener(this);
        this.setOnPreparedListener(this);
        this.setOnBufferUpdateListener(this);
        this.setOnCompletionListener(this);
    }

    public void setSrc(final String uriString, final String type, final boolean isNetwork, final boolean isAsset) {
        mSrcUriString = uriString;
        mSrcType = type;
        mSrcIsNetwork = isNetwork;
        mSrcIsAsset = isAsset;

        mMediaPlayerValid = false;
        mVideoDuration = 0;
        mVideoBufferedDuration = 0;

        Uri parsedUrl = Uri.parse(uriString);
        RenderBuilder builder = parsedUrl == null ? null : getRendererBuilder(MediaSourceUtil.getType(parsedUrl), parsedUrl);
        //WidevineDrm drm = new WidevineDrm(getContext().getApplicationContext());
        //drm.acquireRights(uriString);
        try {
            if (isNetwork) {
                // Use the shared CookieManager to access the cookies
                // set by WebViews inside the same app
                CookieManager cookieManager = CookieManager.getInstance();

                Uri.Builder builtUrl = parsedUrl.buildUpon();

                String cookie = cookieManager.getCookie(builtUrl.build().toString());

                Map<String, String> headers = new HashMap<String, String>();

                if (cookie != null) {
                    headers.put("Cookie", cookie);
                }

                setVideoURI(parsedUrl, builder);
            } else if (isAsset) {
                if (uriString.startsWith("content://")) {
                    setVideoURI(parsedUrl, builder);
                } else {
                    setVideoURI(parsedUrl, builder);
                }
            } else {
                throw new IllegalArgumentException("raw resource not supported by ExoPlayer");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        WritableMap src = Arguments.createMap();
        src.putString(ReactVideoViewManager.PROP_SRC_URI, uriString);
        src.putString(ReactVideoViewManager.PROP_SRC_TYPE, type);
        src.putBoolean(ReactVideoViewManager.PROP_SRC_IS_NETWORK, isNetwork);
        WritableMap event = Arguments.createMap();
        event.putMap(ReactVideoViewManager.PROP_SRC, src);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD_START.toString(), event);
    }

    /**
     * Creates and returns the correct render builder for the specified VideoType and uri.
     *
     * @param renderType The RenderType to use for creating the correct RenderBuilder
     * @param uri        The video's Uri
     * @return The appropriate RenderBuilder
     */
    protected RenderBuilder getRendererBuilder(MediaSourceType renderType, Uri uri) {
        switch (renderType) {
            case HLS:
                return new HlsRenderBuilder(getContext().getApplicationContext(), getUserAgent(), uri.toString());
            case DASH:
                //return new DashDRMRenderBuilder(getContext().getApplicationContext(), getUserAgent(), uri.toString(),
                //        new WidevineTestMediaDrmCallback(getContext().getApplicationContext(), uri.toString(), "eyJ1c2VySWQiOiIxMjM0NSIsInNlc3Npb25JZCI6ImV3b2dJQ0p3Y205bWFXeGxJaUE2SUhzS0lDQWdJQ0p3ZFhKamFHRnpaU0lnT2lCN0lIMEtJQ0I5TEFvZ0lDSnZkWFJ3ZFhSUWNtOTBaV04wYVc5dUlpQTZJSHNLSUNBZ0lDSmthV2RwZEdGc0lpQTZJR1poYkhObExBb2dJQ0FnSW1GdVlXeHZaM1ZsSWlBNklHWmhiSE5sTEFvZ0lDQWdJbVZ1Wm05eVkyVWlJRG9nWm1Gc2MyVUtJQ0I5TEFvZ0lDSnpkRzl5WlV4cFkyVnVjMlVpSURvZ1ptRnNjMlVLZlFvSyIsIm1lcmNoYW50IjoiY2FibGVsYWJzIn0K"));
                return new DashRenderBuilder(getContext().getApplicationContext(), getUserAgent(), uri.toString());
            case SMOOTH_STREAM:
                return new SmoothStreamRenderBuilder(getContext().getApplicationContext(), getUserAgent(), uri.toString());
            default:
                return new RenderBuilder(getContext().getApplicationContext(), getUserAgent(), uri.toString());
        }
    }

    /**
     * Retrieves the user agent that the EMVideoView will use when communicating
     * with media servers
     *
     * @return The String user agent for the EMVideoView
     */
    public String getUserAgent() {
        return String.format(USER_AGENT_FORMAT, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", Build.VERSION.RELEASE, Build.MODEL);
    }

    public void setResizeModeModifier(final ScaleType resizeMode) {
        mResizeMode = resizeMode;

        if (mMediaPlayerValid) {
            setScaleType(resizeMode);
            invalidate();
        }
    }

    public void setRepeatModifier(final boolean repeat) {
        mRepeat = repeat;
        // TODO: no looping support from ExoPlayer yet, so do it manually at complete event
        //if (mMediaPlayerValid) {
        //    setLooping(repeat);
        //}
    }

    public void setPausedModifier(final boolean paused) {
        mPaused = paused;

        if (!mMediaPlayerValid) {
            return;
        }

        if (mPaused) {
            if (this.isPlaying()) {
                pause();
            }
        } else {
            if (!this.isPlaying()) {
                start();
            }
        }
    }

    public void setMutedModifier(final boolean muted) {
        mMuted = muted;

        if (!mMediaPlayerValid) {
            return;
        }

        if (mMuted) {
            setVolume(0);
        } else {
            setVolume(mVolume);
        }
    }

    public void setVolumeModifier(final float volume) {
        mVolume = volume;
        setMutedModifier(mMuted);
    }

    public void setRateModifier(final float rate) {
        mRate = rate;

        if (mMediaPlayerValid) {
            // TODO: Implement this.
            Log.e(ReactVideoViewManager.REACT_CLASS, "Setting playback rate is not yet supported on Android");
        }
    }

    public void applyModifiers() {
        setResizeModeModifier(mResizeMode);
        // TODO: no looping support from ExoPlayer yet, so do it manually at complete event
        //setRepeatModifier(mRepeat);
        setPausedModifier(mPaused);
        setMutedModifier(mMuted);
        //setRateModifier(mRate);
    }

    public void setPlayInBackground(final boolean playInBackground) {
        mPlayInBackground = playInBackground;
    }

    @Override
    public void onPrepared() {
        mMediaPlayerValid = true;
        mVideoDuration = getDuration();

        WritableMap naturalSize = Arguments.createMap();
        //naturalSize.putInt(EVENT_PROP_WIDTH, mp.getVideoWidth());
        //naturalSize.putInt(EVENT_PROP_HEIGHT, mp.getVideoHeight());
        //if (mp.getVideoWidth() > mp.getVideoHeight())
        //    naturalSize.putString(EVENT_PROP_ORIENTATION, "landscape");
        //else
        //    naturalSize.putString(EVENT_PROP_ORIENTATION, "portrait");

        WritableMap event = Arguments.createMap();
        event.putDouble(EVENT_PROP_DURATION, mVideoDuration / 1000.0);
        event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
        event.putMap(EVENT_PROP_NATURALSIZE, naturalSize);
        // TODO: Actually check if you can.
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_FORWARD, true);
        event.putBoolean(EVENT_PROP_SLOW_REVERSE, true);
        event.putBoolean(EVENT_PROP_REVERSE, true);
        event.putBoolean(EVENT_PROP_FAST_FORWARD, true);
        event.putBoolean(EVENT_PROP_STEP_BACKWARD, true);
        event.putBoolean(EVENT_PROP_STEP_FORWARD, true);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_LOAD.toString(), event);

        applyModifiers();
    }

    @Override
    public boolean onError() {
        WritableMap error = Arguments.createMap();
        error.putInt(EVENT_PROP_WHAT, 0);
        error.putInt(EVENT_PROP_EXTRA, 0);
        WritableMap event = Arguments.createMap();
        event.putMap(EVENT_PROP_ERROR, error);
        mEventEmitter.receiveEvent(getId(), Events.EVENT_ERROR.toString(), event);
        return true;
    }

    @Override
    public void onBufferingUpdate(@IntRange(from = 0, to = 100) int percent) {
        mVideoBufferedDuration = (int) Math.round((double) (mVideoDuration * percent) / 100.0);
    }

    @Override
    public void seekTo(int msec) {

        if (mMediaPlayerValid) {
            WritableMap event = Arguments.createMap();
            event.putDouble(EVENT_PROP_CURRENT_TIME, getCurrentPosition() / 1000.0);
            event.putDouble(EVENT_PROP_SEEK_TIME, msec / 1000.0);
            mEventEmitter.receiveEvent(getId(), Events.EVENT_SEEK.toString(), event);

            super.seekTo(msec);
        }
    }

    @Override
    public void onCompletion() {
        mMediaPlayerValid = false;
        if (mRepeat) {
            setSrc(mSrcUriString, mSrcType, mSrcIsNetwork, mSrcIsAsset);
        } else {
            mMediaPlayerValid = false;
            mEventEmitter.receiveEvent(getId(), Events.EVENT_END.toString(), null);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mMediaPlayerValid = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSrc(mSrcUriString, mSrcType, mSrcIsNetwork, mSrcIsAsset);
    }
}
