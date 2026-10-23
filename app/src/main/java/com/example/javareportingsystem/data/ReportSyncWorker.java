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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//REPORT SYNC WORKER
//Uploads reports and updates their status when online

public final class ReportSyncWorker extends Worker
{
    private static final String UNIQUE_NAME = "report-upload-and-status";

    public ReportSyncWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    //WORK SCHEDULING
    //Retry when online, or start now after a new report
    public static void enqueue(Context context)
    {
        enqueue(context, ExistingWorkPolicy.KEEP);
    }

    //Starts upload now, even if a retrry is scheduled
    public static void enqueueNow(Context context)
    {
        enqueue(context, ExistingWorkPolicy.REPLACE);
    }

    private static void enqueue(Context context, ExistingWorkPolicy policy)
    {
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
    public Result doWork()
    {
        Context context = getApplicationContext();
        String baseUrl = ApiServerConfig.getBaseUrl(context);
        if (!ApiConnection.isReachable(baseUrl)) return Result.retry();
        LoginRepository login = LoginRepository.getInstance(context,
            new ApiLoginDataSource(baseUrl));
        LoggedInUser user = login.getFreshUser();
        // Only signed-in reporting users can upload reports.
        if (user == null || "SAFETY_OFFICER".equalsIgnoreCase(user.getRole()))
        {
            return Result.success();
        }
        ApiReportingDataSource api = new ApiReportingDataSource(baseUrl, user.getAccessToken());
        LocalReportRepository reports = new LocalReportRepository(context);
        try
        {
            DeviceConfigEntity device = reports.getOrCreateDeviceConfig();
            boolean retry = false;
            for (OfflineReportEntity report : reports.listReportsForSync())
            {
                if (isStopped()) return Result.retry();
                try
                {
                    uploadReport(api, reports, report, device.installationUuid);
                }
                catch (IOException | JSONException | RuntimeException error)
                {
                    retry |= reports.recordReportUploadFailure(report.localId,
                        error.getMessage() == null ? "Upload interrupted" : error.getMessage());
                }
            }
            refreshStatuses(api, reports);
            return retry ? Result.retry() : Result.success();
        }
        catch (IOException | JSONException | RuntimeException error)
        {
            return Result.retry();
        }
    }

    //REPORT AND MEDIA UPLOAD
    //Send the report first, then its attachments
    private void uploadReport(ApiReportingDataSource api, LocalReportRepository reports,
                              OfflineReportEntity report, String installationId)
        throws IOException, JSONException
    {
        LocationEntity location = report.locationLocalId == null ? null
            : reports.getLocation(report.locationLocalId);
        if (location == null) throw new IOException("Report location is missing.");
        int locationId;
        try
        {
            locationId = Integer.parseInt(location.serverLocationId);
            if (locationId <= 0) throw new NumberFormatException();
        }
        catch (NumberFormatException error)
        {
            throw new IOException("The report location is not linked to a server location.", error);
        }
        List<OfflineAttachmentEntity> attachments = reports.getOfflineAttachments(report.localId);
        int voiceCount = 0;
        for (OfflineAttachmentEntity attachment : attachments)
        {
            if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType)) voiceCount++;
        }
        try (ImageDatabaseHelper images = new ImageDatabaseHelper(getApplicationContext(),
            report.localId))
        {
            List<ImageDatabaseHelper.ImageRecord> photos = images.getAllImages();
            String reportId = report.serverReportId;
            if (reportId == null)
            {
                String description = report.description == null ? "" : report.description.trim();
                if (description.length() < 3 && voiceCount > 0)
                {
                    description = "Voice note attached.";
                }
                if (description.length() < 3)
                {
                    throw new IOException("Add a description of at least 3 characters or a voice note.");
                }
                JSONObject body = new JSONObject()
                    .put("clientReportId", UUID.nameUUIDFromBytes(
                        (installationId + ":" + report.localId)
                            .getBytes(StandardCharsets.UTF_8)).toString())
                    .put("typeCode", report.typeCode)
                    .put("locationId", locationId)
                    .put("occurredAt", ApiDates.toIso(report.occurredAtEpochMillis))
                    .put("description", description)
                    .put("expectedImageCount", photos.size())
                    .put("expectedVoiceNoteCount", voiceCount);
                JSONObject accepted = api.submitReport(body);
                JSONObject serverReport = accepted.getJSONObject("report");
                reportId = serverReport.getString("reportId");
                reports.markReportAccepted(report.localId, reportId,
                    serverReport.optString("reportNumber", reportId),
                    ApiDates.parseIsoToMillis(serverReport.optString("submittedAt")));
            }
            for (OfflineAttachmentEntity attachment : attachments)
            {
                if (attachment.serverAttachmentId != null) continue;
                File file = new File(attachment.localFilePath);
                if (!file.isFile() || !file.canRead() || file.length() == 0
                    || file.length() > 25L * 1024 * 1024)
                {
                    throw new IOException("A voice note is missing, unreadable or too large. Replace it to continue uploading.");
                }
                JSONObject accepted = api.uploadAttachment(reportId,
                    "voice-" + attachment.localId, attachment.attachmentType,
                    attachment.capturedAtEpochMillis,
                    attachment.mimeType, new FileInputStream(file), file.length());
                reports.markAttachmentAccepted(attachment.localId,
                    accepted.getJSONObject("attachment").getString("attachmentId"),
                    System.currentTimeMillis());
            }
            //Reuse image IDs so retries do not make duplicates
            for (ImageDatabaseHelper.ImageRecord photo : photos)
            {
                api.uploadAttachment(reportId, "image-" + photo.id, ReportValues.PHOTO,
                    photo.createdAt, "image/jpeg",
                    new ByteArrayInputStream(photo.data), photo.data.length);
            }
            reports.finishSync(report.localId, "PENDING_REVIEW", 0L);
        }
    }

    //Updates My Reports status on a background thread
    public static void refreshStatusesNow(Context context)
    {
        String baseUrl = ApiServerConfig.getBaseUrl(context);
        if (!ApiConnection.isReachable(baseUrl)) return;
        LoginRepository login = LoginRepository.getInstance(context,
            new ApiLoginDataSource(baseUrl));
        LoggedInUser user = login.getFreshUser();
        if (user == null || "SAFETY_OFFICER".equalsIgnoreCase(user.getRole())) return;
        LocalReportRepository reports = new LocalReportRepository(context);
        ApiReportingDataSource api = new ApiReportingDataSource(baseUrl, user.getAccessToken());
        try
        {
            refreshStatuses(api, reports);
        }
        catch (IOException | JSONException | RuntimeException ignored)
        {
            //Keep saved statuses when the API is unavailable
        }
    }

    private static void refreshStatuses(ApiReportingDataSource api, LocalReportRepository reports)
        throws IOException, JSONException
    {
        JSONArray items = api.getMyReports();
        for (int i = 0; i < items.length(); i++)
        {
            JSONObject item = items.getJSONObject(i);
            try
            {
                reports.updateWorkflowStatus(item.getString("reportId"),
                    item.getString("statusCode"),
                    ApiDates.parseIsoToMillis(item.optString("statusUpdatedAt")),
                    item.isNull("reviewReason") ? null
                        : item.optString("reviewReason", null));
            }
            catch (IllegalArgumentException ignored)
            {
                //Some attachments may still need to upload
            }
        }
    }
}
