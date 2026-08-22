# Codex Remote Visual System

## Direction

Control room yang tenang: the app is a focused Android operating surface, not a terminal imitation. Conversation takes the largest area; status is compact telemetry.

## Material and Color

- Ink background: `#101417`
- Panel: `#172026`
- Elevated panel: `#202B32`
- Amber action and active state: `#F5B84B`
- Green connection state: `#74D39B`
- Error: `#F28B82`
- Text: `#E9EDF0`, muted text: `#9AA7AF`

## Components

Use Android-native touch targets at least 48dp. Messages are full-width readable blocks with 12dp corners; the composer is anchored to the bottom and stays visible above the keyboard. Connection settings are progressive disclosure from the top app bar.

## States

The connection row communicates ready, unconfigured, working, and error states. Send disables while Codex runs. Empty state gives a direct next action. Errors identify bridge failure and leave the composer available for recovery.

## Typography

System sans is used through the Android type system. Uppercase is reserved for the compact product mark; body copy remains sentence case for scanning and accessibility.
