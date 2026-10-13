package com.example.javareportingsystem.ui;

import android.os.Bundle;
import android.widget.Button;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportVoiceActivity extends AppCompatActivity {
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private Button btnBack;
    private LocalReportRepository reports;
    private long draftId;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_voice);
        draftId = ReportDraftNavigation.readId(this);
        if (draftId <= 0) {
            return;
        }
        reports = new LocalReportRepository(this);

        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> returnToDescription());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                returnToDescription();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void returnToDescription() {
        if (busy) {
            return;
        }
        busy = true;
        btnBack.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                reports.setDraftStep(draftId, ReportValues.STEP_DESCRIPTION);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        finish();
                    }
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    busy = false;
                    btnBack.setEnabled(true);
                    Toast.makeText(this, R.string.report_draft_save_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
