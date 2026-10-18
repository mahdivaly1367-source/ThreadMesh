# ThreadMesh: real-device offline mesh test plan

This plan is for physical Android devices. GitHub Actions can test protocol logic and build the APK, but cannot verify Bluetooth/Wi-Fi radio behavior or OEM background restrictions.

## Test setup

- Use three Android phones (A, B and C), preferably from two manufacturers.
- Install the same APK artifact from the successful GitHub Actions run on all phones. Keep the app open during the first pass.
- Turn off cellular data and disconnect every phone from Internet Wi-Fi. Leave Bluetooth and Wi-Fi radios enabled, because Nearby Connections may use them for local discovery.
- Grant Nearby/Bluetooth permissions. Start ThreadMesh on all devices and accept the Nearby authentication digits on both sides of each connection.
- On each connected-peer card, wait for «هویت کلید تأیید شد». The SHA-256 fingerprint prefixes should differ between independently installed phones. A connection without that badge must not relay content.

## A. Two-phone delivery

1. Connect A and B. On A, publish a clearly identifiable MESH_PUBLIC topic.
2. Confirm it appears once on B, and that A later records a signed ACK.
3. Publish a LOCAL_ONLY item. Confirm it is not transmitted to B.
4. Stop and restart B, reconnect, and confirm already-stored topics are not duplicated.

## B. True multi-hop (A → B → C)

1. Prevent A and C from connecting directly (for example, use different rooms or substantial distance); verify A cannot see C in the peer list.
2. Connect A to B only. Publish a new MESH_PUBLIC topic from A; confirm it is stored on B.
3. Disconnect A from B. Then connect B to C.
4. Confirm C receives the topic from B, with the original author and signature intact. Confirm C stores it once.
5. Close and reopen B before step 3 in a repeat run. The queued forwarding responsibility should survive the process restart.

If C ever connects directly to A, that run does not prove multi-hop; repeat with stricter separation.

## C. Reconnect and recovery

1. Disconnect a peer before its ACK arrives. Confirm the sender does not mark delivery confirmed immediately.
2. Reconnect after the retry window. Confirm the message is retried and stored only once.
3. Keep B offline while A publishes several topics, then connect B. Confirm all unexpired pending topics arrive.
4. Stop ThreadMesh on one device and verify its connection is cleared; restart it and repeat authentication.

## Record the outcome

Record Android versions/device models, whether Internet was disabled, connection fingerprint prefixes, message IDs, delivery/ACK outcome, duplicate count, and relevant diagnostics log. Mark each scenario Pass, Fail, or Not run. Do not describe real-device multi-hop as verified until the three-phone A→B→C scenario passes.
