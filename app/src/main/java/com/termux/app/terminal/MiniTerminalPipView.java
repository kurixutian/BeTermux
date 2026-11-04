package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;

/**
 * A tiny live-preview pip of a TerminalSession. Scales the full terminal screen down to fit
 * its view bounds using Canvas.scale(), preserving all colors and cursor state.
 * Rendered with modern anti-aliased rounded corners and glowing accent border when active.
 */
public class MiniTerminalPipView extends View {

    private TerminalSession mSession;
    private final TerminalRenderer mRenderer;
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClipPath = new Path();
    private final RectF mBounds = new RectF();
    private boolean mIsActive = false;

    private int mActiveColor = 0xFF8B5CF6;
    private static final int COLOR_BORDER_INACTIVE = 0xFF30363D;
    private static final float CORNER_RADIUS_DP = 8f;

    public void setActiveColor(int color) {
        mActiveColor = color;
        invalidate();
    }

    public MiniTerminalPipView(Context context) {
        super(context);
        mRenderer = new TerminalRenderer(12, Typeface.MONOSPACE);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBgPaint.setStyle(Paint.Style.FILL);
    }

    public void setSession(TerminalSession session) {
        mSession = session;
        invalidate();
    }

    public TerminalSession getSession() {
        return mSession;
    }

    public void setActive(boolean active) {
        if (mIsActive == active) return;
        mIsActive = active;
        invalidate();
    }

    /** Call from onTextChanged() — safe from any thread. */
    public void notifyUpdate() {
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float radius = CORNER_RADIUS_DP * density;
        float strokeWidth = (mIsActive ? 2.5f : 1.2f) * density;
        mBorderPaint.setStrokeWidth(strokeWidth);

        mBounds.set(0, 0, getWidth(), getHeight());
        mClipPath.reset();
        mClipPath.addRoundRect(mBounds, radius, radius, Path.Direction.CW);

        TerminalEmulator emulator = (mSession != null) ? mSession.getEmulator() : null;

        canvas.save();
        canvas.clipPath(mClipPath);

        if (emulator == null) {
            mBgPaint.setColor(0xFF161B22);
            canvas.drawRect(mBounds, mBgPaint);
        } else {
            int bgColor = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND];
            mBgPaint.setColor(bgColor != 0 ? bgColor : 0xFF0D1117);
            canvas.drawRect(mBounds, mBgPaint);

            float rendererW = mRenderer.getFontWidth() * emulator.mColumns;
            float rendererH = mRenderer.getFontLineSpacing() * emulator.mRows;

            if (rendererW > 0 && rendererH > 0) {
                float scaleX = getWidth()  / rendererW;
                float scaleY = getHeight() / rendererH;
                canvas.save();
                canvas.scale(scaleX, scaleY);
                mRenderer.render(emulator, canvas, 0, -1, -1, -1, -1);
                canvas.restore();
            }
        }
        canvas.restore();

        // Rounded border — vibrant accent when active, sleek slate when inactive
        mBorderPaint.setColor(mIsActive ? mActiveColor : COLOR_BORDER_INACTIVE);
        float half = strokeWidth / 2f;
        mBounds.set(half, half, getWidth() - half, getHeight() - half);
        canvas.drawRoundRect(mBounds, radius, radius, mBorderPaint);
    }
}
