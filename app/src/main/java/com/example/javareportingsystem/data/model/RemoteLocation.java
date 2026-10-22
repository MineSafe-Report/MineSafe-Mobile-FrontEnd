package com.example.javareportingsystem.data.model;

/**
 * A location supplied by the protected locations API.
 */
public final class RemoteLocation
{
    public final String id;
    public final String code;
    public final String name;

    public RemoteLocation(String id, String code, String name)
    {
        this.id = id;
        this.code = code;
        this.name = name;
    }
}
