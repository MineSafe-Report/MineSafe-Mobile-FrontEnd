package com.example.javareportingsystem.data.local;

//REPORT LIST ITEM
//helper, Holds a singel row shown in my reports section

public class ReportListItem
{
    public String source;
    public long localReportId;
    public String serverReportId;
    public String typeCode;
    public String statusCode;
    public long createdAtEpochMillis;
    public boolean anonymous;
    public String locationName;
}
