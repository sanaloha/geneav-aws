---
description: Start, stop, or check the local geneav docker stack (clamav + backend + frontend)
argument-hint: start | stop | restart | status | logs [svc] | reset | scan <file>
allowed-tools: Bash(./geneav.sh:*), Bash(docker compose:*), Bash(docker version:*), Bash(curl:*)
---

Run the local geneav stack control script with the user's arguments: `$ARGUMENTS`

Execute `./geneav.sh $ARGUMENTS` from the repo root (Git Bash). If no arguments were
given, default to `status` so the user sees current state rather than accidentally
starting or stopping something.

Notes:

- `start` builds images and blocks until `/api/v1/health` responds; a cold start is
  slow because ClamAV downloads its signature database before the backend starts.
- `logs` follows output and will not exit on its own — run it in the background and
  report the recent lines rather than blocking the session.
- `reset` deletes the ClamAV signature DB volume, forcing a multi-minute
  re-download on next start. Confirm with the user before running it.
- `scan` uploads a real file to the scan API. To test EICAR detection, do not write
  the EICAR string to disk on this Windows host (Defender quarantines it) — stream
  it via stdin instead, per the project's EICAR verification notes.

After the command finishes, report the outcome concisely: container status, health,
and the URLs (frontend http://localhost:3000, API http://localhost:8080, docs
http://localhost:8080/docs). If it failed, show the relevant backend log lines.
