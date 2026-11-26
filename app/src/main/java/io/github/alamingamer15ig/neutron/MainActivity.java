package io.github.alamingamer15ig.neutron;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import java.io.IOException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_ELF = 41;
    private static final String PREFS = "neutron_ui";
    private static final String PREF_SELECTED_FILES = "selected_files";
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
        NeutronEnvironment.initialize(this);
        setContentView(R.layout.activity_main);

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

    @Override
    protected void onDestroy() {
        closeTerminalSession();
        super.onDestroy();
    }

    private void showScreen(int destination) {
        if (destination != R.id.nav_terminal) closeTerminalSession();
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

        TextView description = text("The Neutron userspace directories are initialized. Native Linux execution and x86-64 translation are still under development.",
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
                launchButton.setEnabled(false);
                launchButton.setAlpha(0.45f);
                launchButton.setOnClickListener(v -> {
                    Toast.makeText(this,
                            "Neutron's x86-64 execution runtime is not connected yet.",
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

    private TerminalSession terminalSession;
    private TerminalView terminalView;
    private NeutronTermuxTerminalClient terminalClient;

    private LinearLayout buildTerminalScreen() {
        closeTerminalSession();
        NeutronEnvironment environment = NeutronEnvironment.initialize(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView heading = text("Neutron terminal", 22, R.color.neutron_text, true);
        page.addView(heading);

        TextView subtitle = text("Powered by the Termux terminal emulator · Neutron private prefix",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(4);
        page.addView(subtitle, subtitleParams);

        TextView path = text(environment.getRootDirectory().getAbsolutePath(), 11,
                R.color.neutron_muted, false);
        path.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams pathParams = params(-1, -2);
        pathParams.topMargin = dp(6);
        pathParams.bottomMargin = dp(10);
        page.addView(path, pathParams);

        TerminalView view = new TerminalView(this, null);
        view.setBackgroundColor(0xFF0B1018);
        view.setTextSize(14);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams terminalParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(view, terminalParams);

        if (!environment.isReady()) {
            TextView error = text("Neutron's private prefix is unavailable.", 13,
                    R.color.neutron_muted, false);
            page.addView(error);
            return page;
        }

        if (!NeutronBootstrapInstaller.isInstalled(environment)
                || !NeutronRuntimeInstaller.isInstalled(environment)) {
            TextView installStatus = text(
                    "Preparing Neutron's Termux environment and glibc toolchain…",
                    13, R.color.neutron_muted, false);
            LinearLayout.LayoutParams installStatusParams = params(-1, -2);
            installStatusParams.topMargin = dp(12);
            page.addView(installStatus, installStatusParams);

            Context appContext = getApplicationContext();
            new Thread(() -> {
                String failure = null;
                try {
                    NeutronBootstrapInstaller.install(appContext);
                    NeutronRuntimeInstaller.install(appContext);
                    NeutronEnvironment installedEnvironment = NeutronEnvironment.initialize(appContext);
                    if (!NeutronBootstrapInstaller.isInstalled(installedEnvironment)) {
                        throw new IOException("Termux bootstrap verification failed after installation");
                    }
                    if (!NeutronRuntimeInstaller.isInstalled(installedEnvironment)) {
                        throw new IOException("Termux glibc package verification failed after installation");
                    }
                } catch (Exception error) {
                    failure = error.getClass().getSimpleName() + ": "
                            + String.valueOf(error.getMessage());
                }
                final String installError = failure;
                runOnUiThread(() -> {
                    if (isFinishing() || page.getParent() == null) return;
                    if (installError == null) {
                        Toast.makeText(MainActivity.this,
                                "Neutron Linux environment installed", Toast.LENGTH_SHORT).show();
                        showScreen(R.id.nav_terminal);
                    } else {
                        installStatus.setText("Environment installation failed: " + installError
                                + "\nOpen Settings to retry the bootstrap installation.");
                    }
                });
            }, "neutron-terminal-bootstrap").start();
            return page;
        }

        try {
            terminalView = view;
            terminalClient = new NeutronTermuxTerminalClient(this, view);
            view.setTerminalViewClient(terminalClient);

            String prefix = environment.getRootDirectory().getAbsolutePath();
            String home = environment.getHomeDirectory().getAbsolutePath();
            String shellPath = prefix + "/bin/bash";
            String[] shellArgs = new String[]{"-l", "-i"};
            String[] shellEnvironment = {
                    "HOME=" + home,
                    "PREFIX=" + prefix,
                    "PATH=" + prefix + "/bin:/system/bin:/system/xbin",
                    "LD_LIBRARY_PATH=" + prefix + "/lib",
                    "TMPDIR=" + prefix + "/tmp",
                    "SHELL=" + shellPath,
                    "TERM=xterm-256color",
                    "LANG=C.UTF-8",
                    "USER=neutron",
                    "PS1=neutron:\\w $ "
            };

            terminalSession = new TerminalSession(
                    shellPath,
                    home,
                    shellArgs,
                    shellEnvironment,
                    2000,
                    terminalClient
            );
            view.attachSession(terminalSession);
            view.requestFocus();
        } catch (Throwable error) {
            terminalSession = null;
            terminalView = null;
            terminalClient = null;
            Toast.makeText(this, "Could not start Termux terminal: "
                    + error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()), Toast.LENGTH_LONG).show();
        }

        return page;
    }

    private void closeTerminalSession() {
        TerminalSession session = terminalSession;
        terminalSession = null;
        terminalView = null;
        terminalClient = null;
        if (session != null) session.finishIfRunning();
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
        addRuntimeInstallCard(content);
        addBootstrapInstallCard(content);
        addSettingsCard(content, "Graphics", "Vulkan and OpenGL", "Choose rendering mode, display resolution and frame-rate limits.");
        addSettingsCard(content, "Audio & input", "Devices and controls", "Configure audio output, touch controls, keyboard, mouse and gamepads.");
        addSettingsCard(content, "Storage", "Neutron userspace", "Manage the app-private bin/, lib/, lib64/ and home/ directories.");
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

    private void addRuntimeInstallCard(LinearLayout parent) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(15), dp(16), dp(15));
        body.addView(text("GLIBC TOOLCHAIN", 10, R.color.neutron_primary, true));

        TextView heading = text("Install glibc through Termux pkg", 16,
                R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(6);
        body.addView(heading, headingParams);

        TextView description = text(
                "Neutron runs Termux's own package manager to install glibc-repo, glibc-runner, gcc-glibc and gcc-libs-glibc. These packages are installed inside Neutron's private prefix; Android's system libraries are untouched.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams descriptionParams = params(-1, -2);
        descriptionParams.topMargin = dp(8);
        body.addView(description, descriptionParams);

        TextView status = text("", 12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(12);
        body.addView(status, statusParams);

        MaterialButton install = button("Install glibc packages");
        LinearLayout.LayoutParams buttonParams = params(-1, -2);
        buttonParams.topMargin = dp(10);
        body.addView(install, buttonParams);

        NeutronEnvironment environment = NeutronEnvironment.initialize(this);
        boolean installed = NeutronRuntimeInstaller.isInstalled(environment);
        status.setText(installed
                ? "Installed · Termux glibc packages are ready."
                : "Not installed · Neutron will run pkg to install the glibc toolchain.");
        if (installed) {
            install.setText("Installed");
            install.setEnabled(false);
        } else {
            install.setOnClickListener(view -> {
                install.setEnabled(false);
                install.setText("Installing…");
                status.setText("Running pkg update and installing the glibc packages. Keep Neutron open…");
                Context appContext = getApplicationContext();
                new Thread(() -> {
                    String failure = null;
                    try {
                        NeutronRuntimeInstaller.install(appContext);
                    } catch (Exception error) {
                        failure = error.getClass().getSimpleName() + ": "
                                + String.valueOf(error.getMessage());
                    }
                    final String installError = failure;
                    runOnUiThread(() -> {
                        boolean ready = NeutronRuntimeInstaller.isInstalled(
                                NeutronEnvironment.initialize(MainActivity.this));
                        if (ready) {
                            status.setText("Installed · Termux glibc packages are ready.");
                            install.setText("Installed");
                            install.setEnabled(false);
                            Toast.makeText(MainActivity.this,
                                    "Termux glibc packages installed", Toast.LENGTH_LONG).show();
                        } else {
                            status.setText("Installation failed: "
                                    + (installError == null ? "runtime verification failed" : installError));
                            install.setText("Retry installation");
                            install.setEnabled(true);
                        }
                    });
                }, "neutron-runtime-installer").start();
            });
        }

        MaterialCardView runtimeCard = card();
        runtimeCard.addView(body);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(16);
        parent.addView(runtimeCard, cardParams);
    }

    private void addBootstrapInstallCard(LinearLayout parent) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(15), dp(16), dp(15));
        body.addView(text("NEUTRON BOOTSTRAP", 10, R.color.neutron_primary, true));

        TextView heading = text("ARM64 Linux command-line environment", 16,
                R.color.neutron_text, true);
        LinearLayout.LayoutParams headingParams = params(-1, -2);
        headingParams.topMargin = dp(6);
        body.addView(heading, headingParams);

        TextView description = text(
                "Install Termux's Bionic-based Bash, coreutils, apt, dpkg and pkg bootstrap, rebuilt for Neutron's own Android package id and private files/neutron prefix. No separate Termux app is required.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams descriptionParams = params(-1, -2);
        descriptionParams.topMargin = dp(8);
        body.addView(description, descriptionParams);

        TextView status = text("", 12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(12);
        body.addView(status, statusParams);

        MaterialButton install = button("Install Neutron bootstrap");
        LinearLayout.LayoutParams buttonParams = params(-1, -2);
        buttonParams.topMargin = dp(10);
        body.addView(install, buttonParams);

        NeutronEnvironment environment = NeutronEnvironment.initialize(this);
        boolean installed = NeutronBootstrapInstaller.isInstalled(environment);
        status.setText(installed
                ? "Installed · Termux's Bash, coreutils, apt, dpkg and pkg are available."
                : "Not installed · Neutron's fork-specific Termux bootstrap will be installed.");
        if (installed) {
            install.setText("Installed");
            install.setEnabled(false);
        } else {
            install.setOnClickListener(view -> {
                install.setEnabled(false);
                install.setText("Installing…");
                status.setText("Installing the Neutron bootstrap and required runtime…");
                Context appContext = getApplicationContext();
                new Thread(() -> {
                    String failure = null;
                    try {
                        NeutronBootstrapInstaller.install(appContext);
                    } catch (Exception error) {
                        failure = error.getClass().getSimpleName() + ": "
                                + String.valueOf(error.getMessage());
                    }
                    final String installError = failure;
                    runOnUiThread(() -> {
                        boolean ready = NeutronBootstrapInstaller.isInstalled(
                                NeutronEnvironment.initialize(MainActivity.this));
                        if (ready) {
                            status.setText("Installed · open Terminal to use Termux's Bash, pkg, apt and dpkg.");
                            install.setText("Installed");
                            install.setEnabled(false);
                            Toast.makeText(MainActivity.this,
                                    "Neutron bootstrap installed", Toast.LENGTH_LONG).show();
                        } else {
                            status.setText("Installation failed: "
                                    + (installError == null ? "bootstrap verification failed" : installError));
                            install.setText("Retry installation");
                            install.setEnabled(true);
                        }
                    });
                }, "neutron-bootstrap-installer").start();
            });
        }

        MaterialCardView bootstrapCard = card();
        bootstrapCard.addView(body);
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.topMargin = dp(16);
        parent.addView(bootstrapCard, cardParams);
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


    private void chooseElfFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_ELF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_ELF || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
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
