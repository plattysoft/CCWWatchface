/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.plattysoft.ccwwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class CCWWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        Paint mBackgroundPaint;
        Paint mLinesPaint;
        Paint mThickLinesPaint;
        Paint mHandPaint;
        Paint mSecondsPaint;
        Paint mNumbersPaint;
        boolean mAmbient;
        Time mTime;

        /**
         * Handler to update the time once a second in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CCWWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = CCWWatchFace.this.getResources();

            initPaintObjects(resources);

            mTime = new Time();
        }

        private void initPaintObjects(Resources resources) {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mNumbersPaint = new Paint();
            mNumbersPaint.setColor(resources.getColor(R.color.numbers));
            mNumbersPaint.setAntiAlias(true);
            mNumbersPaint.setTextAlign(Paint.Align.CENTER);
            mNumbersPaint.setFakeBoldText(true);
            mNumbersPaint.setTextSize(resources.getDimension(R.dimen.number_size));

            mLinesPaint = new Paint();
            mLinesPaint.setColor(resources.getColor(R.color.lines));
            mLinesPaint.setStrokeWidth(resources.getDimension(R.dimen.lines_stroke));
            mLinesPaint.setAntiAlias(true);
            mLinesPaint.setStyle(Paint.Style.STROKE);
            mLinesPaint.setStrokeCap(Paint.Cap.ROUND);

            mThickLinesPaint = new Paint();
            mThickLinesPaint.setColor(resources.getColor(R.color.lines));
            mThickLinesPaint.setStrokeWidth(resources.getDimension(R.dimen.lines_thick_stroke));
            mThickLinesPaint.setAntiAlias(true);
            mThickLinesPaint.setStyle(Paint.Style.STROKE);
            mThickLinesPaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondsPaint = new Paint();
            mSecondsPaint.setColor(resources.getColor(R.color.analog_hands_seconds));
            mSecondsPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_seconds_stroke));
            mSecondsPaint.setAntiAlias(true);
            mSecondsPaint.setStrokeCap(Paint.Cap.ROUND);

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            drawLinesAndNumbers(canvas, width, height, centerX, centerY);

            drawHands(canvas, centerX, centerY);
        }

        private void drawLinesAndNumbers(Canvas canvas, int width, int height, float centerX, float centerY) {
            // Draw lines for the hours
            float angleInRadians = 0;
            Paint paint;
            float length;
            float textPadding = ((mNumbersPaint.descent() + mNumbersPaint.ascent()) / 2);
            for (int i=0; i<60; i++) {
                if (i%15 == 0) {
                    float textOffset = getResources().getDimension(R.dimen.number_offset);
                    String text = (i>0) ? String.valueOf(i/5) : "12";

                    // Draw text
                    float textX = (float) (centerX - (width/2 - textOffset) * Math.sin(angleInRadians));
                    float textY = (float) (centerY - (height/2 - textOffset) * Math.cos(angleInRadians) - textPadding);
                    canvas.drawText(text, textX, textY, mNumbersPaint);

                    // Shorter line
                    length = getResources().getDimension(R.dimen.lines_thick_size_for_number);
                    paint = mThickLinesPaint;
                }
                else if (i%5 == 0) {
                    length = getResources().getDimension(R.dimen.lines_thick_size);
                    paint = mThickLinesPaint;
                }
                else {
                    length = getResources().getDimension(R.dimen.lines_size);
                    paint = mLinesPaint;
                }

                canvas.drawLine(
                        (float) (centerX - width/2 * Math.sin(angleInRadians)),
                        (float) (centerY - height/2 * Math.cos(angleInRadians)),
                        (float) (centerX - (width/2 - length) * Math.sin(angleInRadians)),
                        (float) (centerY - (height/2 - length) * Math.cos(angleInRadians)),
                        paint);

                angleInRadians += Math.PI/30;
            }
        }

        private void drawHands(Canvas canvas, float centerX, float centerY) {
            // Reverse the rotations to get a backwards clock.
            float secRot = -  mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = - minutes / 30f * (float) Math.PI;
            float hrRot = - ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - getResources().getDimension(R.dimen.seconds_hand_gap);
            float minLength = centerX - getResources().getDimension(R.dimen.minutes_hand_gap);
            float hrLength = centerX - getResources().getDimension(R.dimen.hours_hand_gap);

            float extraLength = getResources().getDimension(R.dimen.hand_extra_length);

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                float secOffsetX = (float) Math.sin(secRot) * extraLength;
                float secOffsetY = (float) -Math.cos(secRot) * extraLength;
                // Gets over the center
                canvas.drawLine(centerX - secOffsetX, centerY - secOffsetY, centerX + secX, centerY + secY, mSecondsPaint);
            }

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            float minOffsetX = (float) Math.sin(minRot) * extraLength;
            float minOffsetY = (float) -Math.cos(minRot) * extraLength;
            canvas.drawLine(centerX - minOffsetX, centerY - minOffsetY, centerX + minX, centerY + minY, mHandPaint);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            float hrOffsetX = (float) Math.sin(hrRot) * extraLength;
            float hrOffsetY = (float) -Math.cos(hrRot) * extraLength;
            canvas.drawLine(centerX - hrOffsetX, centerY - hrOffsetY, centerX + hrX, centerY + hrY, mHandPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            CCWWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            CCWWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
