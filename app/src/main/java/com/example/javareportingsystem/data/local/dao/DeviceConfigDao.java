package com.example.javareportingsystem.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.javareportingsystem.data.local.entity.DeviceConfigEntity;

@Dao
public interface DeviceConfigDao
{
    @Query("SELECT * FROM device_config WHERE id = 1")
    DeviceConfigEntity get();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfAbsent(DeviceConfigEntity config);

    @Query("UPDATE device_config SET serverDeviceId = :serverDeviceId WHERE id = 1")
    void setServerDeviceId(String serverDeviceId);

    @Query("UPDATE device_config SET lastLocationConfigAtEpochMillis = :time WHERE id = 1")
    void setLastLocationConfigAt(long time);
}
