package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

//OFFLINE REPORT ENTITY
// Stores a draft or report waiting to upload.


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

public class OfflineReportEntity
{
    @PrimaryKey(autoGenerate = true)
    public long localId;

    @NonNull
    public String typeCode = "";

    public Long locationLocalId;
    public String description;
    @NonNull
    public String currentStep = "LOCATION";
    public Long occurredAtEpochMillis;
    public long createdAtEpochMillis;
    public Long submittedAtEpochMillis;

    //Incedents keep reporter names here (hazereds and near misis do not keep the name)
    public boolean anonymous;
    public String reporterWorkerId;
    public Integer reporterAccountId;

    @NonNull
    public String syncState = "DRAFT";

    //Vars are set when server accepts the report
    public String serverReportId;
    public String referenceNumber;
    public Long serverReceivedAtEpochMillis;
    public int uploadAttemptCount;
    public Long nextAttemptAtEpochMillis;
    public String lastUploadError;
}
