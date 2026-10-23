package com.example.javareportingsystem.ui;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.javareportingsystem.R;

import org.junit.Test;
import org.junit.runner.RunWith;

// LAYOUT SIZING TEST
// Checks that screen controls remain usable at different sizes.

@RunWith(AndroidJUnit4.class)
public class LayoutSizingTest
{
    private static final int[] SCREEN_LAYOUTS = {
        R.layout.activity_login,
        R.layout.activity_landing_page,
        R.layout.activity_report_start,
        R.layout.activity_draft_list,
        R.layout.activity_report_type,
        R.layout.activity_location_selection,
        R.layout.activity_report_description,
        R.layout.activity_report_voice,
        R.layout.activity_report_images,
        R.layout.activity_report_overview,
        R.layout.activity_my_reports,
        R.layout.item_voice_note
    };

    @Test
    public void controlsHaveUsableTargetsOnCompactAndLargeScreens()
    {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(this::checkLayouts);
    }

    private void checkLayouts()
    {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (float fontScale : new float[]{1f, 1.5f})
        {
            Configuration configuration = new Configuration(target.getResources().getConfiguration());
            configuration.fontScale = fontScale;
            Context configured = target.createConfigurationContext(configuration);
            Context themed = new ContextThemeWrapper(configured,
                R.style.Theme_JavaReportingSystem);
            float density = themed.getResources().getDisplayMetrics().density;
            for (int[] viewport : new int[][]{{320, 480}, {600, 800}})
            {
                int width = Math.round(viewport[0] * density);
                int height = Math.round(viewport[1] * density);
                for (int layoutId : SCREEN_LAYOUTS)
                {
                    View screen = LayoutInflater.from(themed).inflate(layoutId, null);
                    screen.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    screen.layout(0, 0, width, height);
                    assertTargets(screen, Math.round(48 * density));
                }
            }
        }
    }

    private void assertTargets(View view, int minimumPixels)
    {
        if (view.getVisibility() == View.GONE)
        {
            return;
        }
        if (view instanceof Button || view instanceof ImageButton
            || view instanceof EditText || view instanceof SeekBar)
        {
            String name = view.getResources().getResourceEntryName(view.getId());
            assertTrue(name + " width is below 48dp", view.getMeasuredWidth() >= minimumPixels);
            assertTrue(name + " height is below 48dp", view.getMeasuredHeight() >= minimumPixels);
        }
        if (view instanceof ViewGroup)
        {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++)
            {
                assertTargets(group.getChildAt(index), minimumPixels);
            }
        }
    }
}
