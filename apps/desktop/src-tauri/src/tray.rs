use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    AppHandle, Emitter, Manager, Runtime, WindowEvent,
};

pub fn setup_tray<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<()> {
    let toggle_play = MenuItem::with_id(app, "tray_toggle_play", "Play / Pause", true, None::<&str>)?;
    let next_track = MenuItem::with_id(app, "tray_next", "Next Track", true, None::<&str>)?;
    let show_window = MenuItem::with_id(app, "tray_show", "Show Swara", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "tray_quit", "Quit", true, None::<&str>)?;

    let menu = Menu::with_items(app, &[&toggle_play, &next_track, &show_window, &quit])?;

    TrayIconBuilder::new()
        .menu(&menu)
        .tooltip("Swara")
        .on_menu_event(|app, event| match event.id.as_ref() {
            "tray_toggle_play" => {
                let _ = app.emit("swara://tray-toggle-play", ());
            }
            "tray_next" => {
                let _ = app.emit("swara://tray-next", ());
            }
            "tray_show" => {
                if let Some(win) = app.get_webview_window("main") {
                    let _ = win.show();
                    let _ = win.set_focus();
                }
            }
            "tray_quit" => {
                app.exit(0);
            }
            _ => {}
        })
        .build(app)?;

    Ok(())
}

/// Intercepts window close to minimize to the system tray instead of exiting.
pub fn handle_minimize_to_tray<R: Runtime>(window: &tauri::Window<R>, event: &WindowEvent) {
    if let WindowEvent::CloseRequested { api, .. } = event {
        api.prevent_close();
        let _ = window.hide();
    }
}
