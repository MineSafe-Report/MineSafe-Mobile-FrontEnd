package com.example.javareportingsystem.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

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
        version = 1,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

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
                            "mine_safety_reports.db").build();
                }
            }
        }
        return instance;
    }
}
