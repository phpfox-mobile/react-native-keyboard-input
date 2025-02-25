package com.wix.reactnativekeyboardinput;

import android.graphics.Rect;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import com.facebook.react.ReactRootView;
import com.wix.reactnativekeyboardinput.utils.Logger;
import com.wix.reactnativekeyboardinput.utils.RuntimeUtils;

import static com.wix.reactnativekeyboardinput.GlobalDefs.TAG;
import static com.wix.reactnativekeyboardinput.utils.ViewUtils.getWindow;

public class ReactSoftKeyboardMonitor implements ReactScreenMonitor.Listener {

    public interface Listener {
        void onSoftKeyboardVisible(boolean distinct);
        void onSoftKeyboardHidden();
        boolean isUseSafeArea();
    }

    private final ViewTreeObserver.OnGlobalLayoutListener mInnerLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            Integer viewportVisibleHeight = getViewportVisibleHeight();

            if(mExternalListener.isUseSafeArea()){
                viewportVisibleHeight = viewportVisibleHeight + getStatusBarHeight();
            }

            if (viewportVisibleHeight.equals(mLastViewportVisibleHeight)) {
                return;
            }
            mLastViewportVisibleHeight = viewportVisibleHeight;

            if (viewportVisibleHeight < mMaxViewportVisibleHeight) {
                mExternalListener.onSoftKeyboardVisible(!mSoftKeyboardUp);
                refreshKeyboardHeight();
                mSoftKeyboardUp = true;
                Logger.d(TAG, "Keyboard SHOWING!");
            } else {
                mSoftKeyboardUp = false;
                mExternalListener.onSoftKeyboardHidden();
                Logger.d(TAG, "Keyboard GONE!");
            }
        }
    };

    /**
     * Soft-keyboard appearance (yes or no) is deduced according to <b>view-port</b> (window-level display-frame), as
     * root-view height normally remains unaffected during immediate layout. We therefore keep the maximal view-port size so we could
     * concurrently compare heights in each layout.
     */
    private int mMaxViewportVisibleHeight;

    private Integer mLastViewportVisibleHeight;

    /**
     * Soft-keyboard *height* (when visible) is deduced by the effect on the root react-view height. This is ineffective in trying to
     * monitor keyboard appearance -- only for height measuring.
     */
    private int mLocallyVisibleHeight;

    private boolean mSoftKeyboardUp;
    private Integer mKeyboardHeight;
    private Listener mExternalListener;
    private ReactRootView mLastReactRootView;

    public ReactSoftKeyboardMonitor(ReactScreenMonitor screenMonitor) {
        screenMonitor.addListener(this);
    }

    @Override
    public void onNewReactScreen(ReactRootView reactRootView) {
        removeReactRootViewLayoutListener();
        mLastReactRootView = reactRootView;

        if (mLastReactRootView != null) { // 'Null' is applicable when activity is going down (e.g. bundle reload in RN dev mode)
            registerReactRootViewLayoutListener();

            initViewportVisibleHeight(); // TODO: running this each time might be redundant
            initLocallyVisibleHeight();
        }
    }

    public void setListener(Listener listener) {
        mExternalListener = listener;
    }

    @Nullable
    public Integer getKeyboardHeight() {
        if (mKeyboardHeight != null) {
            return mKeyboardHeight;
        }

        return (int) (.5f * mLocallyVisibleHeight);
    }

    private void registerReactRootViewLayoutListener() {
        final ViewTreeObserver viewTreeObserver = mLastReactRootView.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(mInnerLayoutListener);
    }

    private void removeReactRootViewLayoutListener() {
        if (mLastReactRootView != null) {
            final ViewTreeObserver viewTreeObserver = mLastReactRootView.getViewTreeObserver();
            viewTreeObserver.removeOnGlobalLayoutListener(mInnerLayoutListener);
        }
    }

    private void initViewportVisibleHeight() {
        mMaxViewportVisibleHeight = getViewportVisibleHeight();
        mLastViewportVisibleHeight = null;
        Logger.d(TAG, "Measured new max view-port height: "+mMaxViewportVisibleHeight);
    }

    private void initLocallyVisibleHeight() {
        mLocallyVisibleHeight = getLocallyVisibleHeight();
        Logger.d(TAG, "Measured locally visible height: "+mLocallyVisibleHeight);
        mKeyboardHeight = null; // Reset so the keyboard would be measured in the next opportunity.
    }

    private void refreshKeyboardHeight() {
        if (mKeyboardHeight != null) {
            return;
        }

        RuntimeUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                final Integer locallyVisibleHeight = getLocallyVisibleHeight();
                if (locallyVisibleHeight == null) {
                    // Too late to join the party - react-view seems to be gone...
                    return;
                }

                if (mLocallyVisibleHeight > locallyVisibleHeight) {
                    mKeyboardHeight = mLocallyVisibleHeight - locallyVisibleHeight;
                }
            }
        });
    }

    private int getViewportVisibleHeight() {
        final Rect visibleArea = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleArea);
        return visibleArea.height();
    }

    public int getStatusBarHeight() {
        Integer statusBarHeight = null;
        final Rect visibleArea = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleArea);
        statusBarHeight = visibleArea.top;

        return statusBarHeight;

    }

    private Integer getLocallyVisibleHeight() {
        if (mLastReactRootView != null) {
            return mLastReactRootView.getHeight();
        }
        return null;
    }
}
