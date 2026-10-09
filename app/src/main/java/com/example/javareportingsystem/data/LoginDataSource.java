package com.example.javareportingsystem.data;

import com.example.javareportingsystem.data.model.LoggedInUser;

public interface LoginDataSource {
    Result<LoggedInUser> login(String workerId, String password);

    void logout();
}
