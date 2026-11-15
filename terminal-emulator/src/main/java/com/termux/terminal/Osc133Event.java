package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shell integration semantic zone event (OSC 133 / FinalTerm standard).
 * Coordinates ({@link #absRow}, {@link #cursorCol}) are captured at parse time on the
 * emulator thread relative to {@link TerminalBuffer#getTotalTranscriptRows()}.
 */
public final class Osc133Event {

    public static final int TYPE_PROMPT_START = 0;     // A
    public static final int TYPE_COMMAND_START = 1;    // B
    public static final int TYPE_COMMAND_EXECUTED = 2; // C
    public static final int TYPE_COMMAND_FINISHED = 3; // D

    public final int type;
    @Nullable
    public final Integer exitCode;
    @Nullable
    public final String commandLine;
    public final long absRow;
    public final int cursorCol;
    public final long timestampMs;

    public Osc133Event(
        int type,
        @Nullable Integer exitCode,
        @Nullable String commandLine,
        long absRow,
        int cursorCol,
        long timestampMs
    ) {
        this.type = type;
        this.exitCode = exitCode;
        this.commandLine = commandLine;
        this.absRow = absRow;
        this.cursorCol = cursorCol;
        this.timestampMs = timestampMs;
    }

    @NonNull
    @Override
    public String toString() {
        return "Osc133Event{type=" + type +
            ", exitCode=" + exitCode +
            ", commandLine=" + commandLine +
            ", absRow=" + absRow +
            ", cursorCol=" + cursorCol +
            '}';
    }
}
