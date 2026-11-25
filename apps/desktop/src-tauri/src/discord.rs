use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use std::sync::Mutex;
use tauri::State;

pub struct DiscordRpcState(pub Mutex<Option<DiscordIpcClient>>);

impl DiscordRpcState {
    pub fn new(client_id: &str) -> Self {
        let client = DiscordIpcClient::new(client_id).ok().and_then(|mut c| {
            if c.connect().is_ok() {
                Some(c)
            } else {
                None
            }
        });
        Self(Mutex::new(client))
    }
}

#[tauri::command]
pub fn update_discord_presence(
    state: State<'_, DiscordRpcState>,
    title: String,
    artist: String,
    album: Option<String>,
    is_playing: bool,
) -> Result<(), String> {
    let mut guard = state.0.lock().map_err(|e| e.to_string())?;
    let Some(client) = guard.as_mut() else {
        return Ok(());
    };

    if !is_playing {
        let _ = client.clear_activity();
        return Ok(());
    }

    let details = format!("{} — {}", title, artist);
    let state_text = album.unwrap_or_else(|| "Listening on Swara".to_string());

    let payload = activity::Activity::new()
        .details(&details)
        .state(&state_text)
        .assets(activity::Assets::new().large_image("swara_logo").large_text("Swara"));

    client.set_activity(payload).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn clear_discord_presence(state: State<'_, DiscordRpcState>) -> Result<(), String> {
    let mut guard = state.0.lock().map_err(|e| e.to_string())?;
    if let Some(client) = guard.as_mut() {
        let _ = client.clear_activity();
    }
    Ok(())
}
