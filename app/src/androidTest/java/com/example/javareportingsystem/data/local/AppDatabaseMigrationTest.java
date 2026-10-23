package com.example.javareportingsystem.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// APP DATABASE MIGRATION TEST
// Checks that saved drafts survive a database upgrade.

@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigrationTest
{
    @Test
    public void versionOneDraftSurvivesUpgradeToVersionTwo() throws Exception
    {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String databaseName = "migration-test-" + UUID.randomUUID() + ".db";
        try
        {
            JSONObject schema = loadVersionOneSchema();
            SQLiteDatabase oldDatabase = context.openOrCreateDatabase(
                databaseName, Context.MODE_PRIVATE, null);
            try
            {
                oldDatabase.beginTransaction();
                try
                {
                    JSONArray entities = schema.getJSONArray("entities");
                    for (int index = 0; index < entities.length(); index++)
                    {
                        JSONObject entity = entities.getJSONObject(index);
                        String name = entity.getString("tableName");
                        oldDatabase.execSQL(entity.getString("createSql")
                            .replace("${TABLE_NAME}", name));
                        JSONArray indices = entity.optJSONArray("indices");
                        if (indices != null)
                        {
                            for (int item = 0; item < indices.length(); item++)
                            {
                                oldDatabase.execSQL(indices.getJSONObject(item)
                                    .getString("createSql")
                                    .replace("${TABLE_NAME}", name));
                            }
                        }
                    }
                    JSONArray setup = schema.getJSONArray("setupQueries");
                    for (int index = 0; index < setup.length(); index++)
                    {
                        oldDatabase.execSQL(setup.getString(index));
                    }
                    oldDatabase.execSQL("INSERT INTO local_locations "
                        + "(localId, code, name, active, configuredAtEpochMillis) "
                        + "VALUES (1, 'LOC-1', 'Shaft A', 1, 100)");
                    oldDatabase.execSQL("INSERT INTO offline_reports "
                        + "(localId, typeCode, locationLocalId, description, "
                        + "occurredAtEpochMillis, createdAtEpochMillis, anonymous, "
                        + "syncState, uploadAttemptCount) VALUES "
                        + "(7, 'HAZARD', 1, 'Loose rock', 100, 100, 1, 'DRAFT', 0)");
                    oldDatabase.setVersion(1);
                    oldDatabase.setTransactionSuccessful();
                }
                finally
                {
                    oldDatabase.endTransaction();
                }
            }
            finally
            {
                oldDatabase.close();
            }

            AppDatabase upgraded = Room.databaseBuilder(
                    context, AppDatabase.class, databaseName)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build();
            try
            {
                OfflineReportEntity draft = upgraded.offlineReportDao().getReport(7);
                assertNotNull(draft);
                assertEquals("Loose rock", draft.description);
                assertEquals(1L, draft.locationLocalId.longValue());
                assertEquals(ReportValues.STEP_LOCATION, draft.currentStep);
            }
            finally
            {
                upgraded.close();
            }
        }
        finally
        {
            context.deleteDatabase(databaseName);
        }
    }

    private static JSONObject loadVersionOneSchema() throws Exception
    {
        String path = "com.example.javareportingsystem.data.local.AppDatabase/1.json";
        try (InputStream input = InstrumentationRegistry.getInstrumentation()
            .getContext().getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, count);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8))
                .getJSONObject("database");
        }
    }
}
