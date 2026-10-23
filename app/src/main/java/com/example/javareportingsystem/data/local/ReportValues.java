package com.example.javareportingsystem.data.local;

//REPORT VALUES
//Stores the shared report types and status names

public final class ReportValues
{
    public static final String HAZARD = "HAZARD";
    public static final String INCIDENT = "INCIDENT";
    public static final String NEAR_MISS = "NEAR_MISS";

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_UPLOAD = "PENDING_UPLOAD";
    public static final String REPORT_UPLOADED = "REPORT_UPLOADED";
    public static final String UPLOAD_PAUSED = "UPLOAD_PAUSED";
    public static final String FAILED = "FAILED";

    public static final String STEP_TYPE = "TYPE";
    public static final String STEP_LOCATION = "LOCATION";
    public static final String STEP_DESCRIPTION = "DESCRIPTION";
    public static final String STEP_VOICE = "VOICE";
    public static final String STEP_IMAGES = "IMAGES";
    public static final String STEP_OVERVIEW = "OVERVIEW";

    public static final String ATTACHMENT_PENDING = "PENDING_UPLOAD";
    public static final String ATTACHMENT_UPLOADED = "UPLOADED";
    public static final String PHOTO = "PHOTO";
    public static final String VOICE_NOTE = "VOICE_NOTE";
    public static final String CAMERA = "CAMERA";
    public static final String GALLERY = "GALLERY";
    public static final String MICROPHONE = "MICROPHONE";

    private ReportValues()
    {
    }

    public static boolean isReportType(String code)
    {
        return HAZARD.equals(code) || INCIDENT.equals(code) || NEAR_MISS.equals(code);
    }

    public static boolean isAnonymousType(String code)
    {
        return HAZARD.equals(code) || NEAR_MISS.equals(code);
    }

    public static boolean isDraftStep(String step)
    {
        return STEP_TYPE.equals(step) || STEP_LOCATION.equals(step)
            || STEP_DESCRIPTION.equals(step) || STEP_VOICE.equals(step)
            || STEP_IMAGES.equals(step) || STEP_OVERVIEW.equals(step);
    }
}
