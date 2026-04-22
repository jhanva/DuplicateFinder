# Watermark Review Without ML Cleaning

## Problem And Context

The current watermark review flow detects candidate images and then tries to remove the watermark inside the app with an ONNX inpainting model. That cleaning path damages image quality because it generates a full-image output and then replaces the original file with that generated result.

The new goal is narrower and safer:

- Keep watermark detection.
- Remove all in-app watermark cleaning.
- Hand the original image off to Samsung Gallery for manual AI-based editing.
- Consider the review step successful only if the original image was actually modified.

This redesign prioritizes image fidelity over automation.

## Approaches Considered

### Approach A: Samsung Gallery Handoff On The Original File

- How it works:
  The app keeps detecting likely watermark images, but `Remove Watermark` opens Samsung Gallery in edit mode for the original image using Android's standard edit intent flow.
- Advantages:
  No internal image regeneration, no preview overwrite path, uses Samsung's built-in editing tools that already exist on the target device.
- Disadvantages:
  There is no public Samsung API to jump directly into `Object Eraser`; the user must open Gallery and choose the AI tool manually.
- Best when:
  The target experience is Samsung-first and image fidelity matters more than automation.

### Approach B: Samsung Gallery Handoff Using Save-As-Copy

- How it works:
  The app opens Samsung Gallery, but the user is expected to save a new copy instead of editing the original.
- Advantages:
  Protects the original file from accidental destructive edits.
- Disadvantages:
  Reconciliation is much harder because the app must discover the new file, relate it to the original, and decide what to do with the original.
- Best when:
  Product explicitly wants non-destructive editing and accepts more workflow complexity.

### Approach C: Rebuild Internal ML Cleaning Properly

- How it works:
  Keep in-app cleaning, but redesign it around precise masks, cropped ROI editing, high-resolution blending, and metadata preservation.
- Advantages:
  Fully integrated UX and potentially one-tap cleanup.
- Disadvantages:
  Large R&D scope, high visual QA burden, and the current implementation is too far from acceptable quality.
- Best when:
  The product requires a fully native one-tap experience and can fund model and image-processing work.

## Chosen Approach

Approach A was selected.

Explicit product decisions:

- Keep the current watermark detection flow.
- Remove the in-app cleaning model and all preview-replace behavior.
- Support only Samsung Gallery for editing.
- If Samsung Gallery AI editing is unavailable, keep the action visible but disabled with an explanation.
- Edit the original file in place.
- After returning from Gallery, only treat the item as edited if `size` or `dateModified` changed.

## Scope

### Included

- Detect watermark candidates exactly as before.
- Show a Samsung-only edit action from watermark review.
- Launch Samsung Gallery edit flow on the original image.
- Re-check the same image after the user returns.
- Advance the queue only when the original file changed.
- Disable the action on unsupported devices with a clear message.
- Remove all in-app watermark preview generation and replacement logic.

### Excluded

- Internal ML cleaning.
- Internal preview generation.
- Opening third-party editors.
- Forcing Samsung Gallery directly into `Object Eraser`.
- Save-as-copy reconciliation.
- Editing videos or unsupported image types.

## User Experience

### Supported Device

1. User opens `Review Watermarks`.
2. App scans folders and shows candidate images exactly as today.
3. Current item shows:
   - original image
   - watermark score and metadata
   - `Keep`
   - `Mark for Trash`
   - `Open in Samsung Gallery`
4. User taps `Open in Samsung Gallery`.
5. App launches Samsung Gallery edit flow for the original image.
6. User manually chooses `Object Eraser` or another Samsung AI edit inside Gallery.
7. User saves changes to the original image and returns to the app.
8. App reloads the image by `id`.
9. If `size` or `dateModified` changed, the item is marked as `edited in Gallery` and the queue advances.
10. If nothing changed, the item stays pending and the app shows a message that no edit was detected.

### Unsupported Device Or Missing Capability

1. User opens `Review Watermarks`.
2. Candidate item still shows the Samsung edit action.
3. The action is disabled.
4. Helper text explains that Samsung Gallery AI editing is required.

## Functional Design

### Data Model

Add a small external-edit session model owned by the review layer:

- `imageId`
- `startedAt`
- `originalSize`
- `originalDateModified`

Add a result concept owned by the review layer:

- `UNCHANGED`
- `UPDATED`
- `MISSING`
- `FAILED_TO_OPEN`

Track edited items separately from kept items:

- `editedInGalleryIds`

This keeps user intent explicit:

- `keptImageIds` means the user chose to keep the original untouched.
- `editedInGalleryIds` means the user edited the original in Samsung Gallery.

### Components And Responsibilities

#### Watermark Detection

- Responsibility:
  Unchanged. Continue finding candidate images with probable watermarks.
- Notes:
  This still uses the existing detection path and cache.

#### Samsung Gallery Availability Checker

- Responsibility:
  Decide whether Samsung Gallery edit handoff is available on this device for the current image.
- Contract:
  Return either `available` or `unavailable(reason)`.
- Notes:
  This should be Samsung-specific, not a generic editor check.

#### Samsung Gallery Intent Factory

- Responsibility:
  Build the edit `Intent` for the current image.
- Contract:
  Input: `ImageItem`
  Output: Samsung Gallery edit `Intent`
- Required intent behavior:
  - action `Intent.ACTION_EDIT`
  - data `image.uri`
  - MIME type `image.mimeType`
  - read/write URI grants
  - constrained to Samsung Gallery package

#### Overlay Review ViewModel

- Responsibility:
  Orchestrate external edit sessions.
- Required behavior:
  - expose whether Samsung editing is available
  - expose a pending launch event for the screen
  - record the image metadata before launching
  - reconcile the image after the screen returns
  - mark the item edited only if metadata changed

#### Overlay Review Screen

- Responsibility:
  Launch Samsung Gallery and forward the result to the ViewModel.
- Required behavior:
  - use Activity Result APIs
  - launch only when the ViewModel emits a pending editor request
  - display disabled-state explanation when Samsung editing is unavailable

## Data Flow

### Launch

1. ViewModel reads the current item.
2. Availability checker validates Samsung Gallery support.
3. ViewModel snapshots `size` and `dateModified`.
4. ViewModel emits a pending external edit request.
5. Screen launches Samsung Gallery via Activity Result API.

### Return

1. Screen receives control back from the external activity.
2. Screen notifies the ViewModel.
3. ViewModel reloads the image using `imageRepository.getImageById(imageId)`.
4. ViewModel compares reloaded `size` and `dateModified` with the snapshot.
5. Outcome:
   - changed: mark as `editedInGalleryIds += imageId`, clear session, advance queue
   - unchanged: clear session, keep item current, show message
   - missing: remove or skip the item with a warning that the image no longer exists

## Edge Cases

### Samsung Gallery Not Installed Or Not Resolvable

- Behavior:
  Disable the action and show `Requires Samsung Gallery AI editing on a supported Samsung device.`

### User Opens Gallery But Cancels Without Saving

- Behavior:
  Treat as `UNCHANGED`.
- UI:
  Keep the same item selected and show `No changes were detected in Samsung Gallery.`

### User Edits And Saves The Original

- Behavior:
  Treat as `UPDATED`.
- UI:
  Advance to the next undecided item.

### User Deletes Or Moves The File In Gallery

- Behavior:
  Treat as `MISSING`.
- UI:
  Remove the stale item from the active queue and show a warning.

### Unsupported MIME Type

- Behavior:
  Keep existing watermark-review behavior for unsupported formats.
- UI:
  Samsung edit action stays disabled with the same Samsung requirement message.

### Multiple Rapid Taps

- Behavior:
  Only one external edit session may be active at a time.

## Removal Design

The following capabilities are intentionally retired:

- cleaning preview generation
- preview side-by-side UI
- keep-cleaned preview action
- delete-all-from-preview action
- ONNX cleaning model download/bundling
- any repository or use case dedicated only to cleaning/replacing images

Detection remains in scope. Cleaning does not.

## UI Decisions

- Rename the primary action from `Remove Watermark` to `Open in Samsung Gallery`.
- Add helper copy on disabled state:
  `Requires Samsung Gallery AI editing on a supported Samsung device.`
- Remove preview-related buttons:
  - `Keep Cleaned`
  - `Delete All`
  - `Skip`
- Add summary count:
  `Edited in Samsung Gallery`

## Verification Criteria

The redesign is correct only if all of the following are true:

1. No code path in watermark review generates a cleaned bitmap inside the app.
2. No code path overwrites the original image from an internally generated preview.
3. Watermark candidate detection still works as before.
4. On a supported Samsung device, tapping the Samsung action opens Samsung Gallery edit flow for the current image.
5. If the user returns without changing the image, the item remains pending.
6. If the user returns after saving edits to the original image, the item is marked edited and the queue advances.
7. On unsupported devices, the Samsung action is visible but disabled with explanatory copy.
8. Existing `Keep` and `Mark for Trash` flows still work.

## Testing Strategy

### Unit Tests

- Samsung Gallery availability checker:
  - available on supported Samsung resolver
  - unavailable on non-Samsung resolver
  - unavailable when nothing resolves
- Intent factory:
  - builds `ACTION_EDIT`
  - preserves image URI and MIME type
  - grants read/write permissions
  - targets Samsung Gallery package
- ViewModel:
  - emits editor launch request
  - marks item edited when metadata changes
  - keeps item pending when metadata does not change
  - handles missing image after return
  - prevents multiple simultaneous edit sessions

### UI Tests

- supported device state shows enabled Samsung action
- unsupported device state shows disabled Samsung action with helper text
- preview UI no longer appears anywhere in watermark review

### Regression Checks

- watermark detection scan still fills the queue
- trash flow still works
- pause/resume still works

## Implementation Notes

- Android supports editor handoff through `ACTION_EDIT`.
- Samsung documents `Object eraser` as a feature inside Galaxy Gallery.
- There is no public Samsung developer API in the researched sources for jumping directly into `Object Eraser`, so the user must choose that tool manually after Gallery opens.

Sources:

- Samsung Gallery/Object Eraser support:
  https://www.samsung.com/us/support/answer/ANS10003229/
- Android intent editing model:
  https://developer.android.com/guide/components/intents-filters
- Android `ACTION_EDIT` reference:
  https://developer.android.com/reference/kotlin/android/content/Intent#ACTION_EDIT
- Android Activity Result APIs:
  https://developer.android.com/training/basics/intents/result
- Android `FileProvider` reference:
  https://developer.android.com/reference/androidx/core/content/FileProvider
