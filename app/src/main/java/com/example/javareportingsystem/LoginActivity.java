package com.example.javareportingsystem;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.data.ApiLoginDataSource;
import com.example.javareportingsystem.data.ApiLocationDataSource;
import com.example.javareportingsystem.data.ReportSyncWorker;
import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.Result;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.model.LoggedInUser;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    private final ExecutorService loginExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> requestLocalNetworkPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    attemptLogin();
                } else {
                    showLoginError(getString(R.string.local_network_permission_required));
                }
            });

    private EditText workerIdInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button continueOfflineButton;
    private ProgressBar loginProgress;
    private TextView loginError;
    private LoginRepository loginRepository;
    private ApiLocationDataSource locationApi;
    private LocalReportRepository localReports;
    private boolean debugLocalApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        workerIdInput = findViewById(R.id.workerIdInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        continueOfflineButton = findViewById(R.id.continueOfflineButton);
        loginProgress = findViewById(R.id.loginProgress);
        loginError = findViewById(R.id.loginError);

        String baseUrl = getString(R.string.api_base_url);
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        debugLocalApi = debuggable && baseUrl.startsWith("http://10.0.2.2");
        loginRepository = LoginRepository.getInstance(this, new ApiLoginDataSource(baseUrl));
        locationApi = new ApiLocationDataSource(baseUrl);
        localReports = new LocalReportRepository(this);

        loginButton.setOnClickListener(v -> attemptLogin());
        continueOfflineButton.setOnClickListener(v -> openLandingPage());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        if (loginRepository.isLoggedIn()) {
            ReportSyncWorker.enqueueNow(this);
            openLandingPage();
        } else if (loginRepository.getCachedUser() != null) {
            setLoading(true);
            loginExecutor.execute(() -> {
                LoggedInUser restored = loginRepository.getFreshUser();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    if (restored != null) {
                        ReportSyncWorker.enqueueNow(this);
                        openLandingPage();
                    }
                });
            });
        }
    }

    private void attemptLogin() {
        if (loginProgress.getVisibility() == View.VISIBLE) {
            return;
        }

        workerIdInput.setError(null);
        passwordInput.setError(null);
        loginError.setVisibility(View.GONE);

        String workerId = workerIdInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (workerId.isEmpty()) {
            workerIdInput.setError(getString(R.string.worker_id_required));
            workerIdInput.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError(getString(R.string.password_required));
            passwordInput.requestFocus();
            return;
        }

        if (debugLocalApi && Build.VERSION.SDK_INT >= 37
                && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        != PackageManager.PERMISSION_GRANTED) {
            requestLocalNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK);
            return;
        }

        setLoading(true);
        loginExecutor.execute(() -> {
            Result<LoggedInUser> result = loginRepository.login(workerId, password);
            boolean locationsFailed = false;
            if (result instanceof Result.Success) {
                LoggedInUser user = (LoggedInUser) ((Result.Success<?>) result).getData();
                try {
                    localReports.replaceRemoteLocations(
                            locationApi.fetchActiveLocations(user.getAccessToken()));
                } catch (IOException | RuntimeException error) {
                    locationsFailed = true;
                }
            }
            boolean showLocationWarning = locationsFailed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                if (result instanceof Result.Success) {
                    ReportSyncWorker.enqueueNow(this);
                    passwordInput.getText().clear();
                    if (showLocationWarning) {
                        Toast.makeText(this, R.string.locations_refresh_failed,
                                Toast.LENGTH_LONG).show();
                    }
                    openLandingPage();
                } else {
                    String message = ((Result.Error) result).getError().getMessage();
                    showLoginError(message == null ? getString(R.string.login_failed) : message);
                }
            });
        });
    }

    private void openLandingPage() {
        startActivity(new Intent(this, LandingPageActivity.class));
        finish();
    }

    private void showLoginError(String message) {
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        workerIdInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        continueOfflineButton.setEnabled(!loading);
        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        loginExecutor.shutdownNow();
        super.onDestroy();
    }
}
