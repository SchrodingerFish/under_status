package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.music.LrcParser;
import com.cn.schrodinger.understatus.music.MusicApiClient;
import com.cn.schrodinger.understatus.music.MusicAudioPlayer;
import com.cn.schrodinger.understatus.music.MusicSong;
import com.cn.schrodinger.understatus.music.PlaybackMode;
import com.cn.schrodinger.understatus.settings.SettingsRepository;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.openide.util.RequestProcessor;

/**
 * Swing Music Player panel supporting online search, audio streaming, playlist queue,
 * favorites persistence, playback mode switching, and LRC lyric synchronization.
 */
public class MusicTabPanel extends JPanel implements MusicAudioPlayer.PlayerListener {

    private static final Logger LOGGER = Logger.getLogger(MusicTabPanel.class.getName());
    private static final RequestProcessor ASYNC_WORKER = new RequestProcessor("MusicTabPanel Worker", 4, true);

    // API & Player Engine
    private final MusicApiClient apiClient = new MusicApiClient();
    private final MusicAudioPlayer audioPlayer = new MusicAudioPlayer();
    private final Random random = new Random();

    // Data lists & Cache
    private final List<MusicSong> searchResults = new ArrayList<>();
    private final List<MusicSong> playlist = new ArrayList<>();
    private final List<MusicSong> favorites = new ArrayList<>();
    private List<LrcParser.LrcLine> currentLyrics = new ArrayList<>();

    private final Map<String, ImageIcon> coverCache = new ConcurrentHashMap<>();
    private final Map<String, List<LrcParser.LrcLine>> lyricCache = new ConcurrentHashMap<>();
    private int lastActiveLyricIndex = -1;

    // Playback state
    private PlaybackMode currentMode = PlaybackMode.LIST_LOOP;
    private int currentPlayingIndex = -1;
    private boolean isFavoriteCurrent = false;

    // Search Bar Components
    private JTextField searchTextField;
    private JComboBox<SourceItem> sourceComboBox;
    private JButton searchButton;
    private JLabel searchStatusLabel;

    // Center Tabs & Tables
    private JTabbedPane centerTabbedPane;
    private JTable searchTable;
    private DefaultTableModel searchTableModel;

    private JTable playlistTable;
    private DefaultTableModel playlistTableModel;

    private JTable favoritesTable;
    private DefaultTableModel favoritesTableModel;

    // Lyric View
    private JList<LrcParser.LrcLine> lyricList;
    private DefaultListModel<LrcParser.LrcLine> lyricListModel;

    // Bottom Control Bar Components
    private JLabel coverLabel;
    private JLabel songTitleLabel;
    private JLabel songArtistLabel;
    private JButton favoriteButton;

    private JButton prevButton;
    private JButton playPauseButton;
    private JButton nextButton;
    private JButton modeButton;

    private JLabel timeLabel;
    private JProgressBar progressBar;

    public MusicTabPanel() {
        audioPlayer.setListener(this);
        loadFavoritesFromPreferences();
        initComponents();
        updateFavoritesTable();
    }

    private static class SourceItem {
        final String code;
        final String displayName;

        SourceItem(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // 1. Top Search Header
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);

        JLabel searchIconLabel = new JLabel("🎵 搜索歌曲:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        searchPanel.add(searchIconLabel, gbc);

        searchTextField = new JTextField();
        searchTextField.setToolTipText("输入歌曲名称或歌手名关键词，回车搜索");
        searchTextField.addActionListener(e -> performSearch());
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        searchPanel.add(searchTextField, gbc);

        sourceComboBox = new JComboBox<>(new SourceItem[]{
            new SourceItem("netease", "网易云音乐"),
            new SourceItem("tencent", "QQ音乐"),
            new SourceItem("kugou", "酷狗音乐"),
            new SourceItem("kuwo", "酷我音乐"),
            new SourceItem("migu", "咪咕音乐")
        });
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        searchPanel.add(sourceComboBox, gbc);

        searchButton = new JButton("🔍 搜索");
        searchButton.addActionListener(e -> performSearch());
        gbc.gridx = 3;
        searchPanel.add(searchButton, gbc);

        searchStatusLabel = new JLabel("输入关键词开始搜索");
        searchStatusLabel.setFont(searchStatusLabel.getFont().deriveFont(11f));
        searchStatusLabel.setForeground(Color.GRAY);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.WEST;
        searchPanel.add(searchStatusLabel, gbc);

        add(searchPanel, BorderLayout.NORTH);

        // 2. Center Tabs (Search Results, Playlist, Favorites, Lyrics)
        centerTabbedPane = new JTabbedPane();

        // 2.1 Search Table
        String[] columns = {"#", "歌名", "歌手", "专辑", "音源"};
        searchTableModel = createReadOnlyTableModel(columns);
        searchTable = new JTable(searchTableModel);
        setupTableProperties(searchTable);
        searchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && searchTable.getSelectedRow() != -1) {
                    playSelectedSearchResult();
                }
            }
        });
        setupTableContextMenu(searchTable, true);
        centerTabbedPane.addTab("🔍 搜索结果", new JScrollPane(searchTable));

        // 2.2 Playlist Queue Table
        playlistTableModel = createReadOnlyTableModel(columns);
        playlistTable = new JTable(playlistTableModel);
        setupTableProperties(playlistTable);
        playlistTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && playlistTable.getSelectedRow() != -1) {
                    playFromPlaylist(playlistTable.getSelectedRow());
                }
            }
        });
        setupTableContextMenu(playlistTable, false);

        JPanel playlistControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton clearPlaylistBtn = new JButton("🗑️ 清空列表");
        clearPlaylistBtn.addActionListener(e -> {
            playlist.clear();
            updatePlaylistTable();
        });
        playlistControlPanel.add(clearPlaylistBtn);

        JPanel playlistContainer = new JPanel(new BorderLayout());
        playlistContainer.add(playlistControlPanel, BorderLayout.NORTH);
        playlistContainer.add(new JScrollPane(playlistTable), BorderLayout.CENTER);
        centerTabbedPane.addTab("📜 播放列表", playlistContainer);

        // 2.3 Favorites Table
        favoritesTableModel = createReadOnlyTableModel(columns);
        favoritesTable = new JTable(favoritesTableModel);
        setupTableProperties(favoritesTable);
        favoritesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && favoritesTable.getSelectedRow() != -1) {
                    playFromFavorites(favoritesTable.getSelectedRow());
                }
            }
        });
        setupTableContextMenu(favoritesTable, false);

        JPanel favControlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton playAllFavBtn = new JButton("▶️ 播放全部收藏");
        playAllFavBtn.addActionListener(e -> {
            if (!favorites.isEmpty()) {
                playlist.clear();
                playlist.addAll(favorites);
                updatePlaylistTable();
                playFromPlaylist(0);
            }
        });
        favControlPanel.add(playAllFavBtn);

        JPanel favContainer = new JPanel(new BorderLayout());
        favContainer.add(favControlPanel, BorderLayout.NORTH);
        favContainer.add(new JScrollPane(favoritesTable), BorderLayout.CENTER);
        centerTabbedPane.addTab("❤️ 我的收藏", favContainer);

        // 2.4 Lyric View Tab
        lyricListModel = new DefaultListModel<>();
        lyricList = new JList<>(lyricListModel);
        lyricList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lyricList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                if (value instanceof LrcParser.LrcLine) {
                    LrcParser.LrcLine line = (LrcParser.LrcLine) value;
                    label.setText(line.getText().isBlank() ? "♪ ♪ ♪" : line.getText());
                }
                if (isSelected) {
                    label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
                    label.setForeground(new Color(0, 120, 215));
                } else {
                    label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
                }
                label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                return label;
            }
        });
        centerTabbedPane.addTab("🎤 动态歌词", new JScrollPane(lyricList));
        centerTabbedPane.addChangeListener(e -> {
            if (centerTabbedPane.getSelectedIndex() == 3) {
                int idx = lyricList.getSelectedIndex();
                if (idx >= 0) {
                    SwingUtilities.invokeLater(() -> scrollLyricToCenter(idx));
                }
            }
        });

        add(centerTabbedPane, BorderLayout.CENTER);

        // 3. Bottom Playback Bar
        JPanel bottomBar = createPlaybackBar();
        add(bottomBar, BorderLayout.SOUTH);
    }

    private DefaultTableModel createReadOnlyTableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void setupTableProperties(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
    }

    private void setupTableContextMenu(JTable table, boolean isSearchTable) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem playItem = new JMenuItem("▶️ 播放该曲目");
        playItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                if (isSearchTable) playSelectedSearchResult();
                else if (table == playlistTable) playFromPlaylist(row);
                else playFromFavorites(row);
            }
        });
        menu.add(playItem);

        JMenuItem addPlaylistItem = new JMenuItem("➕ 加入播放列表");
        addPlaylistItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                MusicSong song = getSongFromTable(table, row);
                if (song != null && !playlist.contains(song)) {
                    playlist.add(song);
                    updatePlaylistTable();
                }
            }
        });
        menu.add(addPlaylistItem);

        JMenuItem favItem = new JMenuItem("❤️ 收藏 / 取消收藏");
        favItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                MusicSong song = getSongFromTable(table, row);
                if (song != null) toggleFavorite(song);
            }
        });
        menu.add(favItem);

        table.setComponentPopupMenu(menu);
    }

    private MusicSong getSongFromTable(JTable table, int row) {
        if (row < 0) return null;
        if (table == searchTable && row < searchResults.size()) {
            return searchResults.get(row);
        } else if (table == playlistTable && row < playlist.size()) {
            return playlist.get(row);
        } else if (table == favoritesTable && row < favorites.size()) {
            return favorites.get(row);
        }
        return null;
    }

    private JPanel createPlaybackBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        // Left: Cover & Track Info
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        coverLabel = new JLabel("🎵");
        coverLabel.setPreferredSize(new Dimension(42, 42));
        coverLabel.setHorizontalAlignment(SwingConstants.CENTER);
        coverLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
        leftPanel.add(coverLabel);

        JPanel textPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;

        songTitleLabel = new JLabel("暂无播放曲目");
        songTitleLabel.setFont(songTitleLabel.getFont().deriveFont(Font.BOLD, 12f));
        songTitleLabel.setPreferredSize(new Dimension(180, 18));
        gbc.gridy = 0;
        textPanel.add(songTitleLabel, gbc);

        songArtistLabel = new JLabel("等待选择音乐...");
        songArtistLabel.setFont(songArtistLabel.getFont().deriveFont(11f));
        songArtistLabel.setForeground(Color.GRAY);
        songArtistLabel.setPreferredSize(new Dimension(180, 16));
        gbc.gridy = 1;
        textPanel.add(songArtistLabel, gbc);

        leftPanel.add(textPanel);

        favoriteButton = new JButton("♡");
        favoriteButton.setFont(favoriteButton.getFont().deriveFont(14f));
        favoriteButton.setToolTipText("收藏当前播放曲目");
        favoriteButton.setFocusPainted(false);
        favoriteButton.addActionListener(e -> {
            MusicSong current = audioPlayer.getCurrentSong();
            if (current != null) toggleFavorite(current);
        });
        leftPanel.add(favoriteButton);

        bar.add(leftPanel, BorderLayout.WEST);

        // Center: Control Buttons & Progress
        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));

        modeButton = new JButton(currentMode.getDisplayTitle());
        modeButton.setToolTipText("切换播放模式 (顺序/列表循环/单曲循环/随机)");
        modeButton.setFocusPainted(false);
        modeButton.addActionListener(e -> {
            currentMode = currentMode.next();
            modeButton.setText(currentMode.getDisplayTitle());
        });
        buttonsPanel.add(modeButton);

        prevButton = new JButton("⏮️");
        prevButton.setToolTipText("上一首");
        prevButton.addActionListener(e -> playPrev());
        buttonsPanel.add(prevButton);

        playPauseButton = new JButton("▶️");
        playPauseButton.setFont(playPauseButton.getFont().deriveFont(Font.BOLD, 13f));
        playPauseButton.setToolTipText("播放 / 暂停");
        playPauseButton.addActionListener(e -> audioPlayer.togglePause());
        buttonsPanel.add(playPauseButton);

        nextButton = new JButton("⏭️");
        nextButton.setToolTipText("下一首");
        nextButton.addActionListener(e -> playNext(false));
        buttonsPanel.add(nextButton);

        centerPanel.add(buttonsPanel, BorderLayout.NORTH);

        // Progress line
        JPanel progressPanel = new JPanel(new BorderLayout(6, 0));
        timeLabel = new JLabel("00:00");
        timeLabel.setFont(timeLabel.getFont().deriveFont(10f));
        progressPanel.add(timeLabel, BorderLayout.WEST);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        centerPanel.add(progressPanel, BorderLayout.SOUTH);

        bar.add(centerPanel, BorderLayout.CENTER);

        return bar;
    }

    private void performSearch() {
        String kw = searchTextField.getText().trim();
        if (kw.isBlank()) {
            return;
        }
        SourceItem item = (SourceItem) sourceComboBox.getSelectedItem();
        String source = item != null ? item.code : "netease";

        searchButton.setEnabled(false);
        searchStatusLabel.setText("🔍 正在搜索【" + kw + "】...");
        searchResults.clear();
        searchTableModel.setRowCount(0);

        ASYNC_WORKER.post(() -> {
            try {
                List<MusicSong> list = apiClient.search(kw, source, 30);
                SwingUtilities.invokeLater(() -> {
                    searchResults.addAll(list);
                    updateSearchTable();
                    searchStatusLabel.setText("✅ 找到 " + list.size() + " 首相关歌曲");
                    searchButton.setEnabled(true);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Music search failed", ex);
                SwingUtilities.invokeLater(() -> {
                    searchStatusLabel.setText("❌ 搜索失败: " + ex.getMessage());
                    searchButton.setEnabled(true);
                });
            }
        });
    }

    private void updateSearchTable() {
        searchTableModel.setRowCount(0);
        for (int i = 0; i < searchResults.size(); i++) {
            MusicSong s = searchResults.get(i);
            searchTableModel.addRow(new Object[]{
                i + 1, s.getName(), s.getArtist(), s.getAlbum(), s.getSourceDisplayName()
            });
        }
    }

    private void updatePlaylistTable() {
        playlistTableModel.setRowCount(0);
        for (int i = 0; i < playlist.size(); i++) {
            MusicSong s = playlist.get(i);
            playlistTableModel.addRow(new Object[]{
                i + 1, s.getName(), s.getArtist(), s.getAlbum(), s.getSourceDisplayName()
            });
        }
    }

    private void updateFavoritesTable() {
        favoritesTableModel.setRowCount(0);
        for (int i = 0; i < favorites.size(); i++) {
            MusicSong s = favorites.get(i);
            favoritesTableModel.addRow(new Object[]{
                i + 1, s.getName(), s.getArtist(), s.getAlbum(), s.getSourceDisplayName()
            });
        }
    }

    private void playSelectedSearchResult() {
        int row = searchTable.getSelectedRow();
        if (row < 0 || row >= searchResults.size()) return;
        MusicSong song = searchResults.get(row);

        if (!playlist.contains(song)) {
            playlist.add(song);
            updatePlaylistTable();
        }
        currentPlayingIndex = playlist.indexOf(song);
        playSong(song);
    }

    private void playFromPlaylist(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentPlayingIndex = index;
        playSong(playlist.get(index));
    }

    private void playFromFavorites(int index) {
        if (index < 0 || index >= favorites.size()) return;
        MusicSong song = favorites.get(index);
        if (!playlist.contains(song)) {
            playlist.add(song);
            updatePlaylistTable();
        }
        currentPlayingIndex = playlist.indexOf(song);
        playSong(song);
    }

    private void playSong(MusicSong song) {
        if (song == null) return;

        songTitleLabel.setText(song.getName());
        songArtistLabel.setText(song.getArtist() + " · " + song.getSourceDisplayName());
        favoriteButton.setText(favorites.contains(song) ? "❤️" : "♡");
        isFavoriteCurrent = favorites.contains(song);

        // Reset cover & lyric state
        coverLabel.setIcon(null);
        coverLabel.setText("🎵");
        lastActiveLyricIndex = -1;

        searchStatusLabel.setText("▶️ 正在播放: " + song.getName());

        // 1. Instantly resolve audio URL and start playback
        ASYNC_WORKER.post(() -> {
            try {
                String audioUrl = song.getUrl();
                if (audioUrl == null || audioUrl.isBlank()) {
                    audioUrl = apiClient.fetchSongUrl(song.getId(), song.getSource());
                    song.setUrl(audioUrl);
                }
                final String finalAudioUrl = audioUrl;
                SwingUtilities.invokeLater(() -> audioPlayer.play(song, finalAudioUrl));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to resolve song play URL", ex);
                SwingUtilities.invokeLater(() -> searchStatusLabel.setText("❌ 无法解析曲目播放链接: " + ex.getMessage()));
            }
        });

        // 2. Fetch album cover asynchronously
        if (song.getPicUrl() != null && !song.getPicUrl().isBlank()) {
            loadCoverImage(song.getPicUrl());
        } else {
            ASYNC_WORKER.post(() -> {
                try {
                    String picUrl = apiClient.fetchPicUrl(song.getId(), song.getSource());
                    if (!picUrl.isBlank()) {
                        song.setPicUrl(picUrl);
                        SwingUtilities.invokeLater(() -> loadCoverImage(picUrl));
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Failed to fetch album cover URL", ex);
                }
            });
        }

        // 3. Fetch lyric asynchronously (or use cache)
        String songKey = song.getSource() + ":" + song.getId();
        if (lyricCache.containsKey(songKey)) {
            currentLyrics = lyricCache.get(songKey);
            updateLyricList(currentLyrics);
        } else {
            ASYNC_WORKER.post(() -> {
                try {
                    String lrc = song.getLyric();
                    if (lrc == null || lrc.isBlank() || isNumeric(lrc)) {
                        lrc = apiClient.fetchLyric(song.getId(), song.getSource());
                        song.setLyric(lrc);
                    }
                    List<LrcParser.LrcLine> lrcLines = LrcParser.parse(lrc);
                    lyricCache.put(songKey, lrcLines);
                    SwingUtilities.invokeLater(() -> {
                        if (song.equals(audioPlayer.getCurrentSong())) {
                            currentLyrics = lrcLines;
                            updateLyricList(lrcLines);
                        }
                    });
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Failed to fetch lyric", ex);
                }
            });
        }
    }

    private void loadCoverImage(String picUrl) {
        if (picUrl == null || picUrl.isBlank()) {
            coverLabel.setIcon(null);
            coverLabel.setText("🎵");
            return;
        }

        if (coverCache.containsKey(picUrl)) {
            coverLabel.setText("");
            coverLabel.setIcon(coverCache.get(picUrl));
            return;
        }

        ASYNC_WORKER.post(() -> {
            try {
                URL url = URI.create(picUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Referer", "https://music.163.com/");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(5000);

                try (InputStream in = conn.getInputStream()) {
                    Image img = ImageIO.read(in);
                    if (img != null) {
                        Image scaled = img.getScaledInstance(42, 42, Image.SCALE_SMOOTH);
                        ImageIcon icon = new ImageIcon(scaled);
                        coverCache.put(picUrl, icon);
                        SwingUtilities.invokeLater(() -> {
                            coverLabel.setText("");
                            coverLabel.setIcon(icon);
                        });
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to load album cover image", ex);
            }
        });
    }

    private void updateLyricList(List<LrcParser.LrcLine> lines) {
        lyricListModel.clear();
        if (lines == null || lines.isEmpty()) {
            lyricListModel.addElement(new LrcParser.LrcLine(0, "（暂无文本歌词）"));
        } else {
            for (LrcParser.LrcLine line : lines) {
                lyricListModel.addElement(line);
            }
        }
        lyricList.revalidate();
        lyricList.repaint();
    }

    private void playNext(boolean autoFinish) {
        if (playlist.isEmpty()) return;

        if (autoFinish && currentMode == PlaybackMode.SINGLE_LOOP && currentPlayingIndex != -1) {
            playSong(playlist.get(currentPlayingIndex));
            return;
        }

        if (currentMode == PlaybackMode.RANDOM) {
            currentPlayingIndex = random.nextInt(playlist.size());
        } else {
            currentPlayingIndex++;
            if (currentPlayingIndex >= playlist.size()) {
                if (currentMode == PlaybackMode.LIST_LOOP) {
                    currentPlayingIndex = 0;
                } else {
                    currentPlayingIndex = playlist.size() - 1;
                    if (autoFinish) return; // Sequence end
                }
            }
        }

        playSong(playlist.get(currentPlayingIndex));
    }

    private void playPrev() {
        if (playlist.isEmpty()) return;

        if (currentMode == PlaybackMode.RANDOM) {
            currentPlayingIndex = random.nextInt(playlist.size());
        } else {
            currentPlayingIndex--;
            if (currentPlayingIndex < 0) {
                currentPlayingIndex = playlist.size() - 1;
            }
        }

        playSong(playlist.get(currentPlayingIndex));
    }

    private void toggleFavorite(MusicSong song) {
        if (song == null) return;
        if (favorites.contains(song)) {
            favorites.remove(song);
            if (song.equals(audioPlayer.getCurrentSong())) {
                favoriteButton.setText("♡");
            }
        } else {
            favorites.add(song);
            if (song.equals(audioPlayer.getCurrentSong())) {
                favoriteButton.setText("❤️");
            }
        }
        saveFavoritesToPreferences();
        updateFavoritesTable();
    }

    private void loadFavoritesFromPreferences() {
        String data = SettingsRepository.getDefault().loadFavorites();
        favorites.clear();
        favorites.addAll(MusicSong.deserializeList(data));
    }

    private void saveFavoritesToPreferences() {
        String data = MusicSong.serializeList(favorites);
        SettingsRepository.getDefault().saveFavorites(data);
    }

    // AudioPlayer Listener Callbacks
    @Override
    public void onStatusChanged(boolean playing, MusicSong song) {
        playPauseButton.setText(playing ? "⏸️" : "▶️");
        if (song != null) {
            favoriteButton.setText(favorites.contains(song) ? "❤️" : "♡");
        }
    }

    @Override
    public void onProgress(long currentMs) {
        long sec = Math.max(0, currentMs / 1000);
        timeLabel.setText(String.format("%02d:%02d", sec / 60, sec % 60));

        // Update lyric highlight & center scrolling
        if (currentLyrics != null && !currentLyrics.isEmpty()) {
            int activeIndex = LrcParser.findCurrentLineIndex(currentLyrics, currentMs);
            if (activeIndex >= 0 && activeIndex < lyricListModel.size()) {
                if (activeIndex != lastActiveLyricIndex) {
                    lastActiveLyricIndex = activeIndex;
                    lyricList.setSelectedIndex(activeIndex);
                    scrollLyricToCenter(activeIndex);
                }
            }
        }
    }

    private void scrollLyricToCenter(int index) {
        if (index < 0 || index >= lyricListModel.size()) return;
        Rectangle cellBounds = lyricList.getCellBounds(index, index);
        if (cellBounds == null) return;
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, lyricList);
        if (viewport == null) return;

        int viewHeight = viewport.getHeight();
        if (viewHeight <= 0) return;

        int targetY = cellBounds.y - (viewHeight - cellBounds.height) / 2;
        int maxY = lyricList.getHeight() - viewHeight;
        if (maxY < 0) maxY = 0;
        targetY = Math.max(0, Math.min(targetY, maxY));
        viewport.setViewPosition(new java.awt.Point(0, targetY));
    }

    @Override
    public void onSongFinished(MusicSong song) {
        playNext(true);
    }

    @Override
    public void onError(String message) {
        searchStatusLabel.setText("❌ " + message);
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isBlank()) return false;
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }
}
