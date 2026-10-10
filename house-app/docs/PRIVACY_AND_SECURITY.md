# House Music network and local state

House Music connects to the configured Pi on the home network. It uses the house service's HTTP API on port 8787 for library, saved radio stations, queue, transport and controller presence; MPD port 6600 for service identity; and Snapcast port 1704 for synchronized audio. The native receiver consumes the stream through the app's loopback relay. No private deployment host is bundled; the user enters it locally at first launch.

The current house-service connection uses cleartext HTTP and the existing trusted-LAN deployment. Physical non-VPN Wi-Fi/Ethernet qualification is an availability rule, not encryption or authentication. Packet routing remains Android's normal routing so Tailscale can stay enabled at home.

Local app-private settings include the configured host, browser/sort state, stable controller/renderer identity and route-specific timing correction. Android backup behavior is governed by the app manifest and device settings. House Music does not read the combined app's or SMB Music's private settings, import their queue, or share live playback state with them.

There is no SMB credential store, SMB transport, music-root mapping or transfer journal in this app. Music stays on the Pi's configured library storage; the phone renders the live Snapcast stream rather than independently downloading the same song over SMB. No analytics service or cloud account is required.

The Radio page sends the pasted station URL and any user-supplied name to the Pi. The Pi probes the URL for station metadata and MPD opens the chosen stream; the phone continues to receive only the house Snapcast audio. The external station therefore sees requests from the Pi's network. This does not add an independent phone-side internet radio player.

Saved station URLs and names persist in the server's station store and are visible to other controllers on the same house service. They are not a private, phone-only favorites list. Renaming changes the shared bookmark. Deleting removes the bookmark without stopping an active broadcast; Undo restores it. Station URLs can contain private query values, so treat the shared station list accordingly and keep complete URLs out of diagnostics and repository examples when they contain credentials or tokens.

Radio mutations and playback commands are not automatically replayed after an ambiguous response or reconnection. The app reads current server state on recovery. Radio Pause disconnects the upstream stream, and Play rejoins the live broadcast; it does not preserve a buffered copy on the phone.

Controller attachment and heartbeats identify this app to the Pi and report its mute/readiness state. Quit releases the phone receiver and detaches this controller; it does not erase the server library or other listeners' queue. Existing server-side renderer ownership may remain recorded so the phone cannot later be mistaken for an auto-starting passive radio.

Keep credentials and user library paths out of diagnostic logs and repository changes. Bundled native components and notices are described in [native/README.md](../native/README.md); redistribute their matching source archive with the APK.
