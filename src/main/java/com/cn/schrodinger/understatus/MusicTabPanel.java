package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.music.LrcParser;
import com.cn.schrodinger.understatus.music.MusicApiClient;
import com.cn.schrodinger.understatus.music.MusicSong;
import com.cn.schrodinger.understatus.music.MusicSession;
import com.cn.schrodinger.understatus.music.PlaybackMode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
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

/**
 * Swing Music Player panel supporting online search, audio streaming, playlist queue,
 * favorites persistence, playback mode switching, and LRC lyric synchronization.
 */
public class MusicTabPanel extends JPanel implements AutoCloseable {

    private final MusicApiClient apiClient = new MusicApiClient();
    private MusicSession session;
    private MusicSession.Subscription subscription;
    private ExecutorService searchWorker;
    private Future<?> searchRequest;
    private long searchGeneration;
    private final List<MusicSong> searchResults = new ArrayList<>();
    private final List<MusicSong> playlist = new ArrayList<>();
    private final List<MusicSong> favorites = new ArrayList<>();
    private List<LrcParser.LrcLine> currentLyrics = List.of();
    private int lastActiveLyricIndex = -1;
    private PlaybackMode currentMode = PlaybackMode.LIST_LOOP;
    private MusicSong displayedSong;
    private String sessionMessage = "";

    // Search Bar Components
    private JTextField searchTextField;
    private JComboBox<SourceItem> sourceComboBox;
    private JButton searchButton;
    private JLabel searchStatusLabel;
    private JLabel favoritesStatusLabel;
    private JButton retryFavoritesButton;
    private final List<JMenuItem> favoriteMenuItems = new ArrayList<>();

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
        initComponents();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (subscription == null) {
            searchWorker = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "UnderStatus-MusicSearch");
                thread.setDaemon(true);
                return thread;
            });
            session = MusicSession.shared();
            subscription = session.subscribe(this::renderSnapshot);
        }
    }

    @Override public void removeNotify() {
        close();
        super.removeNotify();
    }

    @Override public void close() {
        searchGeneration++;
        if (searchRequest != null) searchRequest.cancel(true);
        if (searchWorker != null) searchWorker.shutdownNow();
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        session = null;
        searchButton.setEnabled(true);
    }

    private void renderSnapshot(MusicSession.Snapshot state) {
        if (!playlist.equals(state.playlist())) {
            playlist.clear();
            playlist.addAll(state.playlist());
            updatePlaylistTable();
        }
        if (!favorites.equals(state.favorites())) {
            favorites.clear();
            favorites.addAll(state.favorites());
            updateFavoritesTable();
        }
        currentMode = state.mode();
        modeButton.setText(currentMode.getDisplayTitle());
        playPauseButton.setText(state.playing() ? "⏸️" : "▶️");
        MusicSong song = state.song();
        if (song != displayedSong) {
            displayedSong = song;
            lastActiveLyricIndex = -1;
            songTitleLabel.setText(song == null ? "暂无播放曲目" : song.getName());
            songArtistLabel.setText(song == null ? "等待选择音乐..." : song.getArtist() + " · " + song.getSourceDisplayName());
        }
        favoriteButton.setText(song != null && favorites.contains(song) ? "❤️" : "♡");
        favoriteButton.setEnabled(state.favoritesReady());
        favoriteMenuItems.forEach(item -> item.setEnabled(state.favoritesReady()));
        favoritesStatusLabel.setText(state.favoritesMessage());
        retryFavoritesButton.setVisible(state.favoritesRetryable());
        retryFavoritesButton.setEnabled(!state.favoritesBusy());
        coverLabel.setIcon(state.cover());
        coverLabel.setText(state.cover() == null ? "🎵" : "");
        if (currentLyrics != state.lyrics()) {
            currentLyrics = state.lyrics();
            lastActiveLyricIndex = -1;
            updateLyricList(currentLyrics);
        }
        if (!sessionMessage.equals(state.message())) {
            sessionMessage = state.message();
            searchStatusLabel.setText(sessionMessage);
        }
        onProgress(state.positionMs());
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

        JPanel favoritesStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        favoritesStatusLabel = new JLabel("正在加载收藏…");
        favoritesStatusLabel.setFont(favoritesStatusLabel.getFont().deriveFont(11f));
        favoritesStatus.add(favoritesStatusLabel);
        retryFavoritesButton = new JButton("重试收藏读写");
        retryFavoritesButton.addActionListener(e -> { if (session != null) session.retryFavorites(); });
        retryFavoritesButton.setVisible(false);
        favoritesStatus.add(retryFavoritesButton);
        gbc.gridy = 2;
        searchPanel.add(favoritesStatus, gbc);

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
            session.clearPlaylist();
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
                session.playFavorites();
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
                    session.addToPlaylist(song);
                }
            }
        });
        menu.add(addPlaylistItem);

        JMenuItem favItem = new JMenuItem("❤️ 收藏 / 取消收藏");
        favoriteMenuItems.add(favItem);
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
            MusicSong current = displayedSong;
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
            session.nextMode();
        });
        buttonsPanel.add(modeButton);

        prevButton = new JButton("⏮️");
        prevButton.setToolTipText("上一首");
        prevButton.addActionListener(e -> playPrev());
        buttonsPanel.add(prevButton);

        playPauseButton = new JButton("▶️");
        playPauseButton.setFont(playPauseButton.getFont().deriveFont(Font.BOLD, 13f));
        playPauseButton.setToolTipText("播放 / 暂停");
        playPauseButton.addActionListener(e -> session.togglePause());
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
        if (session == null) return;
        String kw = searchTextField.getText().trim();
        if (kw.isBlank()) return;
        SourceItem item = (SourceItem) sourceComboBox.getSelectedItem();
        String source = item != null ? item.code : "netease";
        long token = ++searchGeneration;
        if (searchRequest != null) searchRequest.cancel(true);
        searchButton.setEnabled(false);
        searchStatusLabel.setText("🔍 正在搜索【" + kw + "】...");
        searchResults.clear();
        searchTableModel.setRowCount(0);
        searchRequest = searchWorker.submit(() -> {
            try {
                List<MusicSong> list = apiClient.search(kw, source, 30);
                SwingUtilities.invokeLater(() -> {
                    if (session == null || token != searchGeneration) return;
                    searchResults.clear();
                    searchResults.addAll(list);
                    updateSearchTable();
                    searchStatusLabel.setText("✅ 找到 " + list.size() + " 首相关歌曲");
                    searchButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (session == null || token != searchGeneration) return;
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
        if (row >= 0 && row < searchResults.size()) session.select(searchResults.get(row));
    }

    private void playFromPlaylist(int index) {
        if (index >= 0 && index < playlist.size()) session.select(playlist.get(index));
    }

    private void playFromFavorites(int index) {
        if (index >= 0 && index < favorites.size()) session.select(favorites.get(index));
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

    private void playNext(boolean automatic) { session.next(automatic); }

    private void playPrev() { session.previous(); }

    private void toggleFavorite(MusicSong song) {
        try {
            session.toggleFavorite(song);
        } catch (RuntimeException ex) {
            searchStatusLabel.setText("收藏保存失败: " + ex.getMessage());
        }
    }

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

}
