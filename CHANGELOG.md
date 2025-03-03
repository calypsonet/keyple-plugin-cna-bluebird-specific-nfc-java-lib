# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Added dependency to `keyple-plugin-storage-card-java-api:1.0.0` for support of storage cards
### Build
- Removed dependency to `keyple-gradle` plugin
### Upgraded
- Dokka to `2.0.0`

## [3.0.0] - 2025-02-20
:warning: Major version!
### Breaking Changes
- Renamed `BluebirdContactReader` to `BluebirdSamReader`
- Renamed `BluebirdContactlessReader` to `BluebirdCardReader`
- Renamed `BluebirdSupportContactlessProtocols` to `BluebirdContactlessProtocols`
- Changed factory method from `getFactory()` to `provideFactory()`
### Added
- New `BluebirdConstants` object containing all constants
- Enhanced Kotlin documentation with `@since` annotations for API compatibility tracking
### Changed
- Moved following constants to `BluebirdConstants`:
  - `PLUGIN_NAME` (was `BluebirdPlugin.PLUGIN_NAME`)
  - `BLUEBIRD_SAM_ANDROID_PERMISSION` (was `BluebirdPlugin.BLUEBIRD_SAM_PERMISSION `)
  - `CARD_READER_NAME` (was `BluebirdContactlessReader.READER_NAME`)
  - `SAM_READER_NAME` (was `BluebirdContactReader.READER_NAME`)
### Upgraded
- Updated Keyple dependencies:
  - `keyple-common-java-api`: `2.0.0` -> `2.0.1`
  - `keyple-plugin-java-api`: `2.0.0` -> `2.3.1`
  - `keyple-util-java-lib`: `2.3.0` -> `2.4.0`
- Upgraded Kotlin and related libraries
- Removed test dependencies
### Technical
- Enhanced Bluebird low level libraries management
- Migrated to modern Gradle configuration standards
- Updated target Android SDK to API 35

## [2.1.4] - 2024-02-21
### Fixed
- Cards using unsupported contactless protocols are now ignored.

## [2.1.3] - 2024-02-06
### Fixed
- Card scanning process. The RF module is now enabled/disabled in addition to the 
  activation/deactivation of the RF field to better improve the battery life of the terminal.

## [2.1.2] - 2024-02-01
### Fixed
- Card scanning process. Now, the low-level card scanning is properly stopped and the RF field is 
  deactivated when the card detection is stopped, resulting in improved battery life for the 
  terminal. 

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

[unreleased]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/3.0.0...HEAD
[3.0.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.4...3.0.0
[2.1.4]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.3...2.1.4
[2.1.3]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.2...2.1.3
[2.1.2]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.1...2.1.2
[2.1.1]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.1.0...2.1.1
[2.1.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/2.0.0...2.1.0
[2.0.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/compare/1.0.0...2.0.0
[1.0.0]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/releases/tag/1.0.0

[#9]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/issues/9
[#7]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/issues/7
[#5]: https://github.com/calypsonet/keyple-plugin-cna-bluebird-specific-nfc-java-lib/issues/5