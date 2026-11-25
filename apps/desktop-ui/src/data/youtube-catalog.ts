import type { Track } from '@swara/core';

/**
 * Katalog awal lagu YouTube Music populer (videoId resmi YouTube)
 * agar aplikasi langsung berisi lagu dan siap diputar saat pertama kali dibuka.
 */
export const INITIAL_YOUTUBE_TRACKS: Track[] = [
  {
    id: 'jfKfPfyJRdk',
    providerId: 'youtube',
    title: 'lofi hip hop radio — beats to relax/study to',
    artists: [{ id: 'yt-lofigirl', providerId: 'youtube', name: 'Lofi Girl' }],
    durationMs: 0,
    artworkUrl: 'https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg',
  },
  {
    id: '4xDzrJKXOOY',
    providerId: 'youtube',
    title: 'synthwave radio — chill synth / retro beats',
    artists: [{ id: 'yt-lofigirl', providerId: 'youtube', name: 'Lofi Girl' }],
    durationMs: 0,
    artworkUrl: 'https://i.ytimg.com/vi/4xDzrJKXOOY/hqdefault.jpg',
  },
  {
    id: 'K4DyBUG242c',
    providerId: 'youtube',
    title: 'On & On (feat. Daniel Levi)',
    artists: [{ id: 'yt-cartoon', providerId: 'youtube', name: 'Cartoon, Jéja' }],
    durationMs: 208_000,
    artworkUrl: 'https://i.ytimg.com/vi/K4DyBUG242c/hqdefault.jpg',
  },
  {
    id: '3nQNiWdeH2Q',
    providerId: 'youtube',
    title: 'Heroes Tonight (feat. Johnning)',
    artists: [{ id: 'yt-janji', providerId: 'youtube', name: 'Janji' }],
    durationMs: 208_000,
    artworkUrl: 'https://i.ytimg.com/vi/3nQNiWdeH2Q/hqdefault.jpg',
  },
  {
    id: 'bM7SZ5SBzyY',
    providerId: 'youtube',
    title: 'Fade',
    artists: [{ id: 'yt-alanwalker', providerId: 'youtube', name: 'Alan Walker' }],
    durationMs: 262_000,
    artworkUrl: 'https://i.ytimg.com/vi/bM7SZ5SBzyY/hqdefault.jpg',
  },
  {
    id: 'AOeY-nDp7hI',
    providerId: 'youtube',
    title: 'Spectre',
    artists: [{ id: 'yt-alanwalker', providerId: 'youtube', name: 'Alan Walker' }],
    durationMs: 226_000,
    artworkUrl: 'https://i.ytimg.com/vi/AOeY-nDp7hI/hqdefault.jpg',
  },
];
