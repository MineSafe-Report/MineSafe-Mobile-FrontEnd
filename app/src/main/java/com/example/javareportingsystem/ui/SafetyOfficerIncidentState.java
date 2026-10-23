package com.example.javareportingsystem.ui;

import android.content.Context;
import android.content.SharedPreferences;

//SAFETY OFFICER INCIDENT STATE
//Remembers which incidents were opened on this device.

final class SafetyOfficerIncidentState
{
    private static final String PREFERENCES = "safety_officer_opened_incidents";

    private SafetyOfficerIncidentState()
    {
    }

    static boolean wasOpened(Context context, int accountId, String reportId)
    {
        return preferences(context).getBoolean(key(accountId, reportId), false);
    }

    static void markOpened(Context context, int accountId, String reportId)
    {
        preferences(context).edit().putBoolean(key(accountId, reportId), true).apply();
    }

    private static SharedPreferences preferences(Context context)
    {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private static String key(int accountId, String reportId)
    {
        return accountId + ":" + reportId;
    }
}
