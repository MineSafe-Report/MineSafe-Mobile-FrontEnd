package com.example.javareportingsystem.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.javareportingsystem.R;
import com.example.javareportingsystem.data.local.LocalReportRepository;
import com.example.javareportingsystem.data.local.ReportValues;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Captures and manages image evidence for the current report draft. */
public class ReportImagesActivity extends AppCompatActivity
{

    private static final int MAX_DIMENSION = 1600;
    private static final int JPEG_QUALITY = 80;

    private Button btnBack, btnSaveDraft, btnCamera, btnGallery, btnSkip, btnNext;
    private TextView tvImageCount;
    private RecyclerView rvImageThumbnails;

    private ImageDatabaseHelper database;
    private LocalReportRepository reports;
    private long draftId;
    private ImageThumbnailAdapter adapter;

    private final ArrayList<ImageDatabaseHelper.ImageRecord> images =
        new ArrayList<>();

    // All database and image-processing work runs sequentially here.
    private final ExecutorService databaseExecutor =
        Executors.newSingleThreadExecutor();

    // All UI callbacks are posted to the main thread.
    private final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Void> cameraLauncher =
        registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap ->
            {
                if (bitmap == null)
                {
                    return;
                }

                databaseExecutor.execute(() ->
                {
                    byte[] compressed = compressBitmap(bitmap);

                    if (!bitmap.isRecycled())
                    {
                        bitmap.recycle();
                    }

                    if (compressed == null)
                    {
                        postToUi(() ->
                            showMessage("Could not process image"));
                        return;
                    }

                    int count = database.getImageCount();

                    if (count >= ImageDatabaseHelper.MAX_IMAGES)
                    {
                        postToUi(this::showLimitMessage);
                        return;
                    }

                    long id = database.insertImage(compressed);

                    postToUi(() ->
                    {
                        reloadImages();

                        if (id == -1)
                        {
                            showLimitMessage();
                        }
                        else
                        {
                            showMessage("Image saved successfully");
                        }
                    });
                });
            });

    private final ActivityResultLauncher<String> galleryLauncher =
        registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            this::saveGalleryImages);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_images);
        draftId = ReportDraftNavigation.readId(this);
        if (draftId <= 0)
        {
            return;
        }

        database = new ImageDatabaseHelper(this, draftId);
        reports = new LocalReportRepository(this);

        btnBack = findViewById(R.id.btnBack);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);
        btnSkip = findViewById(R.id.btnSkip);
        btnNext = findViewById(R.id.btnNext);

        tvImageCount = findViewById(R.id.tvImageCount);
        rvImageThumbnails = findViewById(R.id.rvImageThumbnails);

        rvImageThumbnails.setLayoutManager(
            new LinearLayoutManager(this));

        adapter = new ImageThumbnailAdapter(
            images,
            image -> showFullscreenImage(image.id));

        rvImageThumbnails.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnSaveDraft.setOnClickListener(v -> saveDraftAndExit());
        btnCamera.setOnClickListener(v -> checkSlotsAndLaunchCamera());

        btnGallery.setOnClickListener(v -> checkSlotsAndLaunchGallery());

        btnSkip.setOnClickListener(v -> openOverview());

        btnNext.setOnClickListener(v ->
        {
            databaseExecutor.execute(() ->
            {
                int count = database.getImageCount();

                postToUi(() ->
                {
                    if (count == 0)
                    {
                        showMessage("Please add an image or tap Skip");
                    }
                    else
                    {
                        showMessage(
                            "Images saved.");
                        openOverview();
                    }
                });
            });
        });

        reloadImages();
    }

    private void saveDraftAndExit()
    {
        btnSaveDraft.setEnabled(false);
        btnBack.setEnabled(false);
        btnSkip.setEnabled(false);
        btnNext.setEnabled(false);
        btnCamera.setEnabled(false);
        btnGallery.setEnabled(false);
        ReportDraftNavigation.saveAndReturnHome(this, databaseExecutor,
            () -> reports.setDraftStep(draftId, ReportValues.STEP_IMAGES),
            () ->
            {
                btnSaveDraft.setEnabled(true);
                btnBack.setEnabled(true);
                btnSkip.setEnabled(true);
                btnNext.setEnabled(true);
                reloadImages();
            });
    }

    private void openOverview()
    {
        startActivity(ReportDraftNavigation.to(
            this, ReportOverviewActivity.class, draftId));
    }

    // UI helper: always execute the action on the main thread.
    private void postToUi(Runnable action)
    {
        mainHandler.post(() ->
        {
            if (!isFinishing() && !isDestroyed())
            {
                action.run();
            }
        });
    }

    // CAMERA CAPTURE
    private void checkSlotsAndLaunchCamera()
    {
        databaseExecutor.execute(() ->
        {
            boolean full = database.getImageCount()
                >= ImageDatabaseHelper.MAX_IMAGES;

            postToUi(() ->
            {
                if (full)
                {
                    showLimitMessage();
                }
                else
                {
                    cameraLauncher.launch(null);
                }
            });
        });
    }

    private void checkSlotsAndLaunchGallery()
    {
        databaseExecutor.execute(() ->
        {
            boolean full = database.getImageCount()
                >= ImageDatabaseHelper.MAX_IMAGES;

            postToUi(() ->
            {
                if (full)
                {
                    showLimitMessage();
                }
                else
                {
                    galleryLauncher.launch("image/*");
                }
            });
        });
    }

    private void showLimitMessage()
    {
        showMessage("Maximum of 10 images. Delete one to add more.");
    }

    // GALLERY IMPORT
    // Gallery reading, compression, and inserts run on the worker thread.
    private void saveGalleryImages(List<Uri> uris)
    {
        if (uris == null || uris.isEmpty())
        {
            return;
        }

        databaseExecutor.execute(() ->
        {
            int saved = 0;
            int skipped = 0;

            for (Uri uri : uris)
            {
                if (database.getImageCount()
                    >= ImageDatabaseHelper.MAX_IMAGES)
                {
                    skipped++;
                    continue;
                }

                byte[] compressed = compressUri(uri);

                if (compressed == null)
                {
                    skipped++;
                    continue;
                }

                if (database.insertImage(compressed) != -1)
                {
                    saved++;
                }
                else
                {
                    skipped++;
                }
            }

            int finalSaved = saved;
            int finalSkipped = skipped;

            postToUi(() ->
            {
                reloadImages();

                String message = finalSaved + " image(s) saved";

                if (finalSkipped > 0)
                {
                    message += ". " + finalSkipped
                        + " skipped (limit or image read error)";
                }

                showMessage(message);
            });
        });
    }

    // Decode a smaller image rather than loading the full-resolution file.
    private byte[] compressUri(Uri uri)
    {
        Bitmap decoded = null;

        try
        {
            BitmapFactory.Options bounds =
                new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;

            try (InputStream input =
                     getContentResolver().openInputStream(uri))
            {
                if (input == null)
                {
                    return null;
                }

                BitmapFactory.decodeStream(input, null, bounds);
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            {
                return null;
            }

            BitmapFactory.Options options =
                new BitmapFactory.Options();

            options.inSampleSize = calculateSampleSize(
                bounds.outWidth, bounds.outHeight, MAX_DIMENSION);
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            try (InputStream input =
                     getContentResolver().openInputStream(uri))
            {
                if (input == null)
                {
                    return null;
                }

                decoded = BitmapFactory.decodeStream(input, null, options);
            }

            return decoded == null ? null : compressBitmap(decoded);

        }
        catch (Exception e)
        {
            return null;
        }
        finally
        {
            if (decoded != null && !decoded.isRecycled())
            {
                decoded.recycle();
            }
        }
    }

    private int calculateSampleSize(
        int width, int height, int maxDimension)
    {

        int sample = 1;

        while (Math.max(width / sample, height / sample)
            > maxDimension * 2)
        {
            sample *= 2;
        }

        return sample;
    }

    private byte[] compressBitmap(Bitmap source)
    {
        if (source == null)
        {
            return null;
        }

        Bitmap resized = null;

        try
        {
            int width = source.getWidth();
            int height = source.getHeight();

            float scale = Math.min(
                1f,
                (float) MAX_DIMENSION / Math.max(width, height));

            Bitmap outputBitmap = source;

            if (scale < 1f)
            {
                int newWidth = Math.max(1, Math.round(width * scale));
                int newHeight = Math.max(1, Math.round(height * scale));

                resized = Bitmap.createScaledBitmap(
                    source, newWidth, newHeight, true);

                outputBitmap = resized;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();

            boolean success = outputBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                JPEG_QUALITY,
                output);

            return success ? output.toByteArray() : null;

        }
        catch (Exception e)
        {
            return null;
        }
        finally
        {
            if (resized != null
                && resized != source
                && !resized.isRecycled())
            {
                resized.recycle();
            }
        }
    }

    // IMAGE LIST
    // Called on the main thread; the actual database read is asynchronous.
    @MainThread
    private void reloadImages()
    {
        databaseExecutor.execute(() ->
        {
            List<ImageDatabaseHelper.ImageRecord> result =
                database.getAllImages();

            postToUi(() ->
            {
                images.clear();
                images.addAll(result);

                adapter.replaceImages(images);

                tvImageCount.setText("Stored images: " + images.size() + "/" + ImageDatabaseHelper.MAX_IMAGES);

                boolean canAdd =
                    images.size() < ImageDatabaseHelper.MAX_IMAGES;

                btnCamera.setEnabled(canAdd);
                btnGallery.setEnabled(canAdd);
                btnSkip.setVisibility(images.isEmpty() ? View.VISIBLE : View.GONE);
                btnNext.setVisibility(images.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    // Load image bytes and decode the bitmap on the worker thread.
    private void showFullscreenImage(long imageId)
    {
        databaseExecutor.execute(() ->
        {
            ImageDatabaseHelper.ImageRecord image =
                database.getImage(imageId);

            Bitmap bitmap = null;

            if (image != null && image.data != null)
            {
                bitmap = BitmapFactory.decodeByteArray(
                    image.data, 0, image.data.length);
            }

            Bitmap decodedBitmap = bitmap;

            postToUi(() ->
            {
                if (decodedBitmap == null)
                {
                    reloadImages();
                    showMessage(image == null
                        ? "Image not found"
                        : "Unable to display image");
                    return;
                }

                displayFullscreenImage(imageId, decodedBitmap);
            });
        });
    }

    // All dialog creation and view operations happen on the main thread.
    @MainThread
    private void displayFullscreenImage(long imageId, Bitmap bitmap)
    {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_fullscreen_image);
        dialog.setCancelable(true);

        ImageView fullscreenImage =
            dialog.findViewById(R.id.fullscreenImage);

        Button btnDelete =
            dialog.findViewById(R.id.btnDeleteFullscreen);

        Button btnKeep =
            dialog.findViewById(R.id.btnKeepFullscreen);

        fullscreenImage.setImageBitmap(bitmap);

        btnKeep.setOnClickListener(v ->
        {
            dialog.dismiss();
            showMessage("Image kept");
        });

        btnDelete.setOnClickListener(v ->
            new AlertDialog.Builder(ReportImagesActivity.this)
                .setTitle("Delete image")
                .setMessage("Permanently delete this image?")
                .setNegativeButton("Cancel",
                    (confirmation, which) ->
                        confirmation.dismiss())
                .setPositiveButton("Delete",
                    (confirmation, which) ->
                    {
                        databaseExecutor.execute(() ->
                        {
                            boolean deleted =
                                database.deleteImage(imageId);

                            postToUi(() ->
                            {
                                dialog.dismiss();

                                if (deleted)
                                {
                                    reloadImages();
                                    showMessage("Image deleted");
                                }
                                else
                                {
                                    showMessage(
                                        "Could not delete image");
                                }
                            });
                        });
                    })
                .show());

        dialog.setOnDismissListener(v ->
        {
            fullscreenImage.setImageDrawable(null);
            if (!bitmap.isRecycled())
            {
                bitmap.recycle();
            }
        });

        dialog.show();

        Window window = dialog.getWindow();

        if (window != null)
        {
            window.setBackgroundDrawableResource(android.R.color.black);

            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);

            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @MainThread
    private void showMessage(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy()
    {
        if (adapter != null)
        {
            adapter.close();
        }

        databaseExecutor.execute(() ->
        {
            if (database != null)
            {
                database.close();
            }
        });

        databaseExecutor.shutdown();

        mainHandler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}
