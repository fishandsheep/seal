# Seal Design System

## 1. Intent

Seal is used by an operator at a workstation in a bright operations room, often while investigating an active database incident; a high-density light interface minimizes glare shifts, exposes state, and keeps evidence readable. Visual strategy is restrained: cool neutral surfaces with one marine-blue brand accent used only for action, selection, and live state.

## 2. Brand

- Product name: **Seal**.
- Mark: compact seal silhouette in a square field; use with wordmark in global navigation and alone for favicon-size contexts.
- Voice: concise, factual, non-alarmist. Name the condition and next safe action.
- Avoid decorative gradients, glass, novelty controls, and severity conveyed by color alone.

## 3. Color

All authored colors use OKLCH. Tokens are defined in `web/assets/css/input.css`.

| Role | Token | Value | Usage |
| --- | --- | --- | --- |
| Canvas | `--background` | `oklch(0.985 0.004 240)` | Main content |
| Surface | `--surface` | `oklch(1 0 0)` | Tables, forms, header |
| Subtle surface | `--muted` | `oklch(0.955 0.008 240)` | Sidebar, toolbar, hover |
| Ink | `--foreground` | `oklch(0.235 0.025 245)` | Primary text |
| Secondary ink | `--muted-foreground` | `oklch(0.43 0.025 245)` | Supporting text; minimum 4.5:1 |
| Border | `--border` | `oklch(0.875 0.012 240)` | Dividers and controls |
| Brand | `--primary` | `oklch(0.49 0.15 246)` | Primary action/current nav |
| Brand hover | `--primary-hover` | `oklch(0.42 0.145 246)` | Hover/active |
| Success | `--success` | `oklch(0.46 0.115 155)` | Healthy state + label/icon |
| Warning | `--warning` | `oklch(0.55 0.13 72)` | Blind/degraded + label/icon |
| Danger | `--danger` | `oklch(0.5 0.18 25)` | Severe/destructive + label/icon |

Focus rings use primary with a 2px outer offset. Selected table rows combine tint, weight, and `aria-selected`, never color alone.

## 4. Typography

- UI: native system UI sans-serif with `Noto Sans SC` fallback; no web-font runtime dependency.
- Code/data: `ui-monospace`, `SFMono-Regular`, `Consolas`, monospace.
- Fixed scale: 12px metadata, 13px dense table, 14px body/control, 16px section heading, 20px page heading, 24px setup/login heading.
- Headings use 600 weight and `text-wrap: balance`; body prose capped at 72ch. SQL may scroll horizontally and never wraps by default in dense tables.

## 5. Layout

- Desktop shell: 224px navigation rail, sticky 56px header, flexible content.
- Content max width is 1600px; monitoring table may use full width.
- Base spacing unit: 4px. Common gaps: 8, 12, 16, 24, 32px.
- At widths below 860px, navigation becomes horizontal/scrollable, summary bands wrap, forms become one column, and tables gain labeled compact rows or horizontal scrolling.
- Use panels only for real grouping. Separate adjacent data regions with dividers instead of nested cards.

## 6. Components

- Buttons: 36px default, 32px compact; 8px radius. Variants: primary, secondary, ghost, danger. Every state includes hover, focus, active, disabled, and busy.
- Inputs/selects: 36px height, 8px radius, persistent label, inline help/error, 44px minimum hit target when isolated.
- Tables: sticky header, 40px rows, tabular numbers, row action menu/button, meaningful empty and loading rows.
- Badges: compact pill only for status/severity; always include text and optional icon/dot.
- Alerts: full border or tinted background, icon + heading + remediation. No colored side stripe.
- Dialogs: native `<dialog>`, only for destructive confirmation or focused detail that cannot remain inline.
- SQL detail: monospace evidence block, copy button, redaction notice, risk rules followed by on-demand EXPLAIN.

## 7. Motion

State transitions run 150–200ms with `cubic-bezier(0.22, 1, 0.36, 1)`. Animate opacity and transform only for menus/dialogs; table updates do not animate position. Under `prefers-reduced-motion: reduce`, transitions are removed and smooth scrolling is disabled.

## 8. Content States

Every screen defines loading, empty, validation error, authorization error, degraded, and success feedback. Live monitoring always shows capture process, interface/ports, packet drops, parser drops, blind-flow count, and last error. Empty states describe why no data exists and offer one relevant next action.

## 9. Accessibility

- Semantic landmarks, one page `h1`, associated labels, table captions, and `aria-live` for capture/task status.
- Minimum 4.5:1 text contrast and 3:1 component/focus contrast.
- Focus is never removed; skip link targets main content.
- Risk, capture, and task status use text/icon/shape plus color.
- Native controls and dialog semantics preferred; all htmx swaps preserve or restore useful focus.
