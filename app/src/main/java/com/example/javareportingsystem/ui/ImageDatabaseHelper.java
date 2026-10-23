package com.example.javareportingsystem.ui;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

//IMAGE DATABASE HELPER
//Stores repport images on this device

public class ImageDatabaseHelper extends SQLiteOpenHelper
{

    public static final int MAX_IMAGES = 10;

    private static final String DATABASE_NAME = "report_images.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_IMAGES = "report_images";

    private static final String COL_ID = "_id";
    private static final String COL_DATA = "image_data";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_REPORT_LOCAL_ID = "report_local_id";

    private final long reportLocalId;

    private static final String CREATE_TABLE =
        "CREATE TABLE " + TABLE_IMAGES + " ("
            + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COL_DATA + " BLOB NOT NULL, "
            + COL_CREATED_AT + " INTEGER NOT NULL, "
            + COL_REPORT_LOCAL_ID + " INTEGER NOT NULL"
            + ")";


    public static class ImageRecord
    {

        public final long id;
        public final byte[] data;
        public final long createdAt;

        public ImageRecord(long id, @NonNull byte[] data, long createdAt)
        {
            this.id = id;
            this.data = data;
            this.createdAt = createdAt;
        }
    }

    public ImageDatabaseHelper(@NonNull Context context, long reportLocalId)
    {
        super(context.getApplicationContext(),
            DATABASE_NAME, null, DATABASE_VERSION);
        if (reportLocalId <= 0)
        {
            throw new IllegalArgumentException("Report draft ID is required");
        }
        this.reportLocalId = reportLocalId;
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db)
    {
        db.execSQL(CREATE_TABLE);
        db.execSQL("CREATE INDEX index_report_images_report_local_id ON "
            + TABLE_IMAGES + " (" + COL_REPORT_LOCAL_ID + ")");
    }

    @Override
    public void onUpgrade(
        @NonNull SQLiteDatabase db,
        int oldVersion,
        int newVersion)
    {

        if (oldVersion < 2)
        {
            //Do not add old images to new reports.
            db.execSQL("ALTER TABLE " + TABLE_IMAGES + " ADD COLUMN "
                + COL_REPORT_LOCAL_ID + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX index_report_images_report_local_id ON "
                + TABLE_IMAGES + " (" + COL_REPORT_LOCAL_ID + ")");
        }
    }


    @WorkerThread
    public int getImageCount()
    {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
            "SELECT COUNT(*) FROM " + TABLE_IMAGES
                + " WHERE " + COL_REPORT_LOCAL_ID + " = ?",
            new String[]{String.valueOf(reportLocalId)}))
        {

            if (cursor.moveToFirst())
            {
                return cursor.getInt(0);
            }

            return 0;
        }
    }


    @WorkerThread
    @NonNull
    public List<Long> getImageIds()
    {
        List<Long> ids = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_IMAGES, new String[]{COL_ID},
            COL_REPORT_LOCAL_ID + " = ?",
            new String[]{String.valueOf(reportLocalId)}, null, null, COL_ID + " ASC"))
        {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        return ids;
    }


    @WorkerThread
    public long insertImage(@NonNull byte[] imageData)
    {
        if (imageData.length == 0)
        {
            return -1;
        }

        SQLiteDatabase db = getWritableDatabase();

        db.beginTransaction();

        try
        {

            try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_IMAGES
                    + " WHERE " + COL_REPORT_LOCAL_ID + " = ?",
                new String[]{String.valueOf(reportLocalId)}))
            {

                if (!cursor.moveToFirst()
                    || cursor.getInt(0) >= MAX_IMAGES)
                {
                    return -1;
                }
            }

            ContentValues values = new ContentValues();
            values.put(COL_DATA, imageData);
            values.put(COL_CREATED_AT, System.currentTimeMillis());
            values.put(COL_REPORT_LOCAL_ID, reportLocalId);

            long id = db.insert(TABLE_IMAGES, null, values);

            if (id != -1)
            {
                db.setTransactionSuccessful();
            }

            return id;

        }
        finally
        {
            db.endTransaction();
        }
    }

    //Gets saved immages from oldest to newest.
    @WorkerThread
    @NonNull
    public List<ImageRecord> getAllImages()
    {
        List<ImageRecord> result = new ArrayList<>();

        SQLiteDatabase db = getReadableDatabase();

        String[] columns = {
            COL_ID,
            COL_DATA,
            COL_CREATED_AT
        };

        try (Cursor cursor = db.query(
            TABLE_IMAGES,
            columns,
            COL_REPORT_LOCAL_ID + " = ?",
            new String[]{String.valueOf(reportLocalId)},
            null,
            null,
            COL_ID + " ASC"))
        {

            while (cursor.moveToNext())
            {
                long id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(COL_ID));

                byte[] data = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(COL_DATA));

                long createdAt = cursor.getLong(
                    cursor.getColumnIndexOrThrow(COL_CREATED_AT));

                result.add(new ImageRecord(id, data, createdAt));
            }
        }

        return result;
    }

    //Gets one immage by ID.
    @WorkerThread
    @Nullable
    public ImageRecord getImage(long imageId)
    {
        SQLiteDatabase db = getReadableDatabase();

        String selection = COL_ID + " = ? AND " + COL_REPORT_LOCAL_ID + " = ?";
        String[] selectionArgs = {
            String.valueOf(imageId), String.valueOf(reportLocalId)};

        try (Cursor cursor = db.query(
            TABLE_IMAGES,
            new String[]{COL_ID, COL_DATA, COL_CREATED_AT},
            selection,
            selectionArgs,
            null,
            null,
            null,
            "1"))
        {

            if (!cursor.moveToFirst())
            {
                return null;
            }

            return new ImageRecord(
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getBlob(
                    cursor.getColumnIndexOrThrow(COL_DATA)),
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(COL_CREATED_AT))
            );
        }
    }

    //Deletes one immage and reports if it worked.
    @WorkerThread
    public boolean deleteImage(long imageId)
    {
        SQLiteDatabase db = getWritableDatabase();

        int deletedRows = db.delete(
            TABLE_IMAGES,
            COL_ID + " = ? AND " + COL_REPORT_LOCAL_ID + " = ?",
            new String[]{String.valueOf(imageId), String.valueOf(reportLocalId)}
        );

        return deletedRows > 0;
    }

    //Deletes all saved images.
    @WorkerThread
    public void deleteAllImages()
    {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_IMAGES, COL_REPORT_LOCAL_ID + " = ?",
            new String[]{String.valueOf(reportLocalId)});
    }

    //Closes the db after its work is done.
    @WorkerThread
    @Override
    public synchronized void close()
    {
        super.close();
    }
}
