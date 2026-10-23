package com.example.javareportingsystem;

import android.Manifest;
import android.content.Intent;
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
import com.example.javareportingsystem.ui.ReportsWaitingActivity;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// LOGIN ACTIVITY
// Handles sign in and opens the correct home screen.

public class LoginActivity extends AppCompatActivity
{
    // BACKGROUND WORK
    // Keeps login requests off the screen thread.

    private final ExecutorService loginExecutor = Executors.newSingleThreadExecutor();

    // LOCAL NETWORK PERMISSION
    // Continues login after Android grants access to a local server.

    private final ActivityResultLauncher<String> requestLocalNetworkPermission =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
        {
            if (granted)
            {
                attemptLogin();
            }
            else
            {
                showLoginError(getString(R.string.local_network_permission_required));
            }
        });

    // SCREEN CONTROLS

    private EditText workerIdInput, passwordInput;
    private Button loginButton, continueOfflineButton;
    private ProgressBar loginProgress;
    private TextView loginError;

    // DATA ACCESS

    private LoginRepository loginRepository;
    private ApiLocationDataSource locationApi;
    private LocalReportRepository localReports;

    // NETWORK SETTINGS

    private boolean localNetworkApi;

    // SCREEN SETUP AND SESSION RESTORE
    // Restores a saved login when its token is still valid.

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(R.style.Theme_JavaReportingSystem);
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main),
            (view, insets) ->
            {
                Insets systemBars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());

                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom);

                return insets;
            });

        workerIdInput = findViewById(R.id.workerIdInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        continueOfflineButton = findViewById(R.id.continueOfflineButton);
        loginProgress = findViewById(R.id.loginProgress);
        loginError = findViewById(R.id.loginError);

        String baseUrl = getString(R.string.api_base_url);

        localNetworkApi = baseUrl.startsWith("http://10.")
            || baseUrl.startsWith("http://192.168.")
            || baseUrl.startsWith("http://172.");

        loginRepository = LoginRepository.getInstance(this, new ApiLoginDataSource(baseUrl));

        locationApi = new ApiLocationDataSource(baseUrl);
        localReports = new LocalReportRepository(this);

        loginButton.setOnClickListener(view -> attemptLogin());
        continueOfflineButton.setOnClickListener(view -> openLandingPage());

        passwordInput.setOnEditorActionListener((view, actionId, event) ->
        {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                attemptLogin();
                return true;
            }

            return false;
        });

        if (loginRepository.isLoggedIn())
        {
            openHomeForUser(loginRepository.getUser());
        }
        else if (loginRepository.getCachedUser() != null)
        {
            setLoading(true);

            loginExecutor.execute(() ->
            {
                LoggedInUser restoredUser = loginRepository.getFreshUser();

                runOnUiThread(() ->
                {
                    if (isFinishing() || isDestroyed())
                    {
                        return;
                    }

                    setLoading(false);

                    if (restoredUser != null)
                    {
                        openHomeForUser(restoredUser);
                    }
                });
            });
        }
    }

    // SIGN IN
    // Checks the form, then logs in on the background worker.

    private void attemptLogin()
    {
        if (loginProgress.getVisibility() == View.VISIBLE)
        {
            return;
        }

        workerIdInput.setError(null);
        passwordInput.setError(null);
        loginError.setVisibility(View.GONE);

        String workerId = workerIdInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (workerId.isEmpty())
        {
            workerIdInput.setError(getString(R.string.worker_id_required));
            workerIdInput.requestFocus();
            return;
        }

        if (password.isEmpty())
        {
            passwordInput.setError(getString(R.string.password_required));
            passwordInput.requestFocus();
            return;
        }

        // Ask before contacting a server on the local network.
        if (localNetworkApi
            && Build.VERSION.SDK_INT >= 37
            && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
            != PackageManager.PERMISSION_GRANTED)
        {

            requestLocalNetworkPermission.launch(
                Manifest.permission.ACCESS_LOCAL_NETWORK);
            return;
        }

        setLoading(true);

        loginExecutor.execute(() ->
        {
            Result<LoggedInUser> result = loginRepository.login(workerId, password);

            boolean locationsFailed = false;

            if (result instanceof Result.Success)
            {
                LoggedInUser user =
                    (LoggedInUser) ((Result.Success<?>) result).getData();

                // Reporting users need locations; Safety Officers do not.
                if (!"SAFETY_OFFICER".equalsIgnoreCase(user.getRole()))
                {
                    try
                    {
                        localReports.replaceRemoteLocations(
                            locationApi.fetchActiveLocations(
                                user.getAccessToken()));
                    }
                    catch (IOException | RuntimeException error)
                    {
                        locationsFailed = true;
                    }
                }
            }

            boolean showLocationWarning = locationsFailed;

            runOnUiThread(() ->
            {
                if (isFinishing() || isDestroyed())
                {
                    return;
                }

                setLoading(false);

                if (result instanceof Result.Success)
                {
                    passwordInput.getText().clear();

                    if (showLocationWarning)
                    {
                        Toast.makeText(
                            this,
                            R.string.locations_refresh_failed,
                            Toast.LENGTH_LONG).show();
                    }

                    LoggedInUser user =
                        (LoggedInUser) ((Result.Success<?>) result).getData();

                    openHomeForUser(user);
                }
                else
                {
                    String message =
                        ((Result.Error) result).getError().getMessage();

                    showLoginError(
                        message == null
                            ? getString(R.string.login_failed)
                            : message);
                }
            });
        });
    }

    private void openLandingPage()
    {
        startActivity(new Intent(this, LandingPageActivity.class));
        finish();
    }

    // HOME ROUTING
    // Safety Officers see incidents; other users see reporting.

    private void openHomeForUser(LoggedInUser user)
    {
        if (user != null
            && "SAFETY_OFFICER".equalsIgnoreCase(user.getRole()))
        {
            startActivity(new Intent(this, ReportsWaitingActivity.class));
            finish();
            return;
        }

        ReportSyncWorker.enqueueNow(this);
        openLandingPage();
    }

    private void showLoginError(String message)
    {
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading)
    {
        workerIdInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        continueOfflineButton.setEnabled(!loading);

        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy()
    {
        loginExecutor.shutdownNow();
        super.onDestroy();
    }
}
