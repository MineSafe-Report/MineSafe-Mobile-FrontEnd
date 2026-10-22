package com.example.javareportingsystem.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report fields returned by the supervisor review API.
 */
public final class ReviewReport
{
    private final String reportId;
    private final String referenceNumber;
    private final String type;
    private final String locationCode;
    private final String locationName;
    private final String description;
    private final long submittedAtEpochMillis;
    private final long occurredAtEpochMillis;
    private final String status;
    private final boolean anonymous;
    private final int imageCount;
    private final int voiceNoteCount;
    private final String reviewReason;
    private final boolean evidenceComplete;
    private final List<ReviewAttachment> attachments;
    private final String dashboardUrl;

    public ReviewReport(String reportId, String referenceNumber, String type,
                        String locationCode, String locationName, String description,
                        long submittedAtEpochMillis, long occurredAtEpochMillis, String status, boolean anonymous,
                        int imageCount, int voiceNoteCount, String reviewReason,
                        boolean evidenceComplete, List<ReviewAttachment> attachments, String dashboardUrl)
    {
        this.reportId = reportId;
        this.referenceNumber = referenceNumber;
        this.type = type;
        this.locationCode = locationCode;
        this.locationName = locationName;
        this.description = description;
        this.submittedAtEpochMillis = submittedAtEpochMillis;
        this.occurredAtEpochMillis = occurredAtEpochMillis;
        this.status = status;
        this.anonymous = anonymous;
        this.imageCount = imageCount;
        this.voiceNoteCount = voiceNoteCount;
        this.reviewReason = reviewReason;
        this.evidenceComplete = evidenceComplete;
        this.attachments = Collections.unmodifiableList(new ArrayList<>(attachments));
        this.dashboardUrl = dashboardUrl;
    }

    public String getReportId()
    {
        return reportId;
    }

    public String getReferenceNumber()
    {
        return referenceNumber;
    }

    public String getType()
    {
        return type;
    }

    public String getLocationCode()
    {
        return locationCode;
    }

    public String getLocationName()
    {
        return locationName;
    }

    public String getDescription()
    {
        return description;
    }

    public long getSubmittedAtEpochMillis()
    {
        return submittedAtEpochMillis;
    }

    public long getOccurredAtEpochMillis()
    {
        return occurredAtEpochMillis;
    }

    public String getStatus()
    {
        return status;
    }

    public boolean isAnonymous()
    {
        return anonymous;
    }

    public int getImageCount()
    {
        return imageCount;
    }

    public int getVoiceNoteCount()
    {
        return voiceNoteCount;
    }

    public String getReviewReason()
    {
        return reviewReason;
    }

    public boolean isEvidenceComplete()
    {
        return evidenceComplete;
    }

    public List<ReviewAttachment> getAttachments()
    {
        return attachments;
    }

    public String getDashboardUrl()
    {
        return dashboardUrl;
    }
}
