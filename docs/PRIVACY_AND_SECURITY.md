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

SMB Music v0.5.0 connects only to the SMB location supplied by the user and can request the locally installed Tailscale app to connect. It contains no HOUSE API/probe, controller identity, Snapcast receiver, queue transfer or telemetry dependency. The app keeps its own saved session in private no-backup storage, separate from encrypted credentials. House Music is a separate pending app.

## Development hygiene

`.gitignore` excludes common Android signing material and local configuration files. Do not commit:

- `.jks` / `.keystore` files
- `keystore.properties` / signing properties
- real SMB credentials
- private Tailnet/SMB addresses if the repository remains public
