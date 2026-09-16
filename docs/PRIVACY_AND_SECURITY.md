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

The app connects to the SMB location supplied by the user. It contains no hardcoded SMB endpoint. The project has no analytics or telemetry dependency in the current source tree.

## Development hygiene

`.gitignore` excludes common Android signing material and local configuration files. Do not commit:

- `.jks` / `.keystore` files
- `keystore.properties` / signing properties
- real SMB credentials
- private Tailnet/SMB addresses if the repository remains public
