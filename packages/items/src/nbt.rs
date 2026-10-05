//! Minimal, defensive Java-edition NBT reader (big endian, modified UTF-8 strings).
//! Hypixel serialises inventories as gzipped NBT. The reader enforces depth and size
//! limits so a malformed blob from the network can never exhaust memory.

use std::collections::BTreeMap;

use serde_json::{Map, Value};
use thiserror::Error;

#[derive(Debug, Clone, PartialEq)]
pub enum Tag {
    End,
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    ByteArray(Vec<i8>),
    String(String),
    List(Vec<Tag>),
    Compound(BTreeMap<String, Tag>),
    IntArray(Vec<i32>),
    LongArray(Vec<i64>),
}

#[derive(Debug, Error, PartialEq)]
pub enum NbtError {
    #[error("unexpected end of data")]
    Eof,
    #[error("invalid tag type {0}")]
    InvalidType(u8),
    #[error("nesting too deep")]
    TooDeep,
    #[error("array/list too large ({0})")]
    TooLarge(usize),
    #[error("negative length")]
    NegativeLength,
}

const MAX_DEPTH: usize = 512;
const MAX_ELEMENTS: usize = 1 << 22;

struct Reader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> Result<&'a [u8], NbtError> {
        let end = self.pos.checked_add(n).ok_or(NbtError::Eof)?;
        if end > self.data.len() {
            return Err(NbtError::Eof);
        }
        let s = &self.data[self.pos..end];
        self.pos = end;
        Ok(s)
    }
    fn u8(&mut self) -> Result<u8, NbtError> {
        Ok(self.take(1)?[0])
    }
    fn i16(&mut self) -> Result<i16, NbtError> {
        let b = self.take(2)?;
        Ok(i16::from_be_bytes([b[0], b[1]]))
    }
    fn u16(&mut self) -> Result<u16, NbtError> {
        let b = self.take(2)?;
        Ok(u16::from_be_bytes([b[0], b[1]]))
    }
    fn i32(&mut self) -> Result<i32, NbtError> {
        let b = self.take(4)?;
        Ok(i32::from_be_bytes([b[0], b[1], b[2], b[3]]))
    }
    fn i64(&mut self) -> Result<i64, NbtError> {
        let b = self.take(8)?;
        let mut a = [0u8; 8];
        a.copy_from_slice(b);
        Ok(i64::from_be_bytes(a))
    }
    fn len(&mut self) -> Result<usize, NbtError> {
        let n = self.i32()?;
        if n < 0 {
            return Err(NbtError::NegativeLength);
        }
        let n = n as usize;
        if n > MAX_ELEMENTS {
            return Err(NbtError::TooLarge(n));
        }
        // each element needs at least one byte; reject lengths that cannot fit
        if n > self.data.len().saturating_sub(self.pos) * 8 + 8 {
            return Err(NbtError::Eof);
        }
        Ok(n)
    }
    fn string(&mut self) -> Result<String, NbtError> {
        let n = self.u16()? as usize;
        let bytes = self.take(n)?;
        Ok(decode_modified_utf8(bytes))
    }

    fn payload(&mut self, ty: u8, depth: usize) -> Result<Tag, NbtError> {
        if depth > MAX_DEPTH {
            return Err(NbtError::TooDeep);
        }
        Ok(match ty {
            0 => Tag::End,
            1 => Tag::Byte(self.u8()? as i8),
            2 => Tag::Short(self.i16()?),
            3 => Tag::Int(self.i32()?),
            4 => Tag::Long(self.i64()?),
            5 => Tag::Float(f32::from_bits(self.i32()? as u32)),
            6 => Tag::Double(f64::from_bits(self.i64()? as u64)),
            7 => {
                let n = self.len()?;
                Tag::ByteArray(self.take(n)?.iter().map(|b| *b as i8).collect())
            }
            8 => Tag::String(self.string()?),
            9 => {
                let elem = self.u8()?;
                let n = self.len()?;
                if elem == 0 && n > 0 {
                    // list of End with non-zero length is malformed; treat as empty
                    Tag::List(Vec::new())
                } else {
                    let mut v = Vec::with_capacity(n.min(4096));
                    for _ in 0..n {
                        v.push(self.payload(elem, depth + 1)?);
                    }
                    Tag::List(v)
                }
            }
            10 => {
                let mut m = BTreeMap::new();
                loop {
                    let t = self.u8()?;
                    if t == 0 {
                        break;
                    }
                    let name = self.string()?;
                    let value = self.payload(t, depth + 1)?;
                    m.insert(name, value);
                }
                Tag::Compound(m)
            }
            11 => {
                let n = self.len()?;
                let mut v = Vec::with_capacity(n.min(4096));
                for _ in 0..n {
                    v.push(self.i32()?);
                }
                Tag::IntArray(v)
            }
            12 => {
                let n = self.len()?;
                let mut v = Vec::with_capacity(n.min(4096));
                for _ in 0..n {
                    v.push(self.i64()?);
                }
                Tag::LongArray(v)
            }
            other => return Err(NbtError::InvalidType(other)),
        })
    }
}

/// Java "modified UTF-8": NUL encoded as C0 80 and supplementary characters as surrogate pairs.
pub fn decode_modified_utf8(bytes: &[u8]) -> String {
    if let Ok(s) = std::str::from_utf8(bytes) {
        return s.to_string();
    }
    let mut units: Vec<u16> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        let b = bytes[i];
        if b & 0x80 == 0 {
            units.push(b as u16);
            i += 1;
        } else if b & 0xE0 == 0xC0 && i + 1 < bytes.len() {
            units.push((((b & 0x1F) as u16) << 6) | (bytes[i + 1] & 0x3F) as u16);
            i += 2;
        } else if b & 0xF0 == 0xE0 && i + 2 < bytes.len() {
            units.push((((b & 0x0F) as u16) << 12) | (((bytes[i + 1] & 0x3F) as u16) << 6) | (bytes[i + 2] & 0x3F) as u16);
            i += 3;
        } else {
            units.push(0xFFFD);
            i += 1;
        }
    }
    String::from_utf16_lossy(&units)
}

/// Read a named root tag (the standard file/network layout). Returns (root name, tag).
pub fn read_named(data: &[u8]) -> Result<(String, Tag), NbtError> {
    let mut r = Reader { data, pos: 0 };
    let ty = r.u8()?;
    if ty == 0 {
        return Ok((String::new(), Tag::End));
    }
    let name = r.string()?;
    let tag = r.payload(ty, 0)?;
    Ok((name, tag))
}

impl Tag {
    pub fn get(&self, key: &str) -> Option<&Tag> {
        match self {
            Tag::Compound(m) => m.get(key),
            _ => None,
        }
    }

    /// Path lookup: `tag.path(&["tag", "ExtraAttributes", "id"])`.
    pub fn path(&self, keys: &[&str]) -> Option<&Tag> {
        keys.iter().try_fold(self, |t, k| t.get(k))
    }

    pub fn as_str(&self) -> Option<&str> {
        match self {
            Tag::String(s) => Some(s),
            _ => None,
        }
    }

    pub fn as_i64(&self) -> Option<i64> {
        match self {
            Tag::Byte(v) => Some(*v as i64),
            Tag::Short(v) => Some(*v as i64),
            Tag::Int(v) => Some(*v as i64),
            Tag::Long(v) => Some(*v),
            Tag::Float(v) if v.is_finite() => Some(*v as i64),
            Tag::Double(v) if v.is_finite() => Some(*v as i64),
            Tag::String(s) => s.trim().parse().ok(),
            _ => None,
        }
    }

    pub fn as_f64(&self) -> Option<f64> {
        match self {
            Tag::Float(v) => Some(*v as f64).filter(|x| x.is_finite()),
            Tag::Double(v) => Some(*v).filter(|x| x.is_finite()),
            Tag::String(s) => s.trim().parse().ok().filter(|x: &f64| x.is_finite()),
            other => other.as_i64().map(|v| v as f64),
        }
    }

    pub fn as_list(&self) -> &[Tag] {
        match self {
            Tag::List(v) => v,
            _ => &[],
        }
    }

    pub fn as_compound(&self) -> Option<&BTreeMap<String, Tag>> {
        match self {
            Tag::Compound(m) => Some(m),
            _ => None,
        }
    }

    /// Lossless-enough JSON view (used to forward custom data to the web and to mirror the
    /// mod's `minecraft:custom_data` JSON representation).
    pub fn to_json(&self) -> Value {
        match self {
            Tag::End => Value::Null,
            Tag::Byte(v) => Value::from(*v),
            Tag::Short(v) => Value::from(*v),
            Tag::Int(v) => Value::from(*v),
            Tag::Long(v) => Value::from(*v),
            Tag::Float(v) => serde_json::Number::from_f64(*v as f64).map(Value::Number).unwrap_or(Value::Null),
            Tag::Double(v) => serde_json::Number::from_f64(*v).map(Value::Number).unwrap_or(Value::Null),
            Tag::ByteArray(v) => Value::Array(v.iter().map(|x| Value::from(*x)).collect()),
            Tag::String(s) => Value::String(s.clone()),
            Tag::List(v) => Value::Array(v.iter().map(Tag::to_json).collect()),
            Tag::Compound(m) => Value::Object(m.iter().map(|(k, v)| (k.clone(), v.to_json())).collect::<Map<_, _>>()),
            Tag::IntArray(v) => Value::Array(v.iter().map(|x| Value::from(*x)).collect()),
            Tag::LongArray(v) => Value::Array(v.iter().map(|x| Value::from(*x)).collect()),
        }
    }

    /// Build a tag tree from JSON (inverse of `to_json`, numbers become Long/Double).
    pub fn from_json(value: &Value) -> Tag {
        match value {
            Value::Null => Tag::End,
            Value::Bool(b) => Tag::Byte(*b as i8),
            Value::Number(n) => {
                if let Some(i) = n.as_i64() {
                    Tag::Long(i)
                } else {
                    Tag::Double(n.as_f64().unwrap_or(0.0))
                }
            }
            Value::String(s) => Tag::String(s.clone()),
            Value::Array(a) => Tag::List(a.iter().map(Tag::from_json).collect()),
            Value::Object(o) => Tag::Compound(o.iter().map(|(k, v)| (k.clone(), Tag::from_json(v))).collect()),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write_string(out: &mut Vec<u8>, s: &str) {
        out.extend_from_slice(&(s.len() as u16).to_be_bytes());
        out.extend_from_slice(s.as_bytes());
    }

    #[test]
    fn roundtrip_simple_compound() {
        let mut b = vec![10];
        write_string(&mut b, "");
        b.push(8);
        write_string(&mut b, "id");
        write_string(&mut b, "HYPERION");
        b.push(3);
        write_string(&mut b, "n");
        b.extend_from_slice(&42i32.to_be_bytes());
        b.push(0);
        let (_, tag) = read_named(&b).unwrap();
        assert_eq!(tag.get("id").and_then(Tag::as_str), Some("HYPERION"));
        assert_eq!(tag.get("n").and_then(Tag::as_i64), Some(42));
    }

    #[test]
    fn rejects_truncated_and_huge() {
        assert_eq!(read_named(&[10, 0]), Err(NbtError::Eof));
        // list claiming 2^30 ints
        let mut b = vec![9, 0, 0, 3];
        b.extend_from_slice(&(1i32 << 30).to_be_bytes());
        assert!(read_named(&b).is_err());
        assert!(read_named(&[99, 0, 0]).is_err());
    }

    #[test]
    fn modified_utf8_nul() {
        assert_eq!(decode_modified_utf8(&[0x41, 0xC0, 0x80, 0x42]), "A\u{0}B");
    }
}
