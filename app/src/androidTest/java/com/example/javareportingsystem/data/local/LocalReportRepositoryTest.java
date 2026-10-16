package com.example.javareportingsystem.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.javareportingsystem.data.local.entity.DeviceConfigEntity;
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;
import com.example.javareportingsystem.data.local.entity.SyncedReportEntity;
import com.example.javareportingsystem.data.model.RemoteLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class LocalReportRepositoryTest {
    private Context context;
    private String databaseName;
    private AppDatabase database;
    private LocalReportRepository repository;
    private File testMedia;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "reports-test-" + UUID.randomUUID() + ".db";
        database = Room.databaseBuilder(context, AppDatabase.class, databaseName).build();
        repository = new LocalReportRepository(context, database);
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(databaseName);
        if (testMedia != null) {
            testMedia.delete();
        }
    }

    @Test
    public void offlineReportWaitsForEveryAttachmentThenSurvivesRestart() throws Exception {
        DeviceConfigEntity config = repository.getOrCreateDeviceConfig();
        assertNotNull(config.installationUuid);
        assertNull(config.serverDeviceId);
        long locationId = repository.configureLocation(
                "SHAFT-A", "Shaft A", null, true);

        repository.setServerDeviceId("dev-101");
        DeviceConfigEntity registered = repository.getOrCreateDeviceConfig();
        assertEquals(config.installationUuid, registered.installationUuid);
        assertEquals("dev-101", registered.serverDeviceId);
        assertNotNull(registered.lastLocationConfigAtEpochMillis);

        long reportId = repository.createDraft(ReportValues.HAZARD);
        repository.saveDraftDetails(reportId, locationId, "Loose rock above walkway",
                1791622800000L);
        testMedia = new File(repository.getMediaDirectory(),
                "test-" + UUID.randomUUID() + ".jpg");
        try (FileOutputStream output = new FileOutputStream(testMedia)) {
            output.write(new byte[] {1, 2, 3});
        }
        long attachmentId = repository.addPrivateAttachment(reportId, ReportValues.PHOTO,
                ReportValues.CAMERA, testMedia, "image/jpeg");
        repository.submitDraft(reportId);
        assertEquals(ReportValues.PENDING_UPLOAD,
                repository.getOfflineReport(reportId).syncState);

        repository.markReportAccepted(reportId, "rpt-101", "MS-101", 1791623100000L);
        assertThrows(IllegalStateException.class,
                () -> repository.finishSync(reportId, "PENDING_REVIEW", 1791623100000L));
        assertNotNull(repository.getOfflineReport(reportId));
        assertNull(repository.getSyncedReport("rpt-101"));

        repository.markAttachmentAccepted(attachmentId, "att-101", 1791623200000L);
        repository.finishSync(reportId, "PENDING_REVIEW", 1791623200000L);
        assertNull(repository.getOfflineReport(reportId));

        database.close();
        database = Room.databaseBuilder(context, AppDatabase.class, databaseName).build();
        repository = new LocalReportRepository(context, database);
        SyncedReportEntity synced = repository.getSyncedReport("rpt-101");
        assertNotNull(synced);
        assertTrue(synced.anonymous);
        assertNull(synced.reporterAccountId);
        assertEquals("SHAFT-A", synced.locationCode);
        assertEquals("PENDING_REVIEW", synced.workflowStatus);
        assertEquals(1, repository.getSyncedAttachments("rpt-101").size());
        assertTrue(testMedia.exists());
    }

    @Test
    public void draftNeedsLocationAndDescriptionOrVoiceNote() throws Exception {
        long reportId = repository.createDraft(ReportValues.NEAR_MISS);
        assertThrows(IllegalStateException.class, () -> repository.submitDraft(reportId));
        assertEquals(ReportValues.DRAFT, repository.getOfflineReport(reportId).syncState);

        long locationId = repository.configureLocation("LEVEL-2", "Level 2", null, true);
        repository.saveDraftDetails(reportId, locationId, null, System.currentTimeMillis());
        assertThrows(IllegalStateException.class, () -> repository.submitDraft(reportId));

        testMedia = new File(repository.getMediaDirectory(),
                "test-" + UUID.randomUUID() + ".m4a");
        try (FileOutputStream output = new FileOutputStream(testMedia)) {
            output.write(new byte[] {4, 5, 6});
        }
        repository.addPrivateAttachment(reportId, ReportValues.VOICE_NOTE,
                ReportValues.MICROPHONE, testMedia, "audio/mp4");
        repository.submitDraft(reportId);
        OfflineReportEntity submitted = repository.getOfflineReport(reportId);
        assertEquals(ReportValues.PENDING_UPLOAD, submitted.syncState);
        assertTrue(submitted.anonymous);
    }

    @Test
    public void failedIncidentUploadCanBeRetriedWithoutLosingLocalReport() {
        long locationId = repository.configureLocation("PIT-1", "Pit 1", null, true);
        long reportId = repository.createDraft(ReportValues.INCIDENT);
        repository.saveDraftDetails(reportId, locationId, "Worker slipped",
                System.currentTimeMillis());
        repository.identifyIncident(reportId, "WORKER001", null);
        repository.submitDraft(reportId);

        repository.recordReportUploadFailure(reportId, "No connection");
        OfflineReportEntity pending = repository.getOfflineReport(reportId);
        assertEquals(ReportValues.FAILED, pending.syncState);
        assertEquals(1, pending.uploadAttemptCount);
        assertFalse(pending.anonymous);

        repository.markReportAccepted(reportId, "rpt-202", "MS-202",
                System.currentTimeMillis());
        repository.finishSync(reportId, "PENDING_REVIEW", System.currentTimeMillis());
        assertNull(repository.getOfflineReport(reportId));
        assertEquals("WORKER001",
                repository.getSyncedReport("rpt-202").reporterWorkerId);
    }

    @Test
    public void draftStepUpdatesSurviveRestartAndTypeChangeClearsIdentity() {
        long locationId = repository.configureLocation("SHAFT-B", "Shaft B", null, true);
        long reportId = repository.createDraft(ReportValues.INCIDENT);
        repository.saveDraftDescription(reportId, "A worker slipped");
        repository.saveDraftLocation(reportId, locationId);
        repository.identifyIncident(reportId, "WORKER001", 42);

        database.close();
        database = Room.databaseBuilder(context, AppDatabase.class, databaseName).build();
        repository = new LocalReportRepository(context, database);
        OfflineReportEntity reopened = repository.getOfflineReport(reportId);
        assertEquals(locationId, reopened.locationLocalId.longValue());
        assertEquals("A worker slipped", reopened.description);
        assertEquals("WORKER001", reopened.reporterWorkerId);

        repository.changeDraftType(reportId, ReportValues.NEAR_MISS);
        OfflineReportEntity anonymous = repository.getOfflineReport(reportId);
        assertEquals(ReportValues.NEAR_MISS, anonymous.typeCode);
        assertTrue(anonymous.anonymous);
        assertNull(anonymous.reporterWorkerId);
        assertNull(anonymous.reporterAccountId);
        assertEquals(locationId, anonymous.locationLocalId.longValue());
        assertEquals("A worker slipped", anonymous.description);
    }

    @Test
    public void remoteLocationRefreshKeepsDraftReferenceAndStepAfterRestart() {
        repository.replaceRemoteLocations(Arrays.asList(
                new RemoteLocation("loc-1", "LOC-1", "Shaft A"),
                new RemoteLocation("loc-2", "LOC-2", "Processing Plant")));
        long locationId = -1L;
        for (com.example.javareportingsystem.data.local.entity.LocationEntity location
                : repository.listActiveLocations()) {
            if ("loc-1".equals(location.serverLocationId)) {
                locationId = location.localId;
            }
        }
        assertTrue(locationId > 0);
        long reportId = repository.createDraft(ReportValues.HAZARD);
        repository.saveDraftLocation(reportId, locationId);
        repository.setDraftStep(reportId, ReportValues.STEP_DESCRIPTION);

        repository.replaceRemoteLocations(Collections.singletonList(
                new RemoteLocation("loc-1", "LOC-1", "Shaft A Updated")));
        assertEquals(1, repository.listActiveLocations().size());
        assertEquals(locationId, repository.listActiveLocations().get(0).localId);
        assertEquals("Shaft A Updated", repository.listActiveLocations().get(0).name);

        database.close();
        database = Room.databaseBuilder(context, AppDatabase.class, databaseName).build();
        repository = new LocalReportRepository(context, database);
        OfflineReportEntity resumed = repository.getLatestDraft();
        assertNotNull(resumed);
        assertEquals(reportId, resumed.localId);
        assertEquals(locationId, resumed.locationLocalId.longValue());
        assertEquals(ReportValues.STEP_DESCRIPTION, resumed.currentStep);
    }

    @Test
    public void multipleDraftsCanBeListedAndChosenByTheirOwnId() {
        long first = repository.createDraft(ReportValues.HAZARD);
        long second = repository.createDraft(ReportValues.INCIDENT);
        long third = repository.createDraft(ReportValues.NEAR_MISS);

        assertEquals(3, repository.listDrafts().size());
        assertEquals(third, repository.listDrafts().get(0).localId);
        assertEquals(second, repository.listDrafts().get(1).localId);
        assertEquals(first, repository.listDrafts().get(2).localId);
        assertEquals(ReportValues.INCIDENT, repository.getOfflineReport(second).typeCode);
        assertEquals(ReportValues.HAZARD, repository.getOfflineReport(first).typeCode);
    }

    @Test
    public void addingVoiceNotesKeepsEarlierRecordingsUntilIndividuallyDeleted() throws Exception {
        long reportId = repository.createDraft(ReportValues.HAZARD);
        File first = new File(repository.getMediaDirectory(),
                "first-" + UUID.randomUUID() + ".m4a");
        File second = new File(repository.getMediaDirectory(),
                "second-" + UUID.randomUUID() + ".m4a");
        try {
            try (FileOutputStream output = new FileOutputStream(first)) {
                output.write(new byte[] {1, 2, 3});
            }
            try (FileOutputStream output = new FileOutputStream(second)) {
                output.write(new byte[] {4, 5, 6});
            }
            long oldId = repository.addPrivateAttachment(reportId, ReportValues.VOICE_NOTE,
                    ReportValues.MICROPHONE, first, "audio/mp4");
            long newId = repository.addPrivateAttachment(reportId, ReportValues.VOICE_NOTE,
                    ReportValues.MICROPHONE, second, "audio/mp4");

            assertTrue(newId > oldId);
            assertTrue(first.exists());
            assertTrue(second.exists());
            assertEquals(2, repository.getOfflineAttachments(reportId).size());
            assertEquals(oldId, repository.getOfflineAttachments(reportId).get(0).localId);
            assertEquals(newId, repository.getOfflineAttachments(reportId).get(1).localId);

            repository.removeDraftAttachment(oldId);
            assertFalse(first.exists());
            assertTrue(second.exists());
            assertEquals(1, repository.getOfflineAttachments(reportId).size());
            assertEquals(newId, repository.getOfflineAttachments(reportId).get(0).localId);
        } finally {
            first.delete();
            second.delete();
        }
    }
    @Test
    public void myReportsListsSubmittedAndSyncedReportsButNotDrafts() throws Exception {
        repository.createDraft(ReportValues.NEAR_MISS);
        long location = repository.configureLocation("TEST", "Test location", null, true);

        long pendingId = repository.createDraft(ReportValues.HAZARD);
        repository.saveDraftDetails(pendingId, location, "Loose rock", System.currentTimeMillis());
        repository.submitDraft(pendingId);

        long syncedId = repository.createDraft(ReportValues.INCIDENT);
        repository.saveDraftDetails(syncedId, location, "Minor injury", System.currentTimeMillis());
        repository.submitDraft(syncedId);
        repository.markReportAccepted(syncedId, "server-test", "TEST-1",
                System.currentTimeMillis());
        repository.finishSync(syncedId, "APPROVED", System.currentTimeMillis());

        LiveData<List<ReportListItem>> reports = database.reportListDao().observeAll();
        AtomicReference<List<ReportListItem>> result = new AtomicReference<>();
        CountDownLatch loaded = new CountDownLatch(1);
        Observer<List<ReportListItem>> observer = value -> {
            result.set(value);
            loaded.countDown();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> reports.observeForever(observer));
        try {
            assertTrue("Report list never loaded", loaded.await(5, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertEquals(2, result.get().size());
            assertTrue(result.get().stream().anyMatch(item ->
                    item.localReportId == pendingId
                            && ReportValues.PENDING_UPLOAD.equals(item.statusCode)));
            assertTrue(result.get().stream().anyMatch(item ->
                    item.localReportId == syncedId && "APPROVED".equals(item.statusCode)));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> reports.removeObserver(observer));
        }
    }

}
