package com.example.javareportingsystem.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import androidx.annotation.NonNull;

import com.example.javareportingsystem.data.local.dao.DeviceConfigDao;
import com.example.javareportingsystem.data.local.dao.LocationDao;
import com.example.javareportingsystem.data.local.dao.OfflineReportDao;
import com.example.javareportingsystem.data.local.dao.ReportListDao;
import com.example.javareportingsystem.data.local.dao.SyncedReportDao;
import com.example.javareportingsystem.data.local.entity.DeviceConfigEntity;
import com.example.javareportingsystem.data.local.entity.LocationEntity;
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;
import com.example.javareportingsystem.data.local.entity.SyncedAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;

@Database(
        entities = {
                DeviceConfigEntity.class,
                LocationEntity.class,
                OfflineReportEntity.class,
                OfflineAttachmentEntity.class,
                SyncedReportEntity.class,
                SyncedAttachmentEntity.class
        },
        version = 2,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE offline_reports ADD COLUMN currentStep "
                    + "TEXT NOT NULL DEFAULT 'LOCATION'");
        }
    };

    public abstract DeviceConfigDao deviceConfigDao();
    public abstract LocationDao locationDao();
    public abstract OfflineReportDao offlineReportDao();
    public abstract SyncedReportDao syncedReportDao();
    public abstract ReportListDao reportListDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "mine_safety_reports.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}
