mod discord;
mod scanner;
mod tray;

use std::collections::HashMap;
use discord::DiscordRpcState;
use serde::Serialize;

const DISCORD_CLIENT_ID: &str = "1300000000000000000"; // Replace with Swara Discord App ID

#[derive(Serialize)]
struct HttpResponsePayload {
    status: u16,
    body: String,
}

#[tauri::command]
async fn http_request(
    url: String,
    method: Option<String>,
    headers: Option<HashMap<String, String>>,
    body: Option<String>,
) -> Result<HttpResponsePayload, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let m = method.unwrap_or_else(|| "GET".to_string()).to_uppercase();
        let mut req = ureq::request(&m, &url);
        if let Some(hdrs) = headers {
            for (k, v) in hdrs {
                req = req.set(&k, &v);
            }
        }
        let resp = if let Some(b) = body {
            req.send_string(&b)
        } else {
            req.call()
        };
        match resp {
            Ok(r) => {
                let status = r.status();
                let text = r.into_string().map_err(|e| e.to_string())?;
                Ok(HttpResponsePayload { status, body: text })
            }
            Err(ureq::Error::Status(status, r)) => {
                let text = r.into_string().unwrap_or_default();
                Ok(HttpResponsePayload { status, body: text })
            }
            Err(e) => Err(e.to_string()),
        }
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
fn win_minimize(window: tauri::Window) {
    let _ = window.minimize();
}

#[tauri::command]
fn win_toggle_maximize(window: tauri::Window) {
    if window.is_maximized().unwrap_or(false) {
        let _ = window.unmaximize();
    } else {
        let _ = window.maximize();
    }
}

#[tauri::command]
fn win_close(window: tauri::Window) {
    let _ = window.close();
}

#[tauri::command]
fn win_start_dragging(window: tauri::Window) {
    let _ = window.start_dragging();
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(DiscordRpcState::new(DISCORD_CLIENT_ID))
        .setup(|app| {
            tray::setup_tray(app.handle())?;
            Ok(())
        })
        .on_window_event(|window, event| {
            tray::handle_minimize_to_tray(window, event);
        })
        .invoke_handler(tauri::generate_handler![
            scanner::scan_local_music,
            discord::update_discord_presence,
            discord::clear_discord_presence,
            http_request,
            win_minimize,
            win_toggle_maximize,
            win_close,
            win_start_dragging,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Swara desktop");
}
