"""Contract checks for the local debug API."""

import json
from threading import Thread
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from http.server import ThreadingHTTPServer

import mock_api


class MockApiTest(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), mock_api.Handler)
        self.thread = Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def test_login_token_reads_paged_locations(self):
        login = Request(
            self.base_url + "/api/auth/login",
            data=json.dumps({"workerId": "WORKER001", "password": "Demo123!"}).encode(),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urlopen(login) as response:
            token = json.load(response)["accessToken"]

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


if __name__ == "__main__":
    unittest.main()
