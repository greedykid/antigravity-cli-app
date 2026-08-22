# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

delegated: native Android app with a small Node.js bridge and GitHub Actions APK build

## Users

Assumption from the explicit brief: a developer who wants to send prompts to Codex CLI from an Android phone while away from the computer running Codex.

## Product Purpose

The app provides a simple chat GUI for remotely sending prompts to Codex CLI and reading responses without using a terminal or code editor on the phone. Success means a user can connect to a trusted bridge, send a prompt, see progress, and recover from connection or execution errors.

## Positioning

The app is a focused mobile control surface for an existing Codex CLI installation, keeping project execution on the user's own machine while making the interaction comfortable on a phone.

## Operating Context

Codex CLI runs on a computer or server. The Android client connects to a user-hosted bridge over a private network such as Tailscale. The first build targets a single active conversation and a configurable bridge URL.

## Capabilities and Constraints

- Send a text prompt to the bridge and receive a response.
- Show connection, sending, loading, success, and error states.
- Configure the bridge URL and bearer token in the app.
- The bridge invokes the locally installed Codex CLI and must not expose the machine directly to the public internet.
- No claim is made that the first build supports multi-user access, file browsing, or background jobs.

## Brand Commitments

The user asked for a chat GUI rather than a terminal or code editor. The interface should feel calm, technical, direct, and native to Android.

## Evidence on Hand

No existing product assets or visual implementation. The repository was empty at build start.

## Product Principles

- Conversation first: the prompt and response are always the main content.
- Trust through status: connection and execution state are visible without being noisy.
- Phone-ready: touch targets, keyboard behavior, and contrast follow Android conventions.
- User-owned infrastructure: connection details and security remain explicit.

## Accessibility & Inclusion

Use Material color roles, Android-scaled text, 48dp touch targets, content descriptions for icons, and layouts that remain usable with larger system font sizes.
