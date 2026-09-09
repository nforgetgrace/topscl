# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-09-09
- Primary product surfaces: Korean Android app, onboarding, control dashboard, tuning, diagnostics, practice.
- Evidence reviewed: initially empty repository; user requests Galaxy top tap, broad compatibility, reliability and autonomous design. Tap Scroll Play listing is a functional reference, not a visual clone.

## Brand
- Name: 탑탭 · TopTap
- Personality: quiet, precise, friendly utility.
- Trust signals: truthful live connection state, explicit accessibility disclosure, no internet permission, no ads, no analytics.
- Avoid: fake reliability metrics, absolute compatibility claims, permission pressure, ornamental dashboards.

## Product goals
- Goals: one deliberate top tap starts a bounded return-to-top; clear setup and recovery; minimal idle work.
- Non-goals: arbitrary automation, reading/storing screen text, evading Android force-stop, copying another app's brand.
- Success signals: tested list/grid/web scrolling, cancellation on context change, reconnect after process death where OS allows, installable APK.

## Personas and jobs
- Primary personas: Galaxy users familiar with iPhone scroll-to-top.
- User jobs: return from a long feed, article or album without repeated swipes.
- Key contexts: daily browsing, one-handed use, battery management.

## Information architecture
- Primary navigation: home / settings / help via simple tabs.
- Core routes/screens: status and setup; touch and compatibility controls; reliability and privacy; long-list practice.
- Settings explain the default “빠르고 부드럽게” mode and cancellation. Help distinguishes recent-apps Clear all from explicit system Stop.
- Content hierarchy: service state → primary action → usage preview → tuning and health.

## Design principles
- Make the current service state truthful and actionable.
- The entire status bar accepts a single tap. The trigger renders no pixels or press feedback. Downward drags open the system shade.
- Tradeoffs: custom drawing for decorative art; real platform controls and text for accessible interaction.

## Visual language
- Color: warm ivory #F7F8FA, dark navy #17243A, cobalt #315BEE, muted slate #59677B, pale blue #E9EFFE, green #18724C.
- Typography: system sans-serif with Korean fallback; titles 28–32sp, section 18sp, body 14–16sp, labels 12sp.
- Spacing/layout rhythm: 4/8/12/16/24/32dp; centered content up to 600dp.
- Shape/radius/elevation: 20–24dp cards, 14dp controls, thin quiet borders; little shadow.
- Motion: minimal, never perpetual; native feedback; no decorative required animation.
- Imagery/iconography: original vector upward arrow meeting a top line; code-drawn phone illustration. No raster asset needed.

## Components
- Existing components to reuse: Android TextView, Button, Switch, SeekBar, AlertDialog, ScrollView.
- New/changed components: status card, setup cards, phone diagram, settings rows.
- Variants and states: connected/paused/setup/reconnect; selected/unselected; disabled.
- Token/component ownership: UI helper constants and styles.xml.

## Accessibility
- Target standard: readable scalable text and native accessibility semantics.
- Keyboard/focus behavior: native focus on every action.
- Contrast/readability: strong text contrast; never communicate status by color alone.
- Screen-reader semantics: descriptive labels; illustration decorative or one clear description.
- Reduced motion and sensory considerations: optional haptic, no flashing.

## Responsive behavior
- Supported breakpoints/devices: Android 8+ phones and tablets, portrait and landscape.
- Layout adaptations: scrollable content, maximum readable width, system bar/cutout insets, large font reflow. No touch-width, position, marker, or double-tap controls.
- Touch/hover differences: 48dp minimum ordinary controls; top trigger spans the real status bar and becomes non-touchable when that bar is hidden.

## Interaction states
- Loading: short connection wait state, never claim connection solely from a stored setting.
- Empty: no excluded apps → explain selection.
- Error: no supported target, interrupted run, setup unavailable → actionable Korean messages.
- Success: live connected state and real scroll outcomes only.
- Disabled: master pause removes overlay immediately.
- Offline/slow network: fully offline by design.

## Content voice
- Tone: concise everyday Korean.
- Terminology: 접근성, 터치 영역, 맨 위로, 일시정지, 절전 예외.
- Microcopy rules: explain why permission is needed before opening settings; do not say all apps guaranteed or never killed.

## Implementation constraints
- Framework/styling system: Java + Android platform UI, no third-party runtime libraries.
- Design-token constraints: use the color/spacing contract above.
- Performance constraints: event-driven service; bounded scans/runs, no wake lock, no polling while idle; a visible Android foreground session maintains the explicitly enabled feature after Clear all.
- Compatibility constraints: accessibility support varies; OEM status bar and battery handling require hardware validation.
- Test/screenshot expectations: emulator screenshots, lint/build, deterministic core tests and external fixture e2e.

## Open questions
- Galaxy model/One UI version requested asynchronously; use general Android 8+ defaults until known.
- Real Samsung Internet/Gallery and long-duration OEM background survival remain hardware checks.
