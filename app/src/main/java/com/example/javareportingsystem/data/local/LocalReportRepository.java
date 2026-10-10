package com.example.javareportingsystem.data.local;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.example.javareportingsystem.data.local.dao.DeviceConfigDao;
import com.example.javareportingsystem.data.local.dao.LocationDao;
import com.example.javareportingsystem.data.local.dao.OfflineReportDao;
import com.example.javareportingsystem.data.local.dao.SyncedReportDao;
import com.example.javareportingsystem.data.local.entity.DeviceConfigEntity;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;
import com.example.javareportingsystem.data.local.entity.SyncedAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Local source of truth for report capture. Call write and direct read methods from a worker
 * thread. No network request is made by this class.
 */
public final class LocalReportRepository {
    private final AppDatabase database;
    private final DeviceConfigDao deviceDao;
    private final LocationDao locationDao;
    private final OfflineReportDao offlineDao;
    private final SyncedReportDao syncedDao;
    private final ReportMediaStore mediaStore;

    public LocalReportRepository(Context context) {
        this(context, AppDatabase.getInstance(context));
    }

    public LocalReportRepository(Context context, AppDatabase database) {
        this.database = database;
        this.deviceDao = database.deviceConfigDao();
        this.locationDao = database.locationDao();
        this.offlineDao = database.offlineReportDao();
        this.syncedDao = database.syncedReportDao();
        this.mediaStore = new ReportMediaStore(context);
    }

    @WorkerThread
    public DeviceConfigEntity getOrCreateDeviceConfig() {
        DeviceConfigEntity existing = deviceDao.get();
        if (existing != null) {
            return existing;
        }
        DeviceConfigEntity config = new DeviceConfigEntity();
        config.installationUuid = UUID.randomUUID().toString();
        config.installedAtEpochMillis = System.currentTimeMillis();
        deviceDao.insertIfAbsent(config);
        return deviceDao.get();
    }

    @WorkerThread
    public void setServerDeviceId(@NonNull String serverDeviceId) {
        if (serverDeviceId.trim().isEmpty()) {
            throw new IllegalArgumentException("Server device ID is required");
        }
        getOrCreateDeviceConfig();
        deviceDao.setServerDeviceId(serverDeviceId.trim());
    }

    @WorkerThread
    public long configureLocation(@NonNull String code, @NonNull String name,
            String serverLocationId, boolean active) {
        String cleanCode = code.trim();
        String cleanName = name.trim();
        if (cleanCode.isEmpty() || cleanName.isEmpty()) {
            throw new IllegalArgumentException("Location code and name are required");
        }
        LocationEntity location = locationDao.getByCode(cleanCode);
        if (location == null) {
            location = new LocationEntity();
            location.code = cleanCode;
        }
        location.name = cleanName;
        location.serverLocationId = emptyToNull(serverLocationId);
        location.active = active;
        location.configuredAtEpochMillis = System.currentTimeMillis();
        long id;
        if (location.localId == 0) {
            id = locationDao.insert(location);
        } else {
            locationDao.update(location);
            id = location.localId;
        }
        getOrCreateDeviceConfig();
        deviceDao.setLastLocationConfigAt(System.currentTimeMillis());
        return id;
    }

    @WorkerThread
    public List<LocationEntity> listActiveLocations() {
        return locationDao.listActive();
    }

    public LiveData<List<LocationEntity>> observeActiveLocations() {
        return locationDao.observeActive();
    }

    @WorkerThread
    public long createDraft(@NonNull String typeCode) {
        if (!ReportValues.isReportType(typeCode)) {
            throw new IllegalArgumentException("Unknown report type");
        }
        OfflineReportEntity report = new OfflineReportEntity();
        report.typeCode = typeCode;
        report.anonymous = ReportValues.isAnonymousType(typeCode);
        report.createdAtEpochMillis = System.currentTimeMillis();
        report.occurredAtEpochMillis = report.createdAtEpochMillis;
        report.syncState = ReportValues.DRAFT;
        return offlineDao.insertReport(report);
    }

    @WorkerThread
    public void saveDraftDetails(long localReportId, Long locationLocalId,
            String description, Long occurredAtEpochMillis) {
        OfflineReportEntity report = requireDraft(localReportId);
        if (locationLocalId != null && locationDao.get(locationLocalId) == null) {
            throw new IllegalArgumentException("Unknown location");
        }
        report.locationLocalId = locationLocalId;
        report.description = emptyToNull(description);
        report.occurredAtEpochMillis = occurredAtEpochMillis;
        offlineDao.updateReport(report);
    }

    /** Identified incidents can be attributed after an offline draft is created. */
    @WorkerThread
    public void identifyIncident(long localReportId, @NonNull String workerId,
            Integer accountId) {
        OfflineReportEntity report = requireReport(localReportId);
        if (!ReportValues.INCIDENT.equals(report.typeCode)
                || ReportValues.REPORT_UPLOADED.equals(report.syncState)) {
            throw new IllegalStateException("Only an unuploaded incident can be identified");
        }
        if (workerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Worker ID is required");
        }
        report.reporterWorkerId = workerId.trim();
        report.reporterAccountId = accountId;
        offlineDao.updateReport(report);
    }

    @WorkerThread
    public long addAttachmentFromUri(long localReportId, @NonNull String type,
            @NonNull String source, @NonNull Uri uri, @NonNull String mimeType)
            throws IOException {
        requireDraft(localReportId);
        ReportMediaStore.StoredMedia stored = mediaStore.copyFromUri(uri, mimeType);
        try {
            return addPrivateAttachment(localReportId, type, source, stored.file,
                    stored.mimeType);
        } catch (RuntimeException | IOException e) {
            mediaStore.deleteManagedFile(stored.file);
            throw e;
        }
    }

    /** Voice recorders may write directly into getMediaDirectory() and then attach the file. */
    @WorkerThread
    public File getMediaDirectory() throws IOException {
        return mediaStore.getDirectory();
    }

    @WorkerThread
    public long addPrivateAttachment(long localReportId, @NonNull String type,
            @NonNull String source, @NonNull File file, @NonNull String mimeType)
            throws IOException {
        requireDraft(localReportId);
        validateAttachment(type, source);
        if (!mediaStore.isManagedFile(file) || !file.isFile() || file.length() == 0) {
            throw new IllegalArgumentException("Attachment must be a nonempty app-private file");
        }
        if (mimeType.trim().isEmpty()) {
            throw new IllegalArgumentException("MIME type is required");
        }
        OfflineAttachmentEntity attachment = new OfflineAttachmentEntity();
        attachment.reportLocalId = localReportId;
        attachment.attachmentType = type;
        attachment.captureSource = source;
        attachment.localFilePath = file.getCanonicalPath();
        attachment.mimeType = mimeType.trim();
        attachment.fileSizeBytes = file.length();
        attachment.capturedAtEpochMillis = System.currentTimeMillis();
        attachment.syncState = ReportValues.ATTACHMENT_PENDING;
        return offlineDao.insertAttachment(attachment);
    }

    @WorkerThread
    public void removeDraftAttachment(long localAttachmentId) throws IOException {
        OfflineAttachmentEntity attachment = offlineDao.getAttachment(localAttachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Unknown attachment");
        }
        requireDraft(attachment.reportLocalId);
        offlineDao.deleteAttachment(attachment);
        mediaStore.deleteManagedFile(new File(attachment.localFilePath));
    }

    @WorkerThread
    public void submitDraft(long localReportId) {
        database.runInTransaction(() -> {
            OfflineReportEntity report = requireDraft(localReportId);
            if (report.locationLocalId == null
                    || locationDao.get(report.locationLocalId) == null) {
                throw new IllegalStateException("Select a configured location");
            }
            if (report.occurredAtEpochMillis == null) {
                throw new IllegalStateException("Event time is required");
            }
            boolean hasDescription = report.description != null
                    && !report.description.trim().isEmpty();
            if (!hasDescription && offlineDao.countVoiceNotes(localReportId) == 0) {
                throw new IllegalStateException("Add a description or voice note");
            }
            report.submittedAtEpochMillis = System.currentTimeMillis();
            report.syncState = ReportValues.PENDING_UPLOAD;
            offlineDao.updateReport(report);
        });
    }

    @WorkerThread
    public void deleteDraft(long localReportId) throws IOException {
        OfflineReportEntity report = requireDraft(localReportId);
        List<OfflineAttachmentEntity> attachments = offlineDao.listAttachments(localReportId);
        offlineDao.deleteReport(report);
        for (OfflineAttachmentEntity attachment : attachments) {
            mediaStore.deleteManagedFile(new File(attachment.localFilePath));
        }
    }

    @WorkerThread
    public OfflineReportEntity getOfflineReport(long localReportId) {
        return offlineDao.getReport(localReportId);
    }

    @WorkerThread
    public List<OfflineAttachmentEntity> getOfflineAttachments(long localReportId) {
        return offlineDao.listAttachments(localReportId);
    }

    public LiveData<List<OfflineReportEntity>> observeOfflineReports() {
        return offlineDao.observeReports();
    }

    public LiveData<List<ReportListItem>> observeMyReports() {
        return database.reportListDao().observeAll();
    }

    public LiveData<List<SyncedReportEntity>> observeSyncedReports() {
        return syncedDao.observeReports();
    }

    @WorkerThread
    public SyncedReportEntity getSyncedReport(String serverReportId) {
        return syncedDao.getReport(serverReportId);
    }

    @WorkerThread
    public List<SyncedAttachmentEntity> getSyncedAttachments(String serverReportId) {
        return syncedDao.listAttachments(serverReportId);
    }

    @WorkerThread
    public List<OfflineReportEntity> listReportsForSync() {
        return offlineDao.listSubmittedReports();
    }

    /** Called only after POST /api/reports has returned its stable server ID. */
    @WorkerThread
    public void markReportAccepted(long localReportId, @NonNull String serverReportId,
            String referenceNumber, long receivedAtEpochMillis) {
        OfflineReportEntity report = requireReport(localReportId);
        if (ReportValues.DRAFT.equals(report.syncState)) {
            throw new IllegalStateException("Draft has not been submitted");
        }
        if (serverReportId.trim().isEmpty()
                || (report.serverReportId != null
                && !report.serverReportId.equals(serverReportId))) {
            throw new IllegalArgumentException("Conflicting server report ID");
        }
        report.serverReportId = serverReportId;
        report.referenceNumber = referenceNumber;
        report.serverReceivedAtEpochMillis = receivedAtEpochMillis;
        report.syncState = ReportValues.REPORT_UPLOADED;
        report.lastUploadError = null;
        offlineDao.updateReport(report);
    }

    @WorkerThread
    public void markAttachmentAccepted(long localAttachmentId,
            @NonNull String serverAttachmentId, long uploadedAtEpochMillis) {
        OfflineAttachmentEntity attachment = offlineDao.getAttachment(localAttachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Unknown attachment");
        }
        OfflineReportEntity report = requireReport(attachment.reportLocalId);
        if (report.serverReportId == null || serverAttachmentId.trim().isEmpty()
                || (attachment.serverAttachmentId != null
                && !attachment.serverAttachmentId.equals(serverAttachmentId))) {
            throw new IllegalStateException("Report or attachment is not accepted");
        }
        attachment.serverAttachmentId = serverAttachmentId;
        attachment.uploadedAtEpochMillis = uploadedAtEpochMillis;
        attachment.syncState = ReportValues.ATTACHMENT_UPLOADED;
        attachment.lastUploadError = null;
        offlineDao.updateAttachment(attachment);
    }

    @WorkerThread
    public void recordReportUploadFailure(long localReportId, @NonNull String message) {
        OfflineReportEntity report = requireReport(localReportId);
        if (ReportValues.DRAFT.equals(report.syncState)) {
            throw new IllegalStateException("Draft has not been submitted");
        }
        report.uploadAttemptCount++;
        report.lastUploadError = message;
        if (report.serverReportId == null) {
            report.syncState = ReportValues.FAILED;
        }
        offlineDao.updateReport(report);
    }

    @WorkerThread
    public void recordAttachmentUploadFailure(long localAttachmentId,
            @NonNull String message) {
        OfflineAttachmentEntity attachment = offlineDao.getAttachment(localAttachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Unknown attachment");
        }
        if (requireReport(attachment.reportLocalId).serverReportId == null) {
            throw new IllegalStateException("Report is not uploaded yet");
        }
        attachment.uploadAttemptCount++;
        attachment.lastUploadError = message;
        offlineDao.updateAttachment(attachment);
    }

    /** Moves a fully acknowledged report and its media metadata in one SQLite transaction. */
    @WorkerThread
    public void finishSync(long localReportId, @NonNull String workflowStatus,
            long statusUpdatedAtEpochMillis) {
        if (workflowStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow status is required");
        }
        database.runInTransaction(() -> {
            OfflineReportEntity offline = requireReport(localReportId);
            if (offline.serverReportId == null || offline.submittedAtEpochMillis == null
                    || offline.occurredAtEpochMillis == null
                    || offline.serverReceivedAtEpochMillis == null
                    || offline.locationLocalId == null
                    || offlineDao.countUnuploadedAttachments(localReportId) != 0) {
                throw new IllegalStateException("Report or attachments are not fully uploaded");
            }
            LocationEntity location = locationDao.get(offline.locationLocalId);
            if (location == null) {
                throw new IllegalStateException("Report location is missing");
            }
            SyncedReportEntity synced = new SyncedReportEntity();
            synced.serverReportId = offline.serverReportId;
            synced.sourceLocalReportId = offline.localId;
            synced.referenceNumber = offline.referenceNumber;
            synced.typeCode = offline.typeCode;
            synced.locationCode = location.code;
            synced.locationName = location.name;
            synced.description = offline.description;
            synced.occurredAtEpochMillis = offline.occurredAtEpochMillis;
            synced.createdAtEpochMillis = offline.createdAtEpochMillis;
            synced.submittedAtEpochMillis = offline.submittedAtEpochMillis;
            synced.serverReceivedAtEpochMillis = offline.serverReceivedAtEpochMillis;
            synced.anonymous = offline.anonymous;
            synced.reporterWorkerId = offline.reporterWorkerId;
            synced.reporterAccountId = offline.reporterAccountId;
            synced.workflowStatus = workflowStatus.trim();
            synced.statusUpdatedAtEpochMillis = statusUpdatedAtEpochMillis;
            syncedDao.insertReport(synced);

            for (OfflineAttachmentEntity attachment
                    : offlineDao.listAttachments(localReportId)) {
                SyncedAttachmentEntity confirmed = new SyncedAttachmentEntity();
                confirmed.serverAttachmentId = attachment.serverAttachmentId;
                confirmed.serverReportId = offline.serverReportId;
                confirmed.sourceLocalAttachmentId = attachment.localId;
                confirmed.attachmentType = attachment.attachmentType;
                confirmed.captureSource = attachment.captureSource;
                confirmed.localFilePath = attachment.localFilePath;
                confirmed.mimeType = attachment.mimeType;
                confirmed.fileSizeBytes = attachment.fileSizeBytes;
                confirmed.capturedAtEpochMillis = attachment.capturedAtEpochMillis;
                confirmed.uploadedAtEpochMillis = attachment.uploadedAtEpochMillis == null
                        ? statusUpdatedAtEpochMillis : attachment.uploadedAtEpochMillis;
                syncedDao.insertAttachment(confirmed);
            }
            offlineDao.deleteReport(offline);
        });
    }

    @WorkerThread
    public void updateWorkflowStatus(@NonNull String serverReportId,
            @NonNull String status, long updatedAtEpochMillis) {
        SyncedReportEntity report = syncedDao.getReport(serverReportId);
        if (report == null) {
            throw new IllegalArgumentException("Unknown synced report");
        }
        if (updatedAtEpochMillis >= report.statusUpdatedAtEpochMillis) {
            report.workflowStatus = status;
            report.statusUpdatedAtEpochMillis = updatedAtEpochMillis;
            syncedDao.updateReport(report);
        }
    }

    private OfflineReportEntity requireReport(long localReportId) {
        OfflineReportEntity report = offlineDao.getReport(localReportId);
        if (report == null) {
            throw new IllegalArgumentException("Unknown local report");
        }
        return report;
    }

    private OfflineReportEntity requireDraft(long localReportId) {
        OfflineReportEntity report = requireReport(localReportId);
        if (!ReportValues.DRAFT.equals(report.syncState)) {
            throw new IllegalStateException("Report is already submitted");
        }
        return report;
    }

    private static void validateAttachment(String type, String source) {
        boolean photo = ReportValues.PHOTO.equals(type)
                && (ReportValues.CAMERA.equals(source) || ReportValues.GALLERY.equals(source));
        boolean voice = ReportValues.VOICE_NOTE.equals(type)
                && ReportValues.MICROPHONE.equals(source);
        if (!photo && !voice) {
            throw new IllegalArgumentException("Invalid attachment type or source");
        }
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
