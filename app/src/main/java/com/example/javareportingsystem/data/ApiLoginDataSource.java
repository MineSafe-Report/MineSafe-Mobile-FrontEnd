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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Login transport. The debug URL points to the local mock API; release has no URL yet.
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
            request.put("workerId", workerId);
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
                return new Result.Success<>(parseLoginResponse(responseBody));
            }
            if (status == 401) {
                return new Result.Error(new IOException("Worker ID or password is incorrect."));
            }
            if (status == 403) {
                return new Result.Error(new IOException("This account is inactive."));
            }
            return new Result.Error(new IOException("Sign-in failed (HTTP " + status + ")."));
        } catch (IOException e) {
            return new Result.Error(new IOException("Cannot connect to the sign-in service.", e));
        } catch (JSONException | ParseException e) {
            return new Result.Error(new IOException("Invalid sign-in response.", e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static LoggedInUser parseLoginResponse(String body)
            throws JSONException, ParseException {
        JSONObject response = new JSONObject(body);
        JSONObject account = response.getJSONObject("account");
        JSONArray locations = account.getJSONArray("assignedLocations");
        List<String> assignedLocations = new ArrayList<>();
        for (int index = 0; index < locations.length(); index++) {
            assignedLocations.add(locations.getString(index));
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        Date expiry = format.parse(response.getString("expiresAt"));
        if (expiry == null) {
            throw new ParseException("Missing expiry", 0);
        }

        String accessToken = response.getString("accessToken");
        String refreshToken = response.getString("refreshToken");
        if (accessToken.isEmpty() || refreshToken.isEmpty()) {
            throw new JSONException("Missing session token");
        }

        return new LoggedInUser(
                account.getInt("accountId"),
                account.getString("workerId"),
                account.getString("fullName"),
                account.getString("role"),
                accessToken,
                expiry.getTime(),
                refreshToken,
                assignedLocations);
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

    @Override
    public void logout() {
        // TODO: POST /api/auth/logout when the real backend provides it.
    }
}
