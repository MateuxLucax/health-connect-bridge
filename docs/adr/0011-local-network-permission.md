# 11. Ask for the local network permission when the server is local

Status: Accepted

## Context

The app targets SDK 37. On Android 17, apps targeting 37 or higher have
local network access blocked by default and need the runtime permission
`ACCESS_LOCAL_NETWORK` (protection level dangerous, permission group
`NEARBY_DEVICES`, so users who already granted another permission in that
group are not asked again). Blocked TCP connections typically fail with a
timeout, UDP with `EPERM`. DNS traffic to a local resolver on port 53 is
exempt. Apps targeting 36 or lower get an implicit grant through `INTERNET`.
See [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
and [Local network definition](https://developer.android.com/privacy-and-security/local-network-definition).

The reported failure: `https://hc.home.mateux.dev` resolves to
`192.168.2.15`. The phone's browser reached it, but "Test connection" timed
out after 15 s and the request never reached the reverse proxy, because the
app only declared `INTERNET`.

## Decision

- Declare `ACCESS_LOCAL_NETWORK` in the manifest.
- `LocalNetwork.isLocal(url)` treats a server as local when its host ends
  with `.local` (mDNS, not resolved) or when any address the host resolves to
  is private, loopback, link-local or unique local (the ranges the cleartext
  warning already uses: 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, ::1,
  fc00::/7, fe80::/10) or CGNAT 100.64/10, which Android's definition lists
  as local. Loopback and ULA are not in Android's list; including them only
  costs a prompt, while missing a blocked range costs a timeout. A host that
  doesn't resolve is not local (the connection fails anyway).
- `LocalNetwork.isGranted` is true below SDK 37 (the permission doesn't
  exist there), otherwise `checkSelfPermission`.
- The UI asks with the Activity Result API before "Test connection", on Next
  in the onboarding server step and on Save in Settings, only when the URL is
  local and access is missing. The server step shows a short explanation
  while that is the case. A denial shows an inline error with a button to
  the app's settings instead of a timeout; after a "don't ask again" denial
  the app opens its settings page directly.
- The worker can't ask. Before connecting, a blocked local server fails the
  run with `SyncError.Kind.LOCAL_NETWORK_PERMISSION` (WorkManager `failure`,
  not `retry`). As a fallback, a connect timeout or refusal is mapped to the
  same kind when the server is local and access is missing. Home shows the
  error with "Allow local network access", and Settings shows the permission
  state next to the battery state.

## Consequences

- A DNS lookup decides whether to ask. A name that resolves to a public
  address but is reached over the LAN (split DNS changing later) is detected
  at the next check; the fallback mapping covers the gap.
- Users with only a public HTTPS server are never asked.
- The CI emulator runs API 36, where the permission doesn't exist, so the
  end-to-end test covers the implicit grant path only. Enforcement is covered
  by unit tests with injected resolvers and permission checks.
