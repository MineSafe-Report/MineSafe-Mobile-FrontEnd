package com.example.javareportingsystem.data;

import android.content.Context;

import com.example.javareportingsystem.data.model.LoggedInUser;

import java.io.IOException;

/** Holds the current session and restores it while its access token is valid. */
public final class LoginRepository {
    private static LoginRepository instance;

    private final LoginDataSource dataSource;
    private final SessionStore sessionStore;
    private LoggedInUser user;

    private LoginRepository(Context context, LoginDataSource dataSource) {
        this.dataSource = dataSource;
        this.sessionStore = new SessionStore(context);
        this.user = sessionStore.restore();
        if (user != null && user.getAccessExpiresAtMillis() <= System.currentTimeMillis()) {
            user = null;
            sessionStore.clear();
        }
    }

    public static synchronized LoginRepository getInstance(
            Context context, LoginDataSource dataSource) {
        if (instance == null) {
            instance = new LoginRepository(context, dataSource);
        }
        return instance;
    }

    public static synchronized LoginRepository getExistingInstance() {
        return instance;
    }

    public synchronized LoggedInUser getUser() {
        return isLoggedIn() ? user : null;
    }

    public synchronized boolean isLoggedIn() {
        if (user == null) {
            return false;
        }
        if (user.getAccessExpiresAtMillis() <= System.currentTimeMillis()) {
            user = null;
            sessionStore.clear();
            return false;
        }
        return true;
    }

    public Result<LoggedInUser> login(String workerId, String password) {
        Result<LoggedInUser> result = dataSource.login(workerId, password);
        if (result instanceof Result.Success) {
            LoggedInUser authenticated =
                    (LoggedInUser) ((Result.Success<?>) result).getData();
            if (authenticated.getAccessExpiresAtMillis() <= System.currentTimeMillis()) {
                return new Result.Error(new IOException("Sign-in session has already expired."));
            }
            synchronized (this) {
                try {
                    sessionStore.save(authenticated);
                } catch (Exception e) {
                    return new Result.Error(new IOException(
                            "Could not save sign-in on this device.", e));
                }
                user = authenticated;
            }
        }
        return result;
    }

    /** Clears local credentials. Server-side token revocation awaits the real API. */
    public synchronized boolean logout() {
        if (!sessionStore.clear()) {
            return false;
        }
        user = null;
        return true;
    }
}
