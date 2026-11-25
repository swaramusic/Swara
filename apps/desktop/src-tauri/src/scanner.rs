use base64::{engine::general_purpose::STANDARD, Engine as _};
use lofty::file::AudioFile;
use lofty::prelude::{Accessor, TaggedFileExt};
use lofty::probe::Probe;
use serde::Serialize;
use std::path::Path;
use walkdir::WalkDir;

const SUPPORTED_EXTENSIONS: &[&str] = &["mp3", "flac", "wav", "opus", "ogg", "m4a", "aac"];

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalTrackMeta {
    pub id: String,
    pub local_path: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub duration_ms: u64,
    pub track_number: Option<u32>,
    pub artwork_data_uri: Option<String>,
}

fn is_audio_file(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|ext| SUPPORTED_EXTENSIONS.contains(&ext.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

#[tauri::command]
pub fn scan_local_music(directories: Vec<String>) -> Result<Vec<LocalTrackMeta>, String> {
    let mut tracks = Vec::new();

    for dir in directories {
        for entry in WalkDir::new(&dir).follow_links(true).into_iter().flatten() {
            let path = entry.path();
            if !path.is_file() || !is_audio_file(path) {
                continue;
            }

            let path_str = path.to_string_lossy().to_string();
            let fallback_title = path
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("Unknown Track")
                .to_string();

            let tagged = match Probe::open(path).and_then(|p| p.read()) {
                Ok(t) => t,
                Err(_) => {
                    tracks.push(LocalTrackMeta {
                        id: path_str.clone(),
                        local_path: path_str,
                        title: fallback_title,
                        artist: "Unknown Artist".to_string(),
                        album: None,
                        duration_ms: 0,
                        track_number: None,
                        artwork_data_uri: None,
                    });
                    continue;
                }
            };

            let duration_ms = tagged.properties().duration().as_millis() as u64;
            let tag = tagged.primary_tag().or_else(|| tagged.first_tag());

            let (title, artist, album, track_number, artwork_data_uri) = match tag {
                Some(t) => {
                    let art = t.pictures().first().map(|pic| {
                        let mime = pic
                            .mime_type()
                            .map(|m| m.as_str())
                            .unwrap_or("image/jpeg");
                        format!("data:{};base64,{}", mime, STANDARD.encode(pic.data()))
                    });
                    (
                        t.title().map(|s| s.to_string()).unwrap_or(fallback_title),
                        t.artist()
                            .map(|s| s.to_string())
                            .unwrap_or_else(|| "Unknown Artist".to_string()),
                        t.album().map(|s| s.to_string()),
                        t.track(),
                        art,
                    )
                }
                None => (fallback_title, "Unknown Artist".to_string(), None, None, None),
            };

            tracks.push(LocalTrackMeta {
                id: path_str.clone(),
                local_path: path_str,
                title,
                artist,
                album,
                duration_ms,
                track_number,
                artwork_data_uri,
            });
        }
    }

    Ok(tracks)
}
