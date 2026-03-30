#!/usr/bin/env python3
"""Obtain a Tanzu Hub refresh token for use with the Agent Credential Broker.

Performs a headless PKCE authorization code flow through the CSP gateway,
which supports offline_access and issues long-lived refresh tokens.

Usage:
    python3 get-refresh-token.py                                    # uses default hub URL, prompts for credentials
    python3 get-refresh-token.py https://tanzu-hub.example.com      # custom hub URL
    python3 get-refresh-token.py -u admin -p secret                 # non-interactive

The printed refresh token can be pasted into the Agent Credential Broker UI
under the "Provide Refresh Token" button for the tanzu-hub target system.
"""

import argparse
import base64
import getpass
import hashlib
import http.cookiejar
import json
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request


def build_opener():
    class Handle308(urllib.request.HTTPRedirectHandler):
        def http_error_308(self, req, fp, code, msg, headers):
            return self.http_error_302(req, fp, code, msg, headers)

    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), Handle308())


def get(opener, url):
    req = urllib.request.Request(url)
    req.add_header("User-Agent", "TanzuHub-RefreshToken/1.0")
    return opener.open(req)


def post(opener, url, data):
    encoded = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(url, data=encoded)
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    req.add_header("User-Agent", "TanzuHub-RefreshToken/1.0")
    return opener.open(req)


def login(opener, hub_url, username, password):
    resp = get(opener, f"{hub_url}/auth/login")
    page = resp.read().decode()
    match = re.search(r'name="X-Uaa-Csrf"\s+value="([^"]+)"', page)
    if not match:
        print("Error: Could not find CSRF token on login page.", file=sys.stderr)
        sys.exit(1)
    csrf = match.group(1)

    try:
        post(opener, f"{hub_url}/auth/login.do", {
            "X-Uaa-Csrf": csrf,
            "username": username,
            "password": password,
        })
    except urllib.error.HTTPError as e:
        loc = e.headers.get("Location", "")
        if e.code in (301, 302, 303, 307, 308) and loc:
            if loc.startswith("/"):
                loc = f"{hub_url}{loc}"
            get(opener, loc)
        elif e.code == 401:
            print("Error: Invalid username or password.", file=sys.stderr)
            sys.exit(1)
        else:
            raise


def obtain_refresh_token(opener, hub_url):
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode()).digest()
    ).rstrip(b"=").decode()
    redirect_uri = f"{hub_url}/hub/oauth2/callback"

    auth_url = (
        f"{hub_url}/csp/gateway/discovery"
        f"?response_type=code"
        f"&client_id=tp_app"
        f"&redirect_uri={urllib.parse.quote(redirect_uri, safe='')}"
        f"&scope=groups+openid+offline_access+username"
        f"&code_challenge={challenge}"
        f"&code_challenge_method=S256"
    )

    try:
        resp = get(opener, auth_url)
        final_url = resp.url
    except urllib.error.HTTPError as e:
        final_url = e.headers.get("Location", "")

    parsed = urllib.parse.urlparse(final_url)
    params = urllib.parse.parse_qs(parsed.query)
    if "code" not in params:
        print(f"Error: Authorization did not return a code. URL: {final_url}", file=sys.stderr)
        sys.exit(1)

    code = params["code"][0]

    resp = post(opener, f"{hub_url}/csp/gateway/am/api/auth/token", {
        "grant_type": "authorization_code",
        "client_id": "tp_app",
        "redirect_uri": redirect_uri,
        "code": code,
        "code_verifier": verifier,
    })
    token_data = json.loads(resp.read().decode())

    refresh_token = token_data.get("refresh_token")
    if not refresh_token:
        print("Error: No refresh token in response.", file=sys.stderr)
        print(f"Response keys: {list(token_data.keys())}", file=sys.stderr)
        sys.exit(1)

    return refresh_token, token_data.get("expires_in")


def main():
    parser = argparse.ArgumentParser(
        description="Obtain a Tanzu Hub refresh token for the Agent Credential Broker."
    )
    parser.add_argument("hub_url", nargs="?", default="https://tanzu-hub.kuhn-labs.com",
                        help="Tanzu Platform Hub URL (default: https://tanzu-hub.kuhn-labs.com)")
    parser.add_argument("-u", "--username", help="Tanzu Hub username")
    parser.add_argument("-p", "--password", help="Tanzu Hub password")
    args = parser.parse_args()

    hub_url = args.hub_url.rstrip("/")
    username = args.username or input("Username: ")
    password = args.password or getpass.getpass("Password: ")

    opener = build_opener()

    print(f"Logging in to {hub_url}...", file=sys.stderr)
    login(opener, hub_url, username, password)

    print("Obtaining refresh token...", file=sys.stderr)
    refresh_token, expires_in = obtain_refresh_token(opener, hub_url)

    print(f"\nAccess token TTL: {expires_in}s (refresh token renews it indefinitely)", file=sys.stderr)
    print(f"\nRefresh token:\n", file=sys.stderr)
    print(refresh_token)


if __name__ == "__main__":
    main()
