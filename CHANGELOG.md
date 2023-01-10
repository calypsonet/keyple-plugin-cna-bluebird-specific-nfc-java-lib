# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.1.1] - 2023-01-10
### Added
- `bluebird-plugin-mock` module containing an empty mock of the plugin.
### Fixed
- The identification of the protocol currently used to communicate with a card.
### Removed
- Definitions for unusable protocols (`MIFARE_CLASSIC`, `MIFARE_ULTRALIGHT`, `SRT512`).
### CI
- Automation of the right to execute (x) shell scripts.
### Upgraded
- "Keyple Util Library" to version `2.3.0` by removing the use of deprecated methods.

## [2.1.0] - 2022-07-26
### Added
- `CHANGELOG.md` file (issue [#7]).
- CI: Forbid the publication of a version already released (issue [#5]).
- Add `setSkyEcpData` to `BluebirdContactlessReader` interface to allow Sky ECP polling mode (issue [#9]).
### Fixed
- Removal of the unused Jacoco plugin for compiling Android applications that had an unwanted side effect when the application was launched (stacktrace with warnings).
### Upgraded
- "Keyple Util Library" to version `2.1.0` by removing the use of deprecated methods.
### Upgraded examples
- "Calypsonet Terminal Calypso API" to version `1.2.+`
- "Keyple Service Library" to version `2.1.0`
- "Keyple Service Resource Library" to version `2.0.2`
- "Keyple Card Calypso Library" to version `2.2.1`

## [2.0.0] - 2021-10-06
### Changed
- Upgrade the component to comply with the new internal APIs of Keyple 2.0

## [1.0.0] - 2020-12-18
This is the initial release.

[unreleased]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.1...HEAD
[2.1.1]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.0...2.1.1
[2.1.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.0.0...2.1.0
[2.0.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/1.0.0...2.0.0
[1.0.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/releases/tag/1.0.0

[#9]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/issues/9
[#7]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/issues/7
[#5]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/issues/5