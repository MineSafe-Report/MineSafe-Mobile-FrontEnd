package com.example.javareportingsystem.data;

import com.example.javareportingsystem.data.model.LoggedInUser;

//LOGIN DATA SOURCE
//Defin login request used by app

public interface LoginDataSource
{
    Result<LoggedInUser> login(String workerId, String password);

}
