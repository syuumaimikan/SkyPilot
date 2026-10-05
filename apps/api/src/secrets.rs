//! Encryption of per-user secrets (e.g. a player's own translation API key) at rest:
//! AES-256-GCM with a server key from `SKYPILOT_SECRET_KEY` (base64, 32 bytes) or, when
//! unset, a key generated once and kept in `data/cache/secret.key`. Sealed values are
//! `base64(nonce ‖ ciphertext)`; they are never sent back to clients.

use std::path::Path;

use aes_gcm::aead::{Aead, KeyInit, OsRng};
use aes_gcm::{AeadCore, Aes256Gcm, Key, Nonce};
use anyhow::Context;
use base64::Engine;

const B64: base64::engine::GeneralPurpose = base64::engine::general_purpose::STANDARD;

pub struct SecretBox {
    cipher: Aes256Gcm,
}

impl SecretBox {
    pub fn from_key(key: &[u8; 32]) -> SecretBox {
        SecretBox { cipher: Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key)) }
    }

    /// Key from the environment value, else from `<cache_dir>/secret.key` (created when missing).
    pub fn load(env_key: Option<&str>, cache_dir: &Path) -> anyhow::Result<SecretBox> {
        if let Some(k) = env_key.filter(|k| !k.trim().is_empty()) {
            let bytes = B64.decode(k.trim()).context("SKYPILOT_SECRET_KEY must be base64")?;
            let key: [u8; 32] = bytes.try_into().map_err(|_| anyhow::anyhow!("SKYPILOT_SECRET_KEY must decode to 32 bytes"))?;
            return Ok(SecretBox::from_key(&key));
        }
        let path = cache_dir.join("secret.key");
        if let Ok(text) = std::fs::read_to_string(&path) {
            let bytes = B64.decode(text.trim()).context("data/cache/secret.key is not base64")?;
            let key: [u8; 32] = bytes.try_into().map_err(|_| anyhow::anyhow!("data/cache/secret.key must hold 32 bytes"))?;
            return Ok(SecretBox::from_key(&key));
        }
        let key: [u8; 32] = Aes256Gcm::generate_key(&mut OsRng).into();
        std::fs::create_dir_all(cache_dir)?;
        std::fs::write(&path, B64.encode(key)).with_context(|| format!("writing {}", path.display()))?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600));
        }
        tracing::info!(path = %path.display(), "generated a server secret for encrypting user API keys");
        Ok(SecretBox::from_key(&key))
    }

    pub fn seal(&self, plain: &str) -> String {
        let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
        let ct = self.cipher.encrypt(&nonce, plain.as_bytes()).expect("AES-GCM encryption cannot fail for in-memory data");
        let mut out = nonce.to_vec();
        out.extend_from_slice(&ct);
        B64.encode(out)
    }

    /// None when the value was not sealed with this key (or was tampered with).
    pub fn open(&self, sealed: &str) -> Option<String> {
        let raw = B64.decode(sealed).ok()?;
        if raw.len() < 12 + 16 {
            return None;
        }
        let (nonce, ct) = raw.split_at(12);
        let plain = self.cipher.decrypt(Nonce::from_slice(nonce), ct).ok()?;
        String::from_utf8(plain).ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn seal_open_roundtrip_and_tamper() {
        let dir = std::env::temp_dir().join(format!("sp-secret-{}", std::process::id()));
        let a = SecretBox::load(None, &dir).unwrap();
        let sealed = a.seal("dk-123:fx");
        assert_ne!(sealed, "dk-123:fx");
        assert_eq!(a.open(&sealed).as_deref(), Some("dk-123:fx"));
        // the same key file is reused
        let b = SecretBox::load(None, &dir).unwrap();
        assert_eq!(b.open(&sealed).as_deref(), Some("dk-123:fx"));
        // tampering or another key fails closed
        let mut bytes = B64.decode(&sealed).unwrap();
        let last = bytes.len() - 1;
        bytes[last] ^= 1;
        assert!(a.open(&B64.encode(bytes)).is_none());
        let other = SecretBox::from_key(&[7u8; 32]);
        assert!(other.open(&sealed).is_none());
        let _ = std::fs::remove_dir_all(dir);
    }
}
