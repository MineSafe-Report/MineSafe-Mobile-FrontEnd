package com.example.javareportingsystem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.javareportingsystem.R;

public class MyReportsActivity extends AppCompatActivity {

    /**
     *
     * DATABASE INTEGRATION PROMPT: add code to the MyReportsActivity that ensures a report is clickable
     and when it is clicked it returns all data from a sql lite reports table associated with the signed-in user.
     *
     *
     1. Create DatabaseHelper.java
     *
     *
     public class DatabaseHelper extends SQLiteOpenHelper {

     private static final String DATABASE_NAME = "reports.db";
     private static final int DATABASE_VERSION = 1;

     public static final String TABLE_REPORTS = "reports";

     public DatabaseHelper(Context context) {
     super(context, DATABASE_NAME, null, DATABASE_VERSION);
     }

     @Override
     public void onCreate(SQLiteDatabase db) {

     String createTable = "CREATE TABLE " + TABLE_REPORTS + " ("
     + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
     + "user_id TEXT NOT NULL, "
     + "report_number INTEGER NOT NULL, "
     + "title TEXT, "
     + "status TEXT, "
     + "description TEXT, "
     + "created_at TEXT"
     + ")";

     db.execSQL(createTable);
     }

     @Override
     public void onUpgrade(
     SQLiteDatabase db,
     int oldVersion,
     int newVersion) {

     // Add database migration logic here when the schema changes.
     }

     // Retrieve every column for the selected user's report.
     public Cursor getReport(int userId, int reportNumber) {

     SQLiteDatabase db = this.getReadableDatabase();

     return db.query(
     TABLE_REPORTS,
     null, // null returns all columns
     "user_id = ? AND report_number = ?",
     new String[]{
     String.valueOf(userId),
     String.valueOf(reportNumber)
     },
     null,
     null,
     null
     );
     }

     // Retrieve every column for the selected user's report.
     public Cursor getReport(String userId, int reportNumber) {

     SQLiteDatabase db = this.getReadableDatabase();

     return db.query(
     TABLE_REPORTS,
     null,
     "user_id = ? AND report_number = ?",
     new String[]{
     userId,
     String.valueOf(reportNumber)
     },
     null,
     null,
     null
     );
     }

     // Example method for inserting a report.
     public long insertReport(
     String userId,
     int reportNumber,
     String title,
     String status,
     String description,
     String createdAt) {

     SQLiteDatabase db = this.getWritableDatabase();

     ContentValues values = new ContentValues();
     values.put("user_id", userId);
     values.put("report_number", reportNumber);
     values.put("title", title);
     values.put("status", status);
     values.put("description", description);
     values.put("created_at", createdAt);

     return db.insert(TABLE_REPORTS, null, values);
     }
     }

     *
      *
      *
     2. Create MainActivity.java
      *
      *
     public class MainActivity extends AppCompatActivity {

     private DatabaseHelper databaseHelper;

     @Override
     protected void onCreate(Bundle savedInstanceState) {
     super.onCreate(savedInstanceState);
     setContentView(R.layout.activity_main);

     databaseHelper = new DatabaseHelper(this);

     // Make each report row clickable.
     findViewById(R.id.row4).setOnClickListener(
     v -> loadReport(4));

     findViewById(R.id.row3).setOnClickListener(
     v -> loadReport(3));

     findViewById(R.id.row2).setOnClickListener(
     v -> loadReport(2));

     findViewById(R.id.row1).setOnClickListener(
     v -> loadReport(1));

     Button btnBack = findViewById(R.id.btnBack);
     btnBack.setOnClickListener(v -> finish());
     }

     private void loadReport(int reportNumber) {

     // Get the ID saved by your login screen.
     String userId = getSharedPreferences(
     "MyAppPrefs",
     MODE_PRIVATE
     ).getString("signed_in_user_id", null);

     if (userId == null || userId.trim().isEmpty()) {
     Toast.makeText(
     this,
     "Please sign in first.",
     Toast.LENGTH_SHORT
     ).show();
     return;
     }

     Cursor cursor = null;

     try {
     cursor = databaseHelper.getReport(
     userId,
     reportNumber
     );

     if (cursor == null || cursor.getCount() == 0) {
     Toast.makeText(
     this,
     "No report found.",
     Toast.LENGTH_SHORT
     ).show();
     return;
     }

     StringBuilder reportData = new StringBuilder();

     // Read every matching record.
     while (cursor.moveToNext()) {

     // Read every column in the table.
     for (int i = 0; i < cursor.getColumnCount(); i++) {

     String columnName =
     cursor.getColumnName(i);

     String value = cursor.isNull(i)
     ? "Not available"
     : cursor.getString(i);

     reportData.append(columnName)
     .append(": ")
     .append(value)
     .append("\n");
     }

     reportData.append("\n----------------\n\n");
     }

     new AlertDialog.Builder(this)
     .setTitle("Report " + reportNumber)
     .setMessage(reportData.toString())
     .setPositiveButton("Close", null)
     .show();

     } catch (Exception e) {

     Toast.makeText(
     this,
     "Unable to load report: " + e.getMessage(),
     Toast.LENGTH_LONG
     ).show();

     } finally {
     if (cursor != null) {
     cursor.close();
     }
     }
     }

     @Override
     protected void onDestroy() {
     super.onDestroy();

     if (databaseHelper != null) {
     databaseHelper.close();
     }
     }
     }
     *
      *
      *
     3. Save the signed-in user's ID after login as a shared preference.
     // userId must be the ID of the authenticated database user.
     getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
     .edit()
     .putString("signed_in_user_id", String.valueOf(userId))
     .apply();
     Replace userId with the actual ID retrieved from your login query. Do not use the username or a hardcoded ID unless that is how your database identifies users.

     When the user logs out, clear the saved ID:

     java
     getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
     .edit()
     .remove("signed_in_user_id")
     .apply();

      *
      *
      *
     * */
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_reports);

        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}