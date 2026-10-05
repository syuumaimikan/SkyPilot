//! Pure, deterministic calculation primitives used by every SkyPilot engine.
//!
//! Nothing in this crate performs I/O or holds game data: tables and constants are
//! passed in by callers (loaded from the data registry). Every function tolerates
//! hostile numeric input (NaN, infinities, negatives, empty samples) without panicking.

pub mod accessories;
pub mod expr;
pub mod farming;
pub mod fees;
pub mod leveling;
pub mod mining;
pub mod minion;
pub mod roi;
pub mod slayer;
pub mod stats;

/// Returns `x` when finite, otherwise `fallback`.
#[inline]
pub fn finite_or(x: f64, fallback: f64) -> f64 {
    if x.is_finite() { x } else { fallback }
}

/// Safe division: returns `None` when the result is not finite.
#[inline]
pub fn safe_div(a: f64, b: f64) -> Option<f64> {
    if b == 0.0 {
        return None;
    }
    let r = a / b;
    r.is_finite().then_some(r)
}
