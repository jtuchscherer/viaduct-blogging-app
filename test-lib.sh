#!/bin/bash

# Shared helpers for the test entry-point scripts (query-tests.sh, e2e.sh).
# Not executable on its own — source it:
#   source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-lib.sh"

# Returns 0 when a TCP port is already bound by a process visible to this user.
port_in_use() {
    lsof -ti:"$1" > /dev/null 2>&1
}

# Prints the first free port at or above $1.
#
# Test scripts use this instead of a fixed port so that concurrent runs — a second
# suite, or the same suite in another git worktree — never share a port and so never
# need to kill whatever is already listening.
find_free_port() {
    local base=$1
    local port
    for offset in $(seq 0 199); do
        port=$((base + offset))
        port_in_use "$port" || { echo "$port"; return 0; }
    done
    echo "No free port found in range ${base}-$((base + 199))" >&2
    return 1
}

# Re-sign an issued token with future/past expirations, preserving its user and
# issuer. JWT_SECRET must match the isolated test server. Requires Python 3.
jwt_expiration_test_tokens() {
    TEST_JWT_TOKEN="$1" python3 - <<'PY'
import base64
import hashlib
import hmac
import json
import os
import time

header, encoded_payload, _ = os.environ['TEST_JWT_TOKEN'].split('.')
payload = json.loads(base64.urlsafe_b64decode(encoded_payload + '=' * (-len(encoded_payload) % 4)))

def with_expiration(expiration):
    claims = dict(payload, exp=expiration)
    encoded = base64.urlsafe_b64encode(json.dumps(claims).encode()).rstrip(b'=').decode()
    unsigned = f'{header}.{encoded}'
    signature = hmac.new(os.environ['JWT_SECRET'].encode(), unsigned.encode(), hashlib.sha256).digest()
    return unsigned + '.' + base64.urlsafe_b64encode(signature).rstrip(b'=').decode()

now = int(time.time())
print(json.dumps({'valid': with_expiration(now + 3600), 'expired': with_expiration(now - 3600)}))
PY
}
