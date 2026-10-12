package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;

public class ReportTypeActivity extends AppCompatActivity {

    private Button btnBack;
    private Button btnIncident;
    private Button btnNearMiss;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_type);

        // Initialize views
        Button btnBack = findViewById(R.id.btnBack);
        Button btnHazard = findViewById(R.id.btnHazard);
        Button btnIncident = findViewById(R.id.btnIncident);
        Button btnNearMiss = findViewById(R.id.btnNearMiss);

        //Back button to LandingPageActivity
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ReportTypeActivity.this, LandingPageActivity.class);
            startActivity(intent);
            finish();
        });

        //Report Type buttons
        //INCIDENT
        btnIncident.setOnClickListener(v -> {
            showMessage("Incident selected");
            Intent intent = new Intent( ReportTypeActivity.this, LocationSelectionActivity.class);
            startActivity(intent);
            finish();
        });

        //HAZARD
        btnHazard.setOnClickListener(v -> {
            showMessage("Hazard selected");
            Intent intent = new Intent( ReportTypeActivity.this, LocationSelectionActivity.class);
            startActivity(intent);
            finish();
        });

        //NEAR MISS
        btnNearMiss.setOnClickListener(v -> {
            showMessage("Near Miss selected");
            Intent intent = new Intent( ReportTypeActivity.this, LocationSelectionActivity.class);
            startActivity(intent);
            finish();
        });

    }
    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}
