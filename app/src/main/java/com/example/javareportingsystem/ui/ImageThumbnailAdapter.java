package com.example.javareportingsystem.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageThumbnailAdapter
    extends RecyclerView.Adapter<ImageThumbnailAdapter.ImageViewHolder>
{

    private static final int THUMBNAIL_SIZE_DP = 80;
    private static final int ITEM_PADDING_DP = 8;
    private static final int IMAGE_MARGIN_DP = 12;

    private final ArrayList<ImageDatabaseHelper.ImageRecord> images =
        new ArrayList<>();

    private final OnImageClickListener listener;

    private final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    private final ExecutorService thumbnailExecutor =
        Executors.newSingleThreadExecutor();

    private volatile boolean closed = false;

    public interface OnImageClickListener
    {
        void onImageClick(ImageDatabaseHelper.ImageRecord image);
    }

    public ImageThumbnailAdapter(
        @NonNull ArrayList<ImageDatabaseHelper.ImageRecord> images,
        @NonNull OnImageClickListener listener)
    {

        this.images.addAll(images);
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position)
    {
        return images.get(position).id;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(
        @NonNull ViewGroup parent,
        int viewType)
    {

        int padding = dp(parent, ITEM_PADDING_DP);
        int thumbnailSize = dp(parent, THUMBNAIL_SIZE_DP);
        int margin = dp(parent, IMAGE_MARGIN_DP);

        LinearLayout container =
            new LinearLayout(parent.getContext());

        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        container.setBackgroundResource(
            android.R.drawable.list_selector_background);

        ImageView thumbnail =
            new ImageView(parent.getContext());

        thumbnail.setLayoutParams(
            new LinearLayout.LayoutParams(
                thumbnailSize, thumbnailSize));

        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackgroundColor(Color.LTGRAY);
        thumbnail.setImageDrawable(
            new ColorDrawable(Color.LTGRAY));
        thumbnail.setContentDescription("Image thumbnail");

        TextView title = new TextView(parent.getContext());

        LinearLayout.LayoutParams titleParams =
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);

        titleParams.leftMargin = margin;
        title.setLayoutParams(titleParams);
        title.setTextColor(Color.BLACK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        container.addView(thumbnail);
        container.addView(title);

        container.setClickable(true);
        container.setFocusable(true);

        return new ImageViewHolder(container, thumbnail, title);
    }

    @Override
    public void onBindViewHolder(
        @NonNull ImageViewHolder holder,
        int position)
    {

        ImageDatabaseHelper.ImageRecord image = images.get(position);

        final long imageId = image.id;
        final byte[] imageData = image.data;

        holder.boundImageId = imageId;

        holder.thumbnail.setImageDrawable(
            new ColorDrawable(Color.LTGRAY));

        holder.title.setText("Image #" + imageId);

        holder.itemView.setOnClickListener(v ->
        {
            if (!closed)
            {
                listener.onImageClick(image);
            }
        });

        // Decode thumbnails in the background.
        thumbnailExecutor.execute(() ->
        {
            if (closed)
            {
                return;
            }

            Bitmap bitmap = decodeThumbnail(imageData);

            if (bitmap == null)
            {
                return;
            }

            mainHandler.post(() ->
            {
                // Compatible with older RecyclerView versions.
                int adapterPosition = holder.getAdapterPosition();

                if (closed
                    || holder.boundImageId != imageId
                    || adapterPosition == RecyclerView.NO_POSITION)
                {

                    if (!bitmap.isRecycled())
                    {
                        bitmap.recycle();
                    }
                    return;
                }

                holder.thumbnail.setImageBitmap(bitmap);
            });
        });
    }

    @Override
    public int getItemCount()
    {
        return images.size();
    }

    /**
     * Replaces the displayed images.
     * Must be called on the main thread.
     */
    public void replaceImages(
        @NonNull List<ImageDatabaseHelper.ImageRecord> newImages)
    {

        if (Looper.myLooper() != Looper.getMainLooper())
        {
            throw new IllegalStateException(
                "replaceImages() must run on the main thread");
        }

        images.clear();
        images.addAll(newImages);

        notifyDataSetChanged();
    }

    /**
     * Decodes a reduced-size thumbnail on the background thread.
     */
    private Bitmap decodeThumbnail(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return null;
        }

        BitmapFactory.Options bounds =
            new BitmapFactory.Options();

        bounds.inJustDecodeBounds = true;

        BitmapFactory.decodeByteArray(
            data, 0, data.length, bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
        {
            return null;
        }

        int sampleSize = 1;

        while (bounds.outWidth / (sampleSize * 2) >= 160
            && bounds.outHeight / (sampleSize * 2) >= 160)
        {
            sampleSize *= 2;
        }

        BitmapFactory.Options options =
            new BitmapFactory.Options();

        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;

        try
        {
            return BitmapFactory.decodeByteArray(
                data, 0, data.length, options);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static int dp(View view, int value)
    {
        return Math.round(
            value * view.getResources()
                .getDisplayMetrics().density);
    }

    @Override
    public void onViewRecycled(
        @NonNull ImageViewHolder holder)
    {

        holder.boundImageId = RecyclerView.NO_ID;

        holder.thumbnail.setImageDrawable(
            new ColorDrawable(Color.LTGRAY));

        super.onViewRecycled(holder);
    }

    /**
     * Stops thumbnail work when the activity is destroyed.
     */
    public void close()
    {
        closed = true;
        thumbnailExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder
    {

        final ImageView thumbnail;
        final TextView title;

        long boundImageId = RecyclerView.NO_ID;

        ImageViewHolder(
            @NonNull View itemView,
            @NonNull ImageView thumbnail,
            @NonNull TextView title)
        {

            super(itemView);
            this.thumbnail = thumbnail;
            this.title = title;
        }
    }
}