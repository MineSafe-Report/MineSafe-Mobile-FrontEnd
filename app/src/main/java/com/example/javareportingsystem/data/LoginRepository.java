package com.example.javareportingsystem.data;

import com.example.javareportingsystem.data.model.LoggedInUser;

/**
 * Holds the current session in memory until persistent token storage is implemented.
 */
public final class LoginRepository {
    private static LoginRepository instance;

    private final LoginDataSource dataSource;
    private LoggedInUser user;

    private LoginRepository(LoginDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static synchronized LoginRepository getInstance(LoginDataSource dataSource) {
        if (instance == null) {
            instance = new LoginRepository(dataSource);
        }
        return instance;
    }

    public static synchronized LoginRepository getExistingInstance() {
        return instance;
    }

    public synchronized LoggedInUser getUser() {
        return user;
    }

    public synchronized boolean isLoggedIn() {
        return user != null && user.getAccessExpiresAtMillis() > System.currentTimeMillis();
    }

    public Result<LoggedInUser> login(String workerId, String password) {
        Result<LoggedInUser> result = dataSource.login(workerId, password);
        if (result instanceof Result.Success) {
            synchronized (this) {
                user = ((Result.Success<LoggedInUser>) result).getData();
            }
        }
        return result;
    }

    public synchronized void logout() {
        user = null;
        dataSource.logout();
    }
}
