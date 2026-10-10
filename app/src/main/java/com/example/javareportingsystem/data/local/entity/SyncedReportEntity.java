package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "synced_reports",
        indices = @Index(value = "sourceLocalReportId", unique = true))
public class SyncedReportEntity {
    @PrimaryKey
    @NonNull
    public String serverReportId = "";

    public long sourceLocalReportId;
    public String referenceNumber;

    @NonNull
    public String typeCode = "";

    @NonNull
    public String locationCode = "";

    @NonNull
    public String locationName = "";

    public String description;
    public long occurredAtEpochMillis;
    public long createdAtEpochMillis;
    public long submittedAtEpochMillis;
    public long serverReceivedAtEpochMillis;
    public boolean anonymous;

    // Present only for identified incident reports.
    public String reporterWorkerId;
    public Integer reporterAccountId;

    @NonNull
    public String workflowStatus = "";

    public long statusUpdatedAtEpochMillis;
}
