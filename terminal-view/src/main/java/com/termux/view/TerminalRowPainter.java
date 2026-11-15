package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Shared row-level Canvas painter extracted from {@link TerminalRenderer} so both
 * full-screen {@link TerminalView} and block-mode Canvas renderers share identical
 * glyph metrics, colors, styles, wide-character scaling, and cursor drawing.
 */
public final class TerminalRowPainter {

    public final int mTextSize;
    public final Typeface mTypeface;
    private final Paint mTextPaint = new Paint();

    /** The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on 'X'. */
    public final float mFontWidth;
    /** The {@link Paint#getFontSpacing()}. */
    public final int mFontLineSpacing;
    /** The {@link Paint#ascent()}. */
    public final int mFontAscent;
    /** The {@link #mFontLineSpacing} + {@link #mFontAscent}. */
    public final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];

    public TerminalRowPainter(int textSize, Typeface typeface) {
        mTextSize = textSize;
        mTypeface = typeface;

        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);

        mFontLineSpacing = (int) Math.ceil(mTextPaint.getFontSpacing());
        mFontAscent = (int) Math.ceil(mTextPaint.ascent());
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");

        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
    }

    /**
     * Draw one {@link TerminalRow} with cell bottom at {@code rowBottomY}.
     *
     * @param cursorColOrMinusOne cursor column on this row if visible, or {@code -1}.
     * @param selX1               inclusive selection start column, or {@code -1} if none.
     * @param selX2               inclusive selection end column, or {@code -1} if none.
     */
    public void drawRow(
        Canvas canvas,
        TerminalRow lineObject,
        int[] palette,
        int columns,
        float rowBottomY,
        int cursorColOrMinusOne,
        int cursorShape,
        int selX1,
        int selX2,
        boolean reverseVideo
    ) {
        if (lineObject == null) return;
        final char[] line = lineObject.mText;
        final int charsUsedInLine = lineObject.getSpaceUsed();

        long lastRunStyle = 0;
        boolean lastRunInsideCursor = false;
        boolean lastRunInsideSelection = false;
        int lastRunStartColumn = -1;
        int lastRunStartIndex = 0;
        boolean lastRunFontWidthMismatch = false;
        int currentCharIndex = 0;
        float measuredWidthForRun = 0.f;

        for (int column = 0; column < columns; ) {
            final char charAtIndex = line[currentCharIndex];
            final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
            final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
            final int codePoint = charIsHighsurrogate
                ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1])
                : charAtIndex;
            final int codePointWcWidth = WcWidth.width(codePoint);
            final boolean insideCursor = (cursorColOrMinusOne == column || (codePointWcWidth == 2 && cursorColOrMinusOne == column + 1));
            final boolean insideSelection = column >= selX1 && column <= selX2;
            final long style = lineObject.getStyle(column);

            final float measuredCodePointWidth = (codePoint < asciiMeasures.length)
                ? asciiMeasures[codePoint]
                : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
            final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;

            if (style != lastRunStyle
                || insideCursor != lastRunInsideCursor
                || insideSelection != lastRunInsideSelection
                || fontWidthMismatch
                || lastRunFontWidthMismatch) {
                if (column != 0) {
                    final int columnWidthSinceLastRun = column - lastRunStartColumn;
                    final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                    int cursorColor = lastRunInsideCursor ? palette[TextStyle.COLOR_INDEX_CURSOR] : 0;
                    boolean invertCursorTextColor = lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                    drawTextRun(
                        canvas, line, palette, rowBottomY, lastRunStartColumn, columnWidthSinceLastRun,
                        lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                        cursorColor, cursorShape, lastRunStyle,
                        reverseVideo || invertCursorTextColor || lastRunInsideSelection
                    );
                }
                measuredWidthForRun = 0.f;
                lastRunStyle = style;
                lastRunInsideCursor = insideCursor;
                lastRunInsideSelection = insideSelection;
                lastRunStartColumn = column;
                lastRunStartIndex = currentCharIndex;
                lastRunFontWidthMismatch = fontWidthMismatch;
            }
            measuredWidthForRun += measuredCodePointWidth;
            column += codePointWcWidth;
            currentCharIndex += charsForCodePoint;
            while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
                currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
            }
        }

        if (lastRunStartColumn >= 0) {
            final int columnWidthSinceLastRun = columns - lastRunStartColumn;
            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
            int cursorColor = lastRunInsideCursor ? palette[TextStyle.COLOR_INDEX_CURSOR] : 0;
            boolean invertCursorTextColor = lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            drawTextRun(
                canvas, line, palette, rowBottomY, lastRunStartColumn, columnWidthSinceLastRun,
                lastRunStartIndex, charsSinceLastRun, measuredWidthForRun,
                cursorColor, cursorShape, lastRunStyle,
                reverseVideo || invertCursorTextColor || lastRunInsideSelection
            );
        }
    }

    public void drawTextRun(
        Canvas canvas,
        char[] text,
        int[] palette,
        float y,
        int startColumn,
        int runWidthColumns,
        int startCharIndex,
        int runWidthChars,
        float mes,
        int cursor,
        int cursorStyle,
        long textStyle,
        boolean reverseVideo
    ) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;

        if ((foreColor & 0xff000000) != 0xff000000) {
            if (bold && foreColor >= 0 && foreColor < 8) foreColor += 8;
            foreColor = palette[foreColor];
        }

        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }

        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }

        float left = startColumn * mFontWidth;
        float right = left + runWidthColumns * mFontWidth;

        mes = mes / mFontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
        }

        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - mFontLineSpacingAndAscent + mFontAscent, right, y, mTextPaint);
        }

        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            float cursorHeight = mFontLineSpacingAndAscent - mFontAscent;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
        }

        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }

            mTextPaint.setFakeBoldText(bold);
            mTextPaint.setUnderlineText(underline);
            mTextPaint.setTextSkewX(italic ? -0.35f : 0.f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);

            canvas.drawTextRun(
                text, startCharIndex, runWidthChars,
                startCharIndex, runWidthChars,
                left, y - mFontLineSpacingAndAscent,
                false, mTextPaint
            );
        }

        if (savedMatrix) canvas.restore();
    }
}
