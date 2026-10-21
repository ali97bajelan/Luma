# Luma UI/UX Review — Fresh Emulator Pass

**Date:** 21 October 2026  
**Scope:** Current Luma app shell and core daily flows  
**Evidence:** Current source and live emulator screens for Today, Timeline, Habits, Weekly Review, More, and Quick Capture. The emulator was running the dark theme at 1080 × 2400.  
**Review boundary:** This review was prepared from the current app and the user-supplied design references. Earlier UI/UX review documents were not opened or used as evidence.

## Product read

Luma is a personal life journal and routine companion. Its core jobs are to capture a mood, journal entry, or memory; log a habit or complete a task; and revisit personal activity over time. The interface should feel calm and personal while keeping those frequent actions quick and progress easy to scan.

## What is working

- The five-item bottom navigation gives the main areas persistent, clear access.
- Today puts capture, habit check-ins, and due tasks close to the top of the page.
- The habit `+1` action is prominent and its goal count and progress track are readable.
- Quick Capture offers the three life-record types and task creation in one place.
- Timeline’s empty state explains how to find or add activity instead of leaving a blank screen.
- More uses descriptive labels and supporting copy, so its destinations are understandable without relying on icons.
- The existing green palette suits a reflective wellness and productivity product and already adapts to dark mode.

## Fresh findings

### 1. Today has competing large blocks

The date controls and three capture actions are followed by a large Habits card and a large Tasks card. On the inspected screen, the first habit card holds two rows with substantial vertical separation; the task card begins below the fold. The primary daily actions are present, but the page asks users to scan several heavy surfaces before they reach recent entries.

**Opportunity:** Keep capture in the first viewport, then give habits and tasks compact, consistently structured previews. Use a clear section hierarchy and let the row actions remain immediately available.

### 2. Habit progress is legible but the preview is oversized

Habit names, `0 of 10 times`, a full-width track, and `+1` actions all read clearly. In Today, however, the two rows sit far apart inside one tall surface, and the repeated “No check-in recorded” line takes as much attention as the progress value.

**Opportunity:** Use a tighter row pattern with the goal and progress together, reserve supporting copy for meaningful states, and keep the `+1` target comfortably tappable.

### 3. Timeline’s controls dominate the first view

Before the feed, Timeline shows Search, date navigation, an app-usage switch, timezone, item counts, and sort order. On an empty date, this pushes the useful empty-state guidance well below the heading and leaves a large quiet area beneath it.

**Opportunity:** Keep date and search easy to reach, group secondary controls into a compact toolbar or filter surface, and bring the empty or populated feed closer to the page title.

### 4. Weekly Review presents too much as one continuous report

The week selector is clear, but the overview combines entry counts, habit totals, mood, usage-import status, integration actions, and activity in one long surface. The information is useful, though the current treatment gives setup status similar weight to the personal summary.

**Opportunity:** Break the report into small, scannable sections. Lead with the period’s personal summary, then show habits and mood, with integration setup presented as a quieter supporting state.

### 5. Shared visual language is inconsistent across destinations

Today and Habits use large filled cards, Weekly Review uses a long report surface, and More uses outlined destination cards. The same app therefore shifts between several surface treatments without a clear reason. Quick Capture also uses a very tall sheet with large full-width choices.

**Opportunity:** Establish one shared surface and spacing system, with a small number of deliberate variants for summaries, actions, and navigation. Keep the large capture choices accessible while making the sheet more compact.

### 6. The visual identity could feel more personal

The current dark theme is coherent and readable, but the screens lean heavily on the same dark background, gray-green cards, and mint actions. The result feels functional, with few visual cues that distinguish reflection, progress, and successful capture.

**Opportunity:** Keep the calm green identity, add a restrained warm accent for reflection and milestones, and use subtle state changes to make completed actions feel acknowledged. Avoid decorative effects that compete with personal content.

## Proposed redesign direction

Refresh the existing app around a **calm daily companion** direction:

- Preserve the five main destinations, current workflows, and all existing data and actions.
- Use a consistent page rhythm, compact section headers, and a small set of reusable card and row patterns.
- Make Today the clearest daily overview: date context, capture, habits, tasks, then recent entries.
- Reduce Timeline’s control footprint and split Weekly Review into readable sections.
- Align More and Quick Capture with the same visual language.
- Retain the green foundation and light/dark theme support, with a restrained warm accent and more deliberate success feedback.
- Keep layouts readable with large text, RTL locales, and narrow screens; maintain at least 48 dp interactive targets.

This direction changes presentation and hierarchy while preserving product behavior. New product capabilities are outside this review.

## Suggested implementation order

1. Define shared page spacing, section headings, surface colors, and compact row/card patterns.
2. Apply the shared system to Today and Habits, where frequent daily actions benefit most.
3. Simplify Timeline’s first-view controls and empty-state presentation.
4. Restructure Weekly Review and align More and Quick Capture with the shared system.
5. Inspect the redesigned screens in the emulator in light and dark themes, with large text and Persian RTL.

## Open design choice

The recommended path is a focused visual refresh that keeps the current information architecture and functionality. A broader reorganization of the main destinations would increase scope and should be treated as a separate product decision.
