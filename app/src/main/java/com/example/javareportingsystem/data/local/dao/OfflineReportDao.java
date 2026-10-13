package com.example.javareportingsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.util.List;

@Dao
public interface OfflineReportDao {
    @Insert
    long insertReport(OfflineReportEntity report);

    @Update
    int updateReport(OfflineReportEntity report);

    @Delete
    int deleteReport(OfflineReportEntity report);

    @Query("SELECT * FROM offline_reports WHERE localId = :localId")
    OfflineReportEntity getReport(long localId);

    @Query("SELECT * FROM offline_reports WHERE syncState = 'DRAFT' "
            + "ORDER BY createdAtEpochMillis DESC, localId DESC LIMIT 1")
    OfflineReportEntity getLatestDraft();

    @Query("SELECT * FROM offline_reports WHERE syncState = 'DRAFT' "
            + "ORDER BY createdAtEpochMillis DESC, localId DESC")
    List<OfflineReportEntity> listDrafts();

    @Query("SELECT * FROM offline_reports ORDER BY createdAtEpochMillis DESC")
    LiveData<List<OfflineReportEntity>> observeReports();

    @Query("SELECT * FROM offline_reports WHERE syncState != 'DRAFT' "
            + "ORDER BY createdAtEpochMillis ASC")
    List<OfflineReportEntity> listSubmittedReports();

    @Insert
    long insertAttachment(OfflineAttachmentEntity attachment);

    @Update
    int updateAttachment(OfflineAttachmentEntity attachment);

    @Delete
    int deleteAttachment(OfflineAttachmentEntity attachment);

    @Query("SELECT * FROM offline_attachments WHERE localId = :localId")
    OfflineAttachmentEntity getAttachment(long localId);

    @Query("SELECT * FROM offline_attachments WHERE reportLocalId = :reportLocalId "
            + "ORDER BY localId")
    List<OfflineAttachmentEntity> listAttachments(long reportLocalId);

    @Query("SELECT COUNT(*) FROM offline_attachments WHERE reportLocalId = :reportLocalId "
            + "AND attachmentType = 'VOICE_NOTE'")
    int countVoiceNotes(long reportLocalId);

    @Query("SELECT COUNT(*) FROM offline_attachments WHERE reportLocalId = :reportLocalId "
            + "AND serverAttachmentId IS NULL")
    int countUnuploadedAttachments(long reportLocalId);
}
