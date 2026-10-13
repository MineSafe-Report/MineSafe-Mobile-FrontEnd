"""Small local API for exercising Android login and location requests. Debug only."""

from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import secrets
from threading import Lock
from urllib.parse import parse_qs, urlsplit

HOST = "127.0.0.1"
PORT = 8080
DEMO_PASSWORD = "Demo123!"
ACCOUNTS = {
    "WORKER001": {
        "accountId": 1,
        "workerId": "WORKER001",
        "fullName": "Demo Worker",
        "role": "WORKER",
        "assignedLocations": ["LOC-1", "LOC-2", "LOC-3"],
        "isActive": True,
    },
    "SUP001": {
        "accountId": 2,
        "workerId": "SUP001",
        "fullName": "Demo Supervisor",
        "role": "SUPERVISOR",
        "assignedLocations": ["LOC-1", "LOC-2", "LOC-3"],
        "isActive": True,
    },
    "INACTIVE001": {
        "accountId": 3,
        "workerId": "INACTIVE001",
        "fullName": "Inactive Worker",
        "role": "WORKER",
        "assignedLocations": [],
        "isActive": False,
    },
}

LOCATIONS = [
    {"locationId": "loc-1", "code": "LOC-1", "name": "Mine Entrance", "parentLocationId": None},
    {"locationId": "loc-2", "code": "LOC-2", "name": "Shaft A", "parentLocationId": None},
    {"locationId": "loc-3", "code": "LOC-3", "name": "Processing Plant", "parentLocationId": None},
]
ACCESS_TOKENS = {}
TOKEN_LOCK = Lock()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        try:
            super().log_message(format, *args)
        except (OSError, ValueError):
            pass  # A detached terminal must not break an API response.

    def send_json(self, status, payload):
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        request_url = urlsplit(self.path)
        if request_url.path == "/health":
            self.send_json(200, {"status": "ok"})
        elif request_url.path == "/api/locations":
            authorization = self.headers.get("Authorization", "")
            token = authorization[7:] if authorization.startswith("Bearer ") else ""
            with TOKEN_LOCK:
                session = ACCESS_TOKENS.get(token)
            if session is None or session["expiresAt"] <= datetime.now(timezone.utc):
                self.send_json(401, {"code": "UNAUTHORIZED", "message": "Sign in is required."})
                return
            try:
                query = parse_qs(request_url.query)
                page = int(query.get("page", ["1"])[0])
                page_size = int(query.get("pageSize", ["100"])[0])
                if page < 1 or not 1 <= page_size <= 100:
                    raise ValueError("Invalid page")
            except ValueError:
                self.send_json(400, {"code": "BAD_REQUEST", "message": "Invalid page request."})
                return
            allowed = set(session["account"]["assignedLocations"])
            locations = [location for location in LOCATIONS if location["code"] in allowed]
            first = (page - 1) * page_size
            self.send_json(200, {
                "items": locations[first:first + page_size],
                "page": page,
                "pageSize": page_size,
                "total": len(locations),
            })
        else:
            self.send_json(404, {"code": "NOT_FOUND", "message": "Not found."})

    def do_POST(self):
        if self.path != "/api/auth/login":
            self.send_json(404, {"code": "NOT_FOUND", "message": "Not found."})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 4096:
                raise ValueError("Invalid body size")
            request = json.loads(self.rfile.read(length))
            if not isinstance(request, dict):
                raise ValueError("Expected a JSON object")
            worker_id = request.get("workerId", "")
            password = request.get("password", "")
            if not isinstance(worker_id, str) or not isinstance(password, str):
                raise ValueError("Invalid credentials")
        except (ValueError, json.JSONDecodeError):
            self.send_json(400, {"code": "BAD_REQUEST", "message": "Invalid JSON request."})
            return

        account = ACCOUNTS.get(worker_id.strip().upper())
        if account is None or password != DEMO_PASSWORD:
            self.send_json(401, {"code": "INVALID_CREDENTIALS", "message": "Worker ID or password is incorrect."})
            return
        if not account["isActive"]:
            self.send_json(403, {"code": "ACCOUNT_INACTIVE", "message": "This account is inactive."})
            return

        expires_at_time = datetime.now(timezone.utc) + timedelta(hours=1)
        expires_at = expires_at_time.strftime("%Y-%m-%dT%H:%M:%SZ")
        public_account = {key: value for key, value in account.items() if key != "isActive"}
        access_token = "demo-" + secrets.token_urlsafe(24)
        with TOKEN_LOCK:
            ACCESS_TOKENS[access_token] = {"account": public_account, "expiresAt": expires_at_time}
        self.send_json(200, {
            "accessToken": access_token,
            "expiresAt": expires_at,
            "refreshToken": "demo-" + secrets.token_urlsafe(24),
            "account": public_account,
        })


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Mock login and locations API: http://{HOST}:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
