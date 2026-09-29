# Privacy and security

## Repository contents

The public repository is intentionally sanitized. It does not contain:

- SMB passwords or usernames
- private SMB/Tailscale addresses
- personal filesystem paths
- device-owner names
- location-specific development comments
- signing keys or Android keystores

## Runtime credential storage

`CredentialStore` saves the SMB address and username in app preferences and encrypts the SMB password with AES/GCM using a key stored in Android Keystore. Credentials are entered at runtime and are not compiled into the APK.

## Network scope

The app connects to the SMB location supplied by the user. The optional House LAN address is also entered locally, without a hardcoded endpoint. HOUSE probes MPD on port 6600, uses the control service on 8787 and Snapcast audio on 1704, all bound to the selected non-VPN Wi-Fi/Ethernet Network. The current LAN service is cleartext and has no pairing/authentication; that remains an open service design item. SMB credentials are not sent to the house API. The project has no analytics or telemetry dependency in the current source tree.

The phone generates a random stable controller ID in its no-backup storage, with an associated Snapcast renderer ID. The Pi stores their association so a phone renderer cannot become a passive auto-start radio after a reconnect. Clearing app data/reinstalling generates a new identity.

## Development hygiene

`.gitignore` excludes common Android signing material and local configuration files. Do not commit:

- `.jks` / `.keystore` files
- `keystore.properties` / signing properties
- real SMB credentials
- private Tailnet/SMB addresses if the repository remains public
