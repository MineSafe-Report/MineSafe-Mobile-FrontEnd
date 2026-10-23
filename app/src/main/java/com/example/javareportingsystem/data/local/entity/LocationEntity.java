package com.example.javareportingsystem.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

//LOCATION ENTITY
//singel location storage on device

@Entity(
    tableName = "local_locations",
    indices = {
        @Index(value = "code", unique = true),
        @Index(value = "serverLocationId", unique = true)
    })

public class LocationEntity
{
    @PrimaryKey(autoGenerate = true)
    public long localId;

    @NonNull
    public String code = "";

    @NonNull
    public String name = "";

    public String serverLocationId;
    public boolean active = true;
    public long configuredAtEpochMillis;
}
