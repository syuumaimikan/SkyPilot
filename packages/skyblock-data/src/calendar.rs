//! SkyBlock calendar conversion (real time <-> SkyBlock year/month/day).

use crate::curated::{CalendarConfig, CalendarEvent};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SkyBlockDate {
    pub year: i64,
    /// 1-based month (1 = Early Spring)
    pub month: u32,
    /// 1-based day
    pub day: u32,
}

impl CalendarConfig {
    fn year_ms(&self) -> i64 {
        self.day_ms * self.days_per_month as i64 * self.months.len() as i64
    }

    pub fn date_at(&self, now_ms: i64) -> SkyBlockDate {
        let elapsed = (now_ms - self.epoch_ms).max(0);
        let year_ms = self.year_ms().max(1);
        let month_ms = self.day_ms * self.days_per_month as i64;
        let year = elapsed / year_ms + 1;
        let in_year = elapsed % year_ms;
        let month = (in_year / month_ms) as u32 + 1;
        let day = ((in_year % month_ms) / self.day_ms) as u32 + 1;
        SkyBlockDate { year, month, day }
    }

    pub fn month_name(&self, month: u32) -> &str {
        self.months.get(month.saturating_sub(1) as usize).map(String::as_str).unwrap_or("?")
    }

    /// Real-time start (ms) of a SkyBlock date.
    pub fn to_ms(&self, date: SkyBlockDate) -> i64 {
        self.epoch_ms
            + (date.year - 1) * self.year_ms()
            + (date.month as i64 - 1) * self.day_ms * self.days_per_month as i64
            + (date.day as i64 - 1) * self.day_ms
    }

    /// (event, start_ms, end_ms, active_now) for the next occurrence of each event.
    pub fn upcoming(&self, now_ms: i64) -> Vec<(CalendarEvent, i64, i64, bool)> {
        let today = self.date_at(now_ms);
        let mut out = Vec::new();
        for e in &self.events {
            for year in [today.year, today.year + 1] {
                let start = self.to_ms(SkyBlockDate { year, month: e.start.month, day: e.start.day });
                let end = self.to_ms(SkyBlockDate { year, month: e.end.month, day: e.end.day }) + self.day_ms;
                if end > now_ms {
                    out.push((e.clone(), start, end, start <= now_ms));
                    break;
                }
            }
        }
        out.sort_by_key(|(_, s, _, _)| *s);
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cfg() -> CalendarConfig {
        CalendarConfig {
            epoch_ms: 1_560_275_700_000,
            day_ms: 1_200_000,
            days_per_month: 31,
            months: (1..=12).map(|i| format!("M{i}")).collect(),
            events: vec![],
        }
    }

    #[test]
    fn epoch_is_year_one_day_one() {
        let c = cfg();
        assert_eq!(c.date_at(1_560_275_700_000), SkyBlockDate { year: 1, month: 1, day: 1 });
        // Election endpoint snapshot (2026-10-05): election year 517 open -> current year 518, Late Spring.
        let d = c.date_at(1_791_167_709_531);
        assert_eq!(d.year, 518);
        assert_eq!(d.month, 3);
        let back = c.to_ms(d);
        assert!(back <= 1_791_167_709_531 && 1_791_167_709_531 - back < c.day_ms);
    }
}
