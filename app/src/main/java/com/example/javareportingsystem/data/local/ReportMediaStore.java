package com.example.javareportingsystem.data.local;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

//REPORT MEDIA STORE
//keeps report files in app store

public final class ReportMediaStore
{
    private static final long MAX_FILE_BYTES = 100L * 1024L * 1024L;
    private final Context context;
    private final File directory;

    public ReportMediaStore(Context context)
    {
        this.context = context.getApplicationContext();
        this.directory = new File(this.context.getFilesDir(), "report_media");
    }

    public File getDirectory() throws IOException
    {
        if (!directory.isDirectory() && !directory.mkdirs())
        {
            throw new IOException("Could not create report media directory");
        }
        return directory;
    }

    @WorkerThread
    public StoredMedia copyFromUri(@NonNull Uri source, @NonNull String mimeType)
        throws IOException
    {
        String extension = extensionFor(mimeType);
        File output = new File(getDirectory(), UUID.randomUUID() + extension);
        long length = 0;
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream stream = new FileOutputStream(output))
        {
            if (input == null)
            {
                throw new IOException("Could not open selected media");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                length += read;
                if (length > MAX_FILE_BYTES)
                {
                    throw new IOException("Attachment is too large");
                }
                stream.write(buffer, 0, read);
            }
            stream.getFD().sync();
        }
        catch (IOException | RuntimeException e)
        {
            output.delete();
            throw e;
        }
        return new StoredMedia(output, mimeType, length);
    }

    public boolean isManagedFile(@NonNull File file) throws IOException
    {
        String root = getDirectory().getCanonicalPath() + File.separator;
        return file.getCanonicalPath().startsWith(root);
    }

    public boolean deleteManagedFile(@NonNull File file) throws IOException
    {
        return isManagedFile(file) && (!file.exists() || file.delete());
    }

    private static String extensionFor(String mimeType)
    {
        switch (mimeType)
        {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/webp":
                return ".webp";
            case "audio/mp4":
                return ".m4a";
            case "audio/mpeg":
                return ".mp3";
            case "audio/3gpp":
                return ".3gp";
            default:
                return ".bin";
        }
    }

    public static final class StoredMedia
    {
        public final File file;
        public final String mimeType;
        public final long sizeBytes;

        StoredMedia(File file, String mimeType, long sizeBytes)
        {
            this.file = file;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
        }
    }
}
