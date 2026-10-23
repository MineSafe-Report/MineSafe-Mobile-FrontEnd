package com.example.javareportingsystem.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

//API REPORTING DATA SOURCE
//uplads reports and atatchements to API

public final class ApiReportingDataSource
{
    private final String baseUrl;
    private final String token;

    public ApiReportingDataSource(String baseUrl, String token)
    {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
    }

    //REPORT TRANSPORT
    //Send report and keeps server respons
    public JSONObject submitReport(JSONObject report) throws IOException
    {
        return jsonRequest("POST", "/api/reports", report);
    }

    public JSONArray getMyReports() throws IOException
    {
        try
        {
            JSONArray all = new JSONArray();
            for (int page = 1; page <= 100; page++)
            {
                JSONObject response = jsonRequest("GET", "/api/reports/mine?page="
                    + page + "&pageSize=100", null);
                JSONArray items = response.getJSONArray("items");
                for (int index = 0; index < items.length(); index++)
                {
                    all.put(items.getJSONObject(index));
                }
                if (all.length() >= response.optInt("total", all.length())
                    || items.length() < 100) return all;
            }
            throw new IOException("Too many report status pages.");
        }
        catch (JSONException error)
        {
            throw new IOException("Invalid My Reports response.", error);
        }
    }

    //MEDIA TRANSPORT
    //Uplad each attatchemnts of a report under the reports own ID
    public JSONObject uploadAttachment(String reportId, String localAttachmentId,
                                       String type, long capturedAt, String mimeType,
                                       InputStream input, long fileSize) throws IOException
    {
        if (fileSize <= 0 || fileSize > 25L * 1024 * 1024)
        {
            throw new IOException("Attachment size is invalid.");
        }
        String boundary = "mine-" + UUID.randomUUID();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeField(header, boundary, "clientAttachmentId", localAttachmentId);
        writeField(header, boundary, "attachmentType", type);
        writeField(header, boundary, "capturedAt", ApiDates.toIso(capturedAt));
        String fileHeader = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"evidence\"\r\n"
            + "Content-Type: " + mimeType + "\r\n\r\n";
        header.write(fileHeader.getBytes(StandardCharsets.UTF_8));
        byte[] ending = ("\r\n--" + boundary + "--\r\n")
            .getBytes(StandardCharsets.UTF_8);
        long total = header.size() + fileSize + ending.length;
        HttpURLConnection connection = open("POST", "/api/reports/" + encode(reportId)
            + "/attachments");
        try
        {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setFixedLengthStreamingMode(total);
            try (OutputStream output = connection.getOutputStream(); InputStream file = input)
            {
                header.writeTo(output);
                byte[] buffer = new byte[8192];
                int count;
                long written = 0;
                while ((count = file.read(buffer)) != -1)
                {
                    written += count;
                    if (written > fileSize)
                        throw new IOException("Attachment changed during upload.");
                    output.write(buffer, 0, count);
                }
                if (written != fileSize) throw new IOException("Attachment changed during upload.");
                output.write(ending);
            }
            return response(connection);
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static void writeField(ByteArrayOutputStream output, String boundary,
                                   String name, String value) throws IOException
    {
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
            + name + "\"\r\n\r\n" + value + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject jsonRequest(String method, String path, JSONObject body) throws IOException
    {
        HttpURLConnection connection = open(method, path);
        try
        {
            if (body != null)
            {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream())
                {
                    output.write(bytes);
                }
            }
            return response(connection);
        }
        finally
        {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String method, String path) throws IOException
    {
        if (baseUrl.isEmpty() || token.isEmpty()) throw new IOException("Sign in to sync reports.");
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private static JSONObject response(HttpURLConnection connection) throws IOException
    {
        int status = connection.getResponseCode();
        InputStream source = status >= 200 && status < 300
            ? connection.getInputStream() : connection.getErrorStream();
        String body = readLimited(source, 512 * 1024);
        try
        {
            JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);
            if (status < 200 || status >= 300)
            {
                throw new IOException(json.optString("message", "Report request failed (HTTP "
                    + status + ")."));
            }
            return json;
        }
        catch (JSONException error)
        {
            throw new IOException("Invalid report service response.", error);
        }
    }

    private static String readLimited(InputStream source, int limit) throws IOException
    {
        if (source == null) return "";
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1)
            {
                if (output.size() + count > limit) throw new IOException("API response too large.");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String encode(String value) throws IOException
    {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }
}
