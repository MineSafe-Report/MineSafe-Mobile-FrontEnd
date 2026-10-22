package com.example.javareportingsystem.data;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ISO 8601 UTC dates exchanged with the API, without requiring java.time on minSdk 25.
 */
public final class ApiDates
{
    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    private ApiDates()
    {
    }

    public static String toIso(long millis)
    {
        return utcFormat().format(new Date(millis));
    }

    public static long parseIsoToMillis(String iso)
    {
        if (iso == null || iso.isEmpty()) return 0L;
        String normalized = iso.replaceAll("\\.\\d+Z$", "Z");
        ParsePosition position = new ParsePosition(0);
        Date date = utcFormat().parse(normalized, position);
        return date != null && position.getIndex() == normalized.length()
            ? date.getTime() : 0L;
    }

    private static SimpleDateFormat utcFormat()
    {
        SimpleDateFormat format = new SimpleDateFormat(PATTERN, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format;
    }
}
