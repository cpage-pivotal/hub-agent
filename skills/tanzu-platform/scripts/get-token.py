#!/usr/bin/env python3
"""
Retrieve a Tanzu Hub access token via the OAuth2 PKCE flow — no browser needed.

Uses only Python standard library (no third-party dependencies).

Usage:
    python3 get-token.py [url] [username] [password]

Environment variables (fallback when args are omitted):
    TANZU_HUB_URL, TANZU_HUB_USER, TANZU_HUB_PASSWORD
"""

import base64
import hashlib
import http.cookiejar
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
HUB_URL = (
    sys.argv[1]
    if len(sys.argv) > 1
    else os.environ.get("TANZU_HUB_URL", "https://tanzu-hub.kuhn-labs.com")
).rstrip("/")
USERNAME = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("TANZU_HUB_USER", "")
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else os.environ.get("TANZU_HUB_PASSWORD", "")

if not USERNAME or not PASSWORD:
    sys.exit(
        "Error: USERNAME and PASSWORD are required.\n"
        "Set TANZU_HUB_USER / TANZU_HUB_PASSWORD or pass them as arguments."
    )


# ---------------------------------------------------------------------------
# HTTP helpers — stdlib equivalents of requests.Session
# ---------------------------------------------------------------------------
class _Redirect308Handler(urllib.request.HTTPRedirectHandler):
    """Handle HTTP 308 (Permanent Redirect) which urllib doesn't follow by default."""

    def http_error_308(self, req, fp, code, msg, headers):
        return self.http_error_302(req, fp, code, msg, headers)


_UA = "Mozilla/5.0 (compatible; TanzuHubCLI/1.0)"

_cookie_jar = http.cookiejar.CookieJar()
_opener = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(_cookie_jar),
    _Redirect308Handler,
)


def _get(url: str) -> urllib.request.Request:
    """Perform a GET and return the response (follows redirects, keeps cookies)."""
    req = urllib.request.Request(url)
    req.add_header("User-Agent", _UA)
    return _opener.open(req)


def _post_form(url: str, data: dict) -> urllib.request.Request:
    """POST url-encoded form data (follows redirects, keeps cookies)."""
    encoded = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=encoded)
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("User-Agent", _UA)
    return _opener.open(req)


# ---------------------------------------------------------------------------
# PKCE helpers
# ---------------------------------------------------------------------------
def pkce_pair() -> tuple:
    """Return (code_verifier, code_challenge)."""
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    digest = hashlib.sha256(verifier.encode()).digest()
    challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode()
    return verifier, challenge


# ---------------------------------------------------------------------------
# Step 1 — fetch login page and extract CSRF token
# ---------------------------------------------------------------------------
try:
    resp = _get(f"{HUB_URL}/auth/login")
except urllib.error.URLError as e:
    sys.exit(f"Failed to reach {HUB_URL}/auth/login: {e}")

page = resp.read().decode()

csrf = re.search(r'name="X-Uaa-Csrf"\s+value="([^"]+)"', page)
if not csrf:
    sys.exit("Could not find CSRF token in login page.")
csrf_token = csrf.group(1)

# ---------------------------------------------------------------------------
# Step 2 — POST credentials to /auth/login.do
# ---------------------------------------------------------------------------
try:
    resp = _post_form(
        f"{HUB_URL}/auth/login.do",
        {
            "X-Uaa-Csrf": csrf_token,
            "username": USERNAME,
            "password": PASSWORD,
        },
    )
except urllib.error.HTTPError as e:
    location = e.headers.get("Location") if hasattr(e, "headers") else None
    if e.code in (301, 302, 303, 307, 308) and location:
        if location.startswith("/"):
            location = f"{HUB_URL}{location}"
        resp = _get(location)
    else:
        sys.exit(f"Login POST failed with HTTP {e.code}: {e.reason}")

if "/auth/login" in resp.url:
    sys.exit("Login failed — check your credentials.")

# ---------------------------------------------------------------------------
# Step 3 — kick off PKCE OAuth flow
# ---------------------------------------------------------------------------
code_verifier, code_challenge = pkce_pair()
redirect_uri = f"{HUB_URL}/hub/oauth2/callback"

discovery_url = (
    f"{HUB_URL}/csp/gateway/discovery"
    f"?response_type=code"
    f"&client_id=tp_app"
    f"&redirect_uri={urllib.parse.quote(redirect_uri, safe='')}"
    f"&scope=groups+openid+offline_access+username"
    f"&code_challenge={code_challenge}"
    f"&code_challenge_method=S256"
)

try:
    resp = _get(discovery_url)
except urllib.error.HTTPError as e:
    # A redirect to the callback with ?code= may come as a "error" if the
    # callback URL isn't actually reachable.  Pull the code from the Location.
    if e.code in (301, 302, 303, 307, 308) and e.headers.get("Location"):
        final_url = e.headers["Location"]
    else:
        sys.exit(f"OAuth discovery failed with HTTP {e.code}: {e.reason}")
else:
    final_url = resp.url

parsed = urllib.parse.urlparse(final_url)
params = urllib.parse.parse_qs(parsed.query)
if "code" not in params:
    sys.exit(f"No authorization code returned. Final URL: {final_url}")

auth_code = params["code"][0]

# ---------------------------------------------------------------------------
# Step 4 — exchange code for token
# ---------------------------------------------------------------------------
try:
    resp = _post_form(
        f"{HUB_URL}/csp/gateway/am/api/auth/token",
        {
            "grant_type": "authorization_code",
            "client_id": "tp_app",
            "redirect_uri": redirect_uri,
            "code": auth_code,
            "code_verifier": code_verifier,
        },
    )
except urllib.error.HTTPError as e:
    sys.exit(f"Token exchange failed with HTTP {e.code}: {e.reason}")

token_data = json.loads(resp.read().decode())
access_token = token_data.get("access_token") or token_data.get("accessToken")

if not access_token:
    sys.exit(f"Token response did not contain an access_token:\n{json.dumps(token_data)}")

print(access_token)
