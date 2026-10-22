package com.example.javareportingsystem.ui;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.javareportingsystem.LandingPageActivity;
import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.concurrent.ExecutorService;

public final class ReportDraftNavigation
{
    private static final String EXTRA_LOCAL_REPORT_ID =
        "com.example.javareportingsystem.LOCAL_REPORT_ID";

    private ReportDraftNavigation()
    {
    }

    public static Intent to(Context context, Class<?> destination, long localReportId)
    {
        return new Intent(context, destination)
            .putExtra(EXTRA_LOCAL_REPORT_ID, localReportId);
    }

    public static Intent[] resumeStack(Context context, OfflineReportEntity draft)
    {
        long id = draft.localId;
        Intent type = to(context, ReportTypeActivity.class, id);
        if (ReportValues.STEP_TYPE.equals(draft.currentStep))
        {
            return new Intent[]{type};
        }
        Intent location = to(context, LocationSelectionActivity.class, id);
        if (ReportValues.STEP_DESCRIPTION.equals(draft.currentStep))
        {
            return new Intent[]{
                type, location,
                to(context, ReportDescriptionActivity.class, id)};
        }
        Intent description = to(context, ReportDescriptionActivity.class, id);
        Intent voice = to(context, ReportVoiceActivity.class, id);
        if (ReportValues.STEP_VOICE.equals(draft.currentStep))
        {
            return new Intent[]{type, location, description, voice};
        }
        Intent images = to(context, ReportImagesActivity.class, id);
        if (ReportValues.STEP_IMAGES.equals(draft.currentStep))
        {
            return new Intent[]{type, location, description, voice, images};
        }
        if (ReportValues.STEP_OVERVIEW.equals(draft.currentStep))
        {
            return new Intent[]{
                type, location, description, voice, images,
                to(context, ReportOverviewActivity.class, id)};
        }
        return new Intent[]{type, location};
    }

    public static void saveAndReturnHome(AppCompatActivity activity,
                                         ExecutorService executor, Runnable persist, Runnable onFailure)
    {
        executor.execute(() ->
        {
            try
            {
                persist.run();
                activity.runOnUiThread(() ->
                {
                    if (activity.isFinishing() || activity.isDestroyed())
                    {
                        return;
                    }
                    Toast.makeText(activity, R.string.report_draft_saved,
                        Toast.LENGTH_SHORT).show();
                    Intent home = new Intent(activity, LandingPageActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(home);
                    activity.finish();
                });
            }
            catch (RuntimeException error)
            {
                activity.runOnUiThread(() ->
                {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                    {
                        onFailure.run();
                        Toast.makeText(activity, R.string.report_draft_save_failed,
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    static long optionalId(AppCompatActivity activity)
    {
        return activity.getIntent().getLongExtra(EXTRA_LOCAL_REPORT_ID, -1L);
    }

    static long readId(AppCompatActivity activity)
    {
        long id = optionalId(activity);
        if (id <= 0)
        {
            Toast.makeText(activity, R.string.report_draft_missing, Toast.LENGTH_SHORT).show();
            activity.finish();
        }
        return id;
    }
}
