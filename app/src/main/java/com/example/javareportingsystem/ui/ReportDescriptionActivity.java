package com.example.javareportingsystem.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//REPORT DESCRIPTION ACTIVITY
//Lets the user describe what happened.

public class ReportDescriptionActivity extends AppCompatActivity
{
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler autosaveHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAutosave;
    private LocalReportRepository reports;
    private Button btnBack;
    private Button btnSaveDraft;
    private EditText txtDescription;
    private Button btnNext;
    private long draftId;
    private boolean busy = true;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_description);
        draftId = ReportDraftNavigation.readId(this);
        if (draftId <= 0)
        {
            return;
        }
        reports = new LocalReportRepository(this);
        btnBack = findViewById(R.id.btnBack);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        txtDescription = findViewById(R.id.txtDescription);
        btnNext = findViewById(R.id.btnNext);

        btnBack.setOnClickListener(v -> saveAndMove(false));
        btnSaveDraft.setOnClickListener(v -> saveDraftAndExit());
        btnNext.setOnClickListener(v -> saveAndMove(true));
        txtDescription.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count)
            {
            }

            @Override
            public void afterTextChanged(Editable text)
            {
                if (!busy)
                {
                    scheduleAutosave();
                }
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true)
        {
            @Override
            public void handleOnBackPressed()
            {
                saveAndMove(false);
            }
        });
        setBusy(true);
        loadDraft(savedInstanceState == null);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadDraft(boolean restoreDescription)
    {
        databaseExecutor.execute(() ->
        {
            try
            {
                OfflineReportEntity draft = reports.getOfflineReport(draftId);
                if (draft == null || !ReportValues.DRAFT.equals(draft.syncState))
                {
                    throw new IllegalStateException("Report draft is missing");
                }
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    if (restoreDescription)
                    {
                        txtDescription.setText(draft.description == null ? "" : draft.description);
                    }
                    setBusy(false);
                });
            }
            catch (RuntimeException error)
            {
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    Toast.makeText(this, R.string.report_draft_load_failed,
                        Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void saveDraftAndExit()
    {
        if (busy)
        {
            return;
        }
        String description = txtDescription.getText().toString().trim();
        cancelPendingAutosave();
        setBusy(true);
        ReportDraftNavigation.saveAndReturnHome(this, databaseExecutor, () ->
        {
            reports.saveDraftDescription(draftId, description);
            reports.setDraftStep(draftId, ReportValues.STEP_DESCRIPTION);
        }, () -> setBusy(false));
    }

    private void saveAndMove(boolean forward)
    {
        if (busy)
        {
            return;
        }
        String description = txtDescription.getText().toString().trim();
        cancelPendingAutosave();
        setBusy(true);
        databaseExecutor.execute(() ->
        {
            try
            {
                reports.saveDraftDescription(draftId, description);
                reports.setDraftStep(draftId, forward
                    ? ReportValues.STEP_VOICE : ReportValues.STEP_LOCATION);
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    setBusy(false);
                    if (forward)
                    {
                        startActivity(ReportDraftNavigation.to(
                            this, ReportVoiceActivity.class, draftId));
                    }
                    else
                    {
                        finish();
                    }
                });
            }
            catch (RuntimeException error)
            {
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(this, R.string.report_draft_save_failed,
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void scheduleAutosave()
    {
        cancelPendingAutosave();
        String description = txtDescription.getText().toString().trim();
        pendingAutosave = () -> enqueueAutosave(description);
        autosaveHandler.postDelayed(pendingAutosave, 400);
    }

    private void enqueueAutosave(String description)
    {
        databaseExecutor.execute(() ->
        {
            try
            {
                reports.saveDraftDescription(draftId, description);
            }
            catch (RuntimeException error)
            {
                runOnUiThread(() ->
                {
                    if (!isFinishing() && !isDestroyed())
                    {
                        Toast.makeText(this, R.string.report_draft_save_failed,
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void cancelPendingAutosave()
    {
        if (pendingAutosave != null)
        {
            autosaveHandler.removeCallbacks(pendingAutosave);
            pendingAutosave = null;
        }
    }

    @Override
    protected void onPause()
    {
        if (draftId > 0 && reports != null && txtDescription != null && !busy)
        {
            cancelPendingAutosave();
            String description = txtDescription.getText().toString().trim();
            enqueueAutosave(description);
        }
        super.onPause();
    }

    private void setBusy(boolean busy)
    {
        this.busy = busy;
        btnBack.setEnabled(!busy);
        btnSaveDraft.setEnabled(!busy);
        btnNext.setEnabled(!busy);
        txtDescription.setEnabled(!busy);
    }

    @Override
    protected void onDestroy()
    {
        cancelPendingAutosave();
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
