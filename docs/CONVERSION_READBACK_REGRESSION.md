# Audio conversion read-back verification

After conversion, output bytes written to a selected SAF document are re-read and SHA-256 compared against the generated local file. This detects silent truncation and corruption but cannot guarantee atomicity or restore a pre-existing destination. Conversion rejects identical source/destination and matching SAF document IDs. Test provider aliasing, read-back permission denial, large audio files, cancellation, storage full, and unsupported codecs on real Android devices.
