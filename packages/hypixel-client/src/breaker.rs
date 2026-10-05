//! Minimal thread-safe circuit breaker (closed -> open -> half-open -> closed).

use std::time::{Duration, Instant};

use parking_lot::Mutex;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BreakerState {
    Closed,
    Open,
    HalfOpen,
}

impl BreakerState {
    pub fn as_str(self) -> &'static str {
        match self {
            BreakerState::Closed => "CLOSED",
            BreakerState::Open => "OPEN",
            BreakerState::HalfOpen => "HALF_OPEN",
        }
    }
}

#[derive(Debug)]
struct Inner {
    state: BreakerState,
    consecutive_failures: u32,
    opened_at: Option<Instant>,
    probe_in_flight: bool,
}

#[derive(Debug)]
pub struct CircuitBreaker {
    threshold: u32,
    cooldown: Duration,
    inner: Mutex<Inner>,
}

impl CircuitBreaker {
    pub fn new(threshold: u32, cooldown: Duration) -> Self {
        CircuitBreaker {
            threshold: threshold.max(1),
            cooldown,
            inner: Mutex::new(Inner { state: BreakerState::Closed, consecutive_failures: 0, opened_at: None, probe_in_flight: false }),
        }
    }

    /// Ok if a request may proceed, Err(time until retry) otherwise.
    pub fn allow(&self) -> Result<(), Duration> {
        let mut i = self.inner.lock();
        match i.state {
            BreakerState::Closed => Ok(()),
            BreakerState::Open => {
                let elapsed = i.opened_at.map(|t| t.elapsed()).unwrap_or(self.cooldown);
                if elapsed >= self.cooldown {
                    i.state = BreakerState::HalfOpen;
                    i.probe_in_flight = true;
                    Ok(())
                } else {
                    Err(self.cooldown - elapsed)
                }
            }
            BreakerState::HalfOpen => {
                if i.probe_in_flight {
                    Err(Duration::from_secs(1))
                } else {
                    i.probe_in_flight = true;
                    Ok(())
                }
            }
        }
    }

    pub fn record_success(&self) {
        let mut i = self.inner.lock();
        i.state = BreakerState::Closed;
        i.consecutive_failures = 0;
        i.opened_at = None;
        i.probe_in_flight = false;
    }

    pub fn record_failure(&self) {
        let mut i = self.inner.lock();
        i.consecutive_failures += 1;
        i.probe_in_flight = false;
        if i.state == BreakerState::HalfOpen || i.consecutive_failures >= self.threshold {
            i.state = BreakerState::Open;
            i.opened_at = Some(Instant::now());
        }
    }

    pub fn state(&self) -> BreakerState {
        let i = self.inner.lock();
        if i.state == BreakerState::Open && i.opened_at.map(|t| t.elapsed() >= self.cooldown).unwrap_or(false) {
            return BreakerState::HalfOpen;
        }
        i.state
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn opens_after_threshold_and_recovers() {
        let b = CircuitBreaker::new(2, Duration::from_millis(30));
        assert!(b.allow().is_ok());
        b.record_failure();
        assert_eq!(b.state(), BreakerState::Closed);
        b.record_failure();
        assert_eq!(b.state(), BreakerState::Open);
        assert!(b.allow().is_err());
        std::thread::sleep(Duration::from_millis(40));
        assert!(b.allow().is_ok()); // half-open probe
        assert!(b.allow().is_err()); // only one probe
        b.record_success();
        assert_eq!(b.state(), BreakerState::Closed);
    }
}
