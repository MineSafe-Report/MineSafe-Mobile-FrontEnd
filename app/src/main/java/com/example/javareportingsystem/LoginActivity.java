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

public class LoginActivity extends AppCompatActivity
{
    // BACKGROUND WORK
    private final ExecutorService loginExecutor = Executors.newSingleThreadExecutor();

    // LOCAL NETWORK PERMISSION
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
    private LoginRepository loginRepository; //Authentication
    private ApiLocationDataSource locationApi; //API locations
    private LocalReportRepository localReports; //Local data storage

    // NETWORK SETTINGS
    private boolean localNetworkApi;

    // SCREEN SETUP AND SESSION RESTORE
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(R.style.Theme_JavaReportingSystem);
        super.onCreate(savedInstanceState);

        // Load the screen and allow it to use the full display area.
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Add space around the screen content for the system bars.
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

        // Connect the Java fields to controls in the screen layout.
        workerIdInput = findViewById(R.id.workerIdInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        continueOfflineButton = findViewById(R.id.continueOfflineButton);
        loginProgress = findViewById(R.id.loginProgress);
        loginError = findViewById(R.id.loginError);

        // Read the configured API address.
        String baseUrl = getString(R.string.api_base_url);

        // Detect whether the API uses a local network address.
        localNetworkApi = baseUrl.startsWith("http://10.")
            || baseUrl.startsWith("http://192.168.")
            || baseUrl.startsWith("http://172.");

        loginRepository = LoginRepository.getInstance(this, new ApiLoginDataSource(baseUrl));

        locationApi = new ApiLocationDataSource(baseUrl);
        localReports = new LocalReportRepository(this);

        // Set the actions for the buttons and the keyboard.
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

        // Open the home screen if a login session already exists.
        if (loginRepository.isLoggedIn())
        {
            openHomeForUser(loginRepository.getUser());
        }
        else if (loginRepository.getCachedUser() != null)
        {
            // Refresh the saved user on the background worker.
            setLoading(true);

            loginExecutor.execute(() ->
            {
                LoggedInUser restoredUser = loginRepository.getFreshUser();

                // Return to the main thread before changing the screen.
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

    // Checks the form, requests permission if needed, and attempts login.
    private void attemptLogin()
    {
        // Do not start another login while one is already running.
        if (loginProgress.getVisibility() == View.VISIBLE)
        {
            return;
        }

        // Clear errors from the previous attempt.
        workerIdInput.setError(null);
        passwordInput.setError(null);
        loginError.setVisibility(View.GONE);

        // Read the values entered by the user.
        String workerId = workerIdInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        // Require a worker ID.
        if (workerId.isEmpty())
        {
            workerIdInput.setError(getString(R.string.worker_id_required));
            workerIdInput.requestFocus();
            return;
        }

        // Require a password.
        if (password.isEmpty())
        {
            passwordInput.setError(getString(R.string.password_required));
            passwordInput.requestFocus();
            return;
        }

        // Request local network permission before contacting a local server.
        if (localNetworkApi
            && Build.VERSION.SDK_INT >= 37
            && checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
            != PackageManager.PERMISSION_GRANTED)
        {

            requestLocalNetworkPermission.launch(
                Manifest.permission.ACCESS_LOCAL_NETWORK);
            return;
        }

        // Show progress while login and location requests run.
        setLoading(true);

        // Run network work on the background worker.
        loginExecutor.execute(() ->
        {
            Result<LoggedInUser> result = loginRepository.login(workerId, password);

            boolean locationsFailed = false;

            // Fetch locations after login for users who are not safety officers.
            if (result instanceof Result.Success)
            {
                LoggedInUser user =
                    (LoggedInUser) ((Result.Success<?>) result).getData();

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

            // Update the screen on Android's main thread.
            runOnUiThread(() ->
            {
                // Stop if this screen is closing or has already closed.
                if (isFinishing() || isDestroyed())
                {
                    return;
                }

                setLoading(false);

                if (result instanceof Result.Success)
                {
                    // Clear the password after a successful login.
                    passwordInput.getText().clear();

                    // Login can succeed even if locations could not refresh.
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
                    // Show the server error, or a default message if there is none.
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

    // Opens the landing page. The offline button uses this function.
    private void openLandingPage()
    {
        startActivity(new Intent(this, LandingPageActivity.class));
        finish();
    }

    // Sends the user to a screen based on their role.
    private void openHomeForUser(LoggedInUser user)
    {
        if (user != null
            && "SAFETY_OFFICER".equalsIgnoreCase(user.getRole()))
        {
            startActivity(new Intent(this, ReportsWaitingActivity.class));
            finish();
            return;
        }

        // Queue report syncing for other users, then open the landing page.
        ReportSyncWorker.enqueueNow(this);
        openLandingPage();
    }

    // Displays an error message on the login screen.
    private void showLoginError(String message)
    {
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
    }

    // Enables or disables the form and shows or hides the progress indicator.
    private void setLoading(boolean loading)
    {
        workerIdInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        continueOfflineButton.setEnabled(!loading);

        loginProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // Stops the background worker when Android closes this screen.
    @Override
    protected void onDestroy()
    {
        loginExecutor.shutdownNow();
        super.onDestroy();
    }
}
