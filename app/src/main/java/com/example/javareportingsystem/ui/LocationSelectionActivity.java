package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;


public class LocationSelectionActivity extends AppCompatActivity {

    private Button btnBack;
    private Button btnLocationDropdown;
    private HorizontalScrollView locationTableContainer;
    private TableLayout locationTable;
    private Button btnNext;

    private boolean isExpanded = false;

    private final String[][] locations = {
            {"001", "Main Office", "Johannesburg"},
            {"002", "Warehouse", "Pretoria"},
            {"003", "Production Plant", "Midrand"},
            {"004", "Storage Facility", "Centurion"},
            {"005", "Site Office", "Sandton"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_location_selection);

        btnBack = findViewById(R.id.btnBack);

        btnLocationDropdown = findViewById(R.id.btnLocationDropdown);

        btnNext = findViewById(R.id.btnNext);

        locationTableContainer = findViewById(R.id.locationTableContainer);

        locationTable = findViewById(R.id.locationTable);


        //Back button to ReportTypeActivity
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(LocationSelectionActivity.this, ReportTypeActivity.class);
            startActivity(intent);
            finish();
        });

        populateLocationTable();

        btnLocationDropdown.setOnClickListener(v -> {
            isExpanded = !isExpanded;

            locationTableContainer.setVisibility(
                    isExpanded ? View.VISIBLE : View.GONE
            );

            if (isExpanded) {
                btnLocationDropdown.setText("Location  ▲");
            } else {
                btnLocationDropdown.setText("Location  ▼");
            }
        });
    }

    private void populateLocationTable() {

        for (String[] location : locations) {

            TableRow row = new TableRow(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.WHITE);
            border.setStroke(1, Color.LTGRAY);
            row.setBackground(border);

            for (String value : location) {

                TextView cell = new TextView(this);
                cell.setText(value);
                cell.setTextColor(Color.BLACK);
                cell.setTextSize(14);
                cell.setPadding(12, 16, 12, 16);

                row.addView(cell);
            }

            row.setClickable(true);
            row.setFocusable(true);

            row.setOnClickListener(v -> {
                String selectedLocation = location[1];

                // Update the dropdown label
                btnLocationDropdown.setText(selectedLocation + "  ▼");
                // Collapse the dropdown
                isExpanded = false;
                locationTableContainer.setVisibility(View.GONE);

                // Show 'Next' only after location selection
                btnNext.setVisibility(View.VISIBLE);

                Toast.makeText(
                        LocationSelectionActivity.this,
                        "Selected: " + selectedLocation,
                        Toast.LENGTH_SHORT
                ).show();
            });

            locationTable.addView(row);
        }

        // Hide Next until a location is selected
        btnNext.setVisibility(View.GONE);

       /* btnNext.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LocationSelectionActivityActivity.this,
                    DescriptionActivity.class
            );

            startActivity(intent);
        });*/




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}