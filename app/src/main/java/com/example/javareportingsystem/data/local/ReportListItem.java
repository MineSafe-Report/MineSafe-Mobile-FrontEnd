package com.example.javareportingsystem.data.local;

/** One row for My Reports, sourced from either local pending or server-confirmed records. */
public class ReportListItem {
    public String source;
    public long localReportId;
    public String serverReportId;
    public String typeCode;
    public String statusCode;
    public long createdAtEpochMillis;
    public boolean anonymous;
    public String locationName;
}
