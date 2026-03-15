#!/usr/bin/env python3
"""
Retrieve a Tanzu Hub access token via the OAuth2 PKCE flow — no browser needed.

Usage:
    python3 get-token.py [url] [username] [password]

Environment variables (fallback when args are omitted):
    TANZU_HUB_URL, TANZU_HUB_USER, TANZU_HUB_PASSWORD
"""

import base64
import hashlib
import os
import re
import secrets
import sys
import urllib.parse

import requests

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
HUB_URL  = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TANZU_HUB_URL", "https://tanzu-hub.kuhn-labs.com")).rstrip("/")
USERNAME = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("TANZU_HUB_USER", "")
PASSWORD = sys.argv[3] if len(sys.argv) > 3 else os.environ.get("TANZU_HUB_PASSWORD", "")

if not USERNAME or not PASSWORD:
    sys.exit("Error: USERNAME and PASSWORD are required.\n"
             "Set TANZU_HUB_USER / TANZU_HUB_PASSWORD or pass them as arguments.")

# ---------------------------------------------------------------------------
# PKCE helpers
# ---------------------------------------------------------------------------
def pkce_pair() -> tuple[str, str]:
    """Return (code_verifier, code_challenge)."""
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    digest    = hashlib.sha256(verifier.encode()).digest()
    challenge = base64.urlsafe_b64encode(digest).rstrip(b"=").decode()
    return verifier, challenge

# ---------------------------------------------------------------------------
# Step 1 – fetch login page and extract CSRF token
# ---------------------------------------------------------------------------
session = requests.Session()
session.verify = True  # set to False if the server uses a self-signed cert

r = session.get(f"{HUB_URL}/auth/login")
r.raise_for_status()

csrf = re.search(r'name="X-Uaa-Csrf"\s+value="([^"]+)"', r.text)
if not csrf:
    sys.exit("Could not find CSRF token in login page.")
csrf_token = csrf.group(1)

# ---------------------------------------------------------------------------
# Step 2 – POST credentials to /auth/login.do
# ---------------------------------------------------------------------------
r = session.post(
    f"{HUB_URL}/auth/login.do",
    data={
        "X-Uaa-Csrf": csrf_token,
        "username":   USERNAME,
        "password":   PASSWORD,
    },
    allow_redirects=True,
)

# After successful login the server redirects to /auth/ and eventually /hub/
if "/auth/login" in r.url:
    sys.exit("Login failed — check your credentials.")

# ---------------------------------------------------------------------------
# Step 3 – kick off PKCE OAuth flow
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

r = session.get(discovery_url, allow_redirects=True)

# The final redirect lands on /hub/oauth2/callback?code=...
parsed = urllib.parse.urlparse(r.url)
params = urllib.parse.parse_qs(parsed.query)
if "code" not in params:
    sys.exit(f"No authorization code returned. Final URL: {r.url}")

auth_code = params["code"][0]

# ---------------------------------------------------------------------------
# Step 4 – exchange code for token
# ---------------------------------------------------------------------------
r = session.post(
    f"{HUB_URL}/csp/gateway/am/api/auth/token",
    data={
        "grant_type":   "authorization_code",
        "client_id":    "tp_app",
        "redirect_uri": redirect_uri,
        "code":         auth_code,
        "code_verifier": code_verifier,
    },
)
r.raise_for_status()

token_data = r.json()
access_token = token_data.get("access_token") or token_data.get("accessToken")

if not access_token:
    sys.exit(f"Token response did not contain an access_token:\n{r.text}")

print(access_token)
