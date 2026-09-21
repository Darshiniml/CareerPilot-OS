"""Live Milestone 22.2 verification: secure n8n external email ingestion webhook.

Run against a started CareerPilot backend that has ingestion ENABLED, i.e. booted with a
configured shared secret (env N8N_INGESTION_SHARED_SECRET or
--careerpilot.ingestion.n8n.shared-secret=...). The SAME secret must be exported to this
script as N8N_INGESTION_SHARED_SECRET. The secret is never hardcoded here.

This script never injects jobs, applications, or database rows directly; every assertion is
made against real API responses. It verifies machine-to-machine authentication, recipient-
derived candidate identity (no body spoofing), evidence-based matching to a REAL application,
idempotent dedupe, cross-candidate isolation, non-injectability of server-owned fields,
malformed-payload rejection, and unauthorized rejection.

Set CAREERPILOT_BASE_URL (default http://localhost:8080/api/v1).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.getenv("CAREERPILOT_BASE_URL", "http://localhost:8080/api/v1").rstrip("/")
INGEST_PATH = "/communications/ingest/n8n"
TOKEN_HEADER = "X-Ingestion-Token"
SECRET = os.getenv("N8N_INGESTION_SHARED_SECRET")

if not SECRET:
    sys.exit("N8N_INGESTION_SHARED_SECRET must be set to the backend's configured ingestion secret")


def request(method, path, body=None, token=None, headers=None, expected=(200,)):
    hdrs = {"Content-Type": "application/json"}
    if token:
        hdrs["Authorization"] = f"Bearer {token}"
    if headers:
        hdrs.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=hdrs, method=method)
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


def ingest(payload, token=SECRET, expected=(201,)):
    headers = {TOKEN_HEADER: token} if token is not None else {}
    return request("POST", INGEST_PATH, payload, headers=headers, expected=expected)


def login(email, password):
    result = request("POST", "/auth/login", {"email": email, "password": password})
    assert result.get("accessToken"), "login did not return an access token"
    return result["accessToken"]


def register_and_login(label):
    password = "Milestone22!Pass"
    email = f"m222.{label}.{int(time.time() * 1000)}.{uuid.uuid4().hex[:6]}@example.test"
    request("POST", "/auth/register", {
        "email": email, "password": password, "firstName": "Milestone",
        "lastName": f"Candidate{label}",
    }, expected=(201,))
    return email, login(email, password)


def n8n_payload(recipient, sender="newsletter@unrelated-digest.com",
                subject="Weekly tech digest", body="This week's roundup.", **extra):
    payload = {
        "externalMessageId": f"msg-{uuid.uuid4()}",
        "threadId": f"thread-{uuid.uuid4()}",
        "sender": sender,
        "recipient": recipient,
        "subject": subject,
        "body": body,
        "receivedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    payload.update(extra)
    return payload


def prepare_application(token):
    jobs = request("GET", "/discovery/jobs", token=token)
    assert jobs and all(j.get("id") and j.get("sourceUrl") for j in jobs), "discovery returned no real jobs"
    seed = jobs[0]
    request("PUT", "/profile/preferences", {
        "workStyle": seed.get("workMode") or "FLEXIBLE",
        "employmentType": "FULL_TIME",
        "preferredRoles": [seed["title"]],
        "preferredLocations": [seed.get("location")] if seed.get("location") else [],
    }, token=token)
    queue = request("GET", "/opportunities?page=0&size=20", token=token)
    opps = queue["content"]
    assert opps, "opportunity queue empty for prepared candidate"
    opp = opps[0]
    created = request("POST", f"/opportunities/{opp['jobId']}/applications", token=token, expected=(201,))
    return opp, created


def main():
    email_a, a = register_and_login("a")
    email_b, b = register_and_login("b")

    # Gate 12 (unauthorized) — checked early so a misconfigured secret fails fast.
    ingest(n8n_payload(email_a), token=None, expected=(401,))
    ingest(n8n_payload(email_a), token="wrong-secret", expected=(401,))
    print("gate 12 PASS: missing/incorrect ingestion token rejected (401)")

    # Gate 1 + 2: authenticated, valid normalized n8n communication.
    ext = f"msg-valid-{uuid.uuid4()}"
    p = n8n_payload(email_a, externalMessageId=ext, threadId="thread-gate1")
    created = ingest(p)
    assert created["id"], "ingest returned no id"
    assert created["provider"] == "N8N", created["provider"]
    assert created["externalMessageId"] == ext
    assert created["threadId"] == "thread-gate1"
    assert created["sender"] == p["sender"] and created["recipient"] == email_a
    assert created["subject"] == p["subject"] and created["body"] == p["body"]
    a_candidate_id = created["candidateId"]
    comm_id = created["id"]
    print(f"gate 1+2 PASS: authenticated n8n ingestion created N8N communication {comm_id}")

    # Gate 3: persistence — retrievable via the candidate-scoped API using A's JWT.
    listing = request("GET", "/communications", token=a)
    assert any(c["id"] == comm_id for c in listing), "ingested communication not persisted"
    fetched = request("GET", f"/communications/{comm_id}", token=a)
    assert fetched["id"] == comm_id and fetched["provider"] == "N8N"
    print("gate 3 PASS: ingested communication persisted and retrievable by owner")

    # Gate 4: real application matching through the ingestion path.
    opp, app = prepare_application(a)
    app_id = app["applicationId"]
    host = urllib.parse.urlparse(opp["sourceUrl"]).netloc or "example.com"
    matched = ingest(n8n_payload(
        email_a,
        sender=f"recruiter@{host}",
        subject=f"Your application for {opp['title']} at {opp['company']}",
        body=f"Thanks for applying to the {opp['title']} role at {opp['company']}.",
    ))
    assert matched["processingStatus"] == "PROCESSED", matched["processingStatus"]
    assert matched["matchedApplicationId"] == app_id, \
        f"matched {matched['matchedApplicationId']} != real application {app_id}"
    assert matched["classification"] == "UNKNOWN", "foundation must not classify"
    print(f"gate 4 PASS: matched REAL application {app_id} (confidence {matched['matchConfidence']})")

    # Gate 5: duplicate ingestion is idempotent (same provider + externalMessageId).
    dup = ingest(p, expected=(201,))
    assert dup["id"] == comm_id, "duplicate ingest created a new record"
    listing2 = request("GET", "/communications", token=a)
    assert sum(c["externalMessageId"] == ext for c in listing2) == 1, "idempotency failed"
    print("gate 5 PASS: duplicate ingestion is idempotent")

    # Gate 6: cross-candidate isolation — B cannot read A's communication, and A's message
    # identity cannot be re-ingested for B (rejected, never silently re-attached).
    request("GET", f"/communications/{comm_id}", token=b, expected=(403,))
    cross = n8n_payload(email_b, externalMessageId=ext)  # same provider+externalMessageId as A's
    ingest(cross, expected=(409,))
    print("gate 6 PASS: cross-candidate read blocked (403) and duplicate re-attach rejected (409)")

    # Gate 7: candidate identity cannot be spoofed via the body.
    spoof = n8n_payload(email_a, candidateId=str(uuid.uuid4()), subject="Spoof attempt", body="x")
    spoof["candidateId"] = a_candidate_id  # even a 'valid-looking' id must be ignored
    spoof_for_b = n8n_payload(email_b, subject="Owned by B", body="y")
    spoof_for_b["candidateId"] = a_candidate_id  # body claims A, recipient is B
    res_b = ingest(spoof_for_b)
    assert res_b["candidateId"] != a_candidate_id, "body candidateId overrode recipient identity"
    res_a = ingest(spoof)
    assert res_a["candidateId"] == a_candidate_id, "recipient-derived identity was not authoritative"
    print("gate 7 PASS: candidate identity derived from recipient, body candidateId ignored")

    # Gate 8: classification cannot be injected.
    inj_cls = ingest(n8n_payload(email_a, subject="Inject classification", body="z",
                                 classification="OFFER", classificationConfidence=0.99))
    assert inj_cls["classification"] == "UNKNOWN", inj_cls["classification"]
    assert inj_cls.get("classificationConfidence") is None, "classificationConfidence injectable"
    print("gate 8 PASS: classification not injectable (stays UNKNOWN)")

    # Gate 9: matchedApplicationId cannot be injected.
    random_app = str(uuid.uuid4())
    inj_app = ingest(n8n_payload(email_a, subject="Inject app", body="z",
                                 matchedApplicationId=random_app, matchConfidence=1.0))
    assert inj_app["matchedApplicationId"] is None, "matchedApplicationId was injectable"
    print("gate 9 PASS: matchedApplicationId not injectable")

    # Gate 10: processingStatus cannot be injected.
    inj_status = ingest(n8n_payload(email_a, subject="Inject status", body="z",
                                    processingStatus="PROCESSED"))
    assert inj_status["processingStatus"] == "UNMATCHED", inj_status["processingStatus"]
    print("gate 10 PASS: processingStatus not injectable (server-derived UNMATCHED)")

    # Gate 11: malformed payloads rejected with 400.
    missing_id = n8n_payload(email_a)
    del missing_id["externalMessageId"]
    ingest(missing_id, expected=(400,))
    ingest(n8n_payload(email_a, receivedAt="not-a-timestamp"), expected=(400,))
    print("gate 11 PASS: malformed payload (missing id / invalid timestamp) rejected (400)")

    print("PASS: M22.2 live gates: M2M auth, normalized N8N ingestion, persistence, real application "
          "matching, idempotent dedupe, cross-candidate isolation, recipient-derived identity, "
          "classification/matchedApplicationId/processingStatus non-injection, malformed and "
          "unauthorized rejection")


if __name__ == "__main__":
    main()
