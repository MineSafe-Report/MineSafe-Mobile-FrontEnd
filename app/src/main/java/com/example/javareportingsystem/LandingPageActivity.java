package com.example.javareportingsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.data.ApiLoginDataSource;
import com.example.javareportingsystem.data.ApiLocationDataSource;
import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.model.LoggedInUser;
import com.example.javareportingsystem.data.model.RemoteLocation;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.entity.DeviceConfigEntity;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.ui.MyReportsActivity;
import com.example.javareportingsystem.ui.ReportStartActivity;
import com.example.javareportingsystem.ui.ReportsWaitingActivity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LandingPageActivity extends AppCompatActivity {
    private final ExecutorService locationExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean locationRefreshQueued = new AtomicBoolean();
    private LoginRepository repository;
    private LocalReportRepository reports;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LoginRepository existingRepository = LoginRepository.getExistingInstance();
        repository = existingRepository != null
                ? existingRepository
                : LoginRepository.getInstance(this,
                        new ApiLoginDataSource(getString(R.string.api_base_url)));

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_landing_page);
        reports = new LocalReportRepository(this);
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            if (!repository.isLoggedIn()) {
                openLogin();
                return;
            }
            if (!repository.logout()) {
                android.widget.Toast.makeText(this, R.string.logout_failed,
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            openLogin();
        });
        findViewById(R.id.btnCreateReport).setOnClickListener(v -> startActivity(
                new Intent(this, ReportStartActivity.class)));
        findViewById(R.id.btnMyReports).setOnClickListener(v -> startActivity(
                new Intent(this, MyReportsActivity.class)));
        findViewById(R.id.btnReviewReports).setOnClickListener(v -> startActivity(
                new Intent(this, ReportsWaitingActivity.class)));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        refreshSessionUi();
    }

    private void refreshLocationsIfNeeded() {
        LoggedInUser user = repository.getUser();
        if (user == null || !locationRefreshQueued.compareAndSet(false, true)) {
            return;
        }
        locationExecutor.execute(() -> {
            try {
                DeviceConfigEntity config = reports.getOrCreateDeviceConfig();
                long lastRefresh = config.lastLocationConfigAtEpochMillis == null
                        ? 0L : config.lastLocationConfigAtEpochMillis;
                boolean hasRemoteLocations = false;
                for (LocationEntity location : reports.listActiveLocations()) {
                    if (location.serverLocationId != null) {
                        hasRemoteLocations = true;
                        break;
                    }
                }
                if (hasRemoteLocations
                        && System.currentTimeMillis() - lastRefresh < 30 * 60 * 1000L) {
                    return;
                }
                ApiLocationDataSource api = new ApiLocationDataSource(
                        getString(R.string.api_base_url));
                List<RemoteLocation> remote =
                        api.fetchActiveLocations(user.getAccessToken());
                if (repository.getUser() != null) {
                    reports.replaceRemoteLocations(remote);
                }
            } catch (IOException | RuntimeException ignored) {
                // Offline use continues with the previously cached catalogue.
            } finally {
                locationRefreshQueued.set(false);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSessionUi();
        refreshLocationsIfNeeded();
    }

    private void refreshSessionUi() {
        LoggedInUser user = repository.getUser();
        TextView welcome = findViewById(R.id.tvWelcome);
        Button sessionAction = findViewById(R.id.btnLogout);
        Button reviewReports = findViewById(R.id.btnReviewReports);
        TextView offlineNotice = findViewById(R.id.tvOfflineNotice);
        if (user == null) {
            welcome.setText(R.string.offline_welcome);
            sessionAction.setText(R.string.sign_in);
            reviewReports.setVisibility(View.GONE);
            offlineNotice.setVisibility(View.VISIBLE);
        } else {
            welcome.setText(getString(R.string.landing_welcome, user.getDisplayName()));
            sessionAction.setText(R.string.logout);
            reviewReports.setVisibility("SUPERVISOR".equalsIgnoreCase(user.getRole())
                    ? View.VISIBLE : View.GONE);
            offlineNotice.setVisibility(View.GONE);
        }
    }

    private void openLogin() {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(login);
        finish();
    }

    @Override
    protected void onDestroy() {
        locationExecutor.shutdown();
        super.onDestroy();
    }
}
