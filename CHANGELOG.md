# Changelog

## 0.3.1 - 2026-07-29

- Add Packet wake controls: follow system, throttle, and block.
- Add a Packet-specific refreeze delay.
- Resolve Packet callback UIDs to package-name rules at runtime.
- Limit repeated Packet block logging per UID.
- Add a narrow provider-startup hook so `system_server` can load policy after cold boot
  without first opening the manager UI.
- Report 22 installed hook targets and synchronize system/local policy revisions.
- Migrate policy schema to v3 while retaining v1/v2 compatibility.
