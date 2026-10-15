package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.LandingPageActivity;

public class ReportOverviewActivity extends AppCompatActivity {

    private Button btnReportType;
    private Button btnLocations;
    private Button btnEditDescription;
    private Button btnEditImages;
    private Button btnSubmitReport;
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

        btnReportType = findViewById(R.id.btnReportType);
        btnLocations = findViewById(R.id.btnLocations);
        btnEditDescription = findViewById(R.id.btnEditDescription);
        btnEditImages = findViewById(R.id.btnEditImages);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);


        btnReportType.setOnClickListener(v -> {
            // Handle Report Type button click
            Intent intent = ReportDraftNavigation.to(this, ReportTypeActivity.class, draftId);
            startActivity(intent);
        });

        btnLocations.setOnClickListener(v -> {
            // Handle Locations button click
            Intent intent = ReportDraftNavigation.to(this, LocationSelectionActivity.class, draftId);
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

        btnSubmitReport.setOnClickListener(v -> {
            // Handle Submit Report button click
            showMessage("Report submitted successfully!");
            Intent intent = new Intent(
                    ReportOverviewActivity.this,
                    LandingPageActivity.class
            );
            startActivity(intent);
        });



        // Disable the back button
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        // Block the back button
                    }
                }
        );




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void showMessage(String s) {
        //Implement code to check if all data is valid before showing this message.

        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}