package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.ReportSyncWorker;
import com.example.javareportingsystem.data.local.AppDatabase;
import com.example.javareportingsystem.data.local.ReportListItem;
import com.example.javareportingsystem.data.local.ReportValues;

import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyReportsActivity extends AppCompatActivity {
    private LinearLayout reportsContainer;
    private TextView emptyNotice;
    private final List<ReportListItem> allItems = new ArrayList<>();
    private final ExecutorService statusExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean statusRefreshing = new AtomicBoolean();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() {
            refreshStatuses();
            refreshHandler.postDelayed(this, 60_000L);
        }
    };
    private EditText searchReports;
    private Button statusFilter;
    private Button typeFilter;
    private int selectedStatus;
    private int selectedType;

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
        refreshHandler.postDelayed(refreshTick, 60_000L);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshTick);
        super.onPause();
    }

    private void refreshStatuses() {
        ReportSyncWorker.enqueue(this);
        if (!statusRefreshing.compareAndSet(false, true)) return;
        statusExecutor.execute(() -> {
            try {
                ReportSyncWorker.refreshStatusesNow(getApplicationContext());
            } finally {
                statusRefreshing.set(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshTick);
        statusExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_reports);
        reportsContainer = findViewById(R.id.reportsContainer);
        emptyNotice = findViewById(R.id.tvNoReports);
        searchReports = findViewById(R.id.searchReports);
        statusFilter = findViewById(R.id.btnStatusFilter);
        typeFilter = findViewById(R.id.btnTypeFilter);
        statusFilter.setOnClickListener(v -> showStatusFilter());
        typeFilter.setOnClickListener(v -> showTypeFilter());
        searchReports.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderReports();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        AppDatabase.getInstance(this).reportListDao().observeAll()
                .observe(this, this::showReports);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + dp(20), bars.top, bars.right + dp(20), bars.bottom);
            return insets;
        });
    }

    private void showReports(List<ReportListItem> items) {
        allItems.clear();
        if (items != null) allItems.addAll(items);
        renderReports();
    }

    private void showStatusFilter() {
        String[] options = getResources().getStringArray(R.array.my_reports_status_options);
        new AlertDialog.Builder(this)
                .setTitle(R.string.my_reports_filter_status_title)
                .setSingleChoiceItems(options, selectedStatus, (dialog, which) -> {
                    selectedStatus = which;
                    statusFilter.setText(options[which]);
                    renderReports();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTypeFilter() {
        String[] options = getResources().getStringArray(R.array.my_reports_type_options);
        new AlertDialog.Builder(this)
                .setTitle(R.string.my_reports_filter_type_title)
                .setSingleChoiceItems(options, selectedType, (dialog, which) -> {
                    selectedType = which;
                    typeFilter.setText(options[which]);
                    renderReports();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renderReports() {
        reportsContainer.removeAllViews();
        String search = searchReports.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<ReportListItem> visible = new ArrayList<>();
        if (search.isEmpty()) {
            for (ReportListItem item : allItems) {
                if (isPinned(item) && matchesPresetFilters(item)) visible.add(item);
            }
            for (ReportListItem item : allItems) {
                if (!isPinned(item) && matchesPresetFilters(item)) visible.add(item);
            }
        } else {
            for (ReportListItem item : allItems) {
                if (!matchesPresetFilters(item)) continue;
                String searchable = (item.localReportId + " " + item.serverReportId + " "
                        + typeLabel(item.typeCode) + " " + item.locationName + " "
                        + displayStatus(item.statusCode).label).toLowerCase(Locale.ROOT);
                if (searchable.contains(search)) visible.add(item);
            }
        }
        boolean empty = visible.isEmpty();
        emptyNotice.setText(allItems.isEmpty() ? R.string.no_submitted_reports
                : R.string.my_reports_no_results);
        emptyNotice.setVisibility(empty ? View.VISIBLE : View.GONE);
        reportsContainer.addView(emptyNotice);
        if (empty) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        boolean olderHeadingShown = false;
        for (int index = 0; index < visible.size(); index++) {
            ReportListItem item = visible.get(index);
            if (index == 0) {
                addHeading(search.isEmpty()
                        ? (isPinned(item) ? R.string.my_reports_pending_heading
                                : R.string.my_reports_older_heading)
                        : R.string.my_reports_search_results_heading);
                olderHeadingShown = search.isEmpty() && !isPinned(item);
            } else if (search.isEmpty() && !isPinned(item) && !olderHeadingShown) {
                addHeading(R.string.my_reports_older_heading);
                olderHeadingShown = true;
            }
            View row = inflater.inflate(R.layout.item_my_report, reportsContainer, false);
            String type = typeLabel(item.typeCode);
            ((TextView) row.findViewById(R.id.tvReportName)).setText(
                    getString(R.string.report_list_name, type, item.localReportId));
            ((TextView) row.findViewById(R.id.tvReportDate)).setText(
                    dates.format(new Date(item.createdAtEpochMillis)));
            ((ImageView) row.findViewById(R.id.imgReportType)).setImageResource(
                    typeIcon(item.typeCode));

            StatusDisplay status = displayStatus(item.statusCode);
            TextView statusText = row.findViewById(R.id.tvReportStatus);
            statusText.setText(status.label);
            statusText.setTextColor(getColor(status.color));
            ImageView statusIcon = row.findViewById(R.id.imgReportStatus);
            statusIcon.setImageResource(status.icon);
            statusIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    getColor(status.color)));

            row.setContentDescription(getString(R.string.report_open_accessibility,
                    getString(R.string.report_list_name, type, item.localReportId),
                    status.label));
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, SubmittedReportOverviewActivity.class);
                intent.putExtra(SubmittedReportOverviewActivity.EXTRA_SOURCE, item.source);
                intent.putExtra(SubmittedReportOverviewActivity.EXTRA_LOCAL_ID,
                        item.localReportId);
                intent.putExtra(SubmittedReportOverviewActivity.EXTRA_SERVER_ID,
                        item.serverReportId);
                startActivity(intent);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            reportsContainer.addView(row, params);
        }
    }

    private boolean matchesPresetFilters(ReportListItem item) {
        if (selectedType != 0) {
            String type = selectedType == 1 ? ReportValues.HAZARD
                    : selectedType == 2 ? ReportValues.INCIDENT : ReportValues.NEAR_MISS;
            if (!type.equals(item.typeCode)) return false;
        }
        if (selectedStatus == 0) return true;
        String status = item.statusCode == null ? "" : item.statusCode.trim()
                .toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (selectedStatus) {
            case 1:
                return ReportValues.PENDING_UPLOAD.equals(status)
                        || ReportValues.REPORT_UPLOADED.equals(status);
            case 2:
                return ReportValues.FAILED.equals(status)
                        || ReportValues.UPLOAD_PAUSED.equals(status);
            case 3:
                return "PENDING_REVIEW".equals(status)
                        || "AWAITING_REVIEW".equals(status)
                        || "SUBMITTED".equals(status);
            case 4:
                return "APPROVED".equals(status);
            case 5:
                return "DENIED".equals(status) || "REJECTED".equals(status);
            default:
                return true;
        }
    }

    private boolean isPinned(ReportListItem item) {
        String status = item.statusCode == null ? "" : item.statusCode.toUpperCase(Locale.ROOT);
        return ReportValues.PENDING_UPLOAD.equals(status)
                || ReportValues.REPORT_UPLOADED.equals(status)
                || ReportValues.FAILED.equals(status)
                || ReportValues.UPLOAD_PAUSED.equals(status)
                || "PENDING_REVIEW".equals(status)
                || "AWAITING_REVIEW".equals(status);
    }

    private void addHeading(int label) {
        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextSize(20);
        heading.setTextColor(getColor(R.color.safety_text));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(14), 0, dp(10));
        reportsContainer.addView(heading);
    }

    private String typeLabel(String code) {
        if (ReportValues.HAZARD.equals(code)) return getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equals(code)) return getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equals(code)) return getString(R.string.report_type_near_miss);
        return getString(R.string.report_type_not_selected);
    }

    private int typeIcon(String code) {
        if (ReportValues.HAZARD.equals(code)) return R.drawable.ic_ms_warning;
        if (ReportValues.INCIDENT.equals(code)) return R.drawable.ic_ms_personal_injury;
        if (ReportValues.NEAR_MISS.equals(code)) return R.drawable.ic_ms_visibility;
        return R.drawable.ic_ms_description;
    }

    private StatusDisplay displayStatus(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case ReportValues.PENDING_UPLOAD:
                return new StatusDisplay(getString(R.string.status_pending_upload),
                        R.drawable.ic_ms_cloud_upload, R.color.safety_status);
            case ReportValues.REPORT_UPLOADED:
                return new StatusDisplay(getString(R.string.status_finishing_upload),
                        R.drawable.ic_ms_cloud_upload, R.color.safety_status);
            case ReportValues.FAILED:
                return new StatusDisplay(getString(R.string.status_upload_failed),
                        R.drawable.ic_ms_cancel, R.color.safety_danger);
            case ReportValues.UPLOAD_PAUSED:
                return new StatusDisplay(getString(R.string.status_upload_paused),
                        R.drawable.ic_ms_cancel, R.color.safety_danger);
            case "PENDING_REVIEW":
            case "AWAITING_REVIEW":
            case "SUBMITTED":
                return new StatusDisplay(getString(R.string.status_pending_review),
                        R.drawable.ic_ms_schedule, R.color.safety_status);
            case "APPROVED":
                return new StatusDisplay(getString(R.string.status_approved),
                        R.drawable.ic_ms_check_circle, R.color.safety_success);
            case "DENIED":
            case "REJECTED":
                return new StatusDisplay(getString(R.string.status_denied),
                        R.drawable.ic_ms_cancel, R.color.safety_danger);
            default:
                return new StatusDisplay(normalized.isEmpty()
                        ? getString(R.string.status_unavailable)
                        : normalized.replace('_', ' ').toLowerCase(Locale.ROOT),
                        R.drawable.ic_ms_schedule, R.color.safety_text_secondary);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class StatusDisplay {
        final String label;
        final int icon;
        final int color;

        StatusDisplay(String label, int icon, int color) {
            this.label = label;
            this.icon = icon;
            this.color = color;
        }
    }
}
