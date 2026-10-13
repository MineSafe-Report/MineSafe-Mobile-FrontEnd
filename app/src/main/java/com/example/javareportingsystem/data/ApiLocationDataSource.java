package com.example.javareportingsystem.data;

import com.example.javareportingsystem.data.model.RemoteLocation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the protected, paged location catalogue from the API. */
public final class ApiLocationDataSource {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final String baseUrl;

    public ApiLocationDataSource(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<RemoteLocation> fetchActiveLocations(String accessToken) throws IOException {
        if (baseUrl == null || baseUrl.isEmpty() || accessToken == null
                || accessToken.isEmpty()) {
            throw new IOException("Location API or session is not configured");
        }
        List<RemoteLocation> locations = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JSONObject response = fetchPage(page, accessToken);
            try {
                int returnedPage = response.getInt("page");
                int pageSize = response.getInt("pageSize");
                int total = response.getInt("total");
                JSONArray items = response.getJSONArray("items");
                if (returnedPage != page || pageSize < 1 || pageSize > PAGE_SIZE
                        || total < 0 || total > PAGE_SIZE * MAX_PAGES
                        || items.length() > pageSize
                        || (items.length() == 0 && locations.size() < total)) {
                    throw new IOException("Invalid locations response");
                }
                for (int index = 0; index < items.length(); index++) {
                    JSONObject item = items.getJSONObject(index);
                    locations.add(new RemoteLocation(
                            item.getString("locationId"),
                            item.getString("code"),
                            item.getString("name")));
                }
                if (locations.size() == total) {
                    return locations;
                }
                if (locations.size() > total) {
                    throw new IOException("Locations response exceeds its total");
                }
            } catch (JSONException error) {
                throw new IOException("Invalid locations response", error);
            }
        }
        throw new IOException("Too many location pages");
    }

    private JSONObject fetchPage(int page, String accessToken) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "/api/locations?page=" + page
                    + "&pageSize=" + PAGE_SIZE);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            if (connection.getResponseCode() != 200) {
                throw new IOException("Locations request failed (HTTP "
                        + connection.getResponseCode() + ")");
            }
            byte[] body = readLimited(connection.getInputStream());
            return new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (JSONException error) {
            throw new IOException("Invalid locations response", error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IOException("Locations response is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
