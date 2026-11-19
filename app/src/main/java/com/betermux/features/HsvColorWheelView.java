package com.betermux.features;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Custom View rendering a 2-D HS disc (angle = hue 0–360°, radius = saturation 0–1).
 * Brightness is controlled externally via a SeekBar.
 */
public class HsvColorWheelView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private Bitmap mBitmap;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSelectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float mHue = 0f;        // 0–360
    private float mSaturation = 0f; // 0–1
    private float mBrightness = 1f; // 0–1

    private OnColorChangedListener mListener;

    public HsvColorWheelView(Context context) {
        super(context);
        init();
    }

    public HsvColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HsvColorWheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mSelectorPaint.setStyle(Paint.Style.STROKE);
        mSelectorPaint.setStrokeWidth(3f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        generateBitmap(w, h);
    }

    private void generateBitmap(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int size = Math.min(w, h);
        mBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        float cx = size / 2f;
        float cy = size / 2f;
        float radius = size / 2f;
        int[] pixels = new int[size * size];
        float[] hsv = new float[]{0f, 0f, mBrightness};
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > radius) {
                    pixels[y * size + x] = 0;
                } else {
                    float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    if (angle < 0) angle += 360f;
                    hsv[0] = angle;
                    hsv[1] = dist / radius;
                    pixels[y * size + x] = Color.HSVToColor(hsv);
                }
            }
        }
        mBitmap.setPixels(pixels, 0, size, 0, 0, size, size);
    }

    /** Call when brightness changes externally; regenerates the disc bitmap. */
    public void setBrightness(float brightness) {
        mBrightness = brightness;
        generateBitmap(getWidth(), getHeight());
        invalidate();
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    public void setColor(int argb) {
        float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);
        mHue = hsv[0];
        mSaturation = hsv[1];
        mBrightness = hsv[2];
        generateBitmap(getWidth(), getHeight());
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(new float[]{mHue, mSaturation, mBrightness});
    }

    public float getBrightness() { return mBrightness; }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap == null) return;
        int w = getWidth();
        int h = getHeight();
        int size = Math.min(w, h);
        float offsetX = (w - size) / 2f;
        float offsetY = (h - size) / 2f;
        canvas.drawBitmap(mBitmap, offsetX, offsetY, mPaint);

        float cx = offsetX + size / 2f;
        float cy = offsetY + size / 2f;
        float radius = size / 2f;
        float angle = (float) Math.toRadians(mHue);
        float sx = cx + (float) Math.cos(angle) * mSaturation * radius;
        float sy = cy + (float) Math.sin(angle) * mSaturation * radius;

        mSelectorPaint.setColor(Color.BLACK);
        canvas.drawCircle(sx, sy, 10f, mSelectorPaint);
        mSelectorPaint.setColor(Color.WHITE);
        canvas.drawCircle(sx, sy, 13f, mSelectorPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(size, size);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h);
            float cx = (w - size) / 2f + size / 2f;
            float cy = (h - size) / 2f + size / 2f;
            float radius = size / 2f;
            float dx = event.getX() - cx;
            float dy = event.getY() - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (angle < 0) angle += 360f;
            mHue = angle;
            mSaturation = Math.min(dist / radius, 1f);
            invalidate();
            if (mListener != null) mListener.onColorChanged(getColor());
            return true;
        }
        return super.onTouchEvent(event);
    }
}
