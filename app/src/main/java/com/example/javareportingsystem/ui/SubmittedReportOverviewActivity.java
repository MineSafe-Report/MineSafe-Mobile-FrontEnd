package com.example.javareportingsystem.ui;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.ReportSyncWorker;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;
import com.example.javareportingsystem.data.local.entity.SyncedAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//SUBMITTED REPORT OVERVIEW ACTIVITY
//Shows a submitted report and its attachments.

public class SubmittedReportOverviewActivity extends AppCompatActivity
{
    public static final String EXTRA_SOURCE = "report_source";
    public static final String EXTRA_LOCAL_ID = "report_local_id";
    public static final String EXTRA_SERVER_ID = "report_server_id";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private long reportNeedingVoice;
    private long voiceNeedingReplacement;
    private final ActivityResultLauncher<String> voicePicker = registerForActivityResult(
        new ActivityResultContracts.GetContent(), this::replaceVoiceNote);
    private MediaPlayer mediaPlayer;
    private AlertDialog voiceDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
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

    private void applyWindowInsets()
    {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + dp(20), bars.top + dp(20),
                bars.right + dp(20), bars.bottom + dp(20));
            return insets;
        });
    }

    //REPORT SOURCE
    //Read from the local report until upload is complete.

    private void loadReport(String source, long localId, String serverId)
    {
        try
        {
            LocalReportRepository reports = new LocalReportRepository(this);
            ReportSnapshot snapshot;
            if ("OFFLINE".equals(source) && localId > 0)
            {
                OfflineReportEntity report = reports.getOfflineReport(localId);
                if (report == null || ReportValues.DRAFT.equals(report.syncState))
                {
                    showUnavailable();
                    return;
                }
                LocationEntity location = report.locationLocalId == null
                    ? null : reports.getLocation(report.locationLocalId);
                List<OfflineAttachmentEntity> attachments = reports.getOfflineAttachments(localId);
                int voiceCount = 0;
                int photoCount = 0;
                long missingVoiceId = 0;
                List<String> voicePaths = new ArrayList<>();
                for (OfflineAttachmentEntity attachment : attachments)
                {
                    if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType))
                    {
                        voiceCount++;
                        voicePaths.add(attachment.localFilePath);
                        File voiceFile = new File(attachment.localFilePath);
                        if (attachment.serverAttachmentId == null
                            && (!voiceFile.isFile() || !voiceFile.canRead()
                            || voiceFile.length() == 0
                            || voiceFile.length() > 25L * 1024 * 1024)
                            && missingVoiceId == 0)
                        {
                            missingVoiceId = attachment.localId;
                        }
                    }
                    if (ReportValues.PHOTO.equals(attachment.attachmentType)) photoCount++;
                }
                List<Long> imageIds = imageIds(localId);
                snapshot = new ReportSnapshot(localId, report.typeCode, report.syncState,
                    location == null ? null : location.name, report.description,
                    report.createdAtEpochMillis, Math.max(photoCount, imageIds.size()),
                    voiceCount, null, report.lastUploadError,
                    missingVoiceId, imageIds, voicePaths);
            }
            else if ("SYNCED".equals(source) && serverId != null
                && !serverId.isEmpty())
            {
                ReportSyncWorker.refreshStatusesNow(this);
                SyncedReportEntity report = reports.getSyncedReport(serverId);
                if (report == null)
                {
                    showUnavailable();
                    return;
                }
                List<SyncedAttachmentEntity> attachments = reports.getSyncedAttachments(serverId);
                int voiceCount = 0;
                int photoCount = 0;
                List<String> voicePaths = new ArrayList<>();
                for (SyncedAttachmentEntity attachment : attachments)
                {
                    if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType))
                    {
                        voiceCount++;
                        voicePaths.add(attachment.localFilePath);
                    }
                    if (ReportValues.PHOTO.equals(attachment.attachmentType)) photoCount++;
                }
                List<Long> imageIds = imageIds(report.sourceLocalReportId);
                snapshot = new ReportSnapshot(report.sourceLocalReportId, report.typeCode,
                    report.workflowStatus, report.locationName, report.description,
                    report.createdAtEpochMillis,
                    Math.max(photoCount, imageIds.size()), voiceCount,
                    report.reviewReason, null, 0, imageIds, voicePaths);
            }
            else
            {
                showUnavailable();
                return;
            }
            runOnUiThread(() ->
            {
                if (!isFinishing() && !isDestroyed()) render(snapshot);
            });
        }
        catch (RuntimeException error)
        {
            showUnavailable();
        }
    }

    private List<Long> imageIds(long localId)
    {
        if (localId <= 0) return new ArrayList<>();
        try (ImageDatabaseHelper images = new ImageDatabaseHelper(this, localId))
        {
            return images.getImageIds();
        }
    }

    private void render(ReportSnapshot report)
    {
        String type = typeLabel(report.typeCode);
        ((TextView) findViewById(R.id.tvReportTitle)).setText(
            getString(R.string.report_list_name, type, report.localId));
        ((TextView) findViewById(R.id.tvReportStatus)).setText(statusLabel(report.statusCode));
        boolean uploadPaused = ReportValues.UPLOAD_PAUSED.equals(report.statusCode);
        findViewById(R.id.reportUploadProblemCard).setVisibility(
            uploadPaused ? View.VISIBLE : View.GONE);
        if (uploadPaused)
        {
            ((TextView) findViewById(R.id.tvUploadProblem)).setText(
                hasText(report.uploadError) ? report.uploadError
                    : getString(R.string.status_upload_failed));
            Button retry = findViewById(R.id.btnRetryUpload);
            Button replace = findViewById(R.id.btnReplaceVoice);
            retry.setVisibility(report.missingVoiceId == 0 ? View.VISIBLE : View.GONE);
            retry.setOnClickListener(v -> retryUpload(report.localId));
            replace.setVisibility(report.missingVoiceId != 0 ? View.VISIBLE : View.GONE);
            replace.setOnClickListener(v ->
            {
                reportNeedingVoice = report.localId;
                voiceNeedingReplacement = report.missingVoiceId;
                voicePicker.launch("audio/*");
            });
        }
        boolean denied = "DENIED".equalsIgnoreCase(report.statusCode)
            || "REJECTED".equalsIgnoreCase(report.statusCode);
        findViewById(R.id.reportReviewReasonCard).setVisibility(denied ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvReportReviewReason)).setText(
            hasText(report.reviewReason) ? report.reviewReason
                : getString(R.string.report_denial_reason_pending));
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
        renderAttachmentActions(report);
        findViewById(R.id.reportContent).setVisibility(View.VISIBLE);
        findViewById(R.id.reportLoading).setVisibility(View.GONE);
    }

    //ATTACHMENT ACTIONS
    //Let the user open each image or voice note.

    private void renderAttachmentActions(ReportSnapshot report)
    {
        LinearLayout actions = findViewById(R.id.reportAttachmentActions);
        actions.removeAllViews();
        for (int i = 0; i < report.imageIds.size(); i++)
        {
            long imageId = report.imageIds.get(i);
            Button button = attachmentButton(getString(R.string.review_view_image, i + 1),
                R.drawable.ic_ms_image);
            button.setOnClickListener(v -> openImage(report.localId, imageId));
            actions.addView(button, attachmentParams());
        }
        for (int i = 0; i < report.voicePaths.size(); i++)
        {
            String path = report.voicePaths.get(i);
            Button button = attachmentButton(getString(R.string.review_play_voice, i + 1),
                R.drawable.ic_ms_play_arrow);
            button.setOnClickListener(v -> playVoice(path));
            actions.addView(button, attachmentParams());
        }
    }

    private Button attachmentButton(String label, int icon)
    {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(getColor(R.color.safety_text));
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.outline_button);
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.safety_text)));
        button.setCompoundDrawablePadding(dp(12));
        button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private LinearLayout.LayoutParams attachmentParams()
    {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        params.topMargin = dp(8);
        return params;
    }

    private void openImage(long localId, long imageId)
    {
        databaseExecutor.execute(() ->
        {
            Bitmap bitmap = null;
            try (ImageDatabaseHelper images = new ImageDatabaseHelper(this, localId))
            {
                ImageDatabaseHelper.ImageRecord image = images.getImage(imageId);
                if (image != null)
                {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(image.data, 0, image.data.length, bounds);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    int width = getResources().getDisplayMetrics().widthPixels;
                    int height = getResources().getDisplayMetrics().heightPixels;
                    while (bounds.outWidth / options.inSampleSize > width * 2
                        || bounds.outHeight / options.inSampleSize > height * 2)
                    {
                        options.inSampleSize *= 2;
                    }
                    bitmap = BitmapFactory.decodeByteArray(
                        image.data, 0, image.data.length, options);
                }
            }
            catch (RuntimeException ignored)
            {
                //The saved image may be missing.
            }
            Bitmap result = bitmap;
            runOnUiThread(() ->
            {
                if (isFinishing() || isDestroyed())
                {
                    if (result != null) result.recycle();
                }
                else if (result == null)
                {
                    attachmentUnavailable();
                }
                else
                {
                    ImageView viewer = new ImageView(this);
                    viewer.setImageBitmap(result);
                    viewer.setAdjustViewBounds(true);
                    viewer.setMaxHeight(getResources().getDisplayMetrics().heightPixels * 3 / 4);
                    viewer.setContentDescription(getString(R.string.report_attachments_label));
                    new AlertDialog.Builder(this).setView(viewer)
                        .setPositiveButton(R.string.ok, null)
                        .setOnDismissListener(dialog ->
                        {
                            viewer.setImageDrawable(null);
                            result.recycle();
                        }).show();
                }
            });
        });
    }

    private void playVoice(String path)
    {
        File file = path == null ? null : new File(path);
        if (file == null || !file.isFile())
        {
            attachmentUnavailable();
            return;
        }
        releasePlayer();
        Button control = attachmentButton(getString(R.string.review_play_voice_action),
            R.drawable.ic_ms_play_arrow);
        control.setMinHeight(dp(56));
        voiceDialog = new AlertDialog.Builder(this)
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
            player.setOnCompletionListener(completed ->
                control.setText(R.string.review_play_voice_action));
            control.setOnClickListener(v ->
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
            voiceDialog.setOnDismissListener(dialog ->
            {
                releasePlayer();
                voiceDialog = null;
            });
            voiceDialog.show();
            player.prepareAsync();
        }
        catch (Exception error)
        {
            if (voiceDialog != null) voiceDialog.dismiss();
            releasePlayer();
            attachmentUnavailable();
        }
    }

    private void attachmentUnavailable()
    {
        Toast.makeText(this, R.string.report_attachment_unavailable, Toast.LENGTH_LONG).show();
    }

    private void releasePlayer()
    {
        if (mediaPlayer != null)
        {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private String typeLabel(String code)
    {
        if (ReportValues.HAZARD.equals(code)) return getString(R.string.report_type_hazard);
        if (ReportValues.INCIDENT.equals(code)) return getString(R.string.report_type_incident);
        if (ReportValues.NEAR_MISS.equals(code)) return getString(R.string.report_type_near_miss);
        return getString(R.string.report_value_unavailable);
    }

    private String statusLabel(String code)
    {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
        switch (normalized)
        {
            case ReportValues.PENDING_UPLOAD:
                return getString(R.string.status_pending_upload);
            case ReportValues.REPORT_UPLOADED:
                return getString(R.string.status_finishing_upload);
            case ReportValues.UPLOAD_PAUSED:
                return getString(R.string.status_upload_paused);
            case ReportValues.FAILED:
                return getString(R.string.status_upload_failed);
            case "PENDING_REVIEW":
            case "AWAITING_REVIEW":
            case "SUBMITTED":
                return getString(R.string.status_pending_review);
            case "APPROVED":
                return getString(R.string.status_approved);
            case "DENIED":
            case "REJECTED":
                return getString(R.string.status_denied);
            default:
                return normalized.isEmpty() ? getString(R.string.status_unavailable)
                    : normalized.replace('_', ' ').toLowerCase(Locale.ROOT);
        }
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    //UPLOAD RECOVERY
    //Restart a paused upload after the user retries or replaces a file.

    private void retryUpload(long localId)
    {
        findViewById(R.id.btnRetryUpload).setEnabled(false);
        databaseExecutor.execute(() ->
        {
            try
            {
                new LocalReportRepository(this).resumePausedUpload(localId);
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    ReportSyncWorker.enqueueNow(this);
                    Toast.makeText(this, R.string.report_upload_retry_started,
                        Toast.LENGTH_LONG).show();
                    finish();
                });
            }
            catch (RuntimeException error)
            {
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    findViewById(R.id.btnRetryUpload).setEnabled(true);
                    Toast.makeText(this, R.string.report_upload_retry_failed,
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void replaceVoiceNote(Uri uri)
    {
        if (uri == null || reportNeedingVoice <= 0 || voiceNeedingReplacement <= 0) return;
        String mimeType = getContentResolver().getType(uri);
        long localId = reportNeedingVoice;
        long attachmentId = voiceNeedingReplacement;
        databaseExecutor.execute(() ->
        {
            try
            {
                if (mimeType == null) throw new IOException("Audio type is missing");
                new LocalReportRepository(this).replacePendingVoiceNote(
                    localId, attachmentId, uri, mimeType);
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    ReportSyncWorker.enqueueNow(this);
                    Toast.makeText(this, R.string.report_upload_retry_started,
                        Toast.LENGTH_LONG).show();
                    finish();
                });
            }
            catch (IOException | RuntimeException error)
            {
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.report_voice_replace_failed,
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showUnavailable()
    {
        runOnUiThread(() ->
        {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, R.string.report_unavailable, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private int dp(int value)
    {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStop()
    {
        if (voiceDialog != null) voiceDialog.dismiss();
        releasePlayer();
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private static final class ReportSnapshot
    {
        final long localId;
        final String typeCode;
        final String statusCode;
        final String location;
        final String description;
        final long createdAt;
        final int imageCount;
        final int voiceCount;
        final String reviewReason;
        final String uploadError;
        final long missingVoiceId;
        final List<Long> imageIds;
        final List<String> voicePaths;

        ReportSnapshot(long localId, String typeCode, String statusCode, String location,
                       String description, long createdAt, int imageCount, int voiceCount,
                       String reviewReason, String uploadError, long missingVoiceId,
                       List<Long> imageIds, List<String> voicePaths)
        {
            this.localId = localId;
            this.typeCode = typeCode;
            this.statusCode = statusCode;
            this.location = location;
            this.description = description;
            this.createdAt = createdAt;
            this.imageCount = imageCount;
            this.voiceCount = voiceCount;
            this.reviewReason = reviewReason;
            this.uploadError = uploadError;
            this.missingVoiceId = missingVoiceId;
            this.imageIds = imageIds;
            this.voicePaths = voicePaths;
        }
    }
}
