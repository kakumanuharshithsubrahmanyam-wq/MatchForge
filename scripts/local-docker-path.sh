#!/usr/bin/env bash
# Sourced by local verify scripts when Docker Desktop credential helper is not on PATH.
# Usage: source scripts/local-docker-path.sh
if [[ -x "/Applications/Docker.app/Contents/Resources/bin/docker-credential-desktop" ]]; then
  export PATH="/Applications/Docker.app/Contents/Resources/bin:${PATH}"
fi