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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportTypeActivity extends AppCompatActivity {
    private static final String STATE_DRAFT_ID = "draft_id";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LocalReportRepository reports;
    private Button btnBack;
    private Button btnHazard;
    private Button btnIncident;
    private Button btnNearMiss;
    private long draftId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_type);
        reports = new LocalReportRepository(this);
        draftId = ReportDraftNavigation.optionalId(this);
        if (savedInstanceState != null) {
            draftId = savedInstanceState.getLong(STATE_DRAFT_ID, -1L);
        }

        btnBack = findViewById(R.id.btnBack);
        btnHazard = findViewById(R.id.btnHazard);
        btnIncident = findViewById(R.id.btnIncident);
        btnNearMiss = findViewById(R.id.btnNearMiss);

        btnBack.setOnClickListener(v -> finish());
        btnHazard.setOnClickListener(v -> selectType(ReportValues.HAZARD));
        btnIncident.setOnClickListener(v -> selectType(ReportValues.INCIDENT));
        btnNearMiss.setOnClickListener(v -> selectType(ReportValues.NEAR_MISS));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void selectType(String typeCode) {
        setBusy(true);
        databaseExecutor.execute(() -> {
            try {
                long id = draftId;
                if (id > 0) {
                    reports.changeDraftType(id, typeCode);
                } else {
                    id = reports.createDraft(typeCode);
                }
                long savedId = id;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    draftId = savedId;
                    setBusy(false);
                    startActivity(ReportDraftNavigation.to(
                            this, LocationSelectionActivity.class, draftId));
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(this, R.string.report_draft_save_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        btnBack.setEnabled(!busy);
        btnHazard.setEnabled(!busy);
        btnIncident.setEnabled(!busy);
        btnNearMiss.setEnabled(!busy);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(STATE_DRAFT_ID, draftId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
