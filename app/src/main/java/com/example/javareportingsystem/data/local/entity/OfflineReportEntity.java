package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "offline_reports",
        foreignKeys = @ForeignKey(
                entity = LocationEntity.class,
                parentColumns = "localId",
                childColumns = "locationLocalId",
                onDelete = ForeignKey.RESTRICT),
        indices = {
                @Index("locationLocalId"),
                @Index(value = "serverReportId", unique = true),
                @Index("syncState")
        })
public class OfflineReportEntity {
    @PrimaryKey(autoGenerate = true)
    public long localId;

    @NonNull
    public String typeCode = "";

    public Long locationLocalId;
    public String description;
    public Long occurredAtEpochMillis;
    public long createdAtEpochMillis;
    public Long submittedAtEpochMillis;

    // Hazard and near-miss reports do not store a reporter identity locally.
    public boolean anonymous;
    public String reporterWorkerId;
    public Integer reporterAccountId;

    @NonNull
    public String syncState = "DRAFT";

    // Set only after the server accepts the report; attachments may still be pending.
    public String serverReportId;
    public String referenceNumber;
    public Long serverReceivedAtEpochMillis;
    public int uploadAttemptCount;
    public Long nextAttemptAtEpochMillis;
    public String lastUploadError;
}
