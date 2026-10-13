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
import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.model.LoggedInUser;
import com.example.javareportingsystem.ui.ReportTypeActivity;

public class LandingPageActivity extends AppCompatActivity {

    private LoginRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LoginRepository existingRepository = LoginRepository.getExistingInstance();
        repository = existingRepository != null
                ? existingRepository
                : LoginRepository.getInstance(this,
                        new ApiLoginDataSource(getString(R.string.login_api_base_url)));

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_landing_page);
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
        findViewById(R.id.btnCreateReport).setOnClickListener(v -> {
            startActivity(new Intent(this, ReportTypeActivity.class));
            finish();
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        refreshSessionUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSessionUi();
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
}
