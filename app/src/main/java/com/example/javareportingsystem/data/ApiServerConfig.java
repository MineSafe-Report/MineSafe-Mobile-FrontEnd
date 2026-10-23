package com.example.javareportingsystem.data;

import android.content.Context;

import com.example.javareportingsystem.R;

import java.net.URI;
import java.net.URISyntaxException;

// API SERVER
// Saves the server address entered on the sign in screen.

public final class ApiServerConfig
{
    private static final String PREFERENCES = "minesafe_server";
    private static final String ADDRESS = "api_base_url";

    private ApiServerConfig()
    {
    }

    public static String getBaseUrl(Context context)
    {
        String fallback = context.getString(R.string.api_base_url);
        return context.getApplicationContext()
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(ADDRESS, fallback);
    }

    public static boolean saveBaseUrl(Context context, String baseUrl)
    {
        return context.getApplicationContext()
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(ADDRESS, baseUrl)
            .commit();
    }

    public static String normalize(String address)
    {
        try
        {
            URI uri = new URI(address.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty()
                    && !uri.getPath().equals("/")))
            {
                throw new IllegalArgumentException("Enter a server address like http://192.168.1.23:5080");
            }

            String result = scheme.toLowerCase() + "://" + host;
            if (uri.getPort() >= 0)
            {
                result += ":" + uri.getPort();
            }
            return result;
        }
        catch (URISyntaxException error)
        {
            throw new IllegalArgumentException("Enter a server address like http://192.168.1.23:5080");
        }
    }
}
