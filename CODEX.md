# CODEX.md — Payment Order Platform
> Codex copy of project session guidance. Source instructions were read from `CLAUDE.md`. This file is maintained separately so `CLAUDE.md` remains unchanged.

---

## Baseline
- Domain: Razorpay-style order-to-payment pipeline
- Java: 21
- Spring Boot: 3.3.5
- Build: Maven multi-module
- Current focus: finish and stabilize test coverage without editing `CLAUDE.md`

---

## Working rules
- Do not modify `CLAUDE.md`.
- Read project guidance from `CLAUDE.md` at the start of a session.
- Prefer root-module Maven runs when shared modules affect downstream compilation.
- Treat existing user changes as authoritative unless they conflict directly with the requested task.

---

## Session notes
- 2026-03-26: Read `CLAUDE.md` and created this separate Codex copy.
- 2026-03-26: Completed gateway filter test rewrites to avoid inline Mockito dependency.
- 2026-03-26: Added explicit Mockito subclass mock-maker config for test modules so tests run on this JDK.
