//! Windows auto-start configuration via HKCU\Software\Microsoft\Windows\CurrentVersion\Run.

use std::path::Path;

const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
const VALUE_NAME: &str = "SkyPilotServer";

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct AutostartStatus {
    pub supported: bool,
    pub enabled: bool,
    pub command: Option<String>,
    pub message: String,
}

pub fn is_supported() -> bool {
    cfg!(target_os = "windows")
}

pub fn is_enabled() -> bool {
    if !is_supported() {
        return false;
    }
    let output = std::process::Command::new("reg")
        .args(["query", RUN_KEY, "/v", VALUE_NAME])
        .output();
    match output {
        Ok(out) => out.status.success(),
        Err(_) => false,
    }
}

pub fn get_command() -> Option<String> {
    if !is_supported() {
        return None;
    }
    let output = std::process::Command::new("reg")
        .args(["query", RUN_KEY, "/v", VALUE_NAME])
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    let text = String::from_utf8_lossy(&output.stdout);
    for line in text.lines() {
        if line.contains(VALUE_NAME) {
            let parts: Vec<&str> = line.split("REG_SZ").collect();
            if parts.len() >= 2 {
                return Some(parts[1].trim().to_string());
            }
        }
    }
    None
}

pub fn status() -> AutostartStatus {
    let supported = is_supported();
    let enabled = is_enabled();
    let command = get_command();
    let message = if !supported {
        "Auto-start is only supported on Windows.".into()
    } else if enabled {
        "SkyPilot server is configured to start automatically on Windows login.".into()
    } else {
        "Auto-start is currently disabled.".into()
    };
    AutostartStatus {
        supported,
        enabled,
        command,
        message,
    }
}

pub fn set_enabled(enabled: bool, root: &Path) -> anyhow::Result<AutostartStatus> {
    if !is_supported() {
        anyhow::bail!("Auto-start is only supported on Windows.");
    }

    if enabled {
        let script_path = root.join("scripts").join("server.ps1");
        let command_str = if script_path.exists() {
            format!(
                r#"powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File "{}" start"#,
                script_path.display()
            )
        } else {
            let exe = std::env::current_exe()?;
            format!(r#""{}""#, exe.display())
        };

        let status = std::process::Command::new("reg")
            .args(["add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", &command_str, "/f"])
            .status()?;

        if !status.success() {
            anyhow::bail!("reg add failed with exit code: {:?}", status.code());
        }
    } else {
        let _ = std::process::Command::new("reg")
            .args(["delete", RUN_KEY, "/v", VALUE_NAME, "/f"])
            .status();
    }

    Ok(self::status())
}

