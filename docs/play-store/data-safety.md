# Data safety form: suggested answers

Play Console → Policy → App content → Data safety. These follow what the app actually does
(see `privacy-policy.md`) and are deliberately conservative: when in doubt, declare. They match
the answers given for Lyceum and Kairos.

## Overview

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes**, only when the user turns on Google Drive backup |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS to Google Drive; the file is also encrypted on the phone first) |
| Do you provide a way for users to request that their data is deleted? | **Yes** (delete hidden app data in Google Drive, or uninstall) |

## Data types

What leaves the phone is the encrypted backup, and only to the user's own Drive. Declare it as
**Collected**, **not shared**, **optional**, purpose **App functionality** only:

| Category | Type | Why |
|---|---|---|
| App activity | Other user-generated content | Tasks, notes, goals and reviews the user wrote |

Everything else (location, personal info, contacts, identifiers, app interactions, diagnostics,
financial info, health, messages, photos, audio, web history): **not collected**.
Advertising ID: **No**.

### Why "not shared"

The backup goes to the user's own Google Drive at their request. The developer never receives it.
