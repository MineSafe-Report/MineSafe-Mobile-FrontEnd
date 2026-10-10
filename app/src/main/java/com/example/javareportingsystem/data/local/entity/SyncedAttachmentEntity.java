package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "synced_attachments",
        foreignKeys = @ForeignKey(
                entity = SyncedReportEntity.class,
                parentColumns = "serverReportId",
                childColumns = "serverReportId",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index("serverReportId"),
                @Index(value = "sourceLocalAttachmentId", unique = true)
        })
public class SyncedAttachmentEntity {
    @PrimaryKey
    @NonNull
    public String serverAttachmentId = "";

    @NonNull
    public String serverReportId = "";

    public long sourceLocalAttachmentId;

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
    public long uploadedAtEpochMillis;
}
