package com.example.javareportingsystem.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.DeviceConfigEntity;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;
import com.example.javareportingsystem.data.model.LoggedInUser;
import com.example.javareportingsystem.ui.ImageDatabaseHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Durable local-first upload and status refresh, resumed when Android sees a network. */
public final class ReportSyncWorker extends Worker {
    private static final String UNIQUE_NAME = "report-upload-and-status";

    public ReportSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context) {
        enqueue(context, ExistingWorkPolicy.KEEP);
    }

    /** A new submission or sign-in should start now, even if an older retry is delayed. */
    public static void enqueueNow(Context context) {
        enqueue(context, ExistingWorkPolicy.REPLACE);
    }

    private static void enqueue(Context context, ExistingWorkPolicy policy) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReportSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_NAME, policy, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String baseUrl = context.getString(R.string.api_base_url);
        if (!ApiConnection.isReachable(baseUrl)) return Result.retry();
        LoginRepository login = LoginRepository.getInstance(context,
                new ApiLoginDataSource(baseUrl));
        LoggedInUser user = login.getFreshUser();
        if (user == null || "SAFETY_OFFICER".equalsIgnoreCase(user.getRole())) {
            return Result.success(); // Sign-in with a reporting role will enqueue again.
        }
        ApiReportingDataSource api = new ApiReportingDataSource(baseUrl, user.getAccessToken());
        LocalReportRepository reports = new LocalReportRepository(context);
        try {
            DeviceConfigEntity device = reports.getOrCreateDeviceConfig();
            String deviceId = api.registerDevice(device.installationUuid);
            if (!deviceId.equals(device.serverDeviceId)) reports.setServerDeviceId(deviceId);
            boolean retry = false;
            for (OfflineReportEntity report : reports.listReportsForSync()) {
                if (isStopped()) return Result.retry();
                try {
                    uploadReport(api, reports, report, deviceId, user);
                } catch (IOException | JSONException | RuntimeException error) {
                    retry |= reports.recordReportUploadFailure(report.localId,
                            error.getMessage() == null ? "Upload interrupted" : error.getMessage());
                }
            }
            refreshStatuses(api, reports, deviceId);
            return retry ? Result.retry() : Result.success();
        } catch (IOException | JSONException | RuntimeException error) {
            return Result.retry();
        }
    }

    private void uploadReport(ApiReportingDataSource api, LocalReportRepository reports,
            OfflineReportEntity report, String deviceId, LoggedInUser user)
            throws IOException, JSONException {
        LocationEntity location = report.locationLocalId == null ? null
                : reports.getLocation(report.locationLocalId);
        if (location == null) throw new IOException("Report location is missing.");
        List<OfflineAttachmentEntity> attachments = reports.getOfflineAttachments(report.localId);
        int voiceCount = 0;
        for (OfflineAttachmentEntity attachment : attachments) {
            if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType)) voiceCount++;
        }
        try (ImageDatabaseHelper images = new ImageDatabaseHelper(getApplicationContext(),
                report.localId)) {
            List<ImageDatabaseHelper.ImageRecord> photos = images.getAllImages();
            String reportId = report.serverReportId;
            if (reportId == null) {
                JSONObject body = new JSONObject()
                        .put("deviceId", deviceId)
                        .put("localReportId", report.localId)
                        .put("typeCode", report.typeCode)
                        .put("locationCode", location.code)
                        .put("locationId", location.serverLocationId == null
                                ? JSONObject.NULL : location.serverLocationId)
                        .put("occurredAtEpochMillis", report.occurredAtEpochMillis)
                        .put("createdAtEpochMillis", report.createdAtEpochMillis)
                        .put("submittedAtEpochMillis", report.submittedAtEpochMillis)
                        .put("description", report.description == null ? "" : report.description)
                        .put("expectedImageCount", photos.size())
                        .put("expectedVoiceNoteCount", voiceCount)
                        .put("anonymous", report.anonymous);
                if (!report.anonymous) body.put("reporterWorkerId", user.getWorkerId());
                JSONObject accepted = api.submitReport(body);
                reportId = accepted.getString("reportId");
                reports.markReportAccepted(report.localId, reportId,
                        accepted.optString("referenceNumber", reportId),
                        accepted.optLong("receivedAtEpochMillis", System.currentTimeMillis()));
            }
            for (OfflineAttachmentEntity attachment : attachments) {
                if (attachment.serverAttachmentId != null) continue;
                File file = new File(attachment.localFilePath);
                if (!file.isFile() || !file.canRead() || file.length() == 0
                        || file.length() > 25L * 1024 * 1024) {
                    throw new IOException("A voice note is missing, unreadable or too large. Replace it to continue uploading.");
                }
                JSONObject accepted = api.uploadAttachment(reportId,
                        "voice-" + attachment.localId, attachment.attachmentType,
                        attachment.captureSource, attachment.capturedAtEpochMillis,
                        attachment.mimeType, new FileInputStream(file), file.length());
                reports.markAttachmentAccepted(attachment.localId,
                        accepted.getString("attachmentId"), System.currentTimeMillis());
            }
            // The legacy image database has no upload receipt column. Retrying these stable
            // image IDs is safe because the server returns the original attachment ID.
            for (ImageDatabaseHelper.ImageRecord photo : photos) {
                api.uploadAttachment(reportId, "image-" + photo.id, ReportValues.PHOTO,
                        ReportValues.GALLERY, photo.createdAt, "image/jpeg",
                        new ByteArrayInputStream(photo.data), photo.data.length);
            }
            // The authoritative status timestamp comes from the API refresh below.
            reports.finishSync(report.localId, "PENDING_REVIEW", 0L);
        }
    }

    /** Immediate status fetch for My Reports; call only from a background thread. */
    public static void refreshStatusesNow(Context context) {
        String baseUrl = context.getString(R.string.api_base_url);
        if (!ApiConnection.isReachable(baseUrl)) return;
        LoginRepository login = LoginRepository.getInstance(context,
                new ApiLoginDataSource(baseUrl));
        LoggedInUser user = login.getFreshUser();
        if (user == null || "SAFETY_OFFICER".equalsIgnoreCase(user.getRole())) return;
        LocalReportRepository reports = new LocalReportRepository(context);
        ApiReportingDataSource api = new ApiReportingDataSource(baseUrl, user.getAccessToken());
        try {
            DeviceConfigEntity device = reports.getOrCreateDeviceConfig();
            String deviceId = api.registerDevice(device.installationUuid);
            if (!deviceId.equals(device.serverDeviceId)) reports.setServerDeviceId(deviceId);
            refreshStatuses(api, reports, deviceId);
        } catch (IOException | JSONException | RuntimeException ignored) {
            // Keep the last known statuses available while offline or on API failure.
        }
    }

    private static void refreshStatuses(ApiReportingDataSource api, LocalReportRepository reports,
            String deviceId) throws IOException, JSONException {
        JSONArray items = api.getMyReports(deviceId);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            try {
                reports.updateWorkflowStatus(item.getString("reportId"),
                        item.getString("status"),
                        item.optLong("statusUpdatedAtEpochMillis", System.currentTimeMillis()),
                        item.isNull("reviewReason") ? null
                                : item.optString("reviewReason", null));
            } catch (IllegalArgumentException ignored) {
                // The device may have an accepted report whose media has not finished syncing.
            }
        }
    }
}
