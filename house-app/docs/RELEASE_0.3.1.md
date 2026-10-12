# House Music v0.3.1 — native page swiping

The previous gesture handler waited for finger release, opened another Activity,
then played a window animation. Users reported that a rapid second swipe could be
missed. This build uses a single native ViewPager2 with Library, Now Playing and
Radio fragments.

- Pages track the finger and settle using the native distance/velocity behavior.
- Another swipe can interrupt settling; no navigation debounce or activity launch
  sits between gestures.
- Adjacent page views stay ready. Only the selected page is resumed, so existing
  page refresh/visit handling still follows actual navigation.
- Page-local control lookup, view cleanup, notification entry points, saved page
  selection and Back navigation are handled by the shared host.
- Text inputs, buttons and playback/seek controls retain their gestures. File and
  station rows use native touch cancellation when a horizontal page drag takes over.
- Existing playback service, server queue, renderer eligibility and audio timing
  are unchanged.

This build addresses the swipe follow-up in issue #9. Station ranking and stable
Radio-visit ordering in issue #12 remain planned separately.

## Verification

Regression coverage uses the actual ViewPager2: movement before release, reversing
a drag, another gesture during settling in both directions, vertical/control
gestures, and page restoration. Existing audio, radio and layout tests remain.
CI compiles both native audio ABIs and checks Android API 26 compatibility.

Physical acceptance is still required: verify smooth dragging and two rapid
swipes on the user's phones, list scrolling/refresh, seeking and text editing,
notification navigation, and uninterrupted playback. Automated tests cannot
establish frame pacing or perceived smoothness on those phones.
