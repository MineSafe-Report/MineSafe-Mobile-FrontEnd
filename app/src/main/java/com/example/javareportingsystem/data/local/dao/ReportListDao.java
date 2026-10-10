package com.example.javareportingsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.example.javareportingsystem.data.local.ReportListItem;

import java.util.List;

@Dao
public interface ReportListDao {
    @Query("SELECT 'OFFLINE' AS source, localId AS localReportId, "
            + "NULL AS serverReportId, typeCode, syncState AS statusCode, "
            + "createdAtEpochMillis, anonymous FROM offline_reports "
            + "UNION ALL "
            + "SELECT 'SYNCED' AS source, sourceLocalReportId AS localReportId, "
            + "serverReportId, typeCode, workflowStatus AS statusCode, "
            + "createdAtEpochMillis, anonymous FROM synced_reports "
            + "ORDER BY createdAtEpochMillis DESC")
    LiveData<List<ReportListItem>> observeAll();
}
