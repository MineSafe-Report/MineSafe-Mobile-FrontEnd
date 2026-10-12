package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;

public class LandingPageActivity extends AppCompatActivity {
/*

// Run this only after successful authentication in LoginActivity.
String role = userRole; // Role retrieved from your database

Intent intent = new Intent(LoginActivity.this, LandingPageActivity.class);

intent.putExtra("USER_ROLE", role);

startActivity(intent);
finish();
*/


    private Button btnCreateReport;
    private Button btnReviewReports;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_landing_page);

        //Initialise Views
        btnCreateReport = findViewById(R.id.btnCreateReport);
        btnReviewReports = findViewById(R.id.btnReviewReports);

        //Get role from LoginActivity
        String role = getIntent().getStringExtra("USER_ROLE");

        //Default button visibility
        //btnReviewReports.setVisibility(View.GONE);

        //Display button based on role
        if ("SUPERVISOR".equals(role)) {
            btnReviewReports.setVisibility(View.VISIBLE);
        }

        //btnCreateReport
        btnCreateReport.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LandingPageActivity.this,
                    ReportTypeActivity.class
            );
            startActivity(intent);
            finish();
        });

        //btnReviewReports ClickListener

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}