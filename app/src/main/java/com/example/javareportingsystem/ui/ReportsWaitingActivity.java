package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.ApiLoginDataSource;
import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.local.AppDatabase;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;
import com.example.javareportingsystem.data.model.LoggedInUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Supervisor queue for server-confirmed reports cached on this device. */
public class ReportsWaitingActivity extends AppCompatActivity {
    private static final int SORT_NEWEST = 0;
    private static final int SORT_OLDEST = 1;
    private static final int SORT_LOCATION_AZ = 2;
    private static final int SORT_LOCATION_ZA = 3;
    private static final int SORT_TYPE_AZ = 4;
    private static final int SORT_TYPE_ZA = 5;

    private final List<SyncedReportEntity> pendingReports = new ArrayList<>();
    private ReviewQueueAdapter adapter;
    private TextView emptyNotice;
    private TextView syncNote;
    private Button locationFilter;
    private Button sortButton;
    private String selectedLocation;
    private int selectedSort = SORT_NEWEST;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reports_waiting);
        applyWindowInsets();

        if (!hasSupervisorSession()) {
            Toast.makeText(this, R.string.supervisor_sign_in_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        RecyclerView recycler = findViewById(R.id.recyclerReports);
        emptyNotice = findViewById(R.id.tvQueueEmpty);
        syncNote = findViewById(R.id.tvQueueSyncNote);
        locationFilter = findViewById(R.id.btnLocationFilter);
        sortButton = findViewById(R.id.btnSortBy);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        locationFilter.setOnClickListener(v -> showLocationFilter());
        sortButton.setOnClickListener(v -> showSortOptions());

        adapter = new ReviewQueueAdapter(report -> {
            Intent intent = new Intent(this, ReviewReportActivity.class);
            intent.putExtra(ReviewReportActivity.EXTRA_SERVER_ID, report.serverReportId);
            startActivity(intent);
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        AppDatabase.getInstance(this).syncedReportDao().observeReports()
                .observe(this, this::showPendingReports);
    }

    private boolean hasSupervisorSession() {
        LoginRepository login = LoginRepository.getExistingInstance();
        if (login == null) {
            login = LoginRepository.getInstance(this,
                    new ApiLoginDataSource(getString(R.string.api_base_url)));
        }
        LoggedInUser user = login.getUser();
        return user != null && "SUPERVISOR".equalsIgnoreCase(user.getRole());
    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(20) + bars.left, dp(16) + bars.top,
                    dp(20) + bars.right, dp(16) + bars.bottom);
            return insets;
        });
    }

    private void showPendingReports(List<SyncedReportEntity> reports) {
        pendingReports.clear();
        if (reports != null) {
            for (SyncedReportEntity report : reports) {
                if (isPendingReview(report.workflowStatus)) pendingReports.add(report);
            }
        }
        updateRows();
    }

    private void updateRows() {
        List<SyncedReportEntity> visible = new ArrayList<>();
        for (SyncedReportEntity report : pendingReports) {
            if (selectedLocation == null || selectedLocation.equals(report.locationName)) {
                visible.add(report);
            }
        }
        Collections.sort(visible, comparatorFor(selectedSort));
        adapter.setReports(visible);

        boolean noReports = visible.isEmpty();
        emptyNotice.setVisibility(noReports ? View.VISIBLE : View.GONE);
        syncNote.setVisibility(pendingReports.isEmpty() ? View.VISIBLE : View.GONE);
        if (pendingReports.isEmpty()) {
            emptyNotice.setText(R.string.review_queue_empty);
        } else {
            emptyNotice.setText(R.string.review_queue_filter_empty);
        }
        locationFilter.setText(selectedLocation == null
                ? getString(R.string.review_filter_all_locations)
                : getString(R.string.review_filter_location, selectedLocation));
        String[] sortOptions = getResources().getStringArray(R.array.review_queue_sort_options);
        sortButton.setText(getString(R.string.review_sort_button, sortOptions[selectedSort]));
    }

    private void showLocationFilter() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (SyncedReportEntity report : pendingReports) {
            if (report.locationName != null && !report.locationName.trim().isEmpty()) {
                names.add(report.locationName.trim());
            }
        }
        List<String> locations = new ArrayList<>();
        locations.add(getString(R.string.review_filter_all_locations));
        locations.addAll(names);
        int checked = selectedLocation == null ? 0 : locations.indexOf(selectedLocation);
        if (checked < 0) checked = 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_filter_location_title)
                .setSingleChoiceItems(locations.toArray(new String[0]), checked,
                        (dialog, which) -> {
                            selectedLocation = which == 0 ? null : locations.get(which);
                            updateRows();
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSortOptions() {
        String[] options = getResources().getStringArray(R.array.review_queue_sort_options);
        new AlertDialog.Builder(this)
                .setTitle(R.string.review_sort_title)
                .setSingleChoiceItems(options, selectedSort, (dialog, which) -> {
                    selectedSort = which;
                    updateRows();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private Comparator<SyncedReportEntity> comparatorFor(int option) {
        switch (option) {
            case SORT_OLDEST:
                return Comparator.comparingLong(report -> report.submittedAtEpochMillis);
            case SORT_LOCATION_AZ:
                return Comparator.comparing(report -> safe(report.locationName),
                        String.CASE_INSENSITIVE_ORDER);
            case SORT_LOCATION_ZA:
                return Comparator.comparing((SyncedReportEntity report) -> safe(report.locationName),
                        String.CASE_INSENSITIVE_ORDER).reversed();
            case SORT_TYPE_AZ:
                return Comparator.comparing(report -> safe(report.typeCode),
                        String.CASE_INSENSITIVE_ORDER);
            case SORT_TYPE_ZA:
                return Comparator.comparing((SyncedReportEntity report) -> safe(report.typeCode),
                        String.CASE_INSENSITIVE_ORDER).reversed();
            case SORT_NEWEST:
            default:
                return Comparator.comparingLong(
                        (SyncedReportEntity report) -> report.submittedAtEpochMillis).reversed();
        }
    }

    private boolean isPendingReview(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return "PENDING_REVIEW".equals(normalized)
                || "AWAITING_REVIEW".equals(normalized)
                || "SUBMITTED".equals(normalized);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}