package io.github.alamingamer15ig.neutron;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

/**
 * Connects Termux's reusable terminal widget to Neutron's private shell environment.
 * Neutron deliberately owns the shell process environment; this class only supplies
 * terminal UI callbacks and clipboard/keyboard integration.
 */
final class NeutronTermuxTerminalClient implements TerminalSessionClient, TerminalViewClient {
    private final MainActivity activity;
    private final TerminalView terminalView;

    NeutronTermuxTerminalClient(MainActivity activity, TerminalView terminalView) {
        this.activity = activity;
        this.terminalView = terminalView;
    }

    @Override public float onScale(float scale) { return scale; }

    @Override
    public void onSingleTapUp(MotionEvent event) {
        terminalView.requestFocus();
        InputMethodManager input = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) {
            terminalView.post(() -> input.showSoftInput(
                    terminalView, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override public boolean shouldEnforceCharBasedInput() { return true; }
    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override public boolean isTerminalViewSelected() { return terminalView.hasFocus(); }
    @Override public void copyModeChanged(boolean copyMode) { }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event, TerminalSession session) { return false; }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }
    @Override public boolean onLongPress(MotionEvent event) { return false; }
    @Override public boolean readControlKey() { return false; }
    @Override public boolean readAltKey() { return false; }
    @Override public boolean readShiftKey() { return false; }
    @Override public boolean readFnKey() { return false; }
    @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }

    @Override
    public void onEmulatorSet() {
        terminalView.setTerminalCursorBlinkerState(true, true);
    }

    @Override public void logError(String tag, String message) { android.util.Log.e(tag, message); }
    @Override public void logWarn(String tag, String message) { android.util.Log.w(tag, message); }
    @Override public void logInfo(String tag, String message) { android.util.Log.i(tag, message); }
    @Override public void logDebug(String tag, String message) { android.util.Log.d(tag, message); }
    @Override public void logVerbose(String tag, String message) { android.util.Log.v(tag, message); }
    @Override public void logStackTraceWithMessage(String tag, String message, Exception error) {
        android.util.Log.e(tag, message, error);
    }
    @Override public void logStackTrace(String tag, Exception error) {
        android.util.Log.e(tag, "Termux terminal error", error);
    }

    @Override
    public void onTextChanged(TerminalSession session) {
        terminalView.onScreenUpdated();
    }

    @Override public void onTitleChanged(TerminalSession session) { }

    @Override
    public void onSessionFinished(TerminalSession session) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, "Terminal session ended (exit "
                        + session.getExitStatus() + ")", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        ClipboardManager clipboard = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Neutron terminal", text));
        }
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        ClipboardManager clipboard = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(activity);
        if (text != null && terminalView.mEmulator != null) {
            terminalView.mEmulator.paste(text.toString());
        }
    }

    @Override public void onBell(TerminalSession session) { }

    @Override
    public void onColorsChanged(TerminalSession session) {
        terminalView.invalidate();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        terminalView.setTerminalCursorBlinkerState(enabled, false);
    }

    @Override public Integer getTerminalCursorStyle() { return null; }
}
