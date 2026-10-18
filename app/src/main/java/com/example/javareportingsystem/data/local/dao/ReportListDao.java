package com.example.javareportingsystem.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.example.javareportingsystem.data.local.ReportListItem;

import java.util.List;

@Dao
public interface ReportListDao {
    @Query("SELECT 'OFFLINE' AS source, o.localId AS localReportId, "
            + "NULL AS serverReportId, o.typeCode, o.syncState AS statusCode, "
            + "o.createdAtEpochMillis, o.anonymous, COALESCE(l.name, '') AS locationName "
            + "FROM offline_reports o LEFT JOIN local_locations l ON l.localId = o.locationLocalId "
            + "WHERE o.syncState != 'DRAFT' "
            + "UNION ALL "
            + "SELECT 'SYNCED' AS source, sourceLocalReportId AS localReportId, "
            + "serverReportId, typeCode, workflowStatus AS statusCode, "
            + "createdAtEpochMillis, anonymous, locationName FROM synced_reports "
            + "ORDER BY createdAtEpochMillis DESC")
    LiveData<List<ReportListItem>> observeAll();
}
