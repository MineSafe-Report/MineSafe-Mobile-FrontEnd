MineSafe Mobile
MineSafe is a mobile incident reporting system for underground mines. Workers report hazards, incidents and near misses from the Android app, supervisors review them, and safety officers follow up on incidents through the web dashboard.

This repository contains the Android app. It connects to the MineSafe API, which stores reports in SQL Server and also serves the web dashboard.

Quick start
You need the .NET 9 SDK, SQL Server LocalDB, sqlcmd and Node.js. Android Studio is only needed to run the mobile app on an emulator.

1. Create the database (first time only)

From the backend folder in PowerShell:

.\scripts\create-local-db.ps1
This creates the MineSafetyDb database and all its tables in (localdb)\MSSQLLocalDB. A new database already has the latest schema, so the migration scripts are only needed for an older existing database.

2. Start the API

From the same folder:

dotnet run --launch-profile lan
Leave this window open and check http://localhost:5080/health in your browser. On the first run the API adds the test accounts below. Swagger, the API test page, is at http://localhost:5080/swagger.

3. Start the web dashboard

In a second window, from the web frontend folder:

node dev-server.js
Open http://127.0.0.1:5500. The dashboard expects the API at http://localhost:5080, so both must run on the same PC.

4. Run the Android app

Open JavaReportingSystem in Android Studio and run it on an emulator. The debug build already connects to the API at http://10.0.2.2:5080.
The sign-in screen has a server address field. On a phone connected to the same Wi-Fi as the API computer, enter that computer's local IP address, for example http://192.168.1.23:5080.

To stop everything, press Ctrl + C in the API and web dashboard windows.

Test accounts
All test accounts use the password Password123!.

Worker ID	Role	What they can do
W1001	Worker	Submit reports at Tunnel 7 and Stope 12B
S2001	Supervisor	Review reports at Tunnel 7 and Stope 12B
S2002	Supervisor	Review reports at Shaft 1 Station and Workshop
O3001	Safety Officer	View incidents across the mine
A9001	Administrator	Manage accounts and locations
Connecting a physical phone
Make sure the phone and the PC are on the same Wi-Fi network or hotspot.
Find the PC's IPv4 address with ipconfig, for example 192.168.1.23.
Allow port 5080 through the Windows firewall (once, in PowerShell as Administrator):
New-NetFirewallRule -DisplayName "MineSafe API 5080" -Direction Inbound -Protocol TCP -LocalPort 5080 -Action Allow
Build the app with http://<your-ip>:5080 as the API address and install it on the phone.
Open http://<your-ip>:5080/health in the phone's browser to check that it can reach the API.
