package com.example.javareportingsystem.data.model;

//REVIEW ATTACHMENT
//Holds atatchments detail that was returned from the PAI

public final class ReviewAttachment
{
    private final String id;
    private final String type;
    private final String mimeType;

    public ReviewAttachment(String id, String type, String mimeType)
    {
        this.id = id;
        this.type = type;
        this.mimeType = mimeType;
    }

    public String getId()
    {
        return id;
    }

    public String getType()
    {
        return type;
    }

    public String getMimeType()
    {
        return mimeType;
    }
}
