# Local login API

Run the mock server from the project root:

```powershell
python tools/mock_api.py
```

Keep that terminal open, then run the **debug** app in the Android emulator.
The emulator connects to the computer at `http://10.0.2.2:8080`.
On Android 17, grant the local-network permission when the app asks.

Demo accounts:

| Worker ID | Password | Result |
| --- | --- | --- |
| `WORKER001` | `Demo123!` | Worker role |
| `SUP001` | `Demo123!` | Supervisor role |
| `INACTIVE001` | `Demo123!` | Inactive-account error |

Check the server from Windows with
`Invoke-RestMethod http://127.0.0.1:8080/health`.
The login endpoint is `POST /api/auth/login` with JSON
`{"workerId":"WORKER001","password":"Demo123!"}`.

This server is for local tests only. Its tokens are fake and it does not use the Accounts database.
The release app has no API URL yet and does not allow HTTP.
