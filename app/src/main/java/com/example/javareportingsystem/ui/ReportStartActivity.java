package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//REPORT START ACTIVITY
//Starts a new report or opens a saved draft.

public class ReportStartActivity extends AppCompatActivity
{
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LocalReportRepository reports;
    private Button resumeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_start);
        reports = new LocalReportRepository(this);
        resumeButton = findViewById(R.id.btnResumeReport);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNewReport).setOnClickListener(v ->
            startActivity(new Intent(this, ReportTypeActivity.class)));
        resumeButton.setOnClickListener(v -> resumeReport());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top,
                systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        resumeButton.setEnabled(false);
        databaseExecutor.execute(() ->
        {
            try
            {
                int count = reports.listDrafts().size();
                runOnUiThread(() ->
                {
                    if (!isFinishing() && !isDestroyed())
                    {
                        resumeButton.setText(getString(R.string.resume_reports_count, count));
                        resumeButton.setEnabled(count > 0);
                    }
                });
            }
            catch (RuntimeException error)
            {
                showLoadError();
            }
        });
    }

    private void resumeReport()
    {
        resumeButton.setEnabled(false);
        databaseExecutor.execute(() ->
        {
            try
            {
                List<OfflineReportEntity> drafts = reports.listDrafts();
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    if (drafts.isEmpty())
                    {
                        resumeButton.setText(getString(R.string.resume_reports_count, 0));
                        return;
                    }
                    resumeButton.setEnabled(true);
                    if (drafts.size() == 1)
                    {
                        startActivities(ReportDraftNavigation.resumeStack(this, drafts.get(0)));
                    }
                    else
                    {
                        startActivity(new Intent(this, DraftListActivity.class));
                    }
                });
            }
            catch (RuntimeException error)
            {
                showLoadError();
            }
        });
    }

    private void showLoadError()
    {
        runOnUiThread(() ->
        {
            if (!isFinishing() && !isDestroyed())
            {
                Toast.makeText(this, R.string.report_draft_load_failed,
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
