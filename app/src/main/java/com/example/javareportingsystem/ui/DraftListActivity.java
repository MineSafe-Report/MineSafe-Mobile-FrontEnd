package com.example.javareportingsystem.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DraftListActivity extends AppCompatActivity {
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LocalReportRepository reports;
    private LinearLayout draftContainer;
    private TextView emptyNotice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_draft_list);
        reports = new LocalReportRepository(this);
        draftContainer = findViewById(R.id.draftContainer);
        emptyNotice = findViewById(R.id.tvNoDrafts);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top,
                    systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDrafts();
    }

    private void loadDrafts() {
        databaseExecutor.execute(() -> {
            try {
                List<DraftOption> options = new ArrayList<>();
                for (OfflineReportEntity draft : reports.listDrafts()) {
                    String locationName = getString(R.string.draft_location_missing);
                    if (draft.locationLocalId != null) {
                        LocationEntity location = reports.getLocation(draft.locationLocalId);
                        if (location != null) {
                            locationName = location.name;
                        }
                    }
                    options.add(new DraftOption(draft.localId,
                            formatDraftLabel(draft, locationName)));
                }
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showDrafts(options);
                    }
                });
            } catch (RuntimeException error) {
                showLoadError();
            }
        });
    }

    private String formatDraftLabel(OfflineReportEntity draft, String locationName) {
        String type = ReportValues.NEAR_MISS.equals(draft.typeCode)
                ? getString(R.string.report_type_near_miss)
                : ReportValues.INCIDENT.equals(draft.typeCode)
                        ? getString(R.string.report_type_incident)
                        : ReportValues.HAZARD.equals(draft.typeCode)
                                ? getString(R.string.report_type_hazard)
                                : getString(R.string.report_type_not_selected);
        String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(draft.createdAtEpochMillis));
        return getString(R.string.draft_option, type, draft.localId, date, locationName);
    }

    private void showDrafts(List<DraftOption> options) {
        draftContainer.removeAllViews();
        emptyNotice.setVisibility(options.isEmpty() ? View.VISIBLE : View.GONE);
        for (DraftOption option : options) {
            Button button = new Button(this);
            button.setText(option.label);
            button.setAllCaps(false);
            button.setTextSize(18);
            button.setTextColor(getColor(R.color.safety_on_action));
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setBackgroundResource(R.drawable.button_yellow);
            button.setBackgroundTintList(null);
            button.setMinHeight(dp(112));
            button.setPadding(dp(16), dp(12), dp(16), dp(12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(14);
            draftContainer.addView(button, params);
            button.setOnClickListener(v -> openDraft(option.id, button));
        }
    }

    private void openDraft(long draftId, Button button) {
        button.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                OfflineReportEntity draft = reports.getOfflineReport(draftId);
                if (draft == null || !ReportValues.DRAFT.equals(draft.syncState)) {
                    throw new IllegalStateException("Draft is no longer available");
                }
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        button.setEnabled(true);
                        startActivities(ReportDraftNavigation.resumeStack(this, draft));
                    }
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        button.setEnabled(true);
                        Toast.makeText(this, R.string.report_draft_load_failed,
                                Toast.LENGTH_SHORT).show();
                        loadDrafts();
                    }
                });
            }
        });
    }

    private void showLoadError() {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, R.string.report_draft_load_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private static final class DraftOption {
        final long id;
        final String label;

        DraftOption(long id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
