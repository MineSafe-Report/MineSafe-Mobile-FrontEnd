package com.example.javareportingsystem.data;

import com.example.javareportingsystem.data.model.ReviewReport;
import com.example.javareportingsystem.data.model.ReviewAttachment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

//API REPORT REVIEW DATA SOURCE
//Loads reports thens ends it to the supervisor for denyal or aproval

public final class ApiReportReviewDataSource
{
    private final String baseUrl;
    private final String accessToken;

    public ApiReportReviewDataSource(String baseUrl, String accessToken)
    {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public List<ReviewReport> getReports(String status) throws IOException
    {
        String query = status == null || status.trim().isEmpty()
            ? "PENDING_REVIEW" : status.trim().toUpperCase();
        return getPagedReports("/api/supervisor/reports?status=" + query + "&", "review queue");
    }

    // Safety Officers can view incidents but cannot review them.
    public List<ReviewReport> getSafetyOfficerIncidents() throws IOException
    {
        return getPagedReports("/api/safety-officer/incidents?", "incident list");
    }

    private List<ReviewReport> getPagedReports(String path, String name) throws IOException
    {
        try
        {
            List<ReviewReport> reports = new ArrayList<>();
            for (int page = 1; page <= 100; page++)
            {
                JSONObject response = request("GET", path + "page=" + page + "&pageSize=100", null);
                JSONArray items = response.getJSONArray("items");
                for (int i = 0; i < items.length(); i++)
                {
                    reports.add(parseReport(items.getJSONObject(i)));
                }
                if (reports.size() >= response.optInt("total", reports.size())
                    || items.length() < 100) return reports;
            }
            throw new IOException("Too many " + name + " pages.");
        }
        catch (JSONException error)
        {
            throw new IOException("The report service returned an invalid " + name + ".", error);
        }
    }

    public ReviewReport getSafetyOfficerIncident(String reportId) throws IOException
    {
        try
        {
            JSONObject response = request("GET", "/api/safety-officer/incidents/"
                + encodePath(reportId), null);
            return parseReport(response.getJSONObject("report"));
        }
        catch (JSONException error)
        {
            throw new IOException("The incident service returned an invalid report.", error);
        }
    }

    public ReviewReport getReport(String reportId) throws IOException
    {
        try
        {
            JSONObject response = request("GET", "/api/supervisor/reports/"
                + encodePath(reportId), null);
            return parseReport(response.getJSONObject("report"));
        }
        catch (JSONException error)
        {
            throw new IOException("The report review service returned an invalid report.", error);
        }
    }

    public ReviewReport submitDecision(String reportId, String decision, String reason)
        throws IOException
    {
        JSONObject body = new JSONObject();
        try
        {
            body.put("decision", decision);
            if (reason != null) body.put("reason", reason.trim());
        }
        catch (JSONException error)
        {
            throw new IOException("Could not create the review request.", error);
        }
        try
        {
            JSONObject response = request("POST", "/api/supervisor/reports/"
                + encodePath(reportId) + "/review", body);
            return parseReport(response.getJSONObject("report"));
        }
        catch (JSONException error)
        {
            throw new IOException("The report review service returned an invalid decision.", error);
        }
    }

    //Atatchement download for viewing and playback
    public File downloadAttachment(String attachmentId, String mimeType, File cacheDir)
        throws IOException
    {
        if (baseUrl.isEmpty() || accessToken.isEmpty())
            throw new IOException("Sign in to view evidence.");
        HttpURLConnection connection = null;
        File target = null;
        try
        {
            connection = (HttpURLConnection) new URL(baseUrl + "/api/attachments/"
                + encodePath(attachmentId)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            if (connection.getResponseCode() != 200)
                throw new IOException("Could not load evidence.");
            String suffix = "audio/wav".equalsIgnoreCase(mimeType) ? ".wav"
                : "audio/mp4".equalsIgnoreCase(mimeType) ? ".m4a"
                : "audio/3gpp".equalsIgnoreCase(mimeType) ? ".3gp"
                : "image/png".equalsIgnoreCase(mimeType) ? ".png"
                : "image/jpeg".equalsIgnoreCase(mimeType) ? ".jpg" : ".bin";
            target = File.createTempFile("review_evidence_", suffix, cacheDir);
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(target))
            {
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = input.read(buffer)) != -1)
                {
                    total += count;
                    if (total > 25L * 1024 * 1024) throw new IOException("Evidence is too large.");
                    output.write(buffer, 0, count);
                }
                if (total == 0) throw new IOException("Evidence is empty.");
            }
            return target;
        }
        catch (IOException error)
        {
            if (target != null) target.delete();
            throw error;
        }
        finally
        {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject request(String method, String path, JSONObject requestBody)
        throws IOException
    {
        if (baseUrl.isEmpty()) throw new IOException("The report review API is not configured.");
        if (accessToken.isEmpty()) throw new IOException("Sign in again to review reports.");

        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            connection.setRequestMethod(method);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            if (requestBody != null)
            {
                byte[] bytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream())
                {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            String responseBody = readBody(status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream());
            JSONObject json = responseBody.isEmpty() ? new JSONObject() : new JSONObject(responseBody);
            if (status < 200 || status >= 300)
            {
                String message = json.optString("message", "Report request failed (HTTP " + status + ").");
                throw new ApiException(status, json.optString("code", "REQUEST_FAILED"), message);
            }
            return json;
        }
        catch (JSONException error)
        {
            throw new IOException("The report review service returned an invalid response.", error);
        }
        finally
        {
            if (connection != null) connection.disconnect();
        }
    }

    private static ReviewReport parseReport(JSONObject json) throws IOException
    {
        String reportId = json.optString("reportId", "");
        String status = json.optString("statusCode", "");
        if (reportId.isEmpty() || status.isEmpty())
        {
            throw new IOException("The report review service returned an incomplete report.");
        }
        List<ReviewAttachment> attachments = new ArrayList<>();
        JSONArray media = json.optJSONArray("attachments");
        if (media != null)
        {
            for (int i = 0; i < media.length(); i++)
            {
                JSONObject item = media.optJSONObject(i);
                if (item != null && !item.optString("attachmentId", "").isEmpty())
                {
                    attachments.add(new ReviewAttachment(item.optString("attachmentId"),
                        item.optString("attachmentType"), item.optString("mimeType")));
                }
            }
        }
        return new ReviewReport(reportId,
            json.optString("reportNumber", reportId),
            json.optString("typeCode", ""),
            json.optString("locationCode", ""),
            json.optString("locationName", ""),
            json.optString("description", ""),
            ApiDates.parseIsoToMillis(json.optString("submittedAt")),
            ApiDates.parseIsoToMillis(json.optString("occurredAt")),
            status,
            json.optBoolean("anonymous", false),
            Math.max(0, json.optInt("imageCount", 0)),
            Math.max(0, json.optInt("voiceNoteCount", 0)),
            json.isNull("reviewReason") ? null : json.optString("reviewReason", null),
            json.optBoolean("evidenceComplete", true), attachments,
            json.optString("dashboardUrl", ""));
    }

    private static String encodePath(String value) throws IOException
    {
        try
        {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8")
                .replace("+", "%20");
        }
        catch (Exception error)
        {
            throw new IOException("Could not encode the report ID.", error);
        }
    }

    private static String readBody(InputStream input) throws IOException
    {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1)
            {
                if (output.size() + count > 131072) throw new IOException("Response is too large.");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static final class ApiException extends IOException
    {
        private final int statusCode;
        private final String errorCode;

        ApiException(int statusCode, String errorCode, String message)
        {
            super(message);
            this.statusCode = statusCode;
            this.errorCode = errorCode;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        public String getErrorCode()
        {
            return errorCode;
        }
    }
}
