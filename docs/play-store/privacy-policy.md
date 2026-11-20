---
title: Privacy policy
permalink: /privacy/
---

# Privacy policy: Telos

_Effective 20 November 2026_

Telos is a free task manager and goal tracker for Android, made by ToolCrate. This policy explains
what happens to what you put in it. In short: **your tasks stay on your phone. If you choose a
backup to your own Google Drive, it is encrypted with a passphrase only you know, and we never
receive it.**

## What Telos keeps, and where

Telos stores the following **only on your phone**, in storage only this app can read:

- your tasks, projects, sections, labels, priorities, due dates, notes and subtasks;
- reminders, recurring rules and a record of completed tasks;
- your goals, their next steps and your quarterly reviews;
- saved filters and your settings.

This data is excluded from Android's own cloud backup and device-to-device transfer. There are no
accounts. Telos has no servers, and we (the developer) never receive any of it.

## What Telos does not do

- No ads and no advertising ID.
- No analytics, tracking, crash reporting or third-party SDKs that collect data.
- No location, contacts, microphone, camera or photo permissions.
- No internet use at all, except the optional Google Drive backup below. A link you share into
  Telos is saved as text; Telos never opens it or fetches its page.

## Permissions

- **Notifications** and **alarms**: to show the reminders you set, on time. Reminders are
  re-registered after your phone restarts or the time zone changes.
- **Internet**: used only by the optional Google Drive backup, and only while it is on.

## Adding tasks from other apps

When you share text to Telos, or choose *Add to Telos* on selected text, Telos receives only that
text and turns it into a task on your phone.

## Optional: backup to your own Google Drive

If you turn it on, Telos keeps one backup file in a hidden app folder in **your own Google Drive**
(the "app data folder"). Telos asks Google only for access to that folder, and can't see anything
else in your Drive.

- The file is **encrypted on your phone before upload** (AES-256-GCM, with a key derived from your
  passphrase using Argon2id). Neither Google nor we can read it without your passphrase.
- Your app-lock PIN is never included.
- The upload goes directly from your phone to Google over an encrypted (HTTPS) connection. It
  never passes through us.
- Google's handling of your Drive is covered by Google's own privacy policy.

To delete it: in Google Drive on the web, open **Settings → Manage apps**, find Telos, and choose
**Delete hidden app data**. Uninstalling Telos deletes everything on your phone.

## Exporting and importing files

You can export your data to a file you choose (JSON or CSV), and import tasks from a file. Exports
are plain text, so keep them somewhere private. Nothing is sent anywhere.

## App lock

If you set a PIN, only a one-way hash of it is stored on your phone. Fingerprint or face unlock
is handled by Android; Telos never sees your biometric data.

## Children

Telos is not directed at children under 13.

## Changes and contact

If this policy changes, the new version will be posted here with a new date.
Questions: admin.toolcrate@gmail.com
