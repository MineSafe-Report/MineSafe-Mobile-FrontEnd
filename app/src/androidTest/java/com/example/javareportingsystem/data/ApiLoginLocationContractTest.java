package com.example.javareportingsystem.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.javareportingsystem.data.model.LoggedInUser;
import com.example.javareportingsystem.data.model.ReviewReport;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Checks the real API's wire shapes without depending on a running development database.
 */
@RunWith(AndroidJUnit4.class)
public class ApiLoginLocationContractTest
{
    private static final String USER = "{\"accountId\":1,\"workerId\":\"W1001\","
        + "\"fullName\":\"Test Worker\",\"roles\":[\"Worker\",\"Supervisor\"],"
        + "\"locations\":[{\"locationId\":2,\"locationCode\":\"T-7\","
        + "\"locationName\":\"Tunnel 7\",\"isDefault\":true}]}";

    @Test
    public void loginSendsBackendFieldsAndReadsRolesAndLocations() throws Exception
    {
        String response = "{\"token\":\"test-token\","
            + "\"expiresAt\":\"2030-01-01T00:00:00.1234567Z\",\"user\":" + USER + "}";
        try (OneRequestServer server = new OneRequestServer(response))
        {
            Result<LoggedInUser> result = new ApiLoginDataSource(server.url())
                .login("W1001", "Password123!");
            assertTrue(result instanceof Result.Success);
            LoggedInUser user = (LoggedInUser) ((Result.Success<?>) result).getData();
            assertEquals("SUPERVISOR", user.getRole());
            assertEquals("test-token", user.getAccessToken());
            assertEquals("T-7", user.getAssignedLocations().get(0));
            assertTrue(user.getAccessExpiresAtMillis() > System.currentTimeMillis());

            RecordedRequest request = server.recorded();
            assertEquals("POST /api/auth/login HTTP/1.1", request.line);
            assertEquals("W1001", new JSONObject(request.body).getString("login"));
            assertFalse(new JSONObject(request.body).has("workerId"));
        }
    }

    @Test
    public void administratorOnlyAccountIsDirectedToWeb() throws Exception
    {
        String admin = USER.replace("[\"Worker\",\"Supervisor\"]", "[\"Administrator\"]");
        String response = "{\"token\":\"test-token\","
            + "\"expiresAt\":\"2030-01-01T00:00:00Z\",\"user\":" + admin + "}";
        try (OneRequestServer server = new OneRequestServer(response))
        {
            Result<LoggedInUser> result = new ApiLoginDataSource(server.url())
                .login("A9001", "Password123!");
            assertTrue(result instanceof Result.Error);
            assertEquals("Administrators use the web dashboard.",
                ((Result.Error) result).getError().getMessage());
        }
    }

    @Test
    public void locationsUsePlainArrayAndNumericIds() throws Exception
    {
        String response = "[{\"locationId\":2,\"locationCode\":\"T-7\","
            + "\"locationName\":\"Tunnel 7\",\"isActive\":true},"
            + "{\"locationId\":3,\"locationCode\":\"ST-12B\","
            + "\"locationName\":\"Stope 12B\",\"isActive\":true}]";
        try (OneRequestServer server = new OneRequestServer(response))
        {
            List<com.example.javareportingsystem.data.model.RemoteLocation> locations =
                new ApiLocationDataSource(server.url()).fetchActiveLocations("test-token");
            assertEquals(2, locations.size());
            assertEquals("2", locations.get(0).id);
            assertEquals("T-7", locations.get(0).code);
            assertEquals("Tunnel 7", locations.get(0).name);
            assertEquals("3", locations.get(1).id);

            RecordedRequest request = server.recorded();
            assertEquals("GET /api/locations HTTP/1.1", request.line);
            assertEquals("Bearer test-token", request.authorization);
        }
    }

    @Test
    public void myReportsUsesMineRouteAndBackendStatusCode() throws Exception
    {
        String response = "{\"items\":[{\"reportId\":42,\"statusCode\":\"DENIED\","
            + "\"status\":\"Denied\"}],\"total\":1,\"page\":1,\"pageSize\":100}";
        try (OneRequestServer server = new OneRequestServer(response))
        {
            JSONArray reports = new ApiReportingDataSource(server.url(), "test-token")
                .getMyReports();
            assertEquals("DENIED", reports.getJSONObject(0).getString("statusCode"));
            RecordedRequest request = server.recorded();
            assertEquals("GET /api/reports/mine?page=1&pageSize=100 HTTP/1.1", request.line);
            assertEquals("Bearer test-token", request.authorization);
        }
    }

    @Test
    public void supervisorQueueReadsBackendCodesAndDates() throws Exception
    {
        String response = "{\"items\":[{\"reportId\":42,\"reportNumber\":\"MS-2026-00042\","
            + "\"typeCode\":\"INCIDENT\",\"statusCode\":\"PENDING_REVIEW\","
            + "\"status\":\"Pending Review\",\"locationCode\":\"T-7\","
            + "\"locationName\":\"Tunnel 7\",\"submittedAt\":\"2026-10-21T08:30:00Z\","
            + "\"occurredAt\":\"2026-10-21T08:00:00Z\",\"imageCount\":2}],"
            + "\"total\":1,\"page\":1,\"pageSize\":100}";
        try (OneRequestServer server = new OneRequestServer(response))
        {
            List<ReviewReport> reports = new ApiReportReviewDataSource(server.url(), "test-token")
                .getReports("PENDING_REVIEW");
            assertEquals(1, reports.size());
            assertEquals("MS-2026-00042", reports.get(0).getReferenceNumber());
            assertEquals("INCIDENT", reports.get(0).getType());
            assertEquals("PENDING_REVIEW", reports.get(0).getStatus());
            assertTrue(reports.get(0).getSubmittedAtEpochMillis() > 0);
            assertEquals(2, reports.get(0).getImageCount());
            assertEquals("GET /api/supervisor/reports?status=PENDING_REVIEW&page=1&pageSize=100 HTTP/1.1",
                server.recorded().line);
        }
    }

    private static final class RecordedRequest
    {
        final String line;
        final String authorization;
        final String body;

        RecordedRequest(String line, String authorization, String body)
        {
            this.line = line;
            this.authorization = authorization;
            this.body = body;
        }
    }

    private static final class OneRequestServer implements AutoCloseable
    {
        private final ServerSocket socket;
        private final FutureTask<RecordedRequest> task;

        OneRequestServer(String response) throws Exception
        {
            socket = new ServerSocket(0);
            task = new FutureTask<>(() ->
            {
                try (Socket client = socket.accept())
                {
                    client.setSoTimeout(5000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(
                        client.getInputStream(), StandardCharsets.UTF_8));
                    String requestLine = input.readLine();
                    String authorization = null;
                    int contentLength = 0;
                    String header;
                    while ((header = input.readLine()) != null && !header.isEmpty())
                    {
                        if (header.regionMatches(true, 0, "Authorization:", 0, 14))
                        {
                            authorization = header.substring(14).trim();
                        }
                        else if (header.regionMatches(true, 0, "Content-Length:", 0, 15))
                        {
                            contentLength = Integer.parseInt(header.substring(15).trim());
                        }
                    }
                    char[] body = new char[contentLength];
                    int count = 0;
                    while (count < body.length)
                    {
                        int read = input.read(body, count, body.length - count);
                        if (read < 0) throw new IllegalStateException("Incomplete request body");
                        count += read;
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    OutputStream output = client.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                    output.write(bytes);
                    output.flush();
                    return new RecordedRequest(requestLine, authorization, new String(body));
                }
            });
            new Thread(task, "api-contract-test-server").start();
        }

        String url()
        {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        RecordedRequest recorded() throws Exception
        {
            return task.get(6, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception
        {
            socket.close();
        }
    }
}
