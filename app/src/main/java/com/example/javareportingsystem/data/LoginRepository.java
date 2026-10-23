package com.example.javareportingsystem.data;

import android.content.Context;

import com.example.javareportingsystem.data.model.LoggedInUser;

import java.io.IOException;

//LOGIN REPOSITORY
//Stores the login and checks wether the auth token is valid.

public final class LoginRepository
{
    private static LoginRepository instance;

    private final LoginDataSource dataSource;
    private final SessionStore sessionStore;
    private LoggedInUser user;

    private LoginRepository(Context context, LoginDataSource dataSource)
    {
        this.dataSource = dataSource;
        this.sessionStore = new SessionStore(context);
        this.user = sessionStore.restore();
    }

    public static synchronized LoginRepository getInstance(
        Context context, LoginDataSource dataSource)
    {
        if (instance == null)
        {
            instance = new LoginRepository(context, dataSource);
        }
        return instance;
    }

    public static synchronized LoginRepository getExistingInstance()
    {
        return instance;
    }

    public synchronized LoggedInUser getUser()
    {
        return isLoggedIn() ? user : null;
    }

    //Use of saved information for navigaiton (API calls do not use this)
    public synchronized LoggedInUser getCachedUser()
    {
        return user;
    }

    public synchronized boolean isLoggedIn()
    {
        return user != null && user.getAccessExpiresAtMillis() > System.currentTimeMillis();
    }


    //Retur a sestion when token is valid
    public synchronized LoggedInUser getFreshUser()
    {
        return isLoggedIn() ? user : null;
    }

    public Result<LoggedInUser> login(String workerId, String password)
    {
        Result<LoggedInUser> result = dataSource.login(workerId, password);
        if (result instanceof Result.Success)
        {
            LoggedInUser authenticated =
                (LoggedInUser) ((Result.Success<?>) result).getData();
            if (authenticated.getAccessExpiresAtMillis() <= System.currentTimeMillis())
            {
                return new Result.Error(new IOException("Sign-in session has already expired."));
            }
            synchronized (this)
            {
                try
                {
                    sessionStore.save(authenticated);
                }
                catch (Exception e)
                {
                    return new Result.Error(new IOException(
                        "Could not save sign-in on this device.", e));
                }
                user = authenticated;
            }
        }
        return result;
    }

    //Logout, Removal of token
    public synchronized boolean logout()
    {
        if (!sessionStore.clear())
        {
            return false;
        }
        user = null;
        return true;
    }
}
