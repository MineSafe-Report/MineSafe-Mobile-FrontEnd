"""Contract checks for the local debug API, including supervisor report review."""

import json
import os
import tempfile
from threading import Thread
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from http.server import ThreadingHTTPServer

import mock_api


class MockApiTest(unittest.TestCase):
    def setUp(self):
        mock_api.reset_reports()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), mock_api.Handler)
        self.thread = Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def request_json(self, method, path, payload=None, token=None):
        data = None if payload is None else json.dumps(payload).encode()
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urlopen(request) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.load(error)

    def login(self, worker_id):
        status, body = self.request_json(
            "POST", "/api/auth/login",
            {"workerId": worker_id, "password": "Demo123!"})
        self.assertEqual(200, status)
        return body["accessToken"]

    def upload_attachment(self, report_id, token, local_id, kind, mime, content):
        boundary = "test-boundary"
        fields = {"localAttachmentId": local_id, "attachmentType": kind,
                  "captureSource": "GALLERY" if kind == "PHOTO" else "MICROPHONE",
                  "capturedAtEpochMillis": "1792360000000"}
        body = b""
        for name, value in fields.items():
            body += (f"--{boundary}\r\nContent-Disposition: form-data; "
                     f'name="{name}"\r\n\r\n{value}\r\n').encode()
        body += (f"--{boundary}\r\nContent-Disposition: form-data; "
                 'name="file"; filename="evidence"\r\n'
                 f"Content-Type: {mime}\r\n\r\n").encode()
        body += content + f"\r\n--{boundary}--\r\n".encode()
        request = Request(self.base_url + f"/api/reports/{report_id}/attachments",
                          data=body, method="POST",
                          headers={"Authorization": "Bearer " + token,
                                   "Content-Type": "multipart/form-data; boundary=" + boundary})
        with urlopen(request) as response:
            return json.load(response)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        mock_api.STATE_PATH = None
        mock_api.SESSIONS_PATH = None

    def test_debug_reports_survive_restart_without_reusing_ids(self):
        with tempfile.TemporaryDirectory() as storage:
            mock_api.load_persistent_state(storage)
            worker = self.login("WORKER001")
            supervisor = self.login("SUP001")
            status, device = self.request_json("POST", "/api/devices",
                                               {"installationId": "phone-one"}, worker)
            self.assertEqual(200, status)
            body = {"deviceId": device["deviceId"], "localReportId": 12,
                    "typeCode": "HAZARD", "locationCode": "LOC-1",
                    "occurredAtEpochMillis": 1792350000000,
                    "submittedAtEpochMillis": 1792360000000,
                    "description": "Test report"}
            status, submitted = self.request_json("POST", "/api/reports", body, worker)
            self.assertEqual(200, status)
            report_id = submitted["reportId"]
            self.assertNotIn(report_id, {"RPT-1005", "RPT-1006"})
            status, _ = self.request_json(
                "POST", f"/api/supervisor/reports/{report_id}/review",
                {"decision": "DENY", "reason": "Test reason"}, supervisor)
            self.assertEqual(200, status)

            # Simulate a new Python process loading its saved state.
            mock_api.reset_reports()
            mock_api.load_persistent_state(storage)
            status, reports = self.request_json(
                "GET", f"/api/reports?view=my&deviceId={device['deviceId']}",
                token=worker)
            self.assertEqual(200, status)
            self.assertEqual("DENIED", reports["items"][0]["status"])
            self.assertEqual(report_id, reports["items"][0]["reportId"])

    def test_login_token_reads_paged_locations(self):
        token = self.login("WORKER001")

        def page(number):
            request = Request(
                self.base_url + f"/api/locations?page={number}&pageSize=2",
                headers={"Authorization": "Bearer " + token},
            )
            with urlopen(request) as response:
                return json.load(response)

        first = page(1)
        second = page(2)
        self.assertEqual(3, first["total"])
        self.assertEqual(2, len(first["items"]))
        self.assertEqual(1, len(second["items"]))
        self.assertEqual("LOC-1", first["items"][0]["code"])

        with self.assertRaises(HTTPError) as error:
            urlopen(self.base_url + "/api/locations")
        self.assertEqual(401, error.exception.code)

        status, body = self.request_json("GET", "/api/supervisor/reports")
        self.assertEqual(401, status)
        self.assertEqual("UNAUTHORIZED", body["code"])

    def test_supervisor_lists_and_fetches_review_reports(self):
        token = self.login("SUP001")
        status, queue = self.request_json(
            "GET", "/api/supervisor/reports", token=token)
        self.assertEqual(200, status)
        self.assertEqual(3, queue["total"])
        report = queue["items"][0]
        self.assertEqual("PENDING_REVIEW", report["status"])
        self.assertIn("imageCount", report)
        self.assertIn("anonymous", report)

        status, detail = self.request_json(
            "GET", "/api/supervisor/reports/RPT-1004", token=token)
        self.assertEqual(200, status)
        self.assertEqual("RPT-1004", detail["report"]["reportId"])
        self.assertEqual(2, detail["report"]["imageCount"])

    def test_worker_cannot_use_supervisor_review_routes(self):
        token = self.login("WORKER001")
        status, body = self.request_json(
            "GET", "/api/supervisor/reports", token=token)
        self.assertEqual(403, status)
        self.assertEqual("FORBIDDEN", body["code"])

        status, body = self.request_json(
            "POST", "/api/supervisor/reports/RPT-1004/review",
            {"decision": "APPROVE"}, token)
        self.assertEqual(403, status)
        self.assertEqual("PENDING_REVIEW", mock_api.REPORTS["RPT-1004"]["status"])

    def test_safety_officer_sees_only_incidents_and_cannot_review(self):
        token = self.login("SO001")
        status, queue = self.request_json(
            "GET", "/api/safety-officer/incidents", token=token)
        self.assertEqual(200, status)
        self.assertTrue(queue["items"])
        self.assertTrue(all(item["type"] == "INCIDENT" for item in queue["items"]))
        incident_id = queue["items"][0]["reportId"]
        status, detail = self.request_json(
            "GET", f"/api/safety-officer/incidents/{incident_id}", token=token)
        self.assertEqual(200, status)
        self.assertEqual("INCIDENT", detail["report"]["type"])
        original = os.environ.get("SO_DASHBOARD_REPORT_URL_TEMPLATE")
        try:
            os.environ["SO_DASHBOARD_REPORT_URL_TEMPLATE"] = (
                "https://dashboard.example/incidents/{reportId}")
            status, linked = self.request_json(
                "GET", f"/api/safety-officer/incidents/{incident_id}", token=token)
            self.assertEqual(200, status)
            self.assertEqual(f"https://dashboard.example/incidents/{incident_id}",
                             linked["report"]["dashboardUrl"])
        finally:
            if original is None:
                os.environ.pop("SO_DASHBOARD_REPORT_URL_TEMPLATE", None)
            else:
                os.environ["SO_DASHBOARD_REPORT_URL_TEMPLATE"] = original

        supervisor = self.login("SUP001")
        status, _ = self.request_json(
            "POST", f"/api/supervisor/reports/{incident_id}/review",
            {"decision": "APPROVE"}, supervisor)
        self.assertEqual(200, status)
        status, updated_queue = self.request_json(
            "GET", "/api/safety-officer/incidents", token=token)
        self.assertEqual(200, status)
        self.assertEqual("APPROVED", next(item["status"] for item in updated_queue["items"]
                                          if item["reportId"] == incident_id))
        self.assertNotIn("RPT-1000", [item["reportId"] for item in updated_queue["items"]])

        status, _ = self.request_json(
            "GET", "/api/supervisor/reports", token=token)
        self.assertEqual(403, status)
        status, _ = self.request_json(
            "POST", f"/api/supervisor/reports/{incident_id}/review",
            {"decision": "APPROVE"}, token)
        self.assertEqual(403, status)
        status, _ = self.request_json(
            "GET", "/api/safety-officer/incidents/RPT-1004", token=token)
        self.assertEqual(404, status)
        status, _ = self.request_json("POST", "/api/reports", {}, token)
        self.assertEqual(403, status)
        status, _ = self.request_json("POST", "/api/devices", {}, token)
        self.assertEqual(403, status)
        worker = self.login("WORKER001")
        status, _ = self.request_json(
            "GET", "/api/safety-officer/incidents", token=worker)
        self.assertEqual(403, status)

    def test_supervisor_can_approve_and_report_cannot_be_reviewed_twice(self):
        token = self.login("SUP001")
        status, body = self.request_json(
            "POST", "/api/supervisor/reports/RPT-1004/review",
            {"decision": "APPROVE"}, token)
        self.assertEqual(200, status)
        self.assertEqual("APPROVED", body["report"]["status"])
        self.assertEqual("SUP001", body["report"]["reviewedByWorkerId"])
        self.assertIsNone(body["report"]["reviewReason"])

        status, body = self.request_json(
            "POST", "/api/supervisor/reports/RPT-1004/review",
            {"decision": "DENY", "reason": "second decision"}, token)
        self.assertEqual(409, status)
        self.assertEqual("ALREADY_REVIEWED", body["code"])

    def test_denial_requires_reason_and_saves_it(self):
        token = self.login("SUP001")
        status, body = self.request_json(
            "POST", "/api/supervisor/reports/RPT-1003/review",
            {"decision": "DENY", "reason": "   "}, token)
        self.assertEqual(400, status)
        self.assertEqual("REASON_REQUIRED", body["code"])

        reason = "Please add the time the incident occurred."
        status, body = self.request_json(
            "POST", "/api/supervisor/reports/RPT-1003/review",
            {"decision": "DENY", "reason": reason}, token)
        self.assertEqual(200, status)
        self.assertEqual("DENIED", body["report"]["status"])
        self.assertEqual(reason, body["report"]["reviewReason"])

        status, queue = self.request_json(
            "GET", "/api/supervisor/reports?status=DENIED", token=token)
        self.assertEqual(200, status)
        denied = next(item for item in queue["items"] if item["reportId"] == "RPT-1003")
        self.assertEqual(reason, denied["reviewReason"])

    def test_report_evidence_review_and_device_status_round_trip(self):
        worker = self.login("WORKER001")
        supervisor = self.login("SUP001")
        status, device = self.request_json("POST", "/api/devices",
                                           {"installationId": "phone-one", "platform": "ANDROID"}, worker)
        self.assertEqual(200, status)
        device_id = device["deviceId"]
        status, repeated = self.request_json("POST", "/api/devices",
                                              {"installationId": "phone-one", "platform": "ANDROID"}, worker)
        self.assertEqual(device_id, repeated["deviceId"])
        report_body = {"deviceId": device_id, "localReportId": 17,
                       "typeCode": "HAZARD", "locationCode": "LOC-1",
                       "occurredAtEpochMillis": 1792350000000,
                       "submittedAtEpochMillis": 1792360000000,
                       "expectedImageCount": 1, "expectedVoiceNoteCount": 1,
                       "description": "Loose rock"}
        status, submitted = self.request_json("POST", "/api/reports", report_body, worker)
        self.assertEqual(200, status)
        report_id = submitted["reportId"]
        status, repeated = self.request_json("POST", "/api/reports", report_body, worker)
        self.assertEqual(report_id, repeated["reportId"])

        status, queue = self.request_json("GET", "/api/supervisor/reports", token=supervisor)
        self.assertEqual(200, status)
        self.assertIn(report_id, [item["reportId"] for item in queue["items"]])
        status, detail = self.request_json("GET", f"/api/supervisor/reports/{report_id}", token=supervisor)
        self.assertFalse(detail["report"]["evidenceComplete"])
        self.assertEqual(report_body["occurredAtEpochMillis"],
                         detail["report"]["occurredAtEpochMillis"])
        self.assertNotIn("reporterWorkerId", detail["report"])
        status, blocked = self.request_json("POST", f"/api/supervisor/reports/{report_id}/review",
                                             {"decision": "APPROVE"}, supervisor)
        self.assertEqual(409, status)
        self.assertEqual("EVIDENCE_PENDING", blocked["code"])

        png, wav = mock_api._sample_media()
        photo = self.upload_attachment(report_id, worker, "image-1", "PHOTO", "image/png", png)
        repeated = self.upload_attachment(report_id, worker, "image-1", "PHOTO", "image/png", png)
        self.assertEqual(photo["attachmentId"], repeated["attachmentId"])
        voice = self.upload_attachment(report_id, worker, "voice-1", "VOICE_NOTE", "audio/wav", wav)
        for attachment, expected in ((photo, png), (voice, wav)):
            request = Request(self.base_url + "/api/attachments/" + attachment["attachmentId"],
                              headers={"Authorization": "Bearer " + supervisor})
            with urlopen(request) as response:
                self.assertEqual(expected, response.read())

        status, detail = self.request_json("GET", f"/api/supervisor/reports/{report_id}", token=supervisor)
        self.assertTrue(detail["report"]["evidenceComplete"])
        self.assertEqual(2, len(detail["report"]["attachments"]))
        status, approved = self.request_json("POST", f"/api/supervisor/reports/{report_id}/review",
                                              {"decision": "APPROVE"}, supervisor)
        self.assertEqual(200, status)
        self.assertEqual("APPROVED", approved["report"]["status"])
        status, mine = self.request_json("GET", f"/api/reports?view=my&deviceId={device_id}&page=1&pageSize=1",
                                         token=worker)
        self.assertEqual(200, status)
        self.assertEqual(1, mine["total"])
        self.assertEqual("APPROVED", mine["items"][0]["status"])

    def test_refresh_rotation_and_logout(self):
        status, login = self.request_json("POST", "/api/auth/login",
                                          {"workerId": "WORKER001", "password": "Demo123!"})
        self.assertEqual(200, status)
        status, refreshed = self.request_json("POST", "/api/auth/refresh",
                                             {"refreshToken": login["refreshToken"]})
        self.assertEqual(200, status)
        self.assertNotEqual(login["refreshToken"], refreshed["refreshToken"])
        status, _ = self.request_json("POST", "/api/auth/refresh",
                                      {"refreshToken": login["refreshToken"]})
        self.assertEqual(401, status)
        status, revoked = self.request_json("POST", "/api/auth/logout",
                                            {"refreshToken": refreshed["refreshToken"]})
        self.assertEqual(200, status)
        self.assertTrue(revoked["revoked"])
        status, _ = self.request_json("POST", "/api/auth/refresh",
                                      {"refreshToken": refreshed["refreshToken"]})
        self.assertEqual(401, status)


if __name__ == "__main__":
    unittest.main()
