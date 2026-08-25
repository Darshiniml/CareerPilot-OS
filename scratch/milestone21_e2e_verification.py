"""Live Milestone 21 API verification.

Run against a started CareerPilot backend only.  This script never injects jobs,
scores, histories, or database records; every assertion is made against API data.
Set CAREERPILOT_BASE_URL (default http://localhost:8080/api/v1) and provide two
already prepared candidate accounts with CAREERPILOT_E2E_USER_A/B and passwords.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.getenv("CAREERPILOT_BASE_URL", "http://localhost:8080/api/v1").rstrip("/")

def request(method, path, body=None, token=None, expected=(200,)):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = response.read().decode()
            assert response.status in expected, (path, response.status, payload)
            return json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        payload = error.read().decode()
        if error.code in expected:
            return json.loads(payload) if payload else None
        raise AssertionError(f"{method} {path}: {error.code} {payload}") from error

def login(email, password):
    result = request("POST", "/auth/login", {"email": email, "password": password})
    assert result.get("accessToken"), "login did not return an access token"
    return result["accessToken"]

def register_and_login(label):
    password = "Milestone21!Pass"
    email = f"m21.{label}.{int(time.time() * 1000)}@example.test"
    request("POST", "/auth/register", {
        "email": email, "password": password, "firstName": "Milestone",
        "lastName": f"Candidate{label}",
    }, expected=(201,))
    return login(email, password)

def assert_opportunity(opp):
    for field in ("jobId", "title", "company", "sourceUrl", "connectorId", "matchScore", "priorityScore", "priorityLevel"):
        assert opp.get(field) not in (None, ""), f"opportunity missing real {field}: {opp}"
    assert 0 <= float(opp["matchScore"]) <= 100
    assert 0 <= float(opp["priorityScore"]) <= 100
    assert opp["priorityLevel"] in {"HIGH_PRIORITY", "MEDIUM_PRIORITY", "LOW_PRIORITY", "NOT_RECOMMENDED"}
    assert opp.get("historicalConfidence") in {"SUFFICIENT", "INSUFFICIENT_DATA"}

def main():
    email_a, password_a = os.getenv("CAREERPILOT_E2E_USER_A"), os.getenv("CAREERPILOT_E2E_PASSWORD_A")
    email_b, password_b = os.getenv("CAREERPILOT_E2E_USER_B"), os.getenv("CAREERPILOT_E2E_PASSWORD_B")
    a = login(email_a, password_a) if email_a and password_a else register_and_login("a")
    b = login(email_b, password_b) if email_b and password_b else register_and_login("b")
    profile_a, profile_b = request("GET", "/profile", token=a), request("GET", "/profile", token=b)
    assert profile_a != profile_b, "candidate context is not isolated"
    jobs = request("GET", "/discovery/jobs", token=a)
    assert jobs and all(job.get("id") and job.get("sourceUrl") for job in jobs), "discovery returned no real jobs"
    seed_job = jobs[0]
    request("PUT", "/profile/preferences", {
        "workStyle": seed_job.get("workMode") or "FLEXIBLE",
        "employmentType": seed_job.get("employmentType") or "FULL_TIME",
        "preferredRoles": [seed_job["title"]],
        "preferredLocations": [seed_job.get("location")] if seed_job.get("location") else [],
    }, token=a)
    queue = request("GET", "/opportunities?page=0&size=20", token=a)
    assert queue["number"] == 0 and queue["size"] == 20
    opportunities = queue["content"]
    assert opportunities, "personalized opportunity queue is empty for prepared candidate"
    for opp in opportunities: assert_opportunity(opp)
    filtered = request("GET", "/opportunities?page=0&size=1&minMatchScore=0", token=a)
    assert filtered["size"] == 1 and len(filtered["content"]) <= 1
    opp = opportunities[0]
    signal = request("GET", "/analytics/historical-success?" + urllib.parse.urlencode({"title": opp["title"], "source": opp["source"]}), token=a)
    assert signal.get("available") in (True, False)
    created = request("POST", f"/opportunities/{opp['jobId']}/applications", token=a, expected=(201,))
    assert created["candidateId"] == request("GET", "/applications", token=a)[-1]["candidateId"]
    duplicate = request("POST", f"/opportunities/{opp['jobId']}/applications", token=a, expected=(201, 400, 409))
    applications = request("GET", "/applications", token=a)
    assert sum(x["jobId"] == opp["jobId"] for x in applications) == 1, "duplicate protection failed"
    app_id = created["applicationId"]
    package = request("GET", f"/applications/{app_id}/package", token=a)
    decision = request("GET", f"/applications/{app_id}/decision", token=a)
    preflight = request("GET", f"/applications/{app_id}/preflight", token=a)
    timeline = request("GET", f"/applications/{app_id}/timeline", token=a)
    assert package and decision and isinstance(preflight.get("checks"), list) and timeline
    assert created["workflowState"] != "SUBMITTED_VERIFIED", "automatic submission bypassed candidate approval"
    other_queue = request("GET", "/opportunities?page=0&size=20", token=b)
    other_applications = request("GET", "/applications", token=b)
    assert all(x["candidateId"] != created["candidateId"] for x in other_applications), "application multi-user isolation failed"
    assert all(x.get("jobId") != opp["jobId"] or x.get("applicationStatus") != created["workflowState"] for x in other_queue["content"]), "queue leaked candidate application state"
    print("PASS: live API gates: login, context, discovery, queue, filters, matching, historical signal, priority, duplicate protection, package, decision, preflight, timeline, and isolation")

if __name__ == "__main__":
    main()
