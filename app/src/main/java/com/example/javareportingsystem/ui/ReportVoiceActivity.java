package com.example.javareportingsystem.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;
import com.example.javareportingsystem.data.local.entity.OfflineAttachmentEntity;
import com.example.javareportingsystem.data.local.entity.OfflineReportEntity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Records app-private voice notes for the current draft and provides local playback. */
public class ReportVoiceActivity extends AppCompatActivity {
    private static final float[] PLAYBACK_SPEEDS = {1f, 1.5f, 2f};
    private static final String[] PLAYBACK_SPEED_LABELS = {"1×", "1.5×", "2×"};

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    Toast.makeText(this, R.string.voice_permission_required,
                            Toast.LENGTH_LONG).show();
                }
            });

    private LocalReportRepository reports;
    private Button btnBack;
    private Button btnSaveDraft;
    private Button btnNext;
    private Button btnRecord;
    private TextView status;
    private TextView recordTimer;
    private LinearLayout audioPlayer;
    private long draftId;
    private boolean busy = true;
    private boolean draftLoaded;
    private boolean saveAfterRecording;
    private boolean seeking;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File recordingFile;
    private long recordingStartedAt;
    private final List<OfflineAttachmentEntity> voiceNotes = new ArrayList<>();
    private final List<VoiceRow> voiceRows = new ArrayList<>();
    private VoiceRow activeRow;

    private final Runnable recordClock = new Runnable() {
        @Override
        public void run() {
            if (recorder != null) {
                recordTimer.setText(formatTime(SystemClock.elapsedRealtime()
                        - recordingStartedAt));
                uiHandler.postDelayed(this, 250);
            }
        }
    };
    private final Runnable playbackClock = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                if (!seeking && activeRow != null) {
                    int position = player.getCurrentPosition();
                    activeRow.seekAudio.setProgress(position);
                    activeRow.currentTime.setText(formatTime(position));
                }
                uiHandler.postDelayed(this, 250);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report_voice);
        draftId = ReportDraftNavigation.readId(this);
        if (draftId <= 0) {
            return;
        }
        reports = new LocalReportRepository(this);

        btnBack = findViewById(R.id.btnBack);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnNext = findViewById(R.id.btnNext);
        btnRecord = findViewById(R.id.btnRecord);
        status = findViewById(R.id.tvVoiceStatus);
        recordTimer = findViewById(R.id.tvRecordTimer);
        audioPlayer = findViewById(R.id.audioPlayer);

        btnBack.setOnClickListener(v -> returnToDescription());
        btnSaveDraft.setOnClickListener(v -> saveDraftAndExit());
        btnRecord.setOnClickListener(v -> {
            if (recorder == null) {
                requestRecording();
            } else {
                stopRecording(false);
            }
        });
        btnNext.setOnClickListener(v -> {
            if (draftLoaded && !busy && recorder == null) {
                startActivity(ReportDraftNavigation.to(
                        this, ReportImagesActivity.class, draftId));
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                returnToDescription();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top,
                    systemBars.right, systemBars.bottom);
            return insets;
        });
        updateControls();
        loadVoiceNotes();
    }

    private void loadVoiceNotes() {
        setBusy(true);
        databaseExecutor.execute(() -> {
            try {
                OfflineReportEntity draft = reports.getOfflineReport(draftId);
                if (draft == null || !ReportValues.DRAFT.equals(draft.syncState)) {
                    throw new IllegalStateException("Report draft is missing");
                }
                List<OfflineAttachmentEntity> attachments =
                        reports.getOfflineAttachments(draftId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    draftLoaded = true;
                    showVoiceNotes(attachments);
                    setBusy(false);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, R.string.report_draft_load_failed,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        });
    }

    private void requestRecording() {
        if (busy || !draftLoaded) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (busy || !draftLoaded || recorder != null) {
            return;
        }
        setBusy(true);
        databaseExecutor.execute(() -> {
            try {
                File file = new File(reports.getMediaDirectory(),
                        UUID.randomUUID() + ".m4a");
                runOnUiThread(() -> beginRecorder(file));
            } catch (IOException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setBusy(false);
                        Toast.makeText(this, R.string.voice_record_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void beginRecorder(File file) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            setBusy(false);
            return;
        }
        releasePlayer();
        MediaRecorder candidate = null;
        try {
            candidate = Build.VERSION.SDK_INT >= 31
                    ? new MediaRecorder(this) : new MediaRecorder();
            candidate.setAudioSource(MediaRecorder.AudioSource.MIC);
            candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            candidate.setOutputFile(file.getAbsolutePath());
            candidate.prepare();
            candidate.start();
            recorder = candidate;
            recordingFile = file;
            recordingStartedAt = SystemClock.elapsedRealtime();
            recordTimer.setText(formatTime(0));
            uiHandler.post(recordClock);
            setBusy(false);
            status.setText(R.string.voice_recording);
            updateControls();
        } catch (IOException | RuntimeException error) {
            if (candidate != null) {
                candidate.release();
            }
            file.delete();
            setBusy(false);
            restoreStatus();
            Toast.makeText(this, R.string.voice_record_failed, Toast.LENGTH_SHORT).show();
            updateControls();
        }
    }

    private void stopRecording(boolean returnAfterSave) {
        if (recorder == null) {
            return;
        }
        setBusy(true);
        uiHandler.removeCallbacks(recordClock);
        MediaRecorder active = recorder;
        recorder = null;
        File file = recordingFile;
        recordingFile = null;
        boolean recorded;
        try {
            active.stop();
            recorded = file != null && file.isFile() && file.length() > 0;
        } catch (RuntimeException error) {
            recorded = false;
        } finally {
            active.release();
        }
        recordTimer.setVisibility(View.GONE);
        if (!recorded) {
            if (file != null) {
                file.delete();
            }
            saveAfterRecording = false;
            setBusy(false);
            restoreStatus();
            Toast.makeText(this, R.string.voice_record_failed, Toast.LENGTH_SHORT).show();
            updateControls();
            return;
        }

        status.setText(R.string.voice_saving);
        updateControls();
        databaseExecutor.execute(() -> {
            boolean attachmentSaved = false;
            try {
                reports.addPrivateAttachment(draftId, ReportValues.VOICE_NOTE,
                        ReportValues.MICROPHONE, file, "audio/mp4");
                attachmentSaved = true;
                List<OfflineAttachmentEntity> attachments =
                        reports.getOfflineAttachments(draftId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    showVoiceNotes(attachments);
                    setBusy(false);
                    if (saveAfterRecording) {
                        saveAfterRecording = false;
                        saveDraftAndExit();
                    } else if (returnAfterSave) {
                        returnToDescription();
                    }
                });
            } catch (IOException | RuntimeException error) {
                if (!attachmentSaved) {
                    file.delete();
                }
                boolean saved = attachmentSaved;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        saveAfterRecording = false;
                        setBusy(false);
                        if (saved) {
                            loadVoiceNotes();
                            return;
                        }
                        restoreStatus();
                        Toast.makeText(this, R.string.voice_save_failed,
                                Toast.LENGTH_SHORT).show();
                        updateControls();
                    }
                });
            }
        });
    }

    private void showVoiceNotes(List<OfflineAttachmentEntity> attachments) {
        releasePlayer();
        voiceNotes.clear();
        voiceRows.clear();
        audioPlayer.removeAllViews();
        for (OfflineAttachmentEntity attachment : attachments) {
            if (ReportValues.VOICE_NOTE.equals(attachment.attachmentType)) {
                voiceNotes.add(attachment);
            }
        }
        for (int index = 0; index < voiceNotes.size(); index++) {
            addVoiceRow(voiceNotes.get(index), index + 1);
        }
        restoreStatus();
        updateControls();
    }

    private void restoreStatus() {
        if (voiceNotes.isEmpty()) {
            status.setText(R.string.voice_hint);
        } else {
            status.setText(getResources().getQuantityString(R.plurals.voice_notes_saved,
                    voiceNotes.size(), voiceNotes.size()));
        }
    }

    private void addVoiceRow(OfflineAttachmentEntity note, int number) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_voice_note,
                audioPlayer, false);
        VoiceRow row = new VoiceRow(note, item, number);
        row.title.setText(getString(R.string.voice_note_number, number));
        row.btnDelete.setContentDescription(getString(R.string.delete_voice_note_number,
                number));
        row.btnPlay.setContentDescription(getString(R.string.play_voice_note_number,
                number));
        row.btnSpeed.setContentDescription(getString(R.string.voice_playback_speed_number,
                number));
        row.seekAudio.setContentDescription(getString(R.string.voice_playback_position_number,
                number));
        row.btnPlay.setOnClickListener(v -> togglePlayback(row));
        row.btnSpeed.setOnClickListener(v -> changePlaybackSpeed(row));
        row.btnDelete.setOnClickListener(v -> confirmDelete(row));
        row.seekAudio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    row.currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && activeRow == row) {
                    player.seekTo(bar.getProgress());
                }
                seeking = false;
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        audioPlayer.addView(item, params);
        voiceRows.add(row);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean preparePlayer(VoiceRow row) {
        releasePlayer();
        MediaPlayer candidate = new MediaPlayer();
        try {
            candidate.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            candidate.setDataSource(row.note.localFilePath);
            candidate.prepare();
            candidate.setOnCompletionListener(completed -> {
                row.seekAudio.setProgress(0);
                row.currentTime.setText(formatTime(0));
                updatePlayButton(row, false);
                uiHandler.removeCallbacks(playbackClock);
            });
            player = candidate;
            activeRow = row;
            row.seekAudio.setMax(Math.max(1, player.getDuration()));
            row.duration.setText(formatTime(player.getDuration()));
            return true;
        } catch (IOException | RuntimeException error) {
            candidate.release();
            Toast.makeText(this, R.string.voice_play_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void togglePlayback(VoiceRow row) {
        if (busy || recorder != null) {
            return;
        }
        if ((player == null || activeRow != row) && !preparePlayer(row)) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                uiHandler.removeCallbacks(playbackClock);
                updatePlayButton(row, false);
            } else {
                player.start();
                if (row.speedIndex != 0) {
                    player.setPlaybackParams(new PlaybackParams()
                            .setSpeed(PLAYBACK_SPEEDS[row.speedIndex]));
                }
                updatePlayButton(row, true);
                uiHandler.removeCallbacks(playbackClock);
                uiHandler.post(playbackClock);
            }
        } catch (RuntimeException error) {
            releasePlayer();
            Toast.makeText(this, R.string.voice_play_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void changePlaybackSpeed(VoiceRow row) {
        if (busy || recorder != null) {
            return;
        }
        row.speedIndex = (row.speedIndex + 1) % PLAYBACK_SPEEDS.length;
        row.btnSpeed.setText(PLAYBACK_SPEED_LABELS[row.speedIndex]);
        if (player != null && activeRow == row && player.isPlaying()) {
            try {
                player.setPlaybackParams(new PlaybackParams()
                        .setSpeed(PLAYBACK_SPEEDS[row.speedIndex]));
            } catch (RuntimeException error) {
                Toast.makeText(this, R.string.voice_play_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmDelete(VoiceRow row) {
        if (busy || recorder != null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.voice_delete_title)
                .setMessage(R.string.voice_delete_message)
                .setNegativeButton(R.string.voice_delete_cancel, null)
                .setPositiveButton(R.string.voice_delete_confirm,
                        (dialog, which) -> deleteVoiceNote(row.note.localId))
                .show();
    }

    private void deleteVoiceNote(long noteId) {
        if (busy || recorder != null) {
            return;
        }
        setBusy(true);
        releasePlayer();
        databaseExecutor.execute(() -> {
            try {
                reports.removeDraftAttachment(noteId);
                List<OfflineAttachmentEntity> attachments =
                        reports.getOfflineAttachments(draftId);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showVoiceNotes(attachments);
                        setBusy(false);
                    }
                });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setBusy(false);
                        Toast.makeText(this, R.string.voice_delete_failed,
                                Toast.LENGTH_SHORT).show();
                        loadVoiceNotes();
                    }
                });
            }
        });
    }

    private void saveDraftAndExit() {
        if (busy || !draftLoaded) {
            return;
        }
        if (recorder != null) {
            saveAfterRecording = true;
            stopRecording(false);
            return;
        }
        setBusy(true);
        releasePlayer();
        ReportDraftNavigation.saveAndReturnHome(this, databaseExecutor,
                () -> reports.setDraftStep(draftId, ReportValues.STEP_VOICE),
                () -> setBusy(false));
    }

    private void returnToDescription() {
        if (busy) {
            return;
        }
        if (recorder != null) {
            stopRecording(true);
            return;
        }
        setBusy(true);
        releasePlayer();
        databaseExecutor.execute(() -> {
            try {
                reports.setDraftStep(draftId, ReportValues.STEP_DESCRIPTION);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        finish();
                    }
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setBusy(false);
                        Toast.makeText(this, R.string.report_draft_save_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateControls() {
        if (btnRecord == null) {
            return;
        }
        boolean recording = recorder != null;
        boolean hasNote = !voiceNotes.isEmpty();
        btnBack.setEnabled(!busy);
        btnSaveDraft.setEnabled(draftLoaded && !busy);
        btnNext.setEnabled(draftLoaded && !busy && !recording);
        btnRecord.setEnabled(draftLoaded && !busy);
        btnRecord.setText(recording
                ? R.string.stop_voice_recording
                : hasNote ? R.string.record_another_voice_note : R.string.record_voice_note);
        recordTimer.setVisibility(recording ? View.VISIBLE : View.GONE);
        audioPlayer.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        for (VoiceRow row : voiceRows) {
            row.btnDelete.setEnabled(!busy && !recording);
            row.btnPlay.setEnabled(!busy && !recording);
            row.btnSpeed.setEnabled(!busy && !recording);
            row.seekAudio.setEnabled(!busy && !recording);
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        updateControls();
    }

    private void updatePlayButton(VoiceRow row, boolean playing) {
        row.btnPlay.setImageResource(playing
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        row.btnPlay.setContentDescription(getString(playing
                ? R.string.pause_voice_note_number : R.string.play_voice_note_number,
                row.number));
    }

    private void releasePlayer() {
        uiHandler.removeCallbacks(playbackClock);
        if (player != null) {
            player.release();
            player = null;
        }
        if (activeRow != null) {
            updatePlayButton(activeRow, false);
            activeRow = null;
        }
    }

    private static final class VoiceRow {
        final OfflineAttachmentEntity note;
        final int number;
        final TextView title;
        final TextView currentTime;
        final TextView duration;
        final ImageButton btnPlay;
        final ImageButton btnDelete;
        final Button btnSpeed;
        final SeekBar seekAudio;
        int speedIndex;

        VoiceRow(OfflineAttachmentEntity note, View item, int number) {
            this.note = note;
            this.number = number;
            title = item.findViewById(R.id.tvVoiceNoteTitle);
            currentTime = item.findViewById(R.id.txtCurrentTime);
            duration = item.findViewById(R.id.txtDuration);
            btnPlay = item.findViewById(R.id.btnPlay);
            btnDelete = item.findViewById(R.id.btnDelete);
            btnSpeed = item.findViewById(R.id.btnSpeed);
            seekAudio = item.findViewById(R.id.seekAudio);
        }
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onPause() {
        if (recorder != null) {
            stopRecording(false);
        }
        releasePlayer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(recordClock);
        uiHandler.removeCallbacks(playbackClock);
        releasePlayer();
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
