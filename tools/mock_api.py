"""Local in-memory reporting API for Android integration tests. Debug only."""

from datetime import datetime, timedelta, timezone
import base64
from email.parser import BytesParser
from email.policy import default
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import secrets
import struct
import tempfile
from threading import Lock
from urllib.parse import parse_qs, urlsplit
import wave
import zlib

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
REFRESH_TOKENS = {}
TOKEN_LOCK = Lock()
REPORT_LOCK = Lock()
DEVICES = {}
ATTACHMENTS = {}
STATE_PATH = None
SESSIONS_PATH = None


def _write_json_atomic(path, value):
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent,
                                     prefix=path.name + ".", delete=False) as output:
        temporary = Path(output.name)
        try:
            json.dump(value, output, separators=(",", ":"))
            output.flush()
            os.fsync(output.fileno())
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    os.replace(temporary, path)


def _save_reports_locked():
    if STATE_PATH is None:
        return
    attachments = {key: {**value, "data": base64.b64encode(value["data"]).decode("ascii")}
                   for key, value in ATTACHMENTS.items()}
    _write_json_atomic(STATE_PATH, {"version": 1, "reports": REPORTS,
                                    "devices": DEVICES, "attachments": attachments})


def _save_sessions_locked():
    if SESSIONS_PATH is None:
        return
    access = {key: {**value, "expiresAt": value["expiresAt"].isoformat()}
              for key, value in ACCESS_TOKENS.items()}
    _write_json_atomic(SESSIONS_PATH, {"access": access, "refresh": REFRESH_TOKENS})


def _now_millis():
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _seed_reports():
    now = _now_millis()
    return {
        "RPT-1004": {
            "reportId": "RPT-1004", "referenceNumber": "RPT-1004",
            "type": "HAZARD", "locationCode": "LOC-1", "locationName": "Mine Entrance",
            "description": "Loose rock on the walkway near the entrance.",
            "submittedAtEpochMillis": now - 35 * 60 * 1000,
            "status": "PENDING_REVIEW", "anonymous": True,
            "imageCount": 2, "voiceNoteCount": 1, "reviewReason": None,
            "reviewedAtEpochMillis": None, "reviewedByWorkerId": None,
        },
        "RPT-1003": {
            "reportId": "RPT-1003", "referenceNumber": "RPT-1003",
            "type": "INCIDENT", "locationCode": "LOC-2", "locationName": "Shaft A",
            "description": "A worker slipped but reported no injury.",
            "submittedAtEpochMillis": now - 2 * 60 * 60 * 1000,
            "status": "PENDING_REVIEW", "anonymous": False,
            "imageCount": 1, "voiceNoteCount": 0, "reviewReason": None,
            "reviewedAtEpochMillis": None, "reviewedByWorkerId": None,
            "reporterWorkerId": "WORKER001",
        },
        "RPT-1002": {
            "reportId": "RPT-1002", "referenceNumber": "RPT-1002",
            "type": "NEAR_MISS", "locationCode": "LOC-3", "locationName": "Processing Plant",
            "description": "A loaded trolley rolled toward the work area and was stopped.",
            "submittedAtEpochMillis": now - 5 * 60 * 60 * 1000,
            "status": "PENDING_REVIEW", "anonymous": True,
            "imageCount": 0, "voiceNoteCount": 1, "reviewReason": None,
            "reviewedAtEpochMillis": None, "reviewedByWorkerId": None,
        },
        "RPT-1001": {
            "reportId": "RPT-1001", "referenceNumber": "RPT-1001",
            "type": "HAZARD", "locationCode": "LOC-2", "locationName": "Shaft A",
            "description": "Guard rail was repaired and verified.",
            "submittedAtEpochMillis": now - 24 * 60 * 60 * 1000,
            "status": "APPROVED", "anonymous": True,
            "imageCount": 1, "voiceNoteCount": 0, "reviewReason": None,
            "reviewedAtEpochMillis": now - 23 * 60 * 60 * 1000,
            "reviewedByWorkerId": "SUP001",
        },
        "RPT-1000": {
            "reportId": "RPT-1000", "referenceNumber": "RPT-1000",
            "type": "INCIDENT", "locationCode": "LOC-1", "locationName": "Mine Entrance",
            "description": "Incident details need clarification.",
            "submittedAtEpochMillis": now - 48 * 60 * 60 * 1000,
            "status": "DENIED", "anonymous": False,
            "imageCount": 0, "voiceNoteCount": 0, "reviewReason": "Please add the time the incident occurred.",
            "reviewedAtEpochMillis": now - 47 * 60 * 60 * 1000,
            "reviewedByWorkerId": "SUP001", "reporterWorkerId": "WORKER001",
        },
    }


REPORTS = _seed_reports()


def _sample_media():
    def png_chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff))

    width = height = 32
    pixels = b"".join(b"\x00" + b"\x00\x70\x72" * width for _ in range(height))
    png = (b"\x89PNG\r\n\x1a\n"
           + png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
           + png_chunk(b"IDAT", zlib.compress(pixels))
           + png_chunk(b"IEND", b""))
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(8000)
        audio.writeframes(b"\0\0" * 1600)
    return png, output.getvalue()


def _seed_attachments():
    png, wav = _sample_media()
    for report_id, photo_count, voice_count in (
            ("RPT-1004", 2, 1), ("RPT-1003", 1, 0),
            ("RPT-1002", 0, 1), ("RPT-1001", 1, 0)):
        report = REPORTS[report_id]
        report["attachments"] = []
        report["evidenceComplete"] = True
        for number in range(photo_count + voice_count):
            photo = number < photo_count
            attachment_id = f"ATT-{report_id}-{number + 1}"
            data = png if photo else wav
            mime = "image/png" if photo else "audio/wav"
            metadata = {"attachmentId": attachment_id,
                        "attachmentType": "PHOTO" if photo else "VOICE_NOTE",
                        "mimeType": mime, "fileSizeBytes": len(data),
                        "captureSource": "GALLERY" if photo else "MICROPHONE"}
            ATTACHMENTS[attachment_id] = {"reportId": report_id,
                                          "metadata": metadata, "data": data}
            report["attachments"].append(metadata)
    for report in REPORTS.values():
        report.setdefault("attachments", [])
        report.setdefault("evidenceComplete", True)
        report["statusUpdatedAtEpochMillis"] = report.get(
            "reviewedAtEpochMillis") or report["submittedAtEpochMillis"]


_seed_attachments()


def load_persistent_state(directory=None):
    """Keep local debug reports and sessions across server restarts."""
    global REPORTS, STATE_PATH, SESSIONS_PATH
    storage = Path(directory) if directory is not None else Path(__file__).parent
    STATE_PATH = storage / "mock_api_state.json"
    SESSIONS_PATH = storage / "mock_api_sessions.json"
    if STATE_PATH.exists():
        saved = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        if saved.get("version") != 1:
            raise ValueError("Unsupported mock API state version")
        REPORTS = saved["reports"]
        DEVICES.clear()
        DEVICES.update(saved["devices"])
        ATTACHMENTS.clear()
        ATTACHMENTS.update({key: {**value, "data": base64.b64decode(value["data"])}
                            for key, value in saved["attachments"].items()})
    else:
        # The standalone test server starts empty; unit tests still seed their own data.
        REPORTS = {}
        DEVICES.clear()
        ATTACHMENTS.clear()
        _save_reports_locked()
    if SESSIONS_PATH.exists():
        saved = json.loads(SESSIONS_PATH.read_text(encoding="utf-8"))
        ACCESS_TOKENS.update({key: {**value,
                                     "expiresAt": datetime.fromisoformat(value["expiresAt"])}
                              for key, value in saved["access"].items()})
        REFRESH_TOKENS.update(saved["refresh"])


def reset_reports():
    """Reset demo data between API contract tests."""
    global REPORTS
    with REPORT_LOCK:
        REPORTS = _seed_reports()
        ATTACHMENTS.clear()
        DEVICES.clear()
        _seed_attachments()
    with TOKEN_LOCK:
        ACCESS_TOKENS.clear()
        REFRESH_TOKENS.clear()


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

    def send_binary(self, data, mime_type):
        self.send_response(200)
        self.send_header("Content-Type", mime_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def read_json_request(self, max_bytes=65536):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > max_bytes:
                raise ValueError("Invalid body size")
            request = json.loads(self.rfile.read(length))
            if not isinstance(request, dict):
                raise ValueError("Expected a JSON object")
            return request
        except (ValueError, json.JSONDecodeError):
            self.send_json(400, {"code": "BAD_REQUEST", "message": "Invalid JSON request."})
            return None

    def authenticated_session(self, supervisor=False):
        authorization = self.headers.get("Authorization", "")
        token = authorization[7:] if authorization.startswith("Bearer ") else ""
        with TOKEN_LOCK:
            session = ACCESS_TOKENS.get(token)
        if session is None or session["expiresAt"] <= datetime.now(timezone.utc):
            self.send_json(401, {"code": "UNAUTHORIZED", "message": "Sign in is required."})
            return None
        if supervisor and session["account"].get("role", "").upper() != "SUPERVISOR":
            self.send_json(403, {"code": "FORBIDDEN", "message": "Supervisor access is required."})
            return None
        return session

    @staticmethod
    def public_report(report):
        result = dict(report)
        result.pop("deviceId", None)
        result.pop("localReportId", None)
        result.pop("expectedImageCount", None)
        result.pop("expectedVoiceNoteCount", None)
        if result.get("anonymous"):
            result.pop("reporterWorkerId", None)
        return result

    @staticmethod
    def owns_device(session, device_id):
        return any(device["deviceId"] == device_id
                   and device["accountId"] == session["account"]["accountId"]
                   for device in DEVICES.values())

    def may_view_report(self, session, report):
        return session["account"]["role"] == "SUPERVISOR" or (
            report.get("deviceId") and self.owns_device(session, report["deviceId"]))

    def do_GET(self):
        request_url = urlsplit(self.path)
        if request_url.path == "/health":
            self.send_json(200, {"status": "ok"})
        elif request_url.path == "/api/locations":
            session = self.authenticated_session()
            if session is None:
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
        elif request_url.path == "/api/reports":
            session = self.authenticated_session()
            if session is None:
                return
            query = parse_qs(request_url.query)
            if query.get("view", [""])[0] != "my":
                self.send_json(400, {"code": "BAD_REQUEST", "message": "Unsupported report view."})
                return
            device_id = query.get("deviceId", [""])[0]
            with REPORT_LOCK:
                if not self.owns_device(session, device_id):
                    self.send_json(403, {"code": "FORBIDDEN", "message": "Device access denied."})
                    return
                reports = [self.public_report(report) for report in REPORTS.values()
                           if report.get("deviceId") == device_id]
            reports.sort(key=lambda report: report["submittedAtEpochMillis"], reverse=True)
            try:
                page = int(query.get("page", ["1"])[0])
                page_size = int(query.get("pageSize", ["100"])[0])
                if page < 1 or not 1 <= page_size <= 100:
                    raise ValueError()
            except ValueError:
                self.send_json(400, {"code": "BAD_REQUEST", "message": "Invalid page."})
                return
            start = (page - 1) * page_size
            self.send_json(200, {"items": reports[start:start + page_size],
                                 "page": page, "pageSize": page_size, "total": len(reports)})
        elif request_url.path.startswith("/api/attachments/"):
            session = self.authenticated_session()
            if session is None:
                return
            attachment_id = request_url.path.rsplit("/", 1)[-1]
            with REPORT_LOCK:
                attachment = ATTACHMENTS.get(attachment_id)
                report = REPORTS.get(attachment["reportId"]) if attachment else None
                if report is None:
                    self.send_json(404, {"code": "NOT_FOUND", "message": "Attachment not found."})
                    return
                if not self.may_view_report(session, report):
                    self.send_json(403, {"code": "FORBIDDEN", "message": "Attachment access denied."})
                    return
                data = attachment["data"]
                mime = attachment["metadata"]["mimeType"]
            self.send_binary(data, mime)
        elif request_url.path.startswith("/api/reports/"):
            session = self.authenticated_session()
            if session is None:
                return
            report_id = request_url.path.rsplit("/", 1)[-1]
            with REPORT_LOCK:
                report = REPORTS.get(report_id)
                if report is None:
                    self.send_json(404, {"code": "REPORT_NOT_FOUND", "message": "Report not found."})
                    return
                if not self.may_view_report(session, report):
                    self.send_json(403, {"code": "FORBIDDEN", "message": "Report access denied."})
                    return
                result = self.public_report(report)
            self.send_json(200, {"report": result})
        elif request_url.path == "/api/supervisor/reports":
            session = self.authenticated_session(supervisor=True)
            if session is None:
                return
            query = parse_qs(request_url.query)
            status = query.get("status", ["PENDING_REVIEW"])[0].strip().upper()
            if status not in ("PENDING_REVIEW", "APPROVED", "DENIED", "ALL"):
                self.send_json(400, {"code": "BAD_REQUEST", "message": "Unsupported report status."})
                return
            with REPORT_LOCK:
                reports = [self.public_report(report) for report in REPORTS.values()
                           if status == "ALL" or report["status"] == status]
            reports.sort(key=lambda report: report["submittedAtEpochMillis"], reverse=True)
            self.send_json(200, {"items": reports, "total": len(reports)})
        elif request_url.path.startswith("/api/supervisor/reports/"):
            session = self.authenticated_session(supervisor=True)
            if session is None:
                return
            report_id = request_url.path.rsplit("/", 1)[-1]
            with REPORT_LOCK:
                report = REPORTS.get(report_id)
                result = self.public_report(report) if report else None
            if result is None:
                self.send_json(404, {"code": "REPORT_NOT_FOUND", "message": "Report not found."})
                return
            self.send_json(200, {"report": result})
        else:
            self.send_json(404, {"code": "NOT_FOUND", "message": "Not found."})

    def do_POST(self):
        request_url = urlsplit(self.path)
        if request_url.path == "/api/auth/login":
            self.handle_login()
        elif request_url.path == "/api/auth/refresh":
            self.handle_refresh()
        elif request_url.path == "/api/auth/logout":
            self.handle_logout()
        elif request_url.path == "/api/devices":
            self.handle_device()
        elif request_url.path == "/api/reports":
            self.handle_report_submit()
        elif request_url.path.startswith("/api/reports/") and request_url.path.endswith("/attachments"):
            self.handle_attachment(request_url.path)
        elif request_url.path.startswith("/api/supervisor/reports/") and request_url.path.endswith("/review"):
            self.handle_report_review(request_url.path)
        else:
            self.send_json(404, {"code": "NOT_FOUND", "message": "Not found."})

    def handle_login(self):
        request = self.read_json_request(max_bytes=4096)
        if request is None:
            return
        worker_id = request.get("workerId", "")
        password = request.get("password", "")
        if not isinstance(worker_id, str) or not isinstance(password, str):
            self.send_json(400, {"code": "BAD_REQUEST", "message": "Invalid credentials."})
            return

        account = ACCOUNTS.get(worker_id.strip().upper())
        if account is None or password != DEMO_PASSWORD:
            self.send_json(401, {"code": "INVALID_CREDENTIALS", "message": "Worker ID or password is incorrect."})
            return
        if not account["isActive"]:
            self.send_json(403, {"code": "ACCOUNT_INACTIVE", "message": "This account is inactive."})
            return

        public_account = {key: value for key, value in account.items() if key != "isActive"}
        self.issue_session(public_account)

    def issue_session(self, public_account):
        expires_at_time = datetime.now(timezone.utc) + timedelta(hours=1)
        expires_at = expires_at_time.strftime("%Y-%m-%dT%H:%M:%SZ")
        access_token = "demo-" + secrets.token_urlsafe(24)
        refresh_token = "demo-" + secrets.token_urlsafe(24)
        with TOKEN_LOCK:
            ACCESS_TOKENS[access_token] = {"account": public_account, "expiresAt": expires_at_time}
            REFRESH_TOKENS[refresh_token] = public_account
            _save_sessions_locked()
        self.send_json(200, {
            "accessToken": access_token,
            "expiresAt": expires_at,
            "refreshToken": refresh_token,
            "account": public_account,
        })

    def handle_refresh(self):
        request = self.read_json_request(max_bytes=4096)
        if request is None:
            return
        token = request.get("refreshToken", "")
        with TOKEN_LOCK:
            account = REFRESH_TOKENS.pop(token, None)
        if account is None:
            self.send_json(401, {"code": "UNAUTHORIZED", "message": "Session expired."})
            return
        self.issue_session(account)

    def handle_logout(self):
        request = self.read_json_request(max_bytes=4096)
        if request is None:
            return
        with TOKEN_LOCK:
            REFRESH_TOKENS.pop(request.get("refreshToken", ""), None)
            _save_sessions_locked()
        self.send_json(200, {"revoked": True})

    def handle_device(self):
        session = self.authenticated_session()
        if session is None:
            return
        request = self.read_json_request(max_bytes=4096)
        if request is None:
            return
        installation_id = request.get("installationId", "")
        if not isinstance(installation_id, str) or not 1 <= len(installation_id) <= 100:
            self.send_json(400, {"code": "BAD_REQUEST", "message": "Invalid installation ID."})
            return
        with REPORT_LOCK:
            device = DEVICES.get(installation_id)
            if device is None:
                device = {"deviceId": "DEV-" + secrets.token_hex(6),
                          "accountId": session["account"]["accountId"]}
                DEVICES[installation_id] = device
                _save_reports_locked()
            elif device["accountId"] != session["account"]["accountId"]:
                self.send_json(403, {"code": "FORBIDDEN", "message": "Device belongs to another account."})
                return
        self.send_json(200, {"deviceId": device["deviceId"], "registered": True})

    def handle_report_submit(self):
        session = self.authenticated_session()
        if session is None:
            return
        request = self.read_json_request(max_bytes=65536)
        if request is None:
            return
        device_id = request.get("deviceId", "")
        local_id = request.get("localReportId")
        report_type = request.get("typeCode")
        location = next((item for item in LOCATIONS if item["code"] == request.get("locationCode")), None)
        if (not self.owns_device(session, device_id) or not isinstance(local_id, int)
                or local_id <= 0 or report_type not in ("HAZARD", "INCIDENT", "NEAR_MISS")
                or location is None or not isinstance(request.get("occurredAtEpochMillis"), int)
                or not isinstance(request.get("submittedAtEpochMillis"), int)
                or not isinstance(request.get("description", ""), str)):
            self.send_json(400, {"code": "BAD_REPORT", "message": "Invalid report or device."})
            return
        images = request.get("expectedImageCount", 0)
        voices = request.get("expectedVoiceNoteCount", 0)
        if not isinstance(images, int) or not 0 <= images <= 10 or not isinstance(voices, int) or not 0 <= voices <= 10:
            self.send_json(400, {"code": "BAD_REPORT", "message": "Invalid evidence count."})
            return
        with REPORT_LOCK:
            existing = next((r for r in REPORTS.values()
                             if r.get("deviceId") == device_id and r.get("localReportId") == local_id), None)
            if existing is not None:
                report = existing
            else:
                report_id = "RPT-" + secrets.token_hex(12).upper()
                now = _now_millis()
                anonymous = report_type in ("HAZARD", "NEAR_MISS")
                report = {"reportId": report_id, "referenceNumber": report_id,
                          "deviceId": device_id, "localReportId": local_id,
                          "type": report_type, "locationCode": location["code"],
                          "locationName": location["name"], "description": request.get("description", ""),
                          "submittedAtEpochMillis": request["submittedAtEpochMillis"],
                          "receivedAtEpochMillis": now, "statusUpdatedAtEpochMillis": now,
                          "status": "PENDING_REVIEW", "anonymous": anonymous,
                          "imageCount": images, "voiceNoteCount": voices,
                          "expectedImageCount": images, "expectedVoiceNoteCount": voices,
                          "attachments": [], "evidenceComplete": images + voices == 0,
                          "reviewReason": None, "reviewedAtEpochMillis": None,
                          "reviewedByWorkerId": None}
                if not anonymous:
                    report["reporterWorkerId"] = session["account"]["workerId"]
                REPORTS[report_id] = report
                _save_reports_locked()
        self.send_json(200, {"reportId": report["reportId"],
                             "referenceNumber": report["referenceNumber"],
                             "receivedAtEpochMillis": report["receivedAtEpochMillis"],
                             "status": report["status"]})

    def handle_attachment(self, path):
        session = self.authenticated_session()
        if session is None:
            return
        report_id = path.split("/")[3]
        try:
            length = int(self.headers.get("Content-Length", "0"))
            content_type = self.headers.get("Content-Type", "")
            if not 0 < length <= 26 * 1024 * 1024 or not content_type.startswith("multipart/form-data;"):
                raise ValueError()
            envelope = ("Content-Type: " + content_type + "\r\nMIME-Version: 1.0\r\n\r\n").encode()
            message = BytesParser(policy=default).parsebytes(envelope + self.rfile.read(length))
            fields = {}
            file_data = None
            mime = None
            for part in message.iter_parts():
                name = part.get_param("name", header="content-disposition")
                if name == "file":
                    file_data = part.get_payload(decode=True)
                    mime = part.get_content_type()
                elif name:
                    fields[name] = part.get_content().strip()
            local_id = fields["localAttachmentId"]
            kind = fields["attachmentType"]
            source = fields["captureSource"]
            captured_at = int(fields["capturedAtEpochMillis"])
            if (not local_id or len(local_id) > 100 or kind not in ("PHOTO", "VOICE_NOTE")
                    or not file_data or len(file_data) > 25 * 1024 * 1024
                    or (kind == "PHOTO" and not mime.startswith("image/"))
                    or (kind == "VOICE_NOTE" and not mime.startswith("audio/"))):
                raise ValueError()
        except (ValueError, KeyError, TypeError):
            self.send_json(400, {"code": "BAD_ATTACHMENT", "message": "Invalid attachment."})
            return
        with REPORT_LOCK:
            report = REPORTS.get(report_id)
            if report is None:
                self.send_json(404, {"code": "REPORT_NOT_FOUND", "message": "Report not found."})
                return
            if not self.may_view_report(session, report):
                self.send_json(403, {"code": "FORBIDDEN", "message": "Report access denied."})
                return
            existing = next((a for a in ATTACHMENTS.values()
                             if a["reportId"] == report_id and a.get("localId") == local_id), None)
            if existing is not None:
                metadata = existing["metadata"]
            else:
                attachment_id = "ATT-" + secrets.token_hex(8)
                metadata = {"attachmentId": attachment_id, "attachmentType": kind,
                            "captureSource": source, "capturedAtEpochMillis": captured_at,
                            "mimeType": mime, "fileSizeBytes": len(file_data)}
                ATTACHMENTS[attachment_id] = {"reportId": report_id, "localId": local_id,
                                               "metadata": metadata, "data": file_data}
                report["attachments"].append(metadata)
                actual_images = sum(a["attachmentType"] == "PHOTO" for a in report["attachments"])
                actual_voices = sum(a["attachmentType"] == "VOICE_NOTE" for a in report["attachments"])
                report["evidenceComplete"] = (actual_images >= report["expectedImageCount"]
                                               and actual_voices >= report["expectedVoiceNoteCount"])
                _save_reports_locked()
        self.send_json(200, metadata)

    def handle_report_review(self, path):
        session = self.authenticated_session(supervisor=True)
        if session is None:
            return
        parts = path.strip("/").split("/")
        if len(parts) != 5 or parts[:3] != ["api", "supervisor", "reports"] or parts[4] != "review":
            self.send_json(404, {"code": "NOT_FOUND", "message": "Not found."})
            return
        report_id = parts[3]
        request = self.read_json_request()
        if request is None:
            return
        decision = request.get("decision")
        reason = request.get("reason", "")
        if not isinstance(decision, str) or decision.strip().upper() not in ("APPROVE", "DENY"):
            self.send_json(400, {"code": "BAD_REQUEST", "message": "Decision must be APPROVE or DENY."})
            return
        decision = decision.strip().upper()
        if not isinstance(reason, str):
            self.send_json(400, {"code": "BAD_REQUEST", "message": "Review reason must be text."})
            return
        reason = reason.strip()
        if decision == "DENY" and not reason:
            self.send_json(400, {"code": "REASON_REQUIRED", "message": "A reason is required to deny a report."})
            return
        if len(reason) > 1000:
            self.send_json(400, {"code": "REASON_TOO_LONG", "message": "Review reason must be 1000 characters or fewer."})
            return

        with REPORT_LOCK:
            report = REPORTS.get(report_id)
            if report is None:
                self.send_json(404, {"code": "REPORT_NOT_FOUND", "message": "Report not found."})
                return
            if report["status"] != "PENDING_REVIEW":
                self.send_json(409, {"code": "ALREADY_REVIEWED", "message": "This report has already been reviewed."})
                return
            if not report.get("evidenceComplete", True):
                self.send_json(409, {"code": "EVIDENCE_PENDING", "message": "Evidence is still uploading."})
                return
            report["status"] = "APPROVED" if decision == "APPROVE" else "DENIED"
            report["reviewReason"] = reason if decision == "DENY" else None
            report["reviewedAtEpochMillis"] = _now_millis()
            report["statusUpdatedAtEpochMillis"] = report["reviewedAtEpochMillis"]
            report["reviewedByWorkerId"] = session["account"]["workerId"]
            _save_reports_locked()
            result = self.public_report(report)
        self.send_json(200, {"report": result})


if __name__ == "__main__":
    load_persistent_state()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Mock reporting API: http://{HOST}:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
