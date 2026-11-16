# Phone upload

1. TV: Settings → Phone upload (or My Photos → Add photos). A QR code, an address and a 6-digit PIN appear.
2. Phone (same Wi-Fi): scan the QR code, enter the PIN.
3. Choose photos (optionally an album). They appear in My Photos immediately.

The dashboard also lets you create/rename/delete albums, add photos to albums, delete photos (with confirmation),
pick Art / Photos / Mixed and the album shown, set the time per picture, and see storage use.

## Security model

- Starts when you open Phone upload and stays available until switched off, 30 idle minutes pass or the
  network drops. Leaving the screen alone does not immediately stop it, so phone controls can show art on TV.
- Accepts connections only from loopback / private (RFC 1918) / link-local addresses; the `Host` header must be
  the TV's own IP (blocks DNS-rebinding from web pages).
- PIN pairing: new random PIN every time the service starts; 5 wrong tries lock pairing for 60 s.
  Sessions are random 192-bit tokens; "Disconnect all devices" revokes them.
- State-changing calls require the token in a header (cookies alone are rejected, which defeats CSRF).
- Uploads: 40 MB limit checked before reading the body; JPEG/PNG/WebP only (verified by decoding, not by file name);
  files are stored under their SHA-256, so client file names can never become paths; duplicates are not stored twice;
  imports stop before using the last 1 GB of storage. Imports are serialised to avoid concurrent decode spikes.
- Large photos are resized to 1920 px on import by default (switch in Settings → Media). Your phone originals
  are untouched. Existing imported photos are not recompressed automatically.
- No credentials are hard-coded and nothing is exposed to the internet. Traffic is plain HTTP on your LAN.

Developer test from a PC while the emulator runs: `adb forward tcp:8080 tcp:8080`, then open `http://127.0.0.1:8080`.

This is a LocalSend-like **browser upload**, not the LocalSend protocol: no phone app or cloud account is needed.
The sender and stick must share a trusted LAN. Guest Wi-Fi/client isolation can block the connection. Turn off
Phone upload after sending, or let its idle timer stop it. Do not forward port 8080 through your router.

## Repeatable upload check

`tools/upload-smoke.postman_collection.json` checks pairing, access control, storage reserve, image optimisation,
duplicate detection, fake images and logout. Run it locally in Postman/Newman with `baseUrl`, `pin`, `photoFile`
and `badFile` variables; use a generated JPEG larger than 1920 pixels and a text file pretending to be a JPEG.
It leaves the synthetic photo in the emulator library for visual checking. It never uploads results to Postman.
Keep PINs/session tokens out of saved environments and version control.

POST responses close their HTTP connection: auth/size/MIME rejection can happen before the body is read, so
connection reuse must not let leftover bytes corrupt the next request. The collection covers this regression.
