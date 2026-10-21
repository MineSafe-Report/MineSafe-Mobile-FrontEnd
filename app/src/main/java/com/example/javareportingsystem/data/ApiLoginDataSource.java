package com.example.javareportingsystem.data;

import com.example.javareportingsystem.data.model.LoggedInUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Login transport for the configured API endpoint.
 */
public final class ApiLoginDataSource implements LoginDataSource {
    private final String baseUrl;

    public ApiLoginDataSource(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Result<LoggedInUser> login(String workerId, String password) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new Result.Error(new IOException("Sign-in API is not configured yet."));
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "/api/auth/login");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject request = new JSONObject();
            request.put("login", workerId);
            request.put("password", password);
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            String responseBody = readBody(
                    status >= 200 && status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream());

            if (status == 200) {
                LoggedInUser user = parseLoginResponse(responseBody);
                if ("ADMINISTRATOR".equals(user.getRole())) {
                    return new Result.Error(new IOException(
                            "Administrators use the web dashboard."));
                }
                return new Result.Success<>(user);
            }
            if (status == 401) {
                return new Result.Error(new IOException("Worker ID or password is incorrect."));
            }
            return new Result.Error(new IOException("Sign-in failed (HTTP " + status + ")."));
        } catch (IOException e) {
            return new Result.Error(new IOException("Cannot connect to the sign-in service.", e));
        } catch (JSONException e) {
            return new Result.Error(new IOException("Invalid sign-in response.", e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static LoggedInUser parseLoginResponse(String body)
            throws JSONException {
        JSONObject response = new JSONObject(body);
        JSONObject account = response.getJSONObject("user");
        JSONArray locations = account.getJSONArray("locations");
        List<String> assignedLocations = new ArrayList<>();
        for (int index = 0; index < locations.length(); index++) {
            assignedLocations.add(locations.getJSONObject(index).getString("locationCode"));
        }
        long expiry = ApiDates.parseIsoToMillis(response.getString("expiresAt"));
        String accessToken = response.getString("token");
        if (accessToken.isEmpty() || expiry == 0L) {
            throw new JSONException("Missing session token");
        }
        return new LoggedInUser(
                account.getInt("accountId"),
                account.getString("workerId"),
                account.getString("fullName"),
                mobileRole(account.getJSONArray("roles")),
                accessToken,
                expiry,
                assignedLocations);
    }

    private static String mobileRole(JSONArray roles) throws JSONException {
        boolean worker = false;
        boolean supervisor = false;
        boolean safetyOfficer = false;
        boolean administrator = false;
        for (int index = 0; index < roles.length(); index++) {
            switch (roles.getString(index)) {
                case "Worker": worker = true; break;
                case "Supervisor": supervisor = true; break;
                case "SafetyOfficer": safetyOfficer = true; break;
                case "Administrator": administrator = true; break;
                default: break;
            }
        }
        if (supervisor) return "SUPERVISOR";
        if (safetyOfficer) return "SAFETY_OFFICER";
        if (worker) return "WORKER";
        if (administrator) return "ADMINISTRATOR";
        throw new JSONException("No supported mobile role");
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > 65536) {
                    throw new IOException("Response is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

}
