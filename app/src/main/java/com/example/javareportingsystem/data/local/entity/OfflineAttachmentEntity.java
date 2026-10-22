package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "offline_attachments",
    foreignKeys = @ForeignKey(
        entity = OfflineReportEntity.class,
        parentColumns = "localId",
        childColumns = "reportLocalId",
        onDelete = ForeignKey.CASCADE),
    indices = {
        @Index("reportLocalId"),
        @Index(value = "serverAttachmentId", unique = true)
    })
public class OfflineAttachmentEntity
{
    @PrimaryKey(autoGenerate = true)
    public long localId;

    public long reportLocalId;

    @NonNull
    public String attachmentType = "";

    @NonNull
    public String captureSource = "";

    @NonNull
    public String localFilePath = "";

    @NonNull
    public String mimeType = "";

    public long fileSizeBytes;
    public long capturedAtEpochMillis;
    public String serverAttachmentId;
    public Long uploadedAtEpochMillis;

    @NonNull
    public String syncState = "PENDING_UPLOAD";

    public int uploadAttemptCount;
    public String lastUploadError;
}
