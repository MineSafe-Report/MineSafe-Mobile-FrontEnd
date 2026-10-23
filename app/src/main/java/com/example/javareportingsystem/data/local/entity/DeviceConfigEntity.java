package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

//DEVICE CONFIG ENTITY
//Local setings of the device is stored

@Entity(tableName = "device_config")

public class DeviceConfigEntity
{
    @PrimaryKey
    public int id = 1;

    @NonNull
    public String installationUuid = "";

    public String serverDeviceId;
    public long installedAtEpochMillis;
    public Long lastLocationConfigAtEpochMillis;
}
