"""Live Milestone 22 (foundation) API verification: HR Communication ingestion & matching.

Run against a started CareerPilot backend only. This script never injects jobs, scores,
applications, or database records directly; every assertion is made against API data.
It verifies that inbound HR communications are ingested for the authenticated candidate,
matched to that candidate's REAL applications using evidence only, deduplicated by
provider+external message id, and isolated between candidates. It also verifies that
server-owned fields (candidateId, classification, processingStatus, matchedApplicationId)
cannot be injected by a client.

Set CAREERPILOT_BASE_URL (default http://localhost:8080/api/v1) and optionally provide two
already prepared candidate accounts with CAREERPILOT_E2E_USER_A/B and passwords.
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

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
    password = "Milestone22!Pass"
    email = f"m22.{label}.{int(time.time() * 1000)}@example.test"
    request("POST", "/auth/register", {
        "email": email, "password": password, "firstName": "Milestone",
        "lastName": f"Candidate{label}",
    }, expected=(201,))
    return login(email, password)


def communication(provider, external_id, sender, subject, body, recipient=None, thread_id=None):
    return {
        "provider": provider,
        "externalMessageId": external_id,
        "threadId": thread_id or f"thread-{uuid.uuid4()}",
        "sender": sender,
        "recipient": recipient,
        "subject": subject,
        "body": body,
        "receivedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }


def prepare_candidate_application(token):
    """Seed preferences from a real discovered job and create one real application."""
    jobs = request("GET", "/discovery/jobs", token=token)
    assert jobs and all(job.get("id") and job.get("sourceUrl") for job in jobs), "discovery returned no real jobs"
    seed_job = jobs[0]
    request("PUT", "/profile/preferences", {
        "workStyle": seed_job.get("workMode") or "FLEXIBLE",
        "employmentType": "FULL_TIME",
        "preferredRoles": [seed_job["title"]],
        "preferredLocations": [seed_job.get("location")] if seed_job.get("location") else [],
    }, token=token)
    queue = request("GET", "/opportunities?page=0&size=20", token=token)
    opportunities = queue["content"]
    assert opportunities, "personalized opportunity queue is empty for prepared candidate"
    opp = opportunities[0]
    created = request("POST", f"/opportunities/{opp['jobId']}/applications", token=token, expected=(201,))
    return opp, created


def main():
    email_a, password_a = os.getenv("CAREERPILOT_E2E_USER_A"), os.getenv("CAREERPILOT_E2E_PASSWORD_A")
    email_b, password_b = os.getenv("CAREERPILOT_E2E_USER_B"), os.getenv("CAREERPILOT_E2E_PASSWORD_B")

    # Gate 1 & 2: both candidates register/login.
    a = login(email_a, password_a) if email_a and password_a else register_and_login("a")
    b = login(email_b, password_b) if email_b and password_b else register_and_login("b")
    print("gate 1+2 PASS: candidate A and B authenticated")

    # Gate 3: A owns a real application created from a real opportunity.
    opp, created = prepare_candidate_application(a)
    app_id = created["applicationId"]
    applications_a = request("GET", "/applications", token=a)
    assert any(x["applicationId"] == app_id for x in applications_a), "created application not owned by A"
    a_candidate_id = created["candidateId"]
    print(f"gate 3 PASS: candidate A owns application {app_id}")

    # Gate 4: A ingests an HR communication that evidences the real application.
    ext_matched = f"msg-matched-{uuid.uuid4()}"
    matched_body = communication(
        "GMAIL", ext_matched,
        sender=f"recruiter@{urllib.parse.urlparse(opp['sourceUrl']).netloc or 'example.com'}",
        subject=f"Your application for {opp['title']} at {opp['company']}",
        body=f"Thanks for applying to the {opp['title']} role at {opp['company']}.",
        recipient=email_a,
    )
    comm_a = request("POST", "/communications", matched_body, token=a, expected=(201,))
    assert comm_a["id"], "ingest did not return a communication id"
    print(f"gate 4 PASS: A ingested communication {comm_a['id']}")

    # Gate 5: the communication is persisted and retrievable.
    listing = request("GET", "/communications", token=a)
    assert any(c["id"] == comm_a["id"] for c in listing), "ingested communication not persisted in list"
    fetched = request("GET", f"/communications/{comm_a['id']}", token=a)
    assert fetched["id"] == comm_a["id"], "ingested communication not retrievable by id"
    print("gate 5 PASS: communication persisted and retrievable")

    # Gate 6: evidence-based match to the REAL application; classification stays UNKNOWN in foundation.
    assert comm_a["matchedApplicationId"] == app_id, \
        f"communication matched to {comm_a['matchedApplicationId']} instead of real application {app_id}"
    assert comm_a["processingStatus"] == "PROCESSED", comm_a["processingStatus"]
    assert comm_a["classification"] == "UNKNOWN", "foundation must not classify communications"
    assert comm_a["matchConfidence"] is not None and float(comm_a["matchConfidence"]) >= 0.6, comm_a["matchConfidence"]
    print(f"gate 6 PASS: matched real application with confidence {comm_a['matchConfidence']}")

    # Gate 7: duplicate provider+external message id is idempotent for the same candidate.
    dup = request("POST", "/communications", matched_body, token=a, expected=(201,))
    assert dup["id"] == comm_a["id"], "duplicate ingest created a second record"
    listing_after = request("GET", "/communications", token=a)
    assert sum(c["externalMessageId"] == ext_matched for c in listing_after) == 1, "duplicate protection failed"
    print("gate 7 PASS: duplicate ingest is idempotent")

    # Gate 8: an unrelated communication stays UNMATCHED with no fabricated application link.
    unrelated = communication(
        "OUTLOOK", f"msg-unrelated-{uuid.uuid4()}",
        sender="newsletter@unrelated-digest.com",
        subject="Weekly tech digest",
        body="Here is this week's roundup of articles.",
        recipient=email_a,
    )
    comm_unmatched = request("POST", "/communications", unrelated, token=a, expected=(201,))
    assert comm_unmatched["processingStatus"] == "UNMATCHED", comm_unmatched["processingStatus"]
    assert comm_unmatched["matchedApplicationId"] is None, "unmatched communication fabricated an application link"
    print("gate 8 PASS: unrelated communication stays UNMATCHED")

    # Gate 9: candidate B cannot read candidate A's communication.
    request("GET", f"/communications/{comm_a['id']}", token=b, expected=(403,))
    print("gate 9 PASS: cross-candidate read blocked (403)")

    # Gate 10: a client-supplied candidateId is ignored; B's ingest is owned by B.
    b_baseline = request("POST", "/communications", communication(
        "GMAIL", f"msg-b-{uuid.uuid4()}", sender="hr@somecompany.com",
        subject="Hello", body="Body text.", recipient=email_b,
    ), token=b, expected=(201,))
    b_candidate_id = b_baseline["candidateId"]
    spoof = communication(
        "GMAIL", f"msg-spoof-{uuid.uuid4()}", sender="hr@somecompany.com",
        subject="Hello again", body="Body text.", recipient=email_b,
    )
    spoof["candidateId"] = a_candidate_id  # must be ignored by the server
    spoof_result = request("POST", "/communications", spoof, token=b, expected=(201,))
    assert spoof_result["candidateId"] == b_candidate_id, "client-supplied candidateId was honoured"
    assert spoof_result["candidateId"] != a_candidate_id, "candidate identity spoof succeeded"
    print("gate 10 PASS: candidate identity derived from principal, not request body")

    # Gate 11: server-owned fields cannot be injected.
    injected = communication(
        "GMAIL", f"msg-inject-{uuid.uuid4()}", sender="newsletter@another-digest.com",
        subject="Promo", body="Promotional body.", recipient=email_a,
    )
    random_app = str(uuid.uuid4())
    injected["classification"] = "OFFER"
    injected["processingStatus"] = "PROCESSED"
    injected["matchedApplicationId"] = random_app
    injected["matchConfidence"] = 1.0
    injected["candidateId"] = b_candidate_id
    inject_result = request("POST", "/communications", injected, token=a, expected=(201,))
    assert inject_result["classification"] == "UNKNOWN", "classification was injectable"
    assert inject_result["processingStatus"] == "UNMATCHED", "processingStatus was injectable"
    assert inject_result["matchedApplicationId"] is None, "matchedApplicationId was injectable"
    assert inject_result["candidateId"] == a_candidate_id, "candidateId was injectable"
    print("gate 11 PASS: server-owned fields are not client-injectable")

    # Gate 12: A can retrieve its own communication.
    own = request("GET", f"/communications/{comm_a['id']}", token=a, expected=(200,))
    assert own["id"] == comm_a["id"] and own["candidateId"] == a_candidate_id
    print("gate 12 PASS: candidate retrieves own communication")

    print("PASS: M22 foundation live API gates: ingestion, persistence, evidence-based matching, "
          "idempotent dedupe, unmatched safety, cross-candidate isolation, identity from principal, "
          "server-owned field protection, and owner retrieval")


if __name__ == "__main__":
    main()
