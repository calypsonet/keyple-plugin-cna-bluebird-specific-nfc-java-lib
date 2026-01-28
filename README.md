# Keyple Plugin CNA Bluebird Specific NFC Java Library

## Overview

The **Keyple Plugin CNA Bluebird Specific NFC Java Library** is an add-on to allow an application using Keyple to interact with Bluebird terminals and their specific NFC readers.

## Features and Dependencies

### Core Features (No Additional Dependencies)

The plugin provides full support for ISO 14443-4 contactless cards:
- **Calypso cards** (Type A and B)
- **Innovatron B Prime cards**
- **SKY ECP mode** (Type A and B)

These features work out-of-the-box with only the Bluebird proprietary libraries.

### Storage Card Support (Requires CNA Libraries)

The plugin can optionally support storage cards through the **Keyple Plugin Storage Card API**:
- **MIFARE Ultralight** (MFOC, MFOICU1)
- **MIFARE Classic**
- **ST25/SRT512**

**Important:** Storage card support requires additional libraries that are:
- Available **exclusively to Calypso Networks Association (CNA) members**
- **Optional** - the plugin works perfectly without them if you only need ISO 14443-4 cards
- Not included in this repository

Required CNA libraries for storage card support:
- `keyple-card-cna-storagecard-java-lib-<version>.jar`
- `keyple-plugin-cna-storagecard-java-lib-<version>.jar`

If you don't have access to these libraries or don't need storage card support, simply omit them from your build.

## Project Structure

This repository contains three main modules:

- **plugin-mock**: Public mock plugin with API interfaces only (no proprietary dependencies)
- **plugin**: Full implementation plugin requiring Bluebird proprietary libraries
- **example-app**: Reference application demonstrating the plugin usage

## Building the Plugin

### Public Mock Plugin (No Proprietary Libraries Required)

The mock plugin can be built without any proprietary libraries

### Full Implementation Plugin (Requires Bluebird Libraries)

To build the full implementation plugin, you must have access to Bluebird's proprietary libraries.

#### Prerequisites

You need the following Bluebird SDK JAR files:
- `bluebird-extnfc.jar` - Bluebird ExtNFC API
- `sam_ng_201710.jar` - SAM library

These libraries are **not included** in this repository due to licensing restrictions and must be obtained separately through Bluebird's official channels.

#### Setup Instructions

1. Obtain the required Bluebird libraries from Bluebird or your authorized distributor
2. Create the `libs` directory in the `plugin` module
3. Copy the Bluebird JAR files into this directory
4. Build the plugin

**Note**: The `plugin/libs/` directory is excluded from version control (`.gitignore`) to prevent accidental distribution of proprietary code.

## Building the Example Application

The example application demonstrates the usage of the full implementation plugin.

### Optional: CNA Storage Card Libraries

The example application can optionally use CNA Storage Card libraries for enhanced MIFARE and ST25 card support:
- `keyple-card-cna-storagecard-java-lib-<version>.jar`
- `keyple-plugin-cna-storagecard-java-lib-<version>.jar`

These libraries are **optional** and available exclusively to **Calypso Networks Association (CNA) members**. If you have access to these libraries, place them in:
```bash
example-app/libs/
```

The example application will work without these libraries, but with limited storage card functionality.

The example app demonstrates:
- Calypso card transactions (ISO 14443-4 A/B)
- MIFARE Ultralight operations (requires CNA Storage Card libraries)
- MIFARE Classic 1K operations (requires CNA Storage Card libraries)
- ST25/SRT512 card operations (requires CNA Storage Card libraries)

### MIFARE Classic Key Management

The plugin provides two approaches for managing MIFARE Classic authentication keys:

#### 1. Using the KeyProvider Interface (Recommended)

Implement the `KeyProvider` SPI to provide keys dynamically.
Pass the provider when creating the plugin factory.

#### 2. Using the loadKey() Method

Load keys programmatically when using `prepareMifareClassicAuthenticate`.

**Important Security Notes:**
- The example app uses a default factory key (`FFFFFFFFFFFF`) for demonstration purposes only
- **Production applications must implement secure key management**:
  - Retrieve keys from a secure server or Hardware Security Module (HSM)
  - Use encrypted key storage
  - Implement proper key rotation and access control policies
- Keys are stored in volatile memory (session-based) and cleared when the channel opens

## License Compliance

This project is licensed under the **Eclipse Public License 2.0 (EPL-2.0)**.

**Important**: The Bluebird proprietary libraries (`bluebird-extnfc.jar`, `sam_ng_201710.jar`) are **NOT** covered by this license and remain the property of Bluebird Inc. Users must comply with Bluebird's licensing terms when using these libraries.

By separating the mock plugin from the full implementation, this project can be publicly distributed while respecting proprietary license restrictions.

## About the Source Code

The code is built with **Gradle** and is compliant with **Java 1.8** in order to address a wide range of applications.

## Continuous Integration

This project uses **GitHub Actions** for continuous integration. Every push and pull request triggers automated builds
and checks to ensure code quality and maintain compatibility with the defined specifications.

**Note**: The CI/CD pipeline only builds and tests the **mock plugin**, as it does not have access to Bluebird's proprietary libraries.
