# TODO

## Open items

| id | status | severity | effort | description |
|---|---|---|---|---|
| FN-3 | open | medium | small | Pump queues end-of-stream on stop while the microphone stays in RECORDSTATE_RECORDING through drain and publication. Stop microphone input at the worker cutoff and verify its native state before cleanup. |
| FN-4 | open | medium | small | RecorderApp restores a cached name and URI without consulting MediaStore, and publication precedes the preferences write. Reconcile the most recent saved recording after rename, deletion or process restart and test these transitions. |
| FN-5 | open | medium | small | RecorderActivity.onStop clears the single Session.changed listener and requests a stop even if a second recorder activity is visible. Keep all visible activities subscribed and preserve capture when they leave, as requested by the owner. |
| FN-6 | open | low | small | RecorderActivity.status reports ready during discovery and after an empty catalog disables Record. Make status, controls and profile details agree in each state. |
| FN-7 | open | medium | small | RecorderTest.checkPlayback accepts Android's completion callback after asynchronous playback errors. Verify a deliberate player error fails the playback oracle and check valid recordings. |
| FN-8 | in_progress | high | small | Newly requested background capture needs a user-started microphone foreground service, an identity-bound Stop notification, and verification of saved audio during the hidden interval. |

## Other items

| id | status | severity | effort | description |
|---|---|---|---|---|
| FN-2 | blocked | medium | small | The RugKing Pad 2 Pro has the app installed. Its secure lock screen prevented the two button-driven recording tests from reaching a resumed activity. Repeat on the unlocked tablet and verify recording and playback. |
