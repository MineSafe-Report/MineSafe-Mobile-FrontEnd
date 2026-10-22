package com.example.javareportingsystem.ui;

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
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportTypeActivity extends AppCompatActivity
{
    private static final String STATE_DRAFT_ID = "draft_id";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LocalReportRepository reports;
    private Button btnBack;
    private Button btnSaveDraft;
    private Button btnHazard;
    private Button btnIncident;
    private Button btnNearMiss;
    private long draftId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_type);
        reports = new LocalReportRepository(this);
        draftId = ReportDraftNavigation.optionalId(this);
        if (savedInstanceState != null)
        {
            draftId = savedInstanceState.getLong(STATE_DRAFT_ID, -1L);
        }

        btnBack = findViewById(R.id.btnBack);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnHazard = findViewById(R.id.btnHazard);
        btnIncident = findViewById(R.id.btnIncident);
        btnNearMiss = findViewById(R.id.btnNearMiss);

        btnBack.setOnClickListener(v -> finish());
        btnSaveDraft.setOnClickListener(v -> saveDraftAndExit());
        btnHazard.setOnClickListener(v -> selectType(ReportValues.HAZARD));
        btnIncident.setOnClickListener(v -> selectType(ReportValues.INCIDENT));
        btnNearMiss.setOnClickListener(v -> selectType(ReportValues.NEAR_MISS));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) ->
        {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (draftId <= 0)
        {
            showSelectedType(null);
            return;
        }

        databaseExecutor.execute(() ->
        {
            try
            {
                OfflineReportEntity draft = reports.getOfflineReport(draftId);
                String selectedType = draft == null ? null : draft.typeCode;
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    showSelectedType(selectedType);
                });
            }
            catch (RuntimeException error)
            {
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.report_draft_load_failed,
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showSelectedType(String typeCode)
    {
        styleTypeButton(btnIncident, R.drawable.ic_ms_personal_injury,
            ReportValues.INCIDENT.equals(typeCode));
        styleTypeButton(btnHazard, R.drawable.ic_ms_warning,
            ReportValues.HAZARD.equals(typeCode));
        styleTypeButton(btnNearMiss, R.drawable.ic_ms_visibility,
            ReportValues.NEAR_MISS.equals(typeCode));
    }

    private void styleTypeButton(Button button, int icon, boolean selected)
    {
        button.setSelected(selected);
        button.setBackgroundResource(selected
            ? R.drawable.report_type_selected : R.drawable.outline_button);
        ViewCompat.setBackgroundTintList(button, null);
        button.setTextColor(getColor(selected ? R.color.safety_action : R.color.safety_text));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0,
            selected ? R.drawable.ic_ms_check_circle : 0, 0);
        button.setContentDescription(selected
            ? getString(R.string.report_type_selected_accessibility, button.getText())
            : button.getText());
    }

    private void saveDraftAndExit()
    {
        setBusy(true);
        ReportDraftNavigation.saveAndReturnHome(this, databaseExecutor, () ->
        {
            if (draftId > 0)
            {
                reports.setDraftStep(draftId, ReportValues.STEP_TYPE);
            }
            else
            {
                draftId = reports.createEmptyDraft();
            }
        }, () -> setBusy(false));
    }

    private void selectType(String typeCode)
    {
        setBusy(true);
        databaseExecutor.execute(() ->
        {
            try
            {
                long id = draftId;
                if (id > 0)
                {
                    reports.changeDraftType(id, typeCode);
                }
                else
                {
                    id = reports.createDraft(typeCode);
                }
                long savedId = id;
                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }
                    draftId = savedId;
                    showSelectedType(typeCode);
                    setBusy(false);
                    startActivity(ReportDraftNavigation.to(
                        this, LocationSelectionActivity.class, draftId));
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

    private void setBusy(boolean busy)
    {
        btnBack.setEnabled(!busy);
        btnSaveDraft.setEnabled(!busy);
        btnHazard.setEnabled(!busy);
        btnIncident.setEnabled(!busy);
        btnNearMiss.setEnabled(!busy);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putLong(STATE_DRAFT_ID, draftId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy()
    {
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
