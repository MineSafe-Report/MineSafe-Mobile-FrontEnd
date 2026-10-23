package com.example.javareportingsystem.data;

import java.net.HttpURLConnection;
import java.net.URL;

//API CONNECTION
//API reachability check, (the code checks wether the API gatway could be reached)

public final class ApiConnection
{
    private ApiConnection()
    {
    }

    public static boolean isReachable(String baseUrl)
    {
        if (baseUrl == null || baseUrl.trim().isEmpty()) return false;
        HttpURLConnection connection = null;
        try
        {
            connection = (HttpURLConnection) new URL(baseUrl + "/health").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setUseCaches(false);
            long started = android.os.SystemClock.elapsedRealtime();
            return connection.getResponseCode() == 200
                && android.os.SystemClock.elapsedRealtime() - started <= 2000;
        }
        catch (Exception ignored)
        {
            return false;
        }
        finally
        {
            if (connection != null) connection.disconnect();
        }
    }
}
