//! Robust statistics for market data. All functions ignore non-finite and negative
//! samples (prices can never be negative) and return `None` for empty samples.

/// Finite, non-negative values sorted ascending.
pub fn clean_sorted(values: &[f64]) -> Vec<f64> {
    let mut v: Vec<f64> = values.iter().copied().filter(|x| x.is_finite() && *x >= 0.0).collect();
    v.sort_by(|a, b| a.total_cmp(b));
    v
}

pub fn mean(values: &[f64]) -> Option<f64> {
    let v = clean_sorted(values);
    if v.is_empty() {
        return None;
    }
    // Kahan-free but overflow-safe: divide first.
    let n = v.len() as f64;
    let m = v.iter().map(|x| x / n).sum::<f64>();
    m.is_finite().then_some(m)
}

/// Linear-interpolated percentile, `p` in 0..=1.
pub fn percentile_sorted(sorted: &[f64], p: f64) -> Option<f64> {
    if sorted.is_empty() || !p.is_finite() {
        return None;
    }
    let p = p.clamp(0.0, 1.0);
    if sorted.len() == 1 {
        return Some(sorted[0]);
    }
    let rank = p * (sorted.len() - 1) as f64;
    let lo = rank.floor() as usize;
    let hi = rank.ceil() as usize;
    let w = rank - lo as f64;
    Some(sorted[lo] * (1.0 - w) + sorted[hi] * w)
}

pub fn percentile(values: &[f64], p: f64) -> Option<f64> {
    percentile_sorted(&clean_sorted(values), p)
}

pub fn median(values: &[f64]) -> Option<f64> {
    percentile(values, 0.5)
}

/// Weighted median of `(value, weight)` pairs. Non-finite/negative values or weights are skipped.
pub fn weighted_median(pairs: &[(f64, f64)]) -> Option<f64> {
    let mut v: Vec<(f64, f64)> = pairs
        .iter()
        .copied()
        .filter(|(x, w)| x.is_finite() && *x >= 0.0 && w.is_finite() && *w > 0.0)
        .collect();
    if v.is_empty() {
        return None;
    }
    v.sort_by(|a, b| a.0.total_cmp(&b.0));
    let total: f64 = v.iter().map(|(_, w)| w).sum();
    if !total.is_finite() || total <= 0.0 {
        return None;
    }
    let half = total / 2.0;
    let mut acc = 0.0;
    for (x, w) in &v {
        acc += w;
        if acc >= half {
            return Some(*x);
        }
    }
    v.last().map(|(x, _)| *x)
}

/// Mean after removing `trim` fraction (0..0.5) from each tail.
pub fn trimmed_mean(values: &[f64], trim: f64) -> Option<f64> {
    let v = clean_sorted(values);
    if v.is_empty() {
        return None;
    }
    let trim = if trim.is_finite() { trim.clamp(0.0, 0.49) } else { 0.0 };
    let k = ((v.len() as f64) * trim).floor() as usize;
    let slice = &v[k..v.len() - k];
    if slice.is_empty() {
        return median(&v);
    }
    let n = slice.len() as f64;
    let m = slice.iter().map(|x| x / n).sum::<f64>();
    m.is_finite().then_some(m)
}

/// Median absolute deviation.
pub fn mad(values: &[f64]) -> Option<f64> {
    let v = clean_sorted(values);
    let med = percentile_sorted(&v, 0.5)?;
    let dev: Vec<f64> = v.iter().map(|x| (x - med).abs()).collect();
    median(&dev)
}

/// Robust z-score (0.6745 scales MAD to sigma for normal data). Returns 0 when MAD is 0.
pub fn robust_z(x: f64, med: f64, mad: f64) -> f64 {
    if !x.is_finite() || !med.is_finite() || !mad.is_finite() || mad <= 0.0 {
        return 0.0;
    }
    0.6745 * (x - med) / mad
}

/// Keep values whose robust z-score magnitude is <= threshold.
pub fn remove_outliers(values: &[f64], threshold: f64) -> Vec<f64> {
    let v = clean_sorted(values);
    let Some(med) = percentile_sorted(&v, 0.5) else { return v };
    let m = mad(&v).unwrap_or(0.0);
    if m <= 0.0 {
        // Degenerate spread: fall back to a ratio filter around the median.
        return v.into_iter().filter(|x| med <= 0.0 || (*x / med) < 10.0 && (*x / med) > 0.1).collect();
    }
    v.into_iter().filter(|x| robust_z(*x, med, m).abs() <= threshold).collect()
}

pub fn stddev(values: &[f64]) -> Option<f64> {
    let v = clean_sorted(values);
    if v.len() < 2 {
        return None;
    }
    let m = mean(&v)?;
    let n = (v.len() - 1) as f64;
    let var = v.iter().map(|x| ((x - m) * (x - m)) / n).sum::<f64>();
    let s = var.sqrt();
    s.is_finite().then_some(s)
}

/// Coefficient of variation (stddev / mean).
pub fn coefficient_of_variation(values: &[f64]) -> Option<f64> {
    let m = mean(values)?;
    if m <= 0.0 {
        return None;
    }
    let s = stddev(values)?;
    let cv = s / m;
    cv.is_finite().then_some(cv)
}

/// Exponentially weighted moving average over values in chronological order.
pub fn ewma(values: &[f64], alpha: f64) -> Option<f64> {
    let alpha = if alpha.is_finite() { alpha.clamp(0.0001, 1.0) } else { 0.3 };
    let mut acc: Option<f64> = None;
    for &x in values.iter().filter(|x| x.is_finite() && **x >= 0.0) {
        acc = Some(match acc {
            None => x,
            Some(prev) => alpha * x + (1.0 - alpha) * prev,
        });
    }
    acc
}

/// Relative change between the medians of the older and newer halves of a chronological series.
pub fn trend(chronological: &[f64]) -> Option<f64> {
    let v: Vec<f64> = chronological.iter().copied().filter(|x| x.is_finite() && *x > 0.0).collect();
    if v.len() < 4 {
        return None;
    }
    let mid = v.len() / 2;
    let old = median(&v[..mid])?;
    let new = median(&v[mid..])?;
    if old <= 0.0 {
        return None;
    }
    let t = (new - old) / old;
    t.is_finite().then_some(t)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn basic_stats() {
        let v = [1.0, 2.0, 3.0, 4.0, 100.0];
        assert_eq!(median(&v), Some(3.0));
        assert_eq!(percentile(&v, 0.0), Some(1.0));
        assert_eq!(percentile(&v, 1.0), Some(100.0));
        assert!((trimmed_mean(&v, 0.2).unwrap() - 3.0).abs() < 1e-9);
        assert_eq!(mad(&v), Some(1.0));
        assert_eq!(remove_outliers(&v, 3.5), vec![1.0, 2.0, 3.0, 4.0]);
    }

    #[test]
    fn weighted() {
        assert_eq!(weighted_median(&[(10.0, 1.0), (20.0, 5.0), (30.0, 1.0)]), Some(20.0));
        assert_eq!(weighted_median(&[(f64::NAN, 1.0)]), None);
    }

    #[test]
    fn hostile() {
        let v = [f64::NAN, f64::INFINITY, -5.0];
        assert_eq!(median(&v), None);
        assert_eq!(mean(&v), None);
        assert_eq!(stddev(&v), None);
        assert_eq!(trimmed_mean(&[], 0.1), None);
        assert_eq!(ewma(&v, 0.5), None);
    }
}
