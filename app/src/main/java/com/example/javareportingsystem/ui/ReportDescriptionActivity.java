package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;

public class ReportDescriptionActivity extends AppCompatActivity {

    private Button btnBack;
    private EditText txtDescription;
    private Button btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_description);

        btnBack = findViewById(R.id.btnBack);
        txtDescription = findViewById(R.id.txtDescription);
        btnNext = findViewById(R.id.btnNext);

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ReportDescriptionActivity.this, LocationSelectionActivity.class);
            startActivity(intent);
            finish();
        });

        // Handle the "Next" button click
        btnNext.setOnClickListener(v -> {
            String description =
                    txtDescription.getText().toString().trim();

            if (description.isEmpty()) {
                txtDescription.setError(
                        "Please enter a description"
                );
                txtDescription.requestFocus();
            } else {
                Toast.makeText(
                        ReportDescriptionActivity.this,
                        "Description saved",
                        Toast.LENGTH_SHORT
                ).show();

                // Navigate to your next screen here.

            }
        });


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}