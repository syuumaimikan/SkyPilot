//! Wire types shared by every SkyPilot component.
//!
//! Every type here is serialised as camelCase JSON. TypeScript bindings are
//! generated into `packages/protocol/ts/src/generated` by `cargo test -p skypilot-protocol`
//! (ts-rs), and the Fabric mod mirrors the small subset it consumes (`mod_api`).

pub mod advisor;
pub mod auth;
pub mod common;
pub mod goals;
pub mod market;
pub mod mod_api;
pub mod profile;
pub mod translation;

pub use common::*;
