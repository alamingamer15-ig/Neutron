package io.github.alamingamer15ig.neutron;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.text.InputType;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_ELF = 41;
    private static final int REQUEST_PICK_ROOTFS = 42;
    private static final int REQUEST_PICK_TERMINAL_ELF = 43;
    private static final String PREFS = "neutron_ui";
    private static final String PREF_SELECTED_FILES = "selected_files";
    private static final String PREF_ROOTFS_URI = "rootfs_tree_uri";
    private static final String PREF_TERMINAL_ELF_URI = "terminal_elf_uri";
    private static final String PREF_TERMINAL_ELF_NAME = "terminal_elf_name";
    private static final String PREF_ROOTFS_RETURN_TERMINAL = "rootfs_return_terminal";
    private static final String PREF_GUEST_RAM_MB = "guest_ram_limit_mb";
    private static final String PREF_GUEST_CORE_COUNT = "guest_core_count";
    private static final int DEFAULT_GUEST_RAM_MB = 512;
    private static final int MIN_GUEST_RAM_MB = 256;
    private static final int MAX_GUEST_RAM_MB = 2048;
    private static final int RAM_STEP_MB = 128;
    private static final int DEFAULT_GUEST_CORE_COUNT = 4;
    private static final int[] GUEST_CORE_OPTIONS = {1, 2, 4, 8};

    private FrameLayout contentContainer;
    private BottomNavigationView bottomNavigation;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (NeutronLog.init(getApplicationContext())) {
            NeutronLog.info("MainActivity", "Neutron application started");
        }
        NeutronEnvironment.initialize(this);\n        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        contentContainer = findViewById(R.id.content_container);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        setSupportActionBar(toolbar);

        bottomNavigation.setOnItemSelectedListener(item -> {
            showScreen(item.getItemId());
            return true;
        });
        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void showScreen(int destination) {
        contentContainer.removeAllViews();

        if (destination == R.id.nav_apps) {
            toolbar.setTitle("Applications");
            contentContainer.addView(buildApplicationsScreen());
        } else if (destination == R.id.nav_terminal) {
            toolbar.setTitle("Terminal");
            contentContainer.addView(buildTerminalScreen());
        } else if (destination == R.id.nav_settings) {
            toolbar.setTitle("Settings");
            contentContainer.addView(buildSettingsScreen());
        } else {
            toolbar.setTitle("Neutron");
            contentContainer.addView(buildDashboardScreen());
        }
    }

    private ScrollView createPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        scrollView.addView(page);
        scrollView.setTag(page);
        return scrollView;
    }

    private LinearLayout pageContent(ScrollView page) {
        return (LinearLayout) page.getTag();
    }

    private ScrollView buildDashboardScreen() {
        ScrollView page = createPage();
        LinearLayout content = pageContent(page);

        TextView eyebrow = text("ANDROID × LINUX", 11, R.color.neutron_primary, true);
        eyebrow.setLetterSpacing(0.12f);
        content.addView(eyebrow);

        TextView heading = text("Your Linux environment.", 27, R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(7);
        content.addView(heading, headingParams);

        TextView intro = text("Run native ARM64 Linux tools and translate x86-64 applications on Android.", 14,
                R.color.neutron_muted, false);
        LinearLayout.LayoutParams introParams = params(-1, -2);
        introParams.topMargin = dp(5);
        content.addView(intro, introParams);

        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(22);
        content.addView(buildStatusCard(), statusParams);

        TextView resourcesTitle = sectionTitle("Host device");
        LinearLayout.LayoutParams sectionParams = params(-1, -2);
        sectionParams.topMargin = dp(24);
        content.addView(resourcesTitle, sectionParams);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metricCard("HOST", "ARM64", "Android device"), weightedParams());
        View gap = new View(this);
        metrics.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        metrics.addView(metricCard("GUEST", "x86-64", "Linux applications"), weightedParams());
        LinearLayout.LayoutParams metricsParams = params(-1, -2);
        metricsParams.topMargin = dp(10);
        content.addView(metrics, metricsParams);

        TextView actionsTitle = sectionTitle("Quick actions");
        LinearLayout.LayoutParams actionTitleParams = params(-1, -2);
        actionTitleParams.topMargin = dp(24);
        content.addView(actionsTitle, actionTitleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton appsButton = button("Browse apps");
        appsButton.setIconResource(R.drawable.ic_apps);
        appsButton.setOnClickListener(v -> bottomNavigation.setSelectedItemId(R.id.nav_apps));
        actions.addView(appsButton, weightedParams());

        View actionGap = new View(this);
        actions.addView(actionGap, new LinearLayout.LayoutParams(dp(10), 1));

        MaterialButton terminalButton = button("Open terminal");
        terminalButton.setIconResource(R.drawable.ic_terminal);
        terminalButton.setOnClickListener(v -> bottomNavigation.setSelectedItemId(R.id.nav_terminal));
        actions.addView(terminalButton, weightedParams());

        LinearLayout.LayoutParams actionsParams = params(-1, -2);
        actionsParams.topMargin = dp(10);
        content.addView(actions, actionsParams);

        TextView note = text("The interface is ready for the runtime integration. Translation, graphics and guest execution are not connected in this build.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams noteParams = params(-1, -2);
        noteParams.topMargin = dp(24);
        content.addView(note, noteParams);

        return page;
    }

    private View buildStatusCard() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(17), dp(18), dp(18));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("RUNTIME STATUS", 11, R.color.neutron_muted, true);
        label.setLetterSpacing(0.08f);
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView state = text("NOT CONNECTED", 10, R.color.neutron_primary, true);
        state.setBackgroundColor(getColor(R.color.neutron_surface_variant));
        state.setPadding(dp(9), dp(6), dp(9), dp(6));
        top.addView(state);
        body.addView(top);

        TextView title = text("Neutron engine not installed", 18, R.color.neutron_text, true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(15);
        body.addView(title, titleParams);

        TextView description = text("The Android interface is running. The native translator and Linux root filesystem will connect in a later milestone.",
                13, R.color.neutron_muted, false);
        LinearLayout.LayoutParams descriptionParams = params(-1, -2);
        descriptionParams.topMargin = dp(6);
        body.addView(description, descriptionParams);

        MaterialCardView card = card();
        card.addView(body);
        return card;
    }

    private View metricCard(String label, String value, String detail) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(15), dp(14), dp(15));
        body.addView(text(label, 10, R.color.neutron_muted, true));

        TextView main = text(value, 21, R.color.neutron_text, true);
        LinearLayout.LayoutParams mainParams = params(-1, -2);
        mainParams.topMargin = dp(8);
        body.addView(main, mainParams);

        TextView sub = text(detail, 11, R.color.neutron_muted, false);
        LinearLayout.LayoutParams subParams = params(-1, -2);
        subParams.topMargin = dp(3);
        body.addView(sub, subParams);

        MaterialCardView card = card();
        card.addView(body);
        return card;
    }

    private ScrollView buildApplicationsScreen() {
        ScrollView page = createPage();
        LinearLayout content = pageContent(page);

        content.addView(text("Your applications", 25, R.color.neutron_text, true));
        TextView subtitle = text("Keep track of Linux ELF files you want to run with Neutron.",
                14, R.color.neutron_muted, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        addRootfsCard(content);

        MaterialButton chooseButton = button("Choose an ELF file");
        chooseButton.setIconResource(android.R.drawable.ic_menu_add);
        chooseButton.setOnClickListener(v -> chooseElfFile());
        LinearLayout.LayoutParams chooseParams = params(-1, -2);
        chooseParams.topMargin = dp(20);
        content.addView(chooseButton, chooseParams);

        TextView listTitle = sectionTitle("Selected files");
        LinearLayout.LayoutParams listTitleParams = params(-1, -2);
        listTitleParams.topMargin = dp(26);
        content.addView(listTitle, listTitleParams);

        ArrayList<String> files = getSelectedFiles();
        if (files.isEmpty()) {
            MaterialCardView emptyCard = card();
            LinearLayout empty = new LinearLayout(this);
            empty.setGravity(Gravity.CENTER);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(22), dp(28), dp(22), dp(28));
            TextView emptyTitle = text("No files selected", 16, R.color.neutron_text, true);
            emptyTitle.setGravity(Gravity.CENTER);
            empty.addView(emptyTitle);
            TextView emptyDescription = text("Choose an ELF file to add an entry here. Execution is not available until the runtime is implemented.",
                    12, R.color.neutron_muted, false);
            emptyDescription.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyDescriptionParams = params(-1, -2);
            emptyDescriptionParams.topMargin = dp(7);
            empty.addView(emptyDescription, emptyDescriptionParams);
            emptyCard.addView(empty);
            LinearLayout.LayoutParams emptyCardParams = params(-1, -2);
            emptyCardParams.topMargin = dp(10);
            content.addView(emptyCard, emptyCardParams);
        } else {
            for (String file : files) {
                MaterialCardView fileCard = card();
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(15), dp(14), dp(15), dp(14));
                TextView icon = text("ELF", 11, R.color.neutron_primary, true);
                row.addView(icon);
                LinearLayout details = new LinearLayout(this);
                details.setOrientation(LinearLayout.VERTICAL);
                details.setPadding(dp(13), 0, 0, 0);
                details.addView(text(file, 14, R.color.neutron_text, true));
                TextView pending = text("Selected · runtime pending", 11, R.color.neutron_muted, false);
                LinearLayout.LayoutParams pendingParams = params(-1, -2);
                pendingParams.topMargin = dp(3);
                details.addView(pending, pendingParams);
                row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

                MaterialButton launchButton = button("Launch");
                launchButton.setMinWidth(dp(76));
                launchButton.setEnabled(isRootfsValid());
                launchButton.setAlpha(launchButton.isEnabled() ? 1.0f : 0.45f);
                launchButton.setOnClickListener(v -> {
                    if (!isRootfsValid()) {
                        Toast.makeText(this, "Select a valid rootfs first.", Toast.LENGTH_SHORT).show();
                        showScreen(R.id.nav_apps);
                        return;
                    }
                    Toast.makeText(this,
                            "Rootfs validated. Guest execution is not implemented yet.",
                            Toast.LENGTH_LONG).show();
                });
                row.addView(launchButton);
                fileCard.addView(row);
                LinearLayout.LayoutParams fileParams = params(-1, -2);
                fileParams.topMargin = dp(9);
                content.addView(fileCard, fileParams);
            }
        }

        TextView disclaimer = text("Selecting a file currently creates a local UI entry only. Neutron does not yet copy, inspect or execute the selected binary.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams disclaimerParams = params(-1, -2);
        disclaimerParams.topMargin = dp(20);
        content.addView(disclaimer, disclaimerParams);
        return page;
    }

    private ScrollView buildTerminalScreen() {
        NeutronEnvironment environment = NeutronEnvironment.initialize(this);
        ScrollView page = createPage();
        LinearLayout content = pageContent(page);

        content.addView(text("Neutron terminal", 25, R.color.neutron_text, true));
        TextView subtitle = text("A Termux-inspired userspace layout, managed inside Neutron.", 14,
                R.color.neutron_muted, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        MaterialCardView environmentCard = card();
        LinearLayout environmentBody = new LinearLayout(this);
        environmentBody.setOrientation(LinearLayout.VERTICAL);
        environmentBody.setPadding(dp(16), dp(15), dp(16), dp(15));
        environmentBody.addView(text("NEUTRON PREFIX", 10, R.color.neutron_primary, true));
        TextView path = text(environment.getRootDirectory().getAbsolutePath(), 12,
                R.color.neutron_text, false);
        path.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams pathParams = params(-1, -2);
        pathParams.topMargin = dp(7);
        environmentBody.addView(path, pathParams);

        TextView layout = text("bin/   lib/   lib64/   home/", 12,
                R.color.neutron_muted, false);
        layout.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams layoutParams = params(-1, -2);
        layoutParams.topMargin = dp(10);
        environmentBody.addView(layout, layoutParams);

        TextView status = text(environment.isReady()
                        ? "Environment directories initialized."
                        : "Environment initialization failed.",
                12, environment.isReady() ? R.color.neutron_primary : R.color.neutron_muted, false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(8);
        environmentBody.addView(status, statusParams);
        environmentCard.addView(environmentBody);
        LinearLayout.LayoutParams environmentCardParams = params(-1, -2);
        environmentCardParams.topMargin = dp(18);
        content.addView(environmentCard, environmentCardParams);

        MaterialCardView terminalCard = card();
        terminalCard.setCardBackgroundColor(0xFF0B1018);
        LinearLayout terminal = new LinearLayout(this);
        terminal.setOrientation(LinearLayout.VERTICAL);
        terminal.setPadding(dp(16), dp(18), dp(16), dp(18));
        terminal.addView(text("NEUTRON / HOST SHELL", 10, R.color.neutron_primary, true));

        TextView transcript = text(environment.isReady()
                        ? "Neutron bootstrap shell · working directory: home/\n"
                        : "Cannot initialize Neutron environment.\n",
                13, R.color.neutron_text, false);
        transcript.setTypeface(Typeface.MONOSPACE);
        transcript.setLineSpacing(dp(5), 1.0f);
        transcript.setTextIsSelectable(true);
        LinearLayout.LayoutParams transcriptParams = params(-1, -2);
        transcriptParams.topMargin = dp(16);
        terminal.addView(transcript, transcriptParams);

        EditText command = new EditText(this);
        command.setSingleLine(true);
        command.setTextSize(13);
        command.setTypeface(Typeface.MONOSPACE);
        command.setHint("Enter a shell command");
        command.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        command.setImeOptions(EditorInfo.IME_ACTION_DONE);
        command.setEnabled(environment.isReady());
        command.setFocusableInTouchMode(true);
        command.setOnClickListener(v -> showTerminalKeyboard(command));
        command.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) command.post(() -> showTerminalKeyboard(command));
        });
        LinearLayout.LayoutParams commandParams = params(-1, -2);
        commandParams.topMargin = dp(10);
        terminal.addView(command, commandParams);

        MaterialButton execute = button("Run command");
        execute.setEnabled(environment.isReady());
        execute.setAlpha(environment.isReady() ? 1.0f : 0.45f);
        LinearLayout.LayoutParams executeParams = params(-1, -2);
        executeParams.topMargin = dp(8);
        terminal.addView(execute, executeParams);

        Runnable runCommand = () -> {
            String entered = command.getText().toString().trim();
            if (entered.isEmpty() || !environment.isReady()) return;
            command.setText("");
            InputMethodManager imm = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(command.getWindowToken(), 0);
            execute.setEnabled(false);
            transcript.append("\n$ " + entered + "\n");
            new Thread(() -> {
                final String result = NeutronEnvironment.runHostCommand(environment, entered);
                runOnUiThread(() -> {
                    transcript.append(result);
                    execute.setEnabled(true);
                });
            }, "neutron-shell-command").start();
        };
        execute.setOnClickListener(v -> runCommand.run());
        command.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEND) {
                runCommand.run();
                return true;
            }
            return false;
        });

        TextView backendInfo = text(
                "This first stage uses Android's /system/bin/sh. The Neutron bin/ directory is on PATH, but glibc, Bash and Linux packages are not installed yet.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams backendInfoParams = params(-1, -2);
        backendInfoParams.topMargin = dp(12);
        terminal.addView(backendInfo, backendInfoParams);

        terminalCard.addView(terminal);
        LinearLayout.LayoutParams terminalParams = params(-1, -2);
        terminalParams.topMargin = dp(16);
        content.addView(terminalCard, terminalParams);
        return page;
    }

    private void showTerminalKeyboard(EditText command) {
        if (!command.isEnabled()) return;
        command.requestFocus();
        command.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(command, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private ScrollView buildSettingsScreen() {
        ScrollView page = createPage();
        LinearLayout content = pageContent(page);
        content.addView(text("Settings", 25, R.color.neutron_text, true));
        TextView subtitle = text("Configure how Neutron will use your device.", 14,
                R.color.neutron_muted, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        addCoreLimiterCard(content);
        addMemoryLimiterCard(content);
        addSettingsCard(content, "Graphics", "Vulkan and OpenGL", "Choose rendering mode, display resolution and frame-rate limits.");
        addSettingsCard(content, "Audio & input", "Devices and controls", "Configure audio output, touch controls, keyboard, mouse and gamepads.");
        addSettingsCard(content, "Storage", "Root filesystem", "Manage the Linux root filesystem, shared folders and package storage.");
        addSettingsCard(content, "Appearance", "Dark theme", "Dark mode is the default Neutron appearance in this first UI build.");

        TextView version = text("NEUTRON 0.1.0 · ANDROID UI PREVIEW", 10, R.color.neutron_muted, true);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = params(-1, -2);
        versionParams.topMargin = dp(24);
        content.addView(version, versionParams);

        // Tiny naming-origin Easter egg, intentionally implemented in Kotlin.
        SettingsEasterEgg.addTo(content, this, dp(28));
        return page;
    }

    private void addCoreLimiterCard(LinearLayout parent) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(15), dp(16), dp(15));

        body.addView(text("PERFORMANCE", 10, R.color.neutron_primary, true));

        TextView heading = text("Guest virtual CPU limit", 16, R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(6);
        body.addView(heading, headingParams);

        TextView value = text("", 24, R.color.neutron_text, true);
        LinearLayout.LayoutParams valueParams = params(-1, -2);
        valueParams.topMargin = dp(12);
        body.addView(value, valueParams);

        int savedCount = getPreferences().getInt(
                PREF_GUEST_CORE_COUNT, DEFAULT_GUEST_CORE_COUNT);
        int initialIndex = 2;
        for (int i = 0; i < GUEST_CORE_OPTIONS.length; i++) {
            if (GUEST_CORE_OPTIONS[i] == savedCount) {
                initialIndex = i;
                break;
            }
        }

        SeekBar limiter = new SeekBar(this);
        limiter.setMax(GUEST_CORE_OPTIONS.length - 1);
        limiter.setProgress(initialIndex);
        value.setText(GUEST_CORE_OPTIONS[initialIndex]
                + (GUEST_CORE_OPTIONS[initialIndex] == 1 ? " virtual core" : " virtual cores"));

        LinearLayout.LayoutParams seekParams = params(-1, -2);
        seekParams.topMargin = dp(4);
        body.addView(limiter, seekParams);

        LinearLayout range = new LinearLayout(this);
        range.setGravity(Gravity.CENTER_VERTICAL);
        TextView minimum = text("1 core", 11, R.color.neutron_muted, false);
        TextView maximum = text("8 cores", 11, R.color.neutron_muted, false);
        range.addView(minimum, new LinearLayout.LayoutParams(0, -2, 1));
        maximum.setGravity(Gravity.END);
        range.addView(maximum);
        body.addView(range);

        TextView detail = text(
                "Maximum number of virtual CPUs available to the guest (1, 2, 4 or 8). " +
                "This does not reserve physical phone cores. The preference is saved locally; " +
                "the runtime scheduler will apply it when guest execution is connected.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(12);
        body.addView(detail, detailParams);

        limiter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int coreCount = GUEST_CORE_OPTIONS[progress];
                value.setText(coreCount
                        + (coreCount == 1 ? " virtual core" : " virtual cores"));
                if (fromUser) {
                    getPreferences().edit()
                            .putInt(PREF_GUEST_CORE_COUNT, coreCount)
                            .apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        MaterialCardView card = card();
        card.addView(body);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(11);
        parent.addView(card, cardParams);
    }

    private void addMemoryLimiterCard(LinearLayout parent) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(15), dp(16), dp(15));

        body.addView(text("MEMORY", 10, R.color.neutron_primary, true));

        TextView heading = text("Guest RAM limit", 16, R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(6);
        body.addView(heading, headingParams);

        TextView value = text("", 24, R.color.neutron_text, true);
        LinearLayout.LayoutParams valueParams = params(-1, -2);
        valueParams.topMargin = dp(12);
        body.addView(value, valueParams);

        SeekBar limiter = new SeekBar(this);
        limiter.setMax((MAX_GUEST_RAM_MB - MIN_GUEST_RAM_MB) / RAM_STEP_MB);
        int savedLimit = getPreferences().getInt(PREF_GUEST_RAM_MB, DEFAULT_GUEST_RAM_MB);
        int initialLimit = Math.max(MIN_GUEST_RAM_MB,
                Math.min(MAX_GUEST_RAM_MB, savedLimit));
        int initialProgress = Math.round((initialLimit - MIN_GUEST_RAM_MB) / (float) RAM_STEP_MB);
        limiter.setProgress(initialProgress);
        value.setText(initialLimit + " MB");

        LinearLayout.LayoutParams seekParams = params(-1, -2);
        seekParams.topMargin = dp(4);
        body.addView(limiter, seekParams);

        LinearLayout range = new LinearLayout(this);
        range.setGravity(Gravity.CENTER_VERTICAL);
        TextView minimum = text(MIN_GUEST_RAM_MB + " MB", 11, R.color.neutron_muted, false);
        TextView maximum = text(MAX_GUEST_RAM_MB + " MB", 11, R.color.neutron_muted, false);
        range.addView(minimum, new LinearLayout.LayoutParams(0, -2, 1));
        maximum.setGravity(Gravity.END);
        range.addView(maximum);
        body.addView(range);

        TextView detail = text("Maximum memory available to the Linux guest. This preference is saved locally; enforcement will activate when the runtime memory manager is implemented.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(12);
        body.addView(detail, detailParams);

        limiter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int limitMb = MIN_GUEST_RAM_MB + progress * RAM_STEP_MB;
                value.setText(limitMb + " MB");
                if (fromUser) {
                    getPreferences().edit().putInt(PREF_GUEST_RAM_MB, limitMb).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        MaterialCardView card = card();
        card.addView(body);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(11);
        parent.addView(card, cardParams);
    }

    private void addSettingsCard(LinearLayout parent, String category, String title, String description) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(15), dp(16), dp(15));
        body.addView(text(category.toUpperCase(), 10, R.color.neutron_primary, true));
        TextView heading = text(title, 16, R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(6);
        body.addView(heading, headingParams);
        TextView detail = text(description, 12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(4);
        body.addView(detail, detailParams);
        MaterialCardView card = card();
        card.addView(body);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(11);
        parent.addView(card, cardParams);
    }


    private void addRootfsCard(LinearLayout parent) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(15), dp(16), dp(15));
        body.addView(text("LINUX ENVIRONMENT", 10, R.color.neutron_primary, true));

        TextView heading = text("Root filesystem", 16, R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(6);
        body.addView(heading, headingParams);

        TextView status = text(getRootfsStatus(), 12,
                isRootfsValid() ? R.color.neutron_primary : R.color.neutron_muted, false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(5);
        body.addView(status, statusParams);

        MaterialButton installDefault = button(RootfsInstaller.isInstalled(this)
                ? "Reinstall ARM64 rootfs" : "Install default ARM64 glibc rootfs");
        installDefault.setOnClickListener(v -> installDefaultRootfs(
                status, installDefault, false, RootfsInstaller.isInstalled(this)));
        LinearLayout.LayoutParams installParams = params(-1, -2);
        installParams.topMargin = dp(12);
        body.addView(installDefault, installParams);

        MaterialButton chooseRootfs = button("Validate external rootfs folder");
        chooseRootfs.setOnClickListener(v -> chooseRootfsFolder());
        LinearLayout.LayoutParams buttonParams = params(-1, -2);
        buttonParams.topMargin = dp(8);
        body.addView(chooseRootfs, buttonParams);

        MaterialCardView rootfsCard = card();
        rootfsCard.addView(body);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(18);
        parent.addView(rootfsCard, cardParams);
    }

    private void installDefaultRootfs(TextView status, MaterialButton installButton, boolean returnToTerminal) {
        installDefaultRootfs(status, installButton, returnToTerminal, false);
    }

    private void installDefaultRootfs(TextView status, MaterialButton installButton,
                                      boolean returnToTerminal, boolean forceReinstall) {
        installButton.setEnabled(false);
        status.setText("Downloading Neutron ARM64 glibc rootfs…");
        new Thread(() -> {
            try {
                File installed = RootfsInstaller.install(this, percent ->
                        runOnUiThread(() -> status.setText(
                                percent < 0 ? "Extracting ARM64 glibc rootfs…" :
                                        "Downloading ARM64 glibc rootfs… " + percent + "%")),
                        forceReinstall);
                runOnUiThread(() -> {
                    status.setText("Neutron minimal glibc · ARM64 · installed in app storage");
                    installButton.setText("Reinstall ARM64 rootfs");
                    installButton.setEnabled(true);
                    Toast.makeText(this, "Neutron glibc rootfs installed.", Toast.LENGTH_LONG).show();
                    showScreen(returnToTerminal ? R.id.nav_terminal : R.id.nav_apps);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status.setText("Installation failed: " + error.getMessage());
                    installButton.setEnabled(true);
                    Toast.makeText(this, "Rootfs installation failed.", Toast.LENGTH_LONG).show();
                });
            }
        }, "neutron-rootfs-installer").start();
    }

    private void chooseRootfsFolder() {
        chooseRootfsFolder(false);
    }

    private void chooseRootfsFolder(boolean returnToTerminal) {
        getPreferences().edit().putBoolean(PREF_ROOTFS_RETURN_TERMINAL, returnToTerminal).apply();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_ROOTFS);
    }

    private boolean isRootfsValid() {
        if (RootfsInstaller.isInstalled(this)) {
            return true;
        }
        Uri treeUri = getSavedRootfsUri();
        if (treeUri == null) {
            return false;
        }

        Set<String> missing = new HashSet<>();
        Collections.addAll(missing, "bin", "lib", "home", "dev", "proc");

        try {
            ContentResolver resolver = getContentResolver();
            String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId);
            String[] rootProjection = {DocumentsContract.Document.COLUMN_MIME_TYPE};
            try (Cursor rootCursor = resolver.query(rootDocumentUri, rootProjection,
                    null, null, null)) {
                if (rootCursor == null || !rootCursor.moveToFirst()) {
                    return false;
                }
                int mimeColumn = rootCursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                if (mimeColumn < 0 || !DocumentsContract.Document.MIME_TYPE_DIR.equals(
                        rootCursor.getString(mimeColumn))) {
                    return false;
                }
            }

            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId);
            String[] childProjection = {
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            };
            try (Cursor children = resolver.query(childrenUri, childProjection,
                    null, null, null)) {
                if (children == null) {
                    return false;
                }
                int nameColumn = children.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int typeColumn = children.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                if (nameColumn < 0 || typeColumn < 0) {
                    return false;
                }
                while (children.moveToNext()) {
                    String name = children.getString(nameColumn);
                    String mime = children.getString(typeColumn);
                    if (name != null && DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        missing.remove(name);
                    }
                }
            }
            if (!missing.isEmpty()) return false;
            Uri busybox = findChildDocumentUri(treeUri, rootDocumentId, "bin", "busybox");
            Uri loader = findChildDocumentUri(treeUri, rootDocumentId, "lib", "ld-linux-aarch64.so.1");
            if (loader == null) {
                loader = findChildDocumentUri(treeUri, rootDocumentId, "lib64", "ld-linux-aarch64.so.1");
            }
            return busybox != null && loader != null
                    && isArm64ElfUri(busybox) && isArm64ElfUri(loader);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Uri findChildDocumentUri(Uri treeUri, String parentId, String directory, String fileName) {
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId);
            String directoryId = null;
            try (Cursor cursor = getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor == null) return null;
                while (cursor.moveToNext()) {
                    if (directory.equals(cursor.getString(1))
                            && DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                        directoryId = cursor.getString(0);
                        break;
                    }
                }
            }
            if (directoryId == null) return null;
            Uri nested = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId);
            try (Cursor cursor = getContentResolver().query(nested,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cursor == null) return null;
                while (cursor.moveToNext()) {
                    if (fileName.equals(cursor.getString(1))) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0));
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private boolean isArm64ElfUri(Uri uri) {
        try (InputStream input = "file".equals(uri.getScheme())
                ? new java.io.FileInputStream(uri.getPath())
                : getContentResolver().openInputStream(uri)) {
            if (input == null) return false;
            byte[] header = new byte[20];
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            int type = (header[16] & 0xff) | ((header[17] & 0xff) << 8);
            int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
            return header[0] == 0x7f && header[1] == 'E' && header[2] == 'L'
                    && header[3] == 'F' && header[4] == 2 && header[5] == 1
                    && (type == 2 || type == 3) && machine == 183;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getRootfsStatus() {
        if (RootfsInstaller.isInstalled(this)) {
            return "Neutron minimal glibc · ARM64 · app-private storage";
        }
        if (getSavedRootfsUri() == null) {
            return "No rootfs selected. Launch is disabled.";
        }
        if (isRootfsValid()) {
            return "External rootfs validated, but SAF folders are not executable here. Install the built-in rootfs to run commands.";
        }
        return "Invalid or inaccessible rootfs. It must contain /bin, /lib, /home, /dev, /proc and the AArch64 glibc loader.";
    }

    @Nullable
    private Uri getSavedRootfsUri() {
        String value = getPreferences().getString(PREF_ROOTFS_URI, null);
        return value == null ? null : Uri.parse(value);
    }

    private void chooseTerminalElf() {
        if (RootfsInstaller.isInstalled(this)) {
            File busybox = new File(RootfsInstaller.getRootfsDirectory(this), "bin/busybox");
            Uri uri = Uri.fromFile(busybox);
            if (isArm64TerminalElf(uri, "busybox")) {
                new AlertDialog.Builder(this)
                        .setTitle("Choose terminal binary")
                        .setItems(new String[]{"/bin/busybox (shell applet)"}, (dialog, which) ->
                                saveTerminalElf(uri, "/bin/busybox"))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_TERMINAL_ELF);
    }

    private void saveTerminalElf(Uri uri, String name) {
        getPreferences().edit()
                .putString(PREF_TERMINAL_ELF_URI, uri.toString())
                .putString(PREF_TERMINAL_ELF_NAME, name)
                .apply();
        Toast.makeText(this, "ARM64 terminal ELF accepted.", Toast.LENGTH_SHORT).show();
        showScreen(R.id.nav_terminal);
    }

    private boolean isArm64TerminalElf(Uri uri, String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        boolean shellName = lower.equals("sh") || lower.equals("ash") || lower.equals("bash")
                || lower.equals("busybox") || lower.equals("dash") || lower.equals("zsh");
        if (!shellName) return false;
        try (InputStream input = "file".equals(uri.getScheme())
                ? new java.io.FileInputStream(uri.getPath())
                : getContentResolver().openInputStream(uri)) {
            if (input == null) return false;
            byte[] header = new byte[20];
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            if (header[0] != 0x7f || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') return false;
            if (header[4] != 2 || header[5] != 1) return false;
            int type = (header[16] & 0xff) | ((header[17] & 0xff) << 8);
            int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
            return (type == 2 || type == 3) && machine == 183;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void chooseElfFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_ELF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != REQUEST_PICK_ELF && requestCode != REQUEST_PICK_ROOTFS && requestCode != REQUEST_PICK_TERMINAL_ELF)
                || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        if (requestCode == REQUEST_PICK_ROOTFS) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
                getPreferences().edit()
                        .putString(PREF_ROOTFS_URI, uri.toString())
                        .remove(PREF_TERMINAL_ELF_URI)
                        .remove(PREF_TERMINAL_ELF_NAME)
                        .apply();
                if (isRootfsValid()) {
                    Toast.makeText(this, "Rootfs validated.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            "Rootfs is missing one or more required directories.",
                            Toast.LENGTH_LONG).show();
                }
            } catch (Exception error) {
                Toast.makeText(this, "Could not save access to this folder.", Toast.LENGTH_LONG).show();
            }
            boolean returnToTerminal = getPreferences().getBoolean(PREF_ROOTFS_RETURN_TERMINAL, false);
            getPreferences().edit().remove(PREF_ROOTFS_RETURN_TERMINAL).apply();
            showScreen(returnToTerminal ? R.id.nav_terminal : R.id.nav_apps);
            return;
        }

        if (requestCode == REQUEST_PICK_TERMINAL_ELF) {
            String name = queryDisplayName(uri);
            if (!isArm64TerminalElf(uri, name)) {
                Toast.makeText(this,
                        "Rejected: choose an ELF64 little-endian AArch64 shell named sh, ash, bash, BusyBox, dash or zsh.",
                        Toast.LENGTH_LONG).show();
                showScreen(R.id.nav_terminal);
                return;
            }
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
                // Some document providers grant temporary access only.
            }
            saveTerminalElf(uri, name);
            return;
        }

        String displayName = queryDisplayName(uri);
        Set<String> selected = new HashSet<>(getPreferences().getStringSet(PREF_SELECTED_FILES, new HashSet<>()));
        selected.add(displayName);
        getPreferences().edit().putStringSet(PREF_SELECTED_FILES, selected).apply();
        showScreen(R.id.nav_apps);
    }

    private String queryDisplayName(Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Fall back to the URI's final path segment.
        }
        return name == null || name.trim().isEmpty() ? "Selected file" : name;
    }

    private ArrayList<String> getSelectedFiles() {
        Set<String> stored = getPreferences().getStringSet(PREF_SELECTED_FILES, new HashSet<>());
        ArrayList<String> files = new ArrayList<>(stored);
        Collections.sort(files, String.CASE_INSENSITIVE_ORDER);
        return files;
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.neutron_outline));
        card.setCardBackgroundColor(getColor(R.color.neutron_surface));
        card.setUseCompatPadding(false);
        return card;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(12);
        button.setCornerRadius(dp(14));
        button.setMinHeight(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private TextView sectionTitle(String value) {
        return text(value, 17, R.color.neutron_text, true);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(getColor(color));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
