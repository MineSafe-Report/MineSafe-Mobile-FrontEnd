package com.example.javareportingsystem.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
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
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationSelectionActivity extends AppCompatActivity {
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LocalReportRepository reports;
    private Button btnBack;
    private Button btnLocationDropdown;
    private HorizontalScrollView locationTableContainer;
    private TableLayout locationTable;
    private Button btnNext;
    private long draftId;
    private Long selectedLocationId;
    private boolean expanded;
    private boolean busy = true;
    private boolean draftLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_location_selection);
        draftId = ReportDraftNavigation.readId(this);
        if (draftId <= 0) {
            return;
        }
        reports = new LocalReportRepository(this);

        btnBack = findViewById(R.id.btnBack);
        btnLocationDropdown = findViewById(R.id.btnLocationDropdown);
        btnNext = findViewById(R.id.btnNext);
        locationTableContainer = findViewById(R.id.locationTableContainer);
        locationTable = findViewById(R.id.locationTable);

        btnBack.setOnClickListener(v -> move(false));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                move(false);
            }
        });
        btnLocationDropdown.setOnClickListener(v -> {
            if (busy) {
                return;
            }
            expanded = !expanded;
            locationTableContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        });
        btnNext.setOnClickListener(v -> move(true));
        btnNext.setVisibility(View.GONE);
        setBusy(true);
        reports.observeActiveLocations().observe(this, locations -> {
            if (draftLoaded) {
                populateLocationTable(locations, selectedLocationId);
            }
        });
        loadLocations();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadLocations() {
        databaseExecutor.execute(() -> {
            try {
                OfflineReportEntity draft = reports.getOfflineReport(draftId);
                if (draft == null || !ReportValues.DRAFT.equals(draft.syncState)) {
                    throw new IllegalStateException("Report draft is missing");
                }
                List<LocationEntity> locations = reports.listActiveLocations();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    populateLocationTable(locations, draft.locationLocalId);
                    draftLoaded = true;
                    setBusy(false);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(this, R.string.report_draft_load_failed,
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void populateLocationTable(List<LocationEntity> locations, Long savedLocationId) {
        while (locationTable.getChildCount() > 1) {
            locationTable.removeViewAt(1);
        }
        selectedLocationId = null;
        btnLocationDropdown.setText(R.string.select_location);

        if (locations.isEmpty()) {
            TableRow row = new TableRow(this);
            TextView message = cell(getString(R.string.no_configured_locations));
            TableRow.LayoutParams params = new TableRow.LayoutParams();
            params.span = 2;
            message.setLayoutParams(params);
            row.addView(message);
            locationTable.addView(row);
        }

        for (LocationEntity location : locations) {
            TableRow row = new TableRow(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.WHITE);
            border.setStroke(1, Color.LTGRAY);
            row.setBackground(border);
            row.addView(cell(location.code));
            row.addView(cell(location.name));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> selectLocation(location));
            locationTable.addView(row);

            if (savedLocationId != null && savedLocationId == location.localId) {
                selectedLocationId = location.localId;
                btnLocationDropdown.setText(location.name);
            }
        }
        btnNext.setVisibility(selectedLocationId == null ? View.GONE : View.VISIBLE);
    }

    private TextView cell(String value) {
        TextView cell = new TextView(this);
        cell.setText(value);
        cell.setTextColor(Color.BLACK);
        cell.setTextSize(14);
        cell.setPadding(12, 16, 12, 16);
        return cell;
    }

    private void selectLocation(LocationEntity location) {
        if (busy) {
            return;
        }
        if (selectedLocationId != null && selectedLocationId == location.localId) {
            expanded = false;
            locationTableContainer.setVisibility(View.GONE);
            return;
        }
        setBusy(true);
        databaseExecutor.execute(() -> {
            try {
                reports.saveDraftLocation(draftId, location.localId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    selectedLocationId = location.localId;
                    btnLocationDropdown.setText(location.name);
                    expanded = false;
                    locationTableContainer.setVisibility(View.GONE);
                    btnNext.setVisibility(View.VISIBLE);
                    setBusy(false);
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

    private void move(boolean forward) {
        if (busy || (forward && selectedLocationId == null)) {
            return;
        }
        setBusy(true);
        databaseExecutor.execute(() -> {
            try {
                reports.setDraftStep(draftId, forward
                        ? ReportValues.STEP_DESCRIPTION : ReportValues.STEP_TYPE);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    if (forward) {
                        startActivity(ReportDraftNavigation.to(
                                this, ReportDescriptionActivity.class, draftId));
                    } else {
                        finish();
                    }
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
        this.busy = busy;
        btnBack.setEnabled(!busy);
        btnLocationDropdown.setEnabled(!busy);
        btnNext.setEnabled(!busy);
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
