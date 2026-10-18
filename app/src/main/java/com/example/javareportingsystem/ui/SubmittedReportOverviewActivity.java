package com.example.javareportingsystem.ui;

import android.os.Bundle;
import android.view.View;
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
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;
import com.example.javareportingsystem.data.local.entity.SyncedAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only summary for a report selected in My Reports. */
public class SubmittedReportOverviewActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE = "report_source";
    public static final String EXTRA_LOCAL_ID = "report_local_id";
    public static final String EXTRA_SERVER_ID = "report_server_id";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_submitted_report_overview);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        applyWindowInsets();
        String source = getIntent().getStringExtra(EXTRA_SOURCE);
        long localId = getIntent().getLongExtra(EXTRA_LOCAL_ID, -1);
        String serverId = getIntent().getStringExtra(EXTRA_SERVER_ID);
        databaseExecutor.execute(() -> loadReport(source, localId, serverId));
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + dp(20), bars.top + dp(20),
                    bars.right + dp(20), bars.bottom + dp(20));
            return insets;
        });
    }

    private void loadReport(String source, long localId, String serverId) {
        try {
            LocalReportRepository reports = new LocalReportRepository(this);
            ReportSnapshot snapshot;
            if ("OFFLINE".equals(source) && localId > 0) {
                OfflineReportEntity report = reports.getOfflineReport(localId);
                if (report == null || ReportValues.DRAFT.equals(report.syncState)) {
                    showUnavailable();
                    return;
                }
                LocationEntity location = report.locationLocalId == null
                        ? null : reports.getLocation(report.locationLocalId);
                List<OfflineAttachmentEntity> attachments = reports.getOfflineAttachments(localId);
                int voiceCount = 0;
                int photoCount = 0;
                for (OfflineAttachmentEntity attachment : attachments) {
                    if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType)) voiceCount++;
                    if (ReportValues.PHOTO.equals(attachment.attachmentType)) photoCount++;
                }
                snapshot = new ReportSnapshot(localId, report.typeCode, report.syncState,
                        location == null ? null : location.name, report.description,
                        report.createdAtEpochMillis, Math.max(photoCount, imageCount(localId)),
                        voiceCount);
            } else if ("SYNCED".equals(source) && serverId != null
                    && !serverId.isEmpty()) {
                SyncedReportEntity report = reports.getSyncedReport(serverId);
                if (report == null) {
                    showUnavailable();
                    return;
                }
                List<SyncedAttachmentEntity> attachments = reports.getSyncedAttachments(serverId);
                int voiceCount = 0;
                int photoCount = 0;
                for (SyncedAttachmentEntity attachment : attachments) {
                    if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType)) voiceCount++;
                    if (ReportValues.PHOTO.equals(attachment.attachmentType)) photoCount++;
                }
                snapshot = new ReportSnapshot(report.sourceLocalReportId, report.typeCode,
                        report.workflowStatus, report.locationName, report.description,
                        report.createdAtEpochMillis,
                        Math.max(photoCount, imageCount(report.sourceLocalReportId)),
                        voiceCount);
            } else {
                showUnavailable();
                return;
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) render(snapshot);
            });
        } catch (RuntimeException error) {
            showUnavailable();
        }
    }

    private int imageCount(long localId) {
        if (localId <= 0) return 0;
        try (ImageDatabaseHelper images = new ImageDatabaseHelper(this, localId)) {
            return images.getImageCount();
        }
    }

    private void render(ReportSnapshot report) {
        String type = typeLabel(report.typeCode);
        ((TextView) findViewById(R.id.tvReportTitle)).setText(
                getString(R.string.report_list_name, type, report.localId));
        ((TextView) findViewById(R.id.tvReportStatus)).setText(statusLabel(report.statusCode));
        ((TextView) findViewById(R.id.tvReportType)).setText(type);
        ((TextView) findViewById(R.id.tvReportLocation)).setText(
                hasText(report.location) ? report.location : getString(R.string.report_value_unavailable));
        ((TextView) findViewById(R.id.tvReportDate)).setText(
                report.createdAt > 0
                        ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(new Date(report.createdAt))
                        : getString(R.string.report_value_unavailable));
        ((TextView) findViewById(R.id.tvReportDescription)).setText(
                hasText(report.description) ? report.description
                        : getString(R.string.report_description_empty));
        ((TextView) findViewById(R.id.tvReportImages)).setText(
                getResources().getQuantityString(R.plurals.report_image_count,
                        report.imageCount, report.imageCount));
        ((TextView) findViewById(R.id.tvReportVoiceNotes)).setText(
                getResources().getQuantityString(R.plurals.report_voice_count,
                        report.voiceCount, report.voiceCount));
        findViewById(R.id.reportContent).setVisibility(View.VISIBLE);
        findViewById(R.id.reportLoading).setVisibility(View.GONE);
    }

    private String typeLabel(String code) {
        if (ReportValues.HAZARD.equals(code)) return getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equals(code)) return getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equals(code)) return getString(R.string.report_type_near_miss);
        return getString(R.string.report_value_unavailable);
    }

    private String statusLabel(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case ReportValues.PENDING_UPLOAD: return getString(R.string.status_pending_upload);
            case ReportValues.REPORT_UPLOADED: return getString(R.string.status_finishing_upload);
            case ReportValues.FAILED: return getString(R.string.status_upload_failed);
            case "PENDING_REVIEW":
            case "AWAITING_REVIEW":
            case "SUBMITTED": return getString(R.string.status_pending_review);
            case "APPROVED": return getString(R.string.status_approved);
            case "DENIED":
            case "REJECTED": return getString(R.string.status_denied);
            default: return normalized.isEmpty() ? getString(R.string.status_unavailable)
                    : normalized.replace('_', ' ').toLowerCase(Locale.ROOT);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void showUnavailable() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, R.string.report_unavailable, Toast.LENGTH_SHORT).show();
            finish();
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

    private static final class ReportSnapshot {
        final long localId;
        final String typeCode;
        final String statusCode;
        final String location;
        final String description;
        final long createdAt;
        final int imageCount;
        final int voiceCount;

        ReportSnapshot(long localId, String typeCode, String statusCode, String location,
                String description, long createdAt, int imageCount, int voiceCount) {
            this.localId = localId;
            this.typeCode = typeCode;
            this.statusCode = statusCode;
            this.location = location;
            this.description = description;
            this.createdAt = createdAt;
            this.imageCount = imageCount;
            this.voiceCount = voiceCount;
        }
    }
}
