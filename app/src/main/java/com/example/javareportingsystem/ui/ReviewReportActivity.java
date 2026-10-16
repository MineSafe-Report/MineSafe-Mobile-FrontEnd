package com.example.javareportingsystem.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.ApiLoginDataSource;
import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.SyncedAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;
import com.example.javareportingsystem.data.model.LoggedInUser;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only report details for supervisors; review decisions await a server endpoint. */
public class ReviewReportActivity extends AppCompatActivity {
    public static final String EXTRA_SERVER_ID = "review_server_report_id";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private String serverReportId;
    private TextView reportTitle;
    private TextView reportStatus;
    private TextView reportType;
    private TextView reportLocation;
    private TextView reportSubmitted;
    private TextView reportDescription;
    private TextView reportImages;
    private TextView reportVoiceNotes;
    private Button approveButton;
    private Button denyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_review_report);
        applyWindowInsets();

        if (!hasSupervisorSession()) {
            Toast.makeText(this, R.string.supervisor_sign_in_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        serverReportId = getIntent().getStringExtra(EXTRA_SERVER_ID);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        approveButton = findViewById(R.id.btnApprove);
        denyButton = findViewById(R.id.btnDeny);
        approveButton.setOnClickListener(v -> showApprovalNotice());
        denyButton.setOnClickListener(v -> showDenialReason());

        reportTitle = findViewById(R.id.tvReportTitle);
        reportStatus = findViewById(R.id.tvReportStatus);
        reportType = findViewById(R.id.tvReportType);
        reportLocation = findViewById(R.id.tvReportLocation);
        reportSubmitted = findViewById(R.id.tvReportSubmitted);
        reportDescription = findViewById(R.id.tvReportDescription);
        reportImages = findViewById(R.id.tvReportImages);
        reportVoiceNotes = findViewById(R.id.tvReportVoiceNotes);

        if (serverReportId == null || serverReportId.trim().isEmpty()) {
            showUnavailable();
            return;
        }
        databaseExecutor.execute(this::loadReport);
    }

    private boolean hasSupervisorSession() {
        LoginRepository login = LoginRepository.getExistingInstance();
        if (login == null) {
            login = LoginRepository.getInstance(this,
                    new ApiLoginDataSource(getString(R.string.api_base_url)));
        }
        LoggedInUser user = login.getUser();
        return user != null && "SUPERVISOR".equalsIgnoreCase(user.getRole());
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(20) + bars.left, dp(12) + bars.top,
                    dp(20) + bars.right, dp(12) + bars.bottom);
            return insets;
        });
    }

    private void loadReport() {
        try {
            LocalReportRepository repository = new LocalReportRepository(this);
            SyncedReportEntity report = repository.getSyncedReport(serverReportId);
            if (report == null) {
                showUnavailable();
                return;
            }
            List<SyncedAttachmentEntity> attachments =
                    repository.getSyncedAttachments(serverReportId);
            int imageCount = 0;
            int voiceCount = 0;
            for (SyncedAttachmentEntity attachment : attachments) {
                if (ReportValues.PHOTO.equals(attachment.attachmentType)) imageCount++;
                if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType)) voiceCount++;
            }
            if (report.sourceLocalReportId > 0) {
                try (ImageDatabaseHelper images =
                             new ImageDatabaseHelper(this, report.sourceLocalReportId)) {
                    imageCount = Math.max(imageCount, images.getImageCount());
                }
            }
            ReportSnapshot snapshot = new ReportSnapshot(report, imageCount, voiceCount);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) render(snapshot);
            });
        } catch (RuntimeException error) {
            showUnavailable();
        }
    }

    private void render(ReportSnapshot snapshot) {
        SyncedReportEntity report = snapshot.report;
        String type = typeLabel(report.typeCode);
        reportTitle.setText(report.referenceNumber == null || report.referenceNumber.trim().isEmpty()
                ? getString(R.string.report_list_name, type, report.sourceLocalReportId)
                : report.referenceNumber.trim());
        reportStatus.setText(statusLabel(report.workflowStatus));
        reportType.setText(type);
        reportLocation.setText(hasText(report.locationName)
                ? report.locationName : getString(R.string.report_value_unavailable));
        long timestamp = report.submittedAtEpochMillis > 0
                ? report.submittedAtEpochMillis : report.createdAtEpochMillis;
        reportSubmitted.setText(timestamp > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(timestamp))
                : getString(R.string.report_value_unavailable));
        reportDescription.setText(hasText(report.description)
                ? report.description : getString(R.string.report_description_empty));
        reportImages.setText(getResources().getQuantityString(R.plurals.report_image_count,
                snapshot.imageCount, snapshot.imageCount));
        reportVoiceNotes.setText(getResources().getQuantityString(R.plurals.report_voice_count,
                snapshot.voiceCount, snapshot.voiceCount));

        boolean pending = isPendingReview(report.workflowStatus);
        approveButton.setEnabled(pending);
        denyButton.setEnabled(pending);
        approveButton.setAlpha(pending ? 1f : 0.55f);
        denyButton.setAlpha(pending ? 1f : 0.55f);
        findViewById(R.id.reviewContent).setVisibility(View.VISIBLE);
        findViewById(R.id.reviewLoading).setVisibility(View.GONE);
    }

    private void showApprovalNotice() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_approve_title)
                .setMessage(R.string.review_approve_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showDenialReason() {
        EditText reasonInput = new EditText(this);
        reasonInput.setHint(R.string.review_deny_reason_hint);
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonInput.setMinLines(3);
        int horizontal = dp(24);
        reasonInput.setPadding(horizontal, dp(14), horizontal, dp(14));
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_deny_title)
                .setView(reasonInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_label, (dialog, which) -> {
                    if (reasonInput.getText().toString().trim().isEmpty()) {
                        Toast.makeText(this, R.string.review_deny_reason_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showDecisionNotSent();
                })
                .show();
    }

    private void showDecisionNotSent() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_action_not_connected_title)
                .setMessage(R.string.review_action_not_connected_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showUnavailable() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, R.string.review_report_unavailable, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private boolean isPendingReview(String status) {
        String normalized = normalizeStatus(status);
        return "PENDING_REVIEW".equals(normalized)
                || "AWAITING_REVIEW".equals(normalized)
                || "SUBMITTED".equals(normalized);
    }

    private String statusLabel(String status) {
        String normalized = normalizeStatus(status);
        if ("PENDING_REVIEW".equals(normalized)
                || "AWAITING_REVIEW".equals(normalized)
                || "SUBMITTED".equals(normalized)) return getString(R.string.status_pending_review);
        if ("APPROVED".equals(normalized)) return getString(R.string.status_approved);
        if ("DENIED".equals(normalized) || "REJECTED".equals(normalized)) {
            return getString(R.string.status_denied);
        }
        return normalized.isEmpty() ? getString(R.string.status_unavailable)
                : normalized.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
    }

    private String typeLabel(String code) {
        if (ReportValues.HAZARD.equals(code)) return getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equals(code)) return getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equals(code)) return getString(R.string.report_type_near_miss);
        return getString(R.string.report_type_not_selected);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
        final SyncedReportEntity report;
        final int imageCount;
        final int voiceCount;

        ReportSnapshot(SyncedReportEntity report, int imageCount, int voiceCount) {
            this.report = report;
            this.imageCount = imageCount;
            this.voiceCount = voiceCount;
        }
    }
}