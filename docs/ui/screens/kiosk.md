# Kiosk

Kiosk mode turns an approved counter station into a shared terminal: the whole app is replaced by the counter, full
screen, and staff sign in by picking their name and typing a PIN. It locks itself when left alone. Touch this when
changing the lock screen or how a kiosk signs people in and out. Registering and approving stations, and how PIN
login works on the server, are in [PINs and stations](../../server/pins-and-stations.md); setting one up is in
[Running a server](../../operations.md#counter-stations-and-kiosks).

![The kiosk's lock screen](../../images/kiosk-lock.png)

## How it works

[`KioskHandler`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/kiosk/components/KioskHandler.kt)
watches this device's `Station` (from `SessionHandler`). Kiosk mode is **active** when the station is approved, has
`kioskEnabled`, and an admin hasn't left it on this device. When `active` turns on, the handler:

1. sets `RemoteHandler.credentialFallback = false`, so a PIN session that runs out isn't quietly replaced by the
   device owner's saved password,
2. asks for full screen through `AppWindow.setFullscreen(true)`,
3. starts the idle watch,
4. locks, which signs out whoever logged the device in.

`App()` sees `kiosk.active` and shows
[`KioskShell`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/kiosk/KioskShell.kt)
instead of the normal shell. It's a `TmBackdrop` with either the `LockScreen`, or a `TmTopBar` (station name, who's
signed in, a Lock button) over the [counter](counter.md).

```mermaid
stateDiagram-v2
    [*] --> Locked: kiosk turns on
    Locked --> Unlocked: pick name + right PIN
    Unlocked --> Locked: Lock button
    Unlocked --> Locked: idle timeout
    Unlocked --> Locked: PIN session can't be refreshed
    Locked --> Left: admin picks Leave kiosk mode + PIN
    Left --> Locked: Settings, Counter station, resume
```

### Locking and the idle timer

- **Activity.** `KioskShell` watches every pointer event (in the `Initial` pass, so it doesn't take them from what's
  underneath) and every key, and calls `recordActivity()`.
- **Idle.** A loop checks once a second; when nobody has touched anything for the station's `idleTimeoutSeconds`,
  it locks.
- **Keeping the session alive.** The server ends a kiosk's PIN session a while after the idle timeout if no
  requests reach it, in case the device was switched off. So the same loop calls `RemoteHandler.keepKioskAlive()`
  once a minute while someone has touched the screen in the last minute. See
  [Kiosk sessions on the server](../../server/pins-and-stations.md#kiosk-sessions-on-the-server).
- **Lock** shows the lock screen at once, clears what was loaded for the last person (`onSignedOut`), then ends
  their session on the server (`RemoteHandler.endSession`) and reloads the staff list.

### Signing in

`LockScreen` loads the staff who can PIN in here (`/api/auth/station/staff`, everyone active with a PIN) and shows
each as a tile. Picking one shows the `PinPad`: big keys for touch, and the number keys and Backspace work too. It
sends the PIN as soon as six digits are in. A wrong PIN shows the server's message ("Wrong PIN, 3 tries left") and
reloads the staff list; five wrong tries lock that person's PIN until an admin resets it.

`unlock()` calls `RemoteHandler.pinLogin`. On success `onSignedIn` loads the user and the lists, and the counter
shows.

### Leaving kiosk mode

"Leave kiosk mode" on the lock screen asks for an admin to pick their name and PIN. `exit()` PINs them in, then:

- an admin: the handler marks itself `suspended`, turns `active` off and the normal app comes back, still signed
  in as that admin. It tells the server (`RemoteHandler.leaveKiosk`), which stops their PIN session ending with the
  kiosk's idle limit,
- anyone else: signed straight back out with "Only an admin can leave kiosk mode".

Leaving only lasts until the app restarts. Settings > Account and station > Counter station has a button that calls
`resume()` to turn it back on.

## Where it lives

| What | Where |
|------|-------|
| State, locking, idle timer, sign in and out | `ui/screens/kiosk/components/KioskHandler.kt` |
| What a kiosk shows | `ui/screens/kiosk/KioskShell.kt` |
| Lock screen and PIN pad | `ui/screens/kiosk/LockScreen.kt`, `PinPad.kt` |
| Full screen request | `ui/AppWindow.kt`, applied in each platform's entry point |
| Tests | `composeApp/src/commonTest/.../ui/screens/kiosk/KioskHandlerTest.kt` (takes a `now` clock to move time along) |
| Render test | `ScreenRenderTest.kioskLock()` |

## Gotchas

- **The kiosk follows the server.** Turning kiosk mode on or off, or changing the timeout, on the admin Stations
  tab reaches the device the next time it refreshes its station: when it connects, or from Refresh in its Counter
  station dialog.
- **Turning on locks immediately.** Whoever was logged in on the device is signed out the moment it becomes a
  kiosk, by design.
- **Keys are seen twice.** The shell's key handler records activity and passes the key on, so the counter's own
  shortcuts still work under it.

## Known gaps

- Full screen isn't a lockdown. Desktop makes the window full screen, web asks the browser, and Android hides the
  status and navigation bars, which a swipe brings back for a moment. None of them stop someone leaving the app.
  Android's lock task mode is in the [fix plan's last phase](../../FIX-PLAN.md#phase-11-android-and-other-machines).
- **Browsers only go full screen from a click or key press.** A kiosk that starts on page load stays windowed until
  someone touches it, and pressing Escape leaves full screen; the web entry point asks again on the next click or
  key press while kiosk mode is on.

## See also

- [PINs and stations](../../server/pins-and-stations.md)
- [Counter screen](counter.md)
- [Settings screen](settings.md)
