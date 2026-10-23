package com.example.javareportingsystem.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.example.javareportingsystem.data.ApiConnection;
import com.example.javareportingsystem.data.ApiReportReviewDataSource;
import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.model.LoggedInUser;
import com.example.javareportingsystem.data.model.ReviewReport;
import com.example.javareportingsystem.data.model.ReviewAttachment;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//REVIEW REPORT ACTIVITY
//Shows report details and supervisor review actions.

public class ReviewReportActivity extends AppCompatActivity
{
    public static final String EXTRA_SERVER_ID = "review_server_report_id";

    private final ExecutorService apiExecutor = Executors.newSingleThreadExecutor();
    private String serverReportId;
    private LoginRepository loginRepository;
    private TextView reportTitle;
    private TextView reportStatus;
    private TextView reportType;
    private TextView reportLocation;
    private TextView reportSubmitted;
    private TextView reportDescription;
    private TextView reportImages;
    private TextView reportVoiceNotes;
    private TextView reportReviewReason;
    private LinearLayout attachmentContainer;
    private TextView evidencePending;
    private MediaPlayer mediaPlayer;
    private boolean currentEvidenceComplete;
    private Button approveButton;
    private Button denyButton;
    private Button dashboardButton;
    private TextView dashboardUnavailable;
    private boolean safetyOfficerMode;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_review_report);
        applyWindowInsets();

        loginRepository = LoginRepository.getInstance(this,
            new ApiLoginDataSource(getString(R.string.api_base_url)));
        LoggedInUser user = loginRepository.getCachedUser();
        safetyOfficerMode = user != null && "SAFETY_OFFICER".equalsIgnoreCase(user.getRole());
        if (user == null || (!safetyOfficerMode
            && !"SUPERVISOR".equalsIgnoreCase(user.getRole())))
        {
            Toast.makeText(this, R.string.supervisor_sign_in_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        serverReportId = getIntent().getStringExtra(EXTRA_SERVER_ID);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        approveButton = findViewById(R.id.btnApprove);
        denyButton = findViewById(R.id.btnDeny);
        dashboardButton = findViewById(R.id.btnOpenDashboard);
        dashboardUnavailable = findViewById(R.id.tvDashboardUnavailable);
        if (safetyOfficerMode)
        {
            findViewById(R.id.reviewDecisionActions).setVisibility(View.GONE);
            findViewById(R.id.soDashboardActions).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tvReviewHeading)).setText(R.string.so_incident_detail_title);
            dashboardButton.setEnabled(false);
        }
        else
        {
            setDecisionButtonsEnabled(false);
            approveButton.setOnClickListener(v -> confirmApproval());
            denyButton.setOnClickListener(v -> showDenialReason());
        }

        reportTitle = findViewById(R.id.tvReportTitle);
        reportStatus = findViewById(R.id.tvReportStatus);
        reportType = findViewById(R.id.tvReportType);
        reportLocation = findViewById(R.id.tvReportLocation);
        reportSubmitted = findViewById(R.id.tvReportSubmitted);
        reportDescription = findViewById(R.id.tvReportDescription);
        reportImages = findViewById(R.id.tvReportImages);
        reportVoiceNotes = findViewById(R.id.tvReportVoiceNotes);
        reportReviewReason = findViewById(R.id.tvReviewReason);
        attachmentContainer = findViewById(R.id.reviewAttachments);
        evidencePending = findViewById(R.id.tvEvidencePending);

        if (serverReportId == null || serverReportId.trim().isEmpty())
        {
            showUnavailable(getString(R.string.review_report_unavailable));
            return;
        }
        apiExecutor.execute(this::loadReport);
    }

    private ApiReportReviewDataSource freshApi() throws IOException
    {
        LoggedInUser user = loginRepository.getFreshUser();
        if (user == null || !(safetyOfficerMode
            ? "SAFETY_OFFICER".equalsIgnoreCase(user.getRole())
            : "SUPERVISOR".equalsIgnoreCase(user.getRole())))
        {
            throw new IOException("Sign in again to view this report.");
        }
        return new ApiReportReviewDataSource(
            getString(R.string.api_base_url), user.getAccessToken());
    }

    private void applyWindowInsets()
    {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) ->
        {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(20) + bars.left, dp(12) + bars.top,
                dp(20) + bars.right, dp(12) + bars.bottom);
            return insets;
        });
    }

    //REPORT DETAILS
    //Load the selected report from the server.

    private void loadReport()
    {
        try
        {
            ApiReportReviewDataSource api = freshApi();
            ReviewReport report = safetyOfficerMode
                ? api.getSafetyOfficerIncident(serverReportId)
                : api.getReport(serverReportId);
            runOnUiThread(() ->
            {
                if (!isFinishing() && !isDestroyed())
                {
                    render(report);
                    if (safetyOfficerMode)
                    {
                        LoggedInUser user = loginRepository.getCachedUser();
                        if (user != null)
                        {
                            SafetyOfficerIncidentState.markOpened(this,
                                user.getAccountId(), report.getReportId());
                        }
                    }
                }
            });
        }
        catch (IOException error)
        {
            showUnavailable(error.getMessage());
        }
    }

    private void render(ReviewReport report)
    {
        if (safetyOfficerMode && !ReportValues.INCIDENT.equalsIgnoreCase(report.getType()))
        {
            showUnavailable(getString(R.string.so_incident_unavailable));
            return;
        }
        String type = typeLabel(report.getType());
        reportTitle.setText(hasText(report.getReferenceNumber())
            ? report.getReferenceNumber().trim() : report.getReportId());
        reportStatus.setText(statusLabel(report.getStatus()));
        reportType.setText(type);
        reportLocation.setText(hasText(report.getLocationName())
            ? report.getLocationName() : getString(R.string.report_value_unavailable));
        long timestamp = report.getSubmittedAtEpochMillis();
        reportSubmitted.setText(timestamp > 0
            ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(new Date(timestamp))
            : getString(R.string.report_value_unavailable));
        reportDescription.setText(hasText(report.getDescription())
            ? report.getDescription() : getString(R.string.report_description_empty));
        reportImages.setText(getResources().getQuantityString(R.plurals.report_image_count,
            report.getImageCount(), report.getImageCount()));
        reportVoiceNotes.setText(getResources().getQuantityString(R.plurals.report_voice_count,
            report.getVoiceNoteCount(), report.getVoiceNoteCount()));
        attachmentContainer.removeAllViews();
        int imageNumber = 0;
        int voiceNumber = 0;
        for (ReviewAttachment attachment : report.getAttachments())
        {
            boolean photo = "PHOTO".equals(attachment.getType());
            int number = photo ? ++imageNumber : ++voiceNumber;
            Button open = new Button(this);
            open.setMinHeight(dp(56));
            open.setAllCaps(false);
            open.setBackgroundResource(R.drawable.outline_button);
            open.setText(photo ? getString(R.string.review_view_image, number)
                : getString(R.string.review_play_voice, number));
            open.setOnClickListener(view -> openAttachment(attachment, photo));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            params.topMargin = dp(8);
            attachmentContainer.addView(open, params);
        }
        evidencePending.setVisibility(report.isEvidenceComplete() ? View.GONE : View.VISIBLE);
        currentEvidenceComplete = report.isEvidenceComplete();
        boolean hasReviewReason = hasText(report.getReviewReason());
        reportReviewReason.setText(hasReviewReason
            ? getString(R.string.review_reason_value, report.getReviewReason()) : "");
        reportReviewReason.setVisibility(hasReviewReason ? View.VISIBLE : View.GONE);

        if (safetyOfficerMode)
        {
            String url = report.getDashboardUrl();
            Uri link = url == null ? null : Uri.parse(url.trim());
            boolean validLink = link != null && ("https".equalsIgnoreCase(link.getScheme())
                || "http".equalsIgnoreCase(link.getScheme()))
                && link.getHost() != null && !link.getHost().isEmpty();
            dashboardButton.setEnabled(validLink);
            dashboardButton.setAlpha(validLink ? 1f : 0.55f);
            dashboardUnavailable.setVisibility(validLink ? View.GONE : View.VISIBLE);
            dashboardButton.setOnClickListener(validLink ? view ->
            {
                try
                {
                    startActivity(new Intent(Intent.ACTION_VIEW, link));
                }
                catch (ActivityNotFoundException error)
                {
                    Toast.makeText(this, R.string.so_browser_unavailable, Toast.LENGTH_LONG).show();
                }
            } : null);
        }
        else
        {
            boolean pending = isPendingReview(report.getStatus()) && report.isEvidenceComplete();
            setDecisionButtonsEnabled(pending);
        }
        findViewById(R.id.reviewContent).setVisibility(View.VISIBLE);
        findViewById(R.id.reviewLoading).setVisibility(View.GONE);
    }

    //EVIDENCE VIEWING
    //Open images and voice notes from protected attachments.

    private void openAttachment(ReviewAttachment attachment, boolean photo)
    {
        apiExecutor.execute(() ->
        {
            try
            {
                if (!ApiConnection.isReachable(getString(R.string.api_base_url)))
                {
                    throw new IOException("Cannot reach the evidence service.");
                }
                File file = freshApi().downloadAttachment(
                    attachment.getId(), attachment.getMimeType(), getCacheDir());
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        file.delete();
                    }
                    else if (photo)
                    {
                        showImage(file);
                    }
                    else
                    {
                        playVoice(file);
                    }
                });
            }
            catch (IOException error)
            {
                runOnUiThread(() -> Toast.makeText(this,
                    R.string.review_evidence_unavailable, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showImage(File file)
    {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null)
        {
            file.delete();
            Toast.makeText(this, R.string.review_evidence_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(dp(520));
        image.setContentDescription(getString(R.string.report_attachments_label));
        new AlertDialog.Builder(this).setView(image).setPositiveButton(R.string.ok, null)
            .setOnDismissListener(dialog ->
            {
                image.setImageDrawable(null);
                bitmap.recycle();
                file.delete();
            }).show();
    }

    private void playVoice(File file)
    {
        releasePlayer();
        Button control = new Button(this);
        control.setMinHeight(dp(56));
        control.setText(R.string.review_play_voice_action);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.report_attachments_label)
            .setView(control).setPositiveButton(R.string.ok, null).create();
        try
        {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            MediaPlayer player = mediaPlayer;
            control.setEnabled(false);
            player.setOnPreparedListener(prepared ->
            {
                control.setEnabled(true);
                prepared.start();
                control.setText(R.string.review_pause_voice);
            });
            player.setOnCompletionListener(completed -> control.setText(R.string.review_play_voice_action));
            control.setOnClickListener(view ->
            {
                if (player.isPlaying())
                {
                    player.pause();
                    control.setText(R.string.review_play_voice_action);
                }
                else
                {
                    player.start();
                    control.setText(R.string.review_pause_voice);
                }
            });
            dialog.setOnDismissListener(ignored ->
            {
                releasePlayer();
                file.delete();
            });
            dialog.show();
            player.prepareAsync();
        }
        catch (IOException | RuntimeException error)
        {
            dialog.dismiss();
            releasePlayer();
            file.delete();
            Toast.makeText(this, R.string.review_evidence_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer()
    {
        if (mediaPlayer != null)
        {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    //REVIEW DECISION
    //Only a supervisor can approve or deny a report.

    private void confirmApproval()
    {
        new AlertDialog.Builder(this)
            .setTitle(R.string.review_approve_title)
            .setMessage(R.string.review_approve_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.review_approve_confirm,
                (dialog, which) -> submitDecision("APPROVE", null))
            .show();
    }

    private void showDenialReason()
    {
        EditText reasonInput = new EditText(this);
        reasonInput.setHint(R.string.review_deny_reason_hint);
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonInput.setMinLines(3);
        int horizontal = dp(24);
        reasonInput.setPadding(horizontal, dp(14), horizontal, dp(14));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.review_deny_title)
            .setView(reasonInput)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.continue_label, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view ->
            {
                if (reasonInput.getText().toString().trim().isEmpty())
                {
                    reasonInput.setError(getString(R.string.review_deny_reason_required));
                    return;
                }
                dialog.dismiss();
                submitDecision("DENY", reasonInput.getText().toString().trim());
            }));
        dialog.show();
    }

    private void submitDecision(String decision, String reason)
    {
        setDecisionButtonsEnabled(false);
        apiExecutor.execute(() ->
        {
            try
            {
                if (!ApiConnection.isReachable(getString(R.string.api_base_url)))
                {
                    throw new IOException("Connection is too weak to send a decision. Try again when connected.");
                }
                ReviewReport updated = freshApi().submitDecision(serverReportId, decision, reason);
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    render(updated);
                    Toast.makeText(this, decision.equals("APPROVE")
                            ? R.string.review_approved_saved : R.string.review_denied_saved,
                        Toast.LENGTH_LONG).show();
                });
            }
            catch (IOException error)
            {
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    setDecisionButtonsEnabled(currentEvidenceComplete && isPendingReviewFromCurrentView());
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.review_request_failed_title)
                        .setMessage(error.getMessage() == null
                            ? getString(R.string.review_request_failed_message)
                            : error.getMessage())
                        .setPositiveButton(R.string.ok, (dialog, which) ->
                        {
                            if (error instanceof ApiReportReviewDataSource.ApiException
                                && ((ApiReportReviewDataSource.ApiException) error)
                                .getStatusCode() == 409)
                            {
                                apiExecutor.execute(this::loadReport);
                            }
                        })
                        .show();
                });
            }
        });
    }

    private boolean isPendingReviewFromCurrentView()
    {
        return reportStatus.getText().toString().equals(getString(R.string.status_pending_review));
    }

    private void setDecisionButtonsEnabled(boolean enabled)
    {
        approveButton.setEnabled(enabled);
        denyButton.setEnabled(enabled);
        approveButton.setAlpha(enabled ? 1f : 0.55f);
        denyButton.setAlpha(enabled ? 1f : 0.55f);
    }

    private void showUnavailable(String detail)
    {
        runOnUiThread(() ->
        {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, hasText(detail) ? detail : getString(R.string.review_report_unavailable),
                Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private boolean isPendingReview(String status)
    {
        String normalized = normalizeStatus(status);
        return "PENDING_REVIEW".equals(normalized)
            || "AWAITING_REVIEW".equals(normalized)
            || "SUBMITTED".equals(normalized);
    }

    private String statusLabel(String status)
    {
        String normalized = normalizeStatus(status);
        if ("PENDING_REVIEW".equals(normalized)
            || "AWAITING_REVIEW".equals(normalized)
            || "SUBMITTED".equals(normalized)) return getString(R.string.status_pending_review);
        if ("APPROVED".equals(normalized)) return getString(R.string.status_approved);
        if ("DENIED".equals(normalized) || "REJECTED".equals(normalized))
        {
            return getString(R.string.status_denied);
        }
        return normalized.isEmpty() ? getString(R.string.status_unavailable)
            : normalized.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status)
    {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
    }

    private String typeLabel(String code)
    {
        if (ReportValues.HAZARD.equalsIgnoreCase(code))
            return getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equalsIgnoreCase(code))
            return getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equalsIgnoreCase(code))
            return getString(R.string.report_type_near_miss);
        return getString(R.string.report_type_not_selected);
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private int dp(int value)
    {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy()
    {
        releasePlayer();
        apiExecutor.shutdown();
        super.onDestroy();
    }
}
