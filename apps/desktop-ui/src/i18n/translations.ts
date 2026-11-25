export type AppLanguage = string;
export type AppCountry = string;

export const SUPPORTED_LANGUAGES: Array<{ code: AppLanguage; label: string }> = [
  { code: 'id', label: 'Bahasa Indonesia' },
  { code: 'en', label: 'English' },
  { code: 'ms', label: 'Bahasa Melayu' },
  { code: 'ja', label: '日本語 (Japanese)' },
  { code: 'ko', label: '한국어 (Korean)' },
  { code: 'zh-CN', label: '简体中文 (Chinese Simplified)' },
  { code: 'zh-TW', label: '繁體中文 (Chinese Traditional)' },
  { code: 'th', label: 'ไทย (Thai)' },
  { code: 'vi', label: 'Tiếng Việt (Vietnamese)' },
  { code: 'fil', label: 'Filipino' },
  { code: 'hi', label: 'हिन्दी (Hindi)' },
  { code: 'ar', label: 'العربية (Arabic)' },
  { code: 'es', label: 'Español (Spanish)' },
  { code: 'fr', label: 'Français (French)' },
  { code: 'de', label: 'Deutsch (German)' },
  { code: 'pt', label: 'Português (Portuguese)' },
  { code: 'it', label: 'Italiano (Italian)' },
  { code: 'nl', label: 'Nederlands (Dutch)' },
  { code: 'ru', label: 'Русский (Russian)' },
  { code: 'tr', label: 'Türkçe (Turkish)' },
  { code: 'pl', label: 'Polski (Polish)' },
];

export const SUPPORTED_COUNTRIES: Array<{ code: AppCountry; label: string }> = [
  { code: 'ID', label: 'Indonesia' },
  { code: 'MY', label: 'Malaysia' },
  { code: 'SG', label: 'Singapore' },
  { code: 'TH', label: 'Thailand' },
  { code: 'VN', label: 'Vietnam' },
  { code: 'PH', label: 'Philippines' },
  { code: 'JP', label: 'Japan (日本)' },
  { code: 'KR', label: 'South Korea (한국)' },
  { code: 'TW', label: 'Taiwan' },
  { code: 'HK', label: 'Hong Kong' },
  { code: 'IN', label: 'India' },
  { code: 'US', label: 'United States' },
  { code: 'GB', label: 'United Kingdom' },
  { code: 'CA', label: 'Canada' },
  { code: 'AU', label: 'Australia' },
  { code: 'NZ', label: 'New Zealand' },
  { code: 'DE', label: 'Germany' },
  { code: 'FR', label: 'France' },
  { code: 'ES', label: 'Spain' },
  { code: 'IT', label: 'Italy' },
  { code: 'PT', label: 'Portugal' },
  { code: 'NL', label: 'Netherlands' },
  { code: 'BE', label: 'Belgium' },
  { code: 'CH', label: 'Switzerland' },
  { code: 'AT', label: 'Austria' },
  { code: 'SE', label: 'Sweden' },
  { code: 'NO', label: 'Norway' },
  { code: 'DK', label: 'Denmark' },
  { code: 'FI', label: 'Finland' },
  { code: 'IE', label: 'Ireland' },
  { code: 'PL', label: 'Poland' },
  { code: 'CZ', label: 'Czechia' },
  { code: 'HU', label: 'Hungary' },
  { code: 'RO', label: 'Romania' },
  { code: 'GR', label: 'Greece' },
  { code: 'UA', label: 'Ukraine' },
  { code: 'RU', label: 'Russia' },
  { code: 'TR', label: 'Turkey' },
  { code: 'SA', label: 'Saudi Arabia' },
  { code: 'AE', label: 'United Arab Emirates' },
  { code: 'EG', label: 'Egypt' },
  { code: 'IL', label: 'Israel' },
  { code: 'ZA', label: 'South Africa' },
  { code: 'NG', label: 'Nigeria' },
  { code: 'KE', label: 'Kenya' },
  { code: 'BR', label: 'Brazil' },
  { code: 'MX', label: 'Mexico' },
  { code: 'AR', label: 'Argentina' },
  { code: 'CO', label: 'Colombia' },
  { code: 'CL', label: 'Chile' },
  { code: 'PE', label: 'Peru' },
];

export interface UIStrings {
  nav: {
    home: string;
    search: string;
    explore: string;
    library: string;
    you: string;
    collection: string;
    liked: string;
    recent: string;
    playlists: string;
    local: string;
    offline: string;
    stats: string;
    settings: string;
  };
  searchPlaceholder: string;
  searching: string;
  searchResultsFor: string;
  noSearchResults: string;
  localFilesBtn: string;
  chooseAudioFiles: string;
  noLocalFiles: string;
  greetingMorning: string;
  greetingAfternoon: string;
  greetingEvening: string;
  greetingNight: string;
  readyToPlay: string;
  playedThisSession: string;
  continueListening: string;
  topPick: string;
  play: string;
  playAll: string;
  recentlyPlayed: string;
  noRecentTracks: string;
  trendingForYou: string;
  quickPicks: string;
  newReleases: string;
  moodsAndGenres: string;
  likedSongs: string;
  noLikedSongs: string;
  playlistNamePlaceholder: string;
  create: string;
  noPlaylists: string;
  songsCount: string;
  selectSongToStart: string;
  queue: string;
  recentlyPlayedTab: string;
  nowPlaying: string;
  nextUp: string;
  queueEmpty: string;
  noNextTracks: string;
  lyrics: string;
  lyricsFaster: string;
  lyricsSlower: string;
  noLyricsAvailable: string;
  settingsTitle: string;
  tabAppearance: string;
  tabPlayer: string;
  tabRegion: string;
  tabStorage: string;
  tabShortcuts: string;
  languageLabel: string;
  languageDesc: string;
  countryLabel: string;
  countryDesc: string;
}

const STRINGS: Record<string, UIStrings> = {
  id: {
    nav: {
      home: 'Beranda',
      search: 'Cari',
      explore: 'Jelajahi',
      library: 'Pustaka',
      you: 'Anda',
      collection: 'Koleksi',
      liked: 'Disukai',
      recent: 'Terakhir Diputar',
      playlists: 'Playlist',
      local: 'File Lokal',
      offline: 'Unduhan',
      stats: 'Statistik',
      settings: 'Pengaturan',
    },
    searchPlaceholder: 'Cari lagu, album, atau artis...',
    searching: 'Mencari...',
    searchResultsFor: 'Hasil untuk',
    noSearchResults: 'Tidak ada lagu yang cocok.',
    localFilesBtn: 'File Lokal',
    chooseAudioFiles: 'Pilih File Audio',
    noLocalFiles: 'Belum ada file lokal. Klik "Pilih File Audio" untuk memutar MP3/FLAC/WAV.',
    greetingMorning: 'Selamat pagi',
    greetingAfternoon: 'Selamat siang',
    greetingEvening: 'Selamat sore',
    greetingNight: 'Selamat malam',
    readyToPlay: 'Siap memutar',
    playedThisSession: 'lagu diputar sesi ini',
    continueListening: 'Lanjutkan Mendengar',
    topPick: 'Pilihan Teratas',
    play: 'Putar',
    playAll: 'Putar semua',
    recentlyPlayed: 'Terakhir Diputar',
    noRecentTracks: 'Belum ada riwayat lagu yang diputar.',
    trendingForYou: 'Trending Untukmu',
    quickPicks: 'Pilihan Cepat',
    newReleases: 'Rilis Baru',
    moodsAndGenres: 'Suasana & Genre',
    likedSongs: 'Lagu Disukai',
    noLikedSongs: 'Belum ada lagu disukai. Arahkan kursor ke lagu lalu klik ikon hati.',
    playlistNamePlaceholder: 'Nama playlist baru...',
    create: 'Buat',
    noPlaylists: 'Belum ada playlist. Buat playlist di atas atau klik ikon tambah pada lagu.',
    songsCount: 'lagu',
    selectSongToStart: 'Pilih lagu untuk mulai',
    queue: 'Antrean',
    recentlyPlayedTab: 'Baru diputar',
    nowPlaying: 'Sedang diputar',
    nextUp: 'Berikutnya',
    queueEmpty: 'Antrean kosong.',
    noNextTracks: 'Tidak ada lagu berikutnya di antrean.',
    lyrics: 'Lirik',
    lyricsFaster: 'Lirik lebih cepat',
    lyricsSlower: 'Lirik lebih lambat',
    noLyricsAvailable: 'Lirik belum tersedia untuk lagu ini.',
    settingsTitle: 'Pengaturan',
    tabAppearance: 'Tampilan',
    tabPlayer: 'Pemutar & EQ',
    tabRegion: 'Bahasa & Negara',
    tabStorage: 'Penyimpanan',
    tabShortcuts: 'Pintasan',
    languageLabel: 'Bahasa Antarmuka',
    languageDesc: 'Mengubah bahasa teks menu dan rekomendasi konten',
    countryLabel: 'Negara / Wilayah Beranda',
    countryDesc: 'Menyesuaikan lagu Trending, Rilis Baru, dan kurasi lokal',
  },
  en: {
    nav: {
      home: 'Home',
      search: 'Search',
      explore: 'Explore',
      library: 'Library',
      you: 'You',
      collection: 'Library',
      liked: 'Liked Songs',
      recent: 'Recently Played',
      playlists: 'Playlists',
      local: 'Local Files',
      offline: 'Downloads',
      stats: 'Stats',
      settings: 'Settings',
    },
    searchPlaceholder: 'Search songs, albums, or artists...',
    searching: 'Searching...',
    searchResultsFor: 'Results for',
    noSearchResults: 'No matching tracks found.',
    localFilesBtn: 'Local Files',
    chooseAudioFiles: 'Choose Audio Files',
    noLocalFiles: 'No local files yet. Click "Choose Audio Files" to play MP3/FLAC/WAV.',
    greetingMorning: 'Good morning',
    greetingAfternoon: 'Good afternoon',
    greetingEvening: 'Good evening',
    greetingNight: 'Good night',
    readyToPlay: 'Ready to play',
    playedThisSession: 'tracks played this session',
    continueListening: 'Continue Listening',
    topPick: 'Top Pick',
    play: 'Play',
    playAll: 'Play all',
    recentlyPlayed: 'Recently Played',
    noRecentTracks: 'No recently played tracks yet.',
    trendingForYou: 'Trending For You',
    quickPicks: 'Quick Picks',
    newReleases: 'New Releases',
    moodsAndGenres: 'Moods & Genres',
    likedSongs: 'Liked Songs',
    noLikedSongs: 'No liked songs yet. Hover over any track and click the heart icon.',
    playlistNamePlaceholder: 'New playlist name...',
    create: 'Create',
    noPlaylists: 'No playlists yet. Create one above or click the add icon on any track.',
    songsCount: 'songs',
    selectSongToStart: 'Select a track to play',
    queue: 'Queue',
    recentlyPlayedTab: 'Recently played',
    nowPlaying: 'Now playing',
    nextUp: 'Next up',
    queueEmpty: 'Queue is empty.',
    noNextTracks: 'No upcoming tracks in the queue.',
    lyrics: 'Lyrics',
    lyricsFaster: 'Faster',
    lyricsSlower: 'Slower',
    noLyricsAvailable: 'No lyrics available for this track.',
    settingsTitle: 'Settings',
    tabAppearance: 'Appearance',
    tabPlayer: 'Player & EQ',
    tabRegion: 'Language & Country',
    tabStorage: 'Storage',
    tabShortcuts: 'Shortcuts',
    languageLabel: 'Display Language',
    languageDesc: 'Changes app menu language and content localization',
    countryLabel: 'Home Country / Region',
    countryDesc: 'Tailors Trending, New Releases, and regional mixes on Home',
  },
  ja: {
    nav: {
      home: 'ホーム',
      search: '検索',
      explore: '探索',
      library: 'ライブラリ',
      you: 'あなた',
      collection: 'ライブラリ',
      liked: 'お気に入り',
      recent: '最近再生した曲',
      playlists: 'プレイリスト',
      local: 'ローカルファイル',
      offline: 'ダウンロード',
      stats: '統計',
      settings: '設定',
    },
    searchPlaceholder: '曲、アルバム、アーティストを検索...',
    searching: '検索中...',
    searchResultsFor: '検索結果',
    noSearchResults: '一致する曲が見つかりません。',
    localFilesBtn: 'ローカル',
    chooseAudioFiles: '音声ファイルを選択',
    noLocalFiles: 'ローカルファイルがありません。',
    greetingMorning: 'おはようございます',
    greetingAfternoon: 'こんにちは',
    greetingEvening: 'こんばんは',
    greetingNight: 'こんばんは',
    readyToPlay: '再生準備完了',
    playedThisSession: '曲を再生しました',
    continueListening: '再生を再開',
    topPick: 'おすすめ',
    play: '再生',
    playAll: 'すべて再生',
    recentlyPlayed: '最近再生した曲',
    noRecentTracks: '最近再生した曲はありません。',
    trendingForYou: 'トレンド',
    quickPicks: 'クイックピック',
    newReleases: 'ニューリリース',
    moodsAndGenres: 'ムード＆ジャンル',
    likedSongs: 'お気に入りの曲',
    noLikedSongs: 'お気に入りの曲はまだありません。',
    playlistNamePlaceholder: '新しいプレイリスト名...',
    create: '作成',
    noPlaylists: 'プレイリストはまだありません。',
    songsCount: '曲',
    selectSongToStart: '曲を選択して再生',
    queue: 'キュー',
    recentlyPlayedTab: '再生履歴',
    nowPlaying: '再生中',
    nextUp: '次の曲',
    queueEmpty: 'キューは空です。',
    noNextTracks: '次の曲はありません。',
    lyrics: '歌詞',
    lyricsFaster: '歌詞を早く',
    lyricsSlower: '歌詞を遅く',
    noLyricsAvailable: 'この曲の歌詞はありません。',
    settingsTitle: '設定',
    tabAppearance: '外観',
    tabPlayer: '再生・EQ',
    tabRegion: '言語と国',
    tabStorage: 'ストレージ',
    tabShortcuts: 'ショートカット',
    languageLabel: '表示言語',
    languageDesc: 'アプリのメニューとおすすめ表示の言語を変更します',
    countryLabel: '国 / 地域',
    countryDesc: 'ホームのトレンドやニューリリースを地域に合わせます',
  },
  ko: {
    nav: {
      home: '홈',
      search: '검색',
      explore: '둘러보기',
      library: '보관함',
      you: '나',
      collection: '보관함',
      liked: '좋아요 표시한 곡',
      recent: '최근 재생한 곡',
      playlists: '재생목록',
      local: '로컬 파일',
      offline: '오프라인',
      stats: '통계',
      settings: '설정',
    },
    searchPlaceholder: '노래, 앨범 또는 아티스트 검색...',
    searching: '검색 중...',
    searchResultsFor: '검색 결과',
    noSearchResults: '일치하는 곡이 없습니다.',
    localFilesBtn: '로컬 파일',
    chooseAudioFiles: '오디오 파일 선택',
    noLocalFiles: '로컬 파일이 없습니다.',
    greetingMorning: '좋은 아침입니다',
    greetingAfternoon: '안녕하세요',
    greetingEvening: '좋은 저녁입니다',
    greetingNight: '편안한 밤 되세요',
    readyToPlay: '재생 준비 완료',
    playedThisSession: '곡 재생됨',
    continueListening: '이어서 듣기',
    topPick: '추천 트랙',
    play: '재생',
    playAll: '모두 재생',
    recentlyPlayed: '최근 재생한 곡',
    noRecentTracks: '최근 재생한 곡이 없습니다.',
    trendingForYou: '인기 급상승',
    quickPicks: '빠른 선곡',
    newReleases: '새 앨범 및 싱글',
    moodsAndGenres: '분위기 및 장르',
    likedSongs: '좋아요 표시한 곡',
    noLikedSongs: '좋아요 표시한 곡이 없습니다.',
    playlistNamePlaceholder: '새 재생목록 이름...',
    create: '만들기',
    noPlaylists: '재생목록이 없습니다.',
    songsCount: '곡',
    selectSongToStart: '재생할 곡을 선택하세요',
    queue: '재생목록',
    recentlyPlayedTab: '최근 재생',
    nowPlaying: '현재 재생 중',
    nextUp: '다음 곡',
    queueEmpty: '재생목록이 비어 있습니다.',
    noNextTracks: '다음 곡이 없습니다.',
    lyrics: '가사',
    lyricsFaster: '가사 빠르게',
    lyricsSlower: '가사 느리게',
    noLyricsAvailable: '이 곡의 가사가 없습니다.',
    settingsTitle: '설정',
    tabAppearance: '화면',
    tabPlayer: '플레이어 및 EQ',
    tabRegion: '언어 및 국가',
    tabStorage: '저장소',
    tabShortcuts: '단축키',
    languageLabel: '표시 언어',
    languageDesc: '앱 메뉴 언어 및 콘텐츠 언어를 변경합니다',
    countryLabel: '홈 국가 / 지역',
    countryDesc: '인기 급상승, 신곡 및 지역별 추천을 맞춤 설정합니다',
  },
};

export function getTranslations(lang: AppLanguage): UIStrings {
  return STRINGS[lang] ?? STRINGS.en!;
}
