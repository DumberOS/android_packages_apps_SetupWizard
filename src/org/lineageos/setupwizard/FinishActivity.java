/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static org.lineageos.setupwizard.SetupWizardApp.LOGV;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.lineageos.setupwizard.util.SetupWizardUtils;

public class FinishActivity extends BaseSetupWizardActivity {

    public static final String TAG = FinishActivity.class.getSimpleName();
    private static final String EXTRA_LAUNCH_TT9 = "launch_tt9";
    private static final String TT9_PACKAGE = "io.github.sspanak.tt9";

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // "Why not just start this activity with an Intent extra?" you might ask. Been there.
    // We need this to affect the theme, and even onCreate is not early enough for that,
    // so "static volatile boolean" it is. Feel free to rework this if you dare.
    private static volatile boolean sIsFinishing;

    private View mRootView;
    private Resources.Theme mEdgeToEdgeWallpaperBackgroundTheme;
    private boolean mShouldLaunchTt9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.translucent_enter,
                R.anim.translucent_exit);
        if (LOGV) {
            logActivityState("onCreate savedInstanceState=" + savedInstanceState);
        }
        mShouldLaunchTt9 = getIntent().getBooleanExtra(EXTRA_LAUNCH_TT9, false);
        updateTt9Prompt();

        // Edge-to-edge. Needed for the background view to fill the full screen.
        final Window window = getWindow();
        window.setDecorFitsSystemWindows(false);

        // Make sure 3-button navigation bar is the same color as the rest of the screen.
        window.setNavigationBarContrastEnforced(false);

        // Ensure the main layout (not including the background view) does not get obscured by bars.
        mRootView = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(mRootView, (view, windowInsets) -> {
            final View linearLayout = findViewById(R.id.linear_layout);
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            final MarginLayoutParams params = (MarginLayoutParams) linearLayout.getLayoutParams();
            params.leftMargin = insets.left;
            params.topMargin = insets.top;
            params.rightMargin = insets.right;
            params.bottomMargin = insets.bottom;
            linearLayout.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });

        if (sIsFinishing) {
            startFinishSequence();
        }
    }

    private void disableActivityTransitions() {
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
    }

    @Override
    protected void applyForwardTransition() {
        if (!sIsFinishing) {
            super.applyForwardTransition();
        }
    }

    @Override
    protected void applyBackwardTransition() {
        if (!sIsFinishing) {
            super.applyBackwardTransition();
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.finish_activity;
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        if (sIsFinishing) {
            if (mEdgeToEdgeWallpaperBackgroundTheme == null) {
                theme.applyStyle(R.style.EdgeToEdgeWallpaperBackground, true);
                mEdgeToEdgeWallpaperBackgroundTheme = theme;
            }
            return mEdgeToEdgeWallpaperBackgroundTheme;
        }
        return theme;
    }

    @Override
    public void onNavigateNext() {
        if (!sIsFinishing) {
            beginFinishSequence(hasTt9LaunchIntent());
        }
        hideNextButton();
    }

    @Override
    public void onSkip() {
        if (!sIsFinishing) {
            beginFinishSequence(false);
        }
        hideSkipButton();
    }

    private void startFinishSequence() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        hideNextButton();
        hideSkipButton();

        // Begin outro animation.
        if (mRootView.isAttachedToWindow()) {
            mHandler.post(() -> animateOut());
        } else {
            mRootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    mHandler.post(() -> animateOut());
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    // Do nothing
                }
            });
        }
    }

    private void animateOut() {
        final int cx = (mRootView.getLeft() + mRootView.getRight()) / 2;
        final int cy = (mRootView.getTop() + mRootView.getBottom()) / 2;
        final float fullRadius = (float) Math.hypot(cx, cy);
        Animator anim =
                ViewAnimationUtils.createCircularReveal(mRootView, cx, cy, fullRadius, 0f);
        anim.setDuration(900);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mRootView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mRootView.setVisibility(View.INVISIBLE);
                mHandler.post(() -> {
                    if (LOGV) {
                        Log.v(TAG, "Animation ended");
                    }
                    finishSetupWizard();
                });
            }
        });
        anim.start();
    }

    private void beginFinishSequence(boolean launchTt9) {
        sIsFinishing = true;
        final Intent finishIntent = new Intent(getIntent())
                .putExtra(EXTRA_LAUNCH_TT9, launchTt9);
        startActivity(finishIntent);
        finish();
        disableActivityTransitions();
    }

    private void finishSetupWizard() {
        SetupWizardUtils.finishSetupWizard(FinishActivity.this);
        if (mShouldLaunchTt9) {
            final Context appContext = getApplicationContext();
            mHandler.post(() -> launchTt9(appContext));
        }
    }

    private void updateTt9Prompt() {
        final View promptContainer = findViewById(R.id.tt9_prompt_container);
        if (hasTt9LaunchIntent()) {
            promptContainer.setVisibility(View.VISIBLE);
            setNextText(R.string.tt9_yes);
            setSkipText(R.string.tt9_no);
        } else {
            promptContainer.setVisibility(View.GONE);
            setNextText(R.string.start);
            hideSkipButton();
        }
    }

    private boolean hasTt9LaunchIntent() {
        return getPackageManager().getLaunchIntentForPackage(TT9_PACKAGE) != null;
    }

    private void hideSkipButton() {
        final NavigationLayout navigationBar = getNavigationBar();
        if (navigationBar != null) {
            navigationBar.getSkipButton().setVisibility(View.INVISIBLE);
        }
    }

    private void launchTt9(Context context) {
        final Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(
                TT9_PACKAGE);
        if (launchIntent == null) {
            Log.w(TAG, "TT9 launch intent not found, skipping");
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launchIntent);
    }
}
