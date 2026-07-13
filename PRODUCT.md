# Product

## Register

product

## Users

Seal serves MySQL DBAs, SREs, security engineers, and backend engineers working from a Linux operations host. They need to observe visible MySQL traffic, triage risky SQL, compare recurring fingerprints, inspect execution plans, and turn a packet capture into an actionable report without shipping query text to another service.

## Product Purpose

Seal is a self-contained MySQL 8 traffic analysis console. It captures or uploads PCAP data, reconstructs supported plaintext MySQL commands, redacts sensitive values, aggregates query fingerprints, scores static risks, and runs an explicit on-demand EXPLAIN against a mapped database connection. Success means an operator can move from traffic to evidence and a safe remediation decision in one local workflow.

## Brand Personality

Forensic, composed, exact. Seal should feel like trusted diagnostic equipment: dense enough for experts, calm under incident pressure, and candid about blind spots or degraded capture state.

## Anti-references

- Not a decorative SaaS analytics landing page with oversized metrics, gradients, glass panels, or excessive cards.
- Not a terminal imitation that hides standard controls behind novelty.
- Not a generic admin template with a large empty sidebar and low-information dashboard tiles.
- Not a security product that dramatizes routine findings or implies certainty when traffic is encrypted, compressed, dropped, or incomplete.
- No SPA-only interaction model, CDN dependency, Alpine.js, daisyUI, or external SOAR surface.

## Design Principles

1. Evidence before verdict: place captured evidence, blind spots, and rule rationale beside every score.
2. Live state must be legible: running, stopped, degraded, dropped, and unparseable states remain visible without opening diagnostics.
3. Density with hierarchy: favor compact tables and progressive detail while keeping primary actions and severe risks easy to scan.
4. Safe by construction: never persist raw query values or prepared parameters; explain disabled actions in context.
5. Familiar operations workflow: standard navigation, forms, tables, dialogs, keyboard focus, and predictable htmx updates.

## Accessibility & Inclusion

Meet WCAG 2.2 AA. All workflows must be keyboard operable with visible focus, semantic labels, useful error text, and status announcements. Color never carries risk or process state alone. Support 200% zoom, narrow/mobile layouts, reduced motion, forced-colors/high-contrast modes, and readable Chinese and English technical strings.
