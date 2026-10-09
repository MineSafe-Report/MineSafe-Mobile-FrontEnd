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

import com.example.javareportingsystem.data.LoginRepository;
import com.example.javareportingsystem.data.model.LoggedInUser;

public class LandingPageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LoginRepository repository = LoginRepository.getExistingInstance();
        if (repository == null || !repository.isLoggedIn()) {
            Intent login = new Intent(this, LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(login);
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_landing_page);
        LoggedInUser user = repository.getUser();
        TextView welcome = findViewById(R.id.tvWelcome);
        welcome.setText(getString(R.string.landing_welcome, user.getDisplayName()));
        Button reviewReports = findViewById(R.id.btnReviewReports);
        reviewReports.setVisibility("SUPERVISOR".equalsIgnoreCase(user.getRole())
                ? View.VISIBLE : View.GONE);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}