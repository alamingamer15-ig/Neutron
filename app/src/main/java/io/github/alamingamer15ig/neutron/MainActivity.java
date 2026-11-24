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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

import androidx.annotation.Nullable;
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
    private static final String PREFS = "neutron_ui";
    private static final String PREF_SELECTED_FILES = "selected_files";
    private static final String PREF_ROOTFS_URI = "rootfs_tree_uri";
    private static final String PREF_GUEST_RAM_MB = "guest_ram_limit_mb";
    private static final int DEFAULT_GUEST_RAM_MB = 512;
    private static final int MIN_GUEST_RAM_MB = 256;
    private static final int MAX_GUEST_RAM_MB = 2048;
    private static final int RAM_STEP_MB = 128;

    private FrameLayout contentContainer;
    private BottomNavigationView bottomNavigation;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        TextView intro = text("Run x86-64 Linux applications on your Android device.", 14,
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
        ScrollView page = createPage();
        LinearLayout content = pageContent(page);
        content.addView(text("Linux terminal", 25, R.color.neutron_text, true));
        TextView subtitle = text("A shell for your guest environment.", 14, R.color.neutron_muted, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        MaterialCardView terminalCard = card();
        terminalCard.setCardBackgroundColor(0xFF0B1018);
        LinearLayout terminal = new LinearLayout(this);
        terminal.setOrientation(LinearLayout.VERTICAL);
        terminal.setPadding(dp(16), dp(18), dp(16), dp(18));
        TextView terminalHeader = text("NEUTRON / GUEST SHELL", 10, R.color.neutron_primary, true);
        terminal.addView(terminalHeader);
        TextView terminalMessage = text("neutron:~$  status\n\nRuntime connection unavailable.\nThe guest shell will be enabled after the native runtime is integrated.",
                13, R.color.neutron_text, false);
        terminalMessage.setTypeface(Typeface.MONOSPACE);
        terminalMessage.setLineSpacing(dp(5), 1.0f);
        LinearLayout.LayoutParams messageParams = params(-1, -2);
        messageParams.topMargin = dp(18);
        terminal.addView(terminalMessage, messageParams);
        terminalCard.addView(terminal);
        LinearLayout.LayoutParams terminalParams = params(-1, -2);
        terminalParams.topMargin = dp(20);
        content.addView(terminalCard, terminalParams);

        TextView info = text("This is a terminal preview, not an active shell. Commands are not executed in this version.",
                12, R.color.neutron_muted, false);
        LinearLayout.LayoutParams infoParams = params(-1, -2);
        infoParams.topMargin = dp(15);
        content.addView(info, infoParams);
        return page;
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

        addSettingsCard(content, "Performance", "CPU workers", "Translation and execution worker limits will be configurable here.");
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
        return page;
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

        MaterialButton installAlpine = button("Install Neutron glibc rootfs (x86-64)");
        installAlpine.setOnClickListener(v -> installAlpineRootfs(status, installAlpine));
        LinearLayout.LayoutParams installParams = params(-1, -2);
        installParams.topMargin = dp(12);
        body.addView(installAlpine, installParams);

        MaterialButton chooseRootfs = button("Choose existing rootfs folder");
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

    private void installAlpineRootfs(TextView status, MaterialButton installButton) {
        installButton.setEnabled(false);
        status.setText("Downloading Neutron glibc rootfs…");
        new Thread(() -> {
            try {
                File installed = RootfsInstaller.install(this, percent ->
                        runOnUiThread(() -> status.setText(
                                percent < 0 ? "Extracting glibc rootfs…" :
                                        "Downloading glibc rootfs… " + percent + "%")));
                runOnUiThread(() -> {
                    status.setText("Neutron minimal glibc · x86-64 · installed in app storage");
                    installButton.setText("Reinstall glibc rootfs");
                    installButton.setEnabled(true);
                    Toast.makeText(this, "Neutron glibc rootfs installed.", Toast.LENGTH_LONG).show();
                    showScreen(R.id.nav_apps);
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
            return missing.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getRootfsStatus() {
        if (RootfsInstaller.isInstalled(this)) {
            return "Neutron minimal glibc · x86-64 · app-private storage";
        }
        if (getSavedRootfsUri() == null) {
            return "No rootfs selected. Launch is disabled.";
        }
        if (isRootfsValid()) {
            return "Valid rootfs · /bin, /lib, /home, /dev and /proc found.";
        }
        return "Invalid or inaccessible rootfs. Required directories: /bin, /lib, /home, /dev and /proc.";
    }

    @Nullable
    private Uri getSavedRootfsUri() {
        String value = getPreferences().getString(PREF_ROOTFS_URI, null);
        return value == null ? null : Uri.parse(value);
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
        if ((requestCode != REQUEST_PICK_ELF && requestCode != REQUEST_PICK_ROOTFS)
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
                getPreferences().edit().putString(PREF_ROOTFS_URI, uri.toString()).apply();
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
            showScreen(R.id.nav_apps);
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
