package com.example.javareportingsystem.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.AppDatabase;
import com.example.javareportingsystem.data.local.ReportListItem;
import com.example.javareportingsystem.data.local.ReportValues;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyReportsActivity extends AppCompatActivity {
    private LinearLayout reportsContainer;
    private TextView emptyNotice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_reports);
        reportsContainer = findViewById(R.id.reportsContainer);
        emptyNotice = findViewById(R.id.tvNoReports);
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
        reportsContainer.removeAllViews();
        boolean empty = items == null || items.isEmpty();
        emptyNotice.setVisibility(empty ? View.VISIBLE : View.GONE);
        reportsContainer.addView(emptyNotice);
        if (empty) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        for (ReportListItem item : items) {
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

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(12);
            reportsContainer.addView(row, params);
        }
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
