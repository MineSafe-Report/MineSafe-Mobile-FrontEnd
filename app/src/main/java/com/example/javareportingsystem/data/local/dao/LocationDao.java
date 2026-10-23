package com.example.javareportingsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.javareportingsystem.data.local.entity.LocationEntity;

import java.util.List;

//LOCATION DAO
//Locations are saved and read here

@Dao

public interface LocationDao
{
    @Insert
    long insert(LocationEntity location);

    @Update
    int update(LocationEntity location);

    @Query("SELECT * FROM local_locations WHERE localId = :localId")
    LocationEntity get(long localId);

    @Query("SELECT * FROM local_locations WHERE code = :code")
    LocationEntity getByCode(String code);

    @Query("SELECT * FROM local_locations WHERE serverLocationId = :serverLocationId")
    LocationEntity getByServerId(String serverLocationId);

    @Query("UPDATE local_locations SET active = 0 WHERE serverLocationId IS NOT NULL")
    void deactivateRemoteLocations();

    @Query("SELECT * FROM local_locations WHERE active = 1 ORDER BY name")
    List<LocationEntity> listActive();

    @Query("SELECT * FROM local_locations WHERE active = 1 ORDER BY name")
    LiveData<List<LocationEntity>> observeActive();
}
