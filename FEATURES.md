# Features

Living list of what ships, what is on a side branch, and what is on the
roadmap. The README only carries a top-level checklist; this file is the
detailed one and is meant to be edited as decisions are made.

Legend: `[x]` shipped on `master` · `[~]` on a side branch, not yet merged ·
`[ ]` planned · `[?]` not committed to (idea / under discussion).

---

## Shipped — UI components

- [x] **Text** — static and reactive (`Supplier<String>`)
- [x] **Button** — click handler in `Style`
- [x] **Image** — texture by `Identifier`, optional explicit texture size
- [x] **Row / Column** — flex-style containers, lambda or vararg children
- [x] **VScroll / HScroll** — scroll containers
- [x] **TextField** — `State<String>` bidir, placeholder, filter, max length, onSubmit, blinking caret
- [x] **TextArea** — multi-line, Tab cycle focus, filter / max length
- [x] **Pagination** — `State<Integer>` page, sibling count, optional click handler
- [x] **ProgressBar** — `State<Double>` and `DoubleSupplier` overloads, label format
- [x] **Slider** — int / double / float / long / short, vertical flag, keyboard support, hover highlight, rounded corners
- [x] **Accordion** — multi-open and single-open variants, header style, optional `State<Set<Integer>>` / `State<Integer>`
- [x] **ComboBox** — searchable popup
- [x] **SelectList** — single / multi select, scrollable
- [x] **Calendar** — date picker, custom day style
- [x] **ColorPicker** — square + hue strip, optional alpha, configurable pad size
- [x] **Skeleton** — placeholder with shimmer animation
- [x] **Spinner** — circular dot rotation, configurable size / color / period
- [x] **Tooltip** — hover-delayed popup, multi-line, edge-flip, custom style
- [x] **Toast** — global stack (info/success/warning/error/custom), slide-in / fade-out
- [x] **PlayerRender** — wraps `InventoryScreen.drawEntity`, cursor-tracked rotation or fixed front view, supplier-based for live entity swap
- [x] **Chart** — LineChart / BarChart / PieChart, multi-series, reactive suppliers, hover tooltip, configurable axis / legend / value formatter

## Shipped — animation

- [x] **Tween** — easing, duration, repeat, onComplete
- [x] **Spring** — default / snappy / bouncy / strong presets, physics-based
- [x] **ColorTween** — ARGB interpolation
- [x] **FadeIn / FadeOut**
- [x] **SlideIn** (Direction)
- [x] **Loop / Yoyo** — animation modifiers

## Shipped — system / primitives

- [x] **State&lt;T&gt;** — reactive primitive with `map`, `combine`, `bindBidirectional`, `dispose` (memory-leak-safe)
- [x] **Subscription** — auto-disposed at component detach
- [x] **Focus management** — `requestFocus`, Tab cycling, key/char dispatch
- [x] **Popup click handlers** — outside-click intercept for combo boxes / pickers
- [x] **DynamicComponent** — re-render on state change without rebuilding the tree
- [x] **AnimatedComponent** — driven by tween / spring
- [x] **Style.borderRadius** — wired through every component that paints a rect

## Shipped — tooling

- [x] **`/demomenu`** — opens the tooltip demo
- [x] **`/demomenu playerrender`** — opens the player render demo
- [x] **`/demomenu chart`** — opens the chart demo
- [x] **DebugOverlay** — bounds + names of all components in tree

---

## On a side branch (not yet merged)

- [~] **Video** — `feature/video-player`, kept as a checkpoint
  - Phase 1 — `VideoPlayer` (FFmpeg / JavaCPP) + `VideoComponent`
  - Phase 1.5 — async open with skeleton placeholder
  - Phase 2.1 — audio decode via OpenAL
  - Phase 2.2 — A/V sync via audio master clock
  - Phase 3a — loop
  - Phase 3b — seek (`-5s` / `+5s` / restart, refresh while paused, `lastPublishedPtsNanos` for reliable currentTime)
  - Phase 3c — perf (native memcpy via access widener `NativeImage.pointer`)
  - Phase 3d — hardware decode probe (deferred, low priority)

---

## Roadmap — selected priorities

User-picked next items, in priority order:

- [ ] **Gradient paints** — `Style.background(Paint)` / `textFill(Paint)` with `Paint.LinearGradient` (angle + multi-stop), `Paint.RadialGradient`, `Paint.ConicGradient`. Backwards-compatible: `backgroundColor(int)` keeps working. Per-glyph color sampling for gradient text, with `bold` rendered via vanilla double-draw.
- [ ] **Custom fonts** — `Style.font(Identifier)` forwarding to Minecraft's resource-pack font system (TTF supported natively by MC). API only: the user supplies the TTF and the `assets/<modid>/font/X.json` provider in their own resource pack.
- [ ] **Markdown renderer** — basic CommonMark subset (headings, bold/italic, links, code, lists, hr). Use case: changelogs, in-game help.
- [ ] **Virtualized list** — render only the rows in the viewport; works with `Supplier<Integer> rowCount` + `IntFunction<UIComponent> rowFactory`. Required once lists exceed a few hundred items.
- [ ] **In-game UI editor** — drag-and-drop screen designer that emits `Components.X(...)` source. Big chunk of work but very high payoff for downstream modders.
- [ ] **Devmode** — hot-reload screens, perf overlay, component inspector (React DevTools-ish). Pairs naturally with the editor above.

## Roadmap — other UI ideas (under discussion)

- [?] **Modal / Dialog** — backdrop, focus trap, ESC-to-close. Currently simulated by stacking containers; a first-class component would simplify a lot.
- [?] **Drag &amp; drop** — drag source, drop target, ghost preview. Useful for inventory-like screens, list reordering.
- [?] **Context menu** — right-click / long-press popover, sub-menus.
- [?] **Tabs** — currently emulated with `DynamicComponent` + `State<Integer>`.
- [?] **Tree view** — collapsible hierarchy, selection. File-explorer style.
- [?] **Form validation** — chainable validators on a `State<T>`, error binding.
- [?] **Theme provider** — light / dark / custom palette injected via context.

## Roadmap — non-UI

- [ ] **Channel communication** — typed client↔server networking (the only non-UI item already on the README).
- [?] **Config system** — JSON / TOML auto-save, hot reload in dev, versioned migrations. Used by every mod.
- [?] **Persistent storage** — per-world / per-player KV wrapper (NBT or JSON), typed.
- [?] **Command framework** — DSL on top of Brigadier, typed parsing, autocompletion.
- [?] **Event bus** — typed publish / subscribe, `State<T>` bridge.
- [?] **Tick scheduler** — `runIn(20).cancel()`, `runEvery(...)`, async-to-main hop.
- [?] **HTTP client** — wrapper used internally by the video player; could be exposed.
- [?] **Asset loader** — dynamic textures / sounds from URL with disk cache.
- [?] **Permission system** — feature-level permissions on top of op-levels.

## Roadmap — animation

- [?] **Irregular family** (light / heavy) — spec to be clarified before implementation.

---

## Notes

- This file is hand-maintained. When a feature ships on master, move it
  from a roadmap section to the matching shipped section and check it off
  in `README.md`'s top-level list.
- The `feature/*` branches are kept around as recovery points; they are
  not stale by default.
