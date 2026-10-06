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

    /// Next (or current, while it runs) start of a recurring event.
    pub fn next_recurring(&self, e: &crate::curated::RecurringEvent, now_ms: i64) -> (i64, i64) {
        let period = self.day_ms * e.every_days.max(1) as i64;
        let first = self.epoch_ms + self.day_ms * e.offset_days as i64;
        let duration = self.day_ms * e.duration_days as i64;
        let k = (now_ms - first).div_euclid(period);
        let start = first + k * period;
        let start = if now_ms < start + duration { start } else { start + period };
        (start, start + duration)
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
            recurring: vec![],
        }
    }

    #[test]
    fn recurring_dark_auction_and_jacob() {
        let c = cfg();
        let rec = |id: &str, offset: u32, duration: u32| crate::curated::RecurringEvent {
            id: id.into(),
            name: id.into(),
            every_days: 3,
            offset_days: offset,
            duration_days: duration,
            source: String::new(),
            checked: String::new(),
        };
        // 2019-06-11 18:05 UTC (10 minutes after the epoch at 17:55)
        let now = 1_560_275_700_000 + 600_000;
        let (da, _) = c.next_recurring(&rec("da", 0, 0), now);
        assert_eq!(da, 1_560_275_700_000 + 3_600_000, "next Dark Auction at 18:55");
        let (jacob, end) = c.next_recurring(&rec("jacob", 1, 1), now);
        assert_eq!(jacob, 1_560_275_700_000 + 1_200_000, "Jacob's contest at 18:15");
        assert_eq!(end - jacob, 1_200_000);
        // while it runs, the running contest is returned
        assert_eq!(c.next_recurring(&rec("jacob", 1, 1), jacob + 60_000).0, jacob);
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
