#!/usr/bin/env bash
# Repeatable MatchForge API demonstration (local or hosted).
# Requires: curl, jq
# Credentials via environment — never commit real secrets.

set -euo pipefail

BASE_URL="${1:-${MATCHFORGE_BASE_URL:-http://localhost:8080}}"
PLAYER_PASSWORD="${DEMO_PLAYER_PASSWORD:-StrongPassword-Example-1}"
ADMIN_USERNAME="${DEMO_ADMIN_USERNAME:-}"
ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:-}"

SUFFIX="$(date +%s)_${RANDOM}"
LAST_PLAYER_ID=""
LAST_TOKEN=""
PLAYER_A="demo_a_${SUFFIX}"
PLAYER_B="demo_b_${SUFFIX}"
OUTSIDER="demo_out_${SUFFIX}"

log() { printf '==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v jq >/dev/null 2>&1 || fail "jq is required"

api() {
	local method="$1"
	local path="$2"
	local token="${3:-}"
	local body="${4:-}"
	local args=(-sS -w "\n%{http_code}" -X "$method" "${BASE_URL}${path}" -H "Content-Type: application/json")
	if [[ -n "$token" ]]; then
		args+=(-H "Authorization: Bearer ${token}")
	fi
	if [[ -n "$body" ]]; then
		args+=(-d "$body")
	fi
	local raw
	raw="$(curl "${args[@]}")"
	HTTP_BODY="$(printf '%s' "$raw" | sed '$d')"
	HTTP_CODE="$(printf '%s' "$raw" | tail -n1)"
}

expect_status() {
	local expected="$1"
	[[ "$HTTP_CODE" == "$expected" ]] || fail "Expected HTTP ${expected} for request, got ${HTTP_CODE}: $(printf '%s' "$HTTP_BODY" | head -c 200)"
}

register_player() {
	local username="$1"
	api POST "/api/v1/auth/register" "" "$(jq -n --arg u "$username" --arg p "$PLAYER_PASSWORD" '{username: $u, password: $p, skillRating: 1000}')"
	expect_status 201
	LAST_PLAYER_ID="$(jq -r '.playerId' <<<"$HTTP_BODY")"
	LAST_TOKEN="$(jq -r '.accessToken' <<<"$HTTP_BODY")"
}

login() {
	local username="$1"
	local password="$2"
	api POST "/api/v1/auth/login" "" "$(jq -n --arg u "$username" --arg p "$password" '{username: $u, password: $p}')"
	expect_status 200
	LAST_TOKEN="$(jq -r '.accessToken' <<<"$HTTP_BODY")"
}

log "Health check at ${BASE_URL}"
api GET "/actuator/health"
expect_status 200

log "Register and authenticate two players"
register_player "$PLAYER_A"
TOKEN_A="$LAST_TOKEN"
PLAYER_A_ID="$LAST_PLAYER_ID"
register_player "$PLAYER_B"
TOKEN_B="$LAST_TOKEN"
PLAYER_B_ID="$LAST_PLAYER_ID"
register_player "$OUTSIDER"
TOKEN_OUT="$LAST_TOKEN"

log "Verify cross-player profile access is forbidden"
api GET "/api/v1/players/${PLAYER_B_ID}" "$TOKEN_A"
expect_status 403

log "Set availability for both players"
api PUT "/api/v1/players/${PLAYER_A_ID}/availability" "$TOKEN_A" '{"available":true}'
expect_status 200
api PUT "/api/v1/players/${PLAYER_B_ID}/availability" "$TOKEN_B" '{"available":true}'
expect_status 200

log "Join matchmaking queue"
api POST "/api/v1/matchmaking/queue" "$TOKEN_A" "$(jq -n --arg id "$PLAYER_A_ID" '{playerId: $id}')"
expect_status 202
api POST "/api/v1/matchmaking/queue" "$TOKEN_B" "$(jq -n --arg id "$PLAYER_B_ID" '{playerId: $id}')"
expect_status 202
STATE="$(jq -r '.state' <<<"$HTTP_BODY")"
[[ "$STATE" == "MATCHED" ]] || fail "Expected queue state MATCHED, got ${STATE}"
MATCH_ID="$(jq -r '.matchId' <<<"$HTTP_BODY")"
[[ -n "$MATCH_ID" && "$MATCH_ID" != "null" ]] || fail "matchId missing after matchmaking"

log "Load match and session"
api GET "/api/v1/matches/${MATCH_ID}" "$TOKEN_A"
expect_status 200
SESSION_ID="$(jq -r '.sessionId' <<<"$HTTP_BODY")"
[[ -n "$SESSION_ID" && "$SESSION_ID" != "null" ]] || fail "sessionId missing on match"

log "Outsider cannot view session"
api GET "/api/v1/sessions/${SESSION_ID}" "$TOKEN_OUT"
expect_status 403

log "Both players join session"
api POST "/api/v1/sessions/${SESSION_ID}/join" "$TOKEN_A" "$(jq -n --arg id "$PLAYER_A_ID" '{playerId: $id}')"
expect_status 200
api POST "/api/v1/sessions/${SESSION_ID}/join" "$TOKEN_B" "$(jq -n --arg id "$PLAYER_B_ID" '{playerId: $id}')"
expect_status 200
SESSION_STATUS="$(jq -r '.status' <<<"$HTTP_BODY")"
[[ "$SESSION_STATUS" == "ACTIVE" ]] || fail "Expected session ACTIVE, got ${SESSION_STATUS}"

log "Heartbeat from player A"
api POST "/api/v1/sessions/${SESSION_ID}/heartbeat" "$TOKEN_A" "$(jq -n --arg id "$PLAYER_A_ID" '{playerId: $id}')"
expect_status 200

log "Player cannot complete session (admin only)"
api POST "/api/v1/sessions/${SESSION_ID}/complete" "$TOKEN_A"
expect_status 403

if [[ -z "$ADMIN_USERNAME" || -z "$ADMIN_PASSWORD" ]]; then
	log "Skipping admin completion — set DEMO_ADMIN_USERNAME and DEMO_ADMIN_PASSWORD to finish the flow"
	printf 'DEMO_PARTIAL_OK (configure admin env vars for full completion)\n'
	exit 0
fi

log "Administrator completes session"
login "$ADMIN_USERNAME" "$ADMIN_PASSWORD"
ADMIN_TOKEN="$LAST_TOKEN"
api POST "/api/v1/sessions/${SESSION_ID}/complete" "$ADMIN_TOKEN"
expect_status 200
[[ "$(jq -r '.status' <<<"$HTTP_BODY")" == "COMPLETED" ]] || fail "Session not completed"

log "Verify player status after completion"
api GET "/api/v1/players/${PLAYER_A_ID}" "$TOKEN_A"
expect_status 200
[[ "$(jq -r '.status' <<<"$HTTP_BODY")" == "AVAILABLE" ]] || fail "Player A not AVAILABLE after session"

printf 'DEMO_SUCCESS\n'