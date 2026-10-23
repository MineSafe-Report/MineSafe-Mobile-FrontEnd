package com.example.javareportingsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.javareportingsystem.data.local.entity.SyncedAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;

import java.util.List;

//SYNCED REPORT DAO
//Saves the reports that are eccepetd by the serve, it also saves the reports

@Dao

public interface SyncedReportDao
{
    @Insert
    void insertReport(SyncedReportEntity report);

    @Insert
    void insertAttachment(SyncedAttachmentEntity attachment);

    @Update
    int updateReport(SyncedReportEntity report);

    @Query("SELECT * FROM synced_reports WHERE serverReportId = :serverReportId")
    SyncedReportEntity getReport(String serverReportId);

    @Query("SELECT * FROM synced_reports ORDER BY createdAtEpochMillis DESC")
    LiveData<List<SyncedReportEntity>> observeReports();

    @Query("SELECT * FROM synced_attachments WHERE serverReportId = :serverReportId "
        + "ORDER BY sourceLocalAttachmentId")
    List<SyncedAttachmentEntity> listAttachments(String serverReportId);
}
