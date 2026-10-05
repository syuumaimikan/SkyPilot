//! SkyBlock item decoding for SkyPilot.
//!
//! * [`nbt`] – defensive NBT reader.
//! * [`item`] – extracts SkyBlock data from legacy, hybrid (API) and 26.2 data-component items.
//! * [`fingerprint`] – normalised value-relevant modifiers and market keys.
//! * [`text`] – formatting codes, rarity and lore stat parsing.

pub mod fingerprint;
pub mod item;
pub mod nbt;
pub mod text;

pub use fingerprint::{GemSlot, ItemFingerprint, PetFingerprint};
pub use item::{DecodeError, ItemFormat, ParsedItem, PetInfo, decode_blob, decode_inventory, from_client_json, parse_item_tag};
pub use nbt::Tag;
pub use text::Rarity;
