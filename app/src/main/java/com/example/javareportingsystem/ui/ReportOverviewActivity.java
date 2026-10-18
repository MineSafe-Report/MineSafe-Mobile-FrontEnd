package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.LandingPageActivity;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.ReportSyncWorker;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportOverviewActivity extends AppCompatActivity {

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LocalReportRepository reports;
    private Button btnSaveDraft;
    private Button btnBack;
    private Button btnReportType;
    private Button btnLocations;
    private Button btnEditDescription;
    private Button btnEditImages;
    private Button btnSubmitReport;
    private TextView validationMessage;
    private ScrollView overviewScroll;
    private boolean showValidationErrors;
    private long draftId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_overview);
        draftId = ReportDraftNavigation.readId(this);
        if (draftId <= 0) {
            return;
        }

        reports = new LocalReportRepository(this);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        btnReportType = findViewById(R.id.btnReportType);
        btnLocations = findViewById(R.id.btnLocations);
        btnEditDescription = findViewById(R.id.btnEditDescription);
        btnEditImages = findViewById(R.id.btnEditImages);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);
        validationMessage = findViewById(R.id.validationMessage);
        overviewScroll = findViewById(R.id.overviewScroll);
        btnSaveDraft.setOnClickListener(v -> saveDraftAndExit());


        btnReportType.setOnClickListener(v -> {
            // Handle Report Type button click
            Intent intent = ReportDraftNavigation.to(this, ReportTypeActivity.class, draftId);
            startActivity(intent);
        });

        btnLocations.setOnClickListener(v -> {
            // Handle Locations button click
            Intent intent = ReportDraftNavigation.to(this, LocationSelectionActivity.class, draftId);
            intent.putExtra(LocationSelectionActivity.EXTRA_FROM_OVERVIEW, true);
            startActivity(intent);
        });

        btnEditDescription.setOnClickListener(v -> {
            // Handle Edit Description button click
            Intent intent = ReportDraftNavigation.to(this, ReportDescriptionActivity.class, draftId);
            startActivity(intent);
        });

        btnEditImages.setOnClickListener(v -> {
            // Handle Edit Images button click
            Intent intent = ReportDraftNavigation.to(this, ReportImagesActivity.class, draftId);
            startActivity(intent);
        });

        btnSubmitReport.setOnClickListener(v -> submitReport());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (draftId <= 0 || reports == null) {
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                OfflineReportEntity draft = reports.getOfflineReport(draftId);
                if (draft == null) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, R.string.report_draft_missing, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                String locationName = null;
                if (draft.locationLocalId != null) {
                    LocationEntity location = reports.getLocation(draft.locationLocalId);
                    if (location != null) {
                        locationName = location.name;
                    }
                }

                int imageCount;
                try (ImageDatabaseHelper images = new ImageDatabaseHelper(this, draftId)) {
                    imageCount = images.getImageCount();
                }

                LocalReportRepository.SubmissionStatus status =
                        reports.getDraftSubmissionStatus(draftId);
                String savedLocationName = locationName;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnReportType.setText(getString(R.string.overview_report_type_value,
                            reportTypeName(draft.typeCode)));
                    btnLocations.setText(getString(R.string.overview_location_value,
                            savedLocationName == null
                                    ? getString(R.string.draft_location_missing)
                                    : savedLocationName));
                    btnEditImages.setText(getString(R.string.overview_images_count,
                            imageCount, ImageDatabaseHelper.MAX_IMAGES));
                    updateSubmissionIndicators(status);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, R.string.report_draft_load_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String reportTypeName(String typeCode) {
        if (ReportValues.INCIDENT.equals(typeCode)) {
            return getString(R.string.report_type_incident);
        }
        if (ReportValues.HAZARD.equals(typeCode)) {
            return getString(R.string.report_type_hazard);
        }
        if (ReportValues.NEAR_MISS.equals(typeCode)) {
            return getString(R.string.report_type_near_miss);
        }
        return getString(R.string.report_type_not_selected);
    }

    private void submitReport() {
        setSavingDraft(true);
        databaseExecutor.execute(() -> {
            try {
                LocalReportRepository.SubmissionStatus status =
                        reports.getDraftSubmissionStatus(draftId);
                if (!status.isReady()) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        showValidationErrors = true;
                        updateSubmissionIndicators(status);
                        setSavingDraft(false);
                        overviewScroll.smoothScrollTo(0, 0);
                        validationMessage.announceForAccessibility(
                                validationMessage.getText());
                    });
                    return;
                }

                reports.submitDraft(draftId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ReportSyncWorker.enqueueNow(this);
                    Toast.makeText(this, R.string.report_submitted, Toast.LENGTH_LONG).show();
                    Intent home = new Intent(this, LandingPageActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(home);
                    finish();
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setSavingDraft(false);
                    Toast.makeText(this, R.string.report_submit_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateSubmissionIndicators(LocalReportRepository.SubmissionStatus status) {
        btnEditDescription.setText(status.missingDetails
                ? R.string.overview_details_missing : R.string.overview_details_present);
        highlightIfMissing(btnReportType, showValidationErrors && status.missingType);
        highlightIfMissing(btnLocations, showValidationErrors && status.missingLocation);
        highlightIfMissing(btnEditDescription, showValidationErrors && status.missingDetails);

        if (!showValidationErrors || status.isReady()) {
            validationMessage.setVisibility(android.view.View.GONE);
            return;
        }

        boolean hasCardError = status.missingType || status.missingLocation
                || status.missingDetails;
        if (hasCardError && status.missingEventTime) {
            validationMessage.setText(getString(R.string.overview_complete_highlighted)
                    + "\n" + getString(R.string.overview_event_time_missing));
        } else if (hasCardError) {
            validationMessage.setText(R.string.overview_complete_highlighted);
        } else {
            validationMessage.setText(R.string.overview_event_time_missing);
        }
        validationMessage.setVisibility(android.view.View.VISIBLE);
    }

    private void highlightIfMissing(Button button, boolean missing) {
        button.setBackgroundResource(missing
                ? R.drawable.report_field_error : R.drawable.outline_button);
        ViewCompat.setBackgroundTintList(button, null);
        int color = getColor(missing ? R.color.safety_danger : R.color.safety_text);
        button.setTextColor(color);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        button.setContentDescription(missing
                ? getString(R.string.overview_field_required_accessibility, button.getText())
                : button.getText());
    }

    private void saveDraftAndExit() {
        setSavingDraft(true);
        ReportDraftNavigation.saveAndReturnHome(this, databaseExecutor,
                () -> reports.setDraftStep(draftId, ReportValues.STEP_OVERVIEW),
                () -> setSavingDraft(false));
    }

    private void setSavingDraft(boolean saving) {
        btnSaveDraft.setEnabled(!saving);
        btnBack.setEnabled(!saving);
        btnReportType.setEnabled(!saving);
        btnLocations.setEnabled(!saving);
        btnEditDescription.setEnabled(!saving);
        btnEditImages.setEnabled(!saving);
        btnSubmitReport.setEnabled(!saving);
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }

}
