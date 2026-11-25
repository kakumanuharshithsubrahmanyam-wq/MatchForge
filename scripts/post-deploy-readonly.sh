#!/usr/bin/env bash
# Read-only smoke checks for a hosted MatchForge instance (safe on production).
# Usage: MATCHFORGE_BASE_URL=https://matchforge-y4f9.onrender.com ./scripts/post-deploy-readonly.sh

set -euo pipefail

BASE_URL="${MATCHFORGE_BASE_URL:-https://matchforge-y4f9.onrender.com}"

curl_bin="$(command -v curl || true)"
[[ -n "$curl_bin" ]] || { echo "curl is required" >&2; exit 1; }

check_status() {
	local path="$1"
	local expected="$2"
	local code
	code="$("$curl_bin" -sS -o /dev/null -w '%{http_code}' --max-time 45 "${BASE_URL}${path}")"
	[[ "$code" == "$expected" ]] || {
		echo "FAIL ${path} expected HTTP ${expected}, got ${code}" >&2
		return 1
	}
	echo "OK   ${path} -> ${code}"
}

echo "Read-only checks against ${BASE_URL}"

check_status "/actuator/health" "200"

body="$("$curl_bin" -sS --max-time 45 "${BASE_URL}/actuator/health")"
echo "$body" | grep -q '"status":"UP"' || {
	echo "FAIL health body missing status UP" >&2
	exit 1
}
echo "OK   /actuator/health body status=UP"

check_status "/actuator/prometheus" "401"
check_status "/api/v1/players" "401"

swagger_code="$("$curl_bin" -sS -o /dev/null -w '%{http_code}' --max-time 45 "${BASE_URL}/swagger-ui/index.html")"
case "$swagger_code" in
	401|403|404) echo "OK   /swagger-ui/index.html -> ${swagger_code}" ;;
	*)
		echo "FAIL /swagger-ui/index.html expected 401/403/404, got ${swagger_code}" >&2
		exit 1
		;;
esac

echo "READ_ONLY_SMOKE_SUCCESS"