package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Versatile developer data & mock generator panel.
 * Supports standard UUID, 32-character clean UUID, 16-character SecureRandom passwords (letters, digits and symbols),
 * random verification codes, mock phone numbers, IPv4 addresses, and test emails.
 *
 * @author peter/antigravity
 */
public class GenTabPanel extends JPanel {

    private static final String[] PHONE_PREFIXES = {"138", "139", "150", "151", "158", "177", "180", "188", "189", "199"};
    private static final String[] EMAIL_DOMAINS = {"example.com", "test.org", "demo.net", "company.io"};
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private JTextArea genResultArea;
    private JComboBox<String> batchCountCombo;
    private JTextArea generationHint;

    public GenTabPanel() {
        initComponents();
        generateData(1); // default 1 standard UUID
    }

    private void initComponents() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        generationHint = new JTextArea(2, 0);
        generationHint.setEditable(false);
        generationHint.setFocusable(false);
        generationHint.setOpaque(false);
        generationHint.setLineWrap(true);
        generationHint.setWrapStyleWord(true);
        generationHint.setFont(UiDefaults.font(java.awt.Font.PLAIN, 12));
        add(generationHint, BorderLayout.NORTH);

        // Center: Multi-line Result Area
        genResultArea = new JTextArea();
        genResultArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        genResultArea.setLineWrap(true);
        genResultArea.setWrapStyleWord(true);
        genResultArea.setEditable(false);
        genResultArea.getAccessibleContext().setAccessibleName(UiDefaults.text("Generator.results"));

        JScrollPane scrollPane = new JScrollPane(genResultArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(UiDefaults.text("Generator.results")));
        add(scrollPane, BorderLayout.CENTER);

        // South: Control Bar & Grid of Generators
        JPanel southPanel = new JPanel(new BorderLayout(4, 4));

        // Generator Buttons Grid
        JPanel gridPanel = new JPanel(new GridLayout(2, 4, 4, 4));

        JButton uuidStandardBtn = new JButton("标准 UUID (带横杠)");
        uuidStandardBtn.addActionListener(e -> generateData(1));

        JButton uuidCleanBtn = new JButton("32位 UUID (无横杠)");
        uuidCleanBtn.addActionListener(e -> generateData(2));

        JButton pwdBtn = new JButton(UiDefaults.text("Generator.password"));
        UiDefaults.describe(pwdBtn, UiDefaults.text("Generator.password"), UiDefaults.text("Generator.passwordHint"));
        pwdBtn.addActionListener(e -> generateData(3));

        JButton code6Btn = new JButton(UiDefaults.text("Generator.mockCode"));
        UiDefaults.describe(code6Btn, UiDefaults.text("Generator.mockCode"), UiDefaults.text("Generator.mockCodeHint"));
        code6Btn.addActionListener(e -> generateData(4));

        JButton phoneBtn = new JButton("Mock 手机号 (11位)");
        phoneBtn.addActionListener(e -> generateData(5));

        JButton ipBtn = new JButton("Mock IPv4 地址");
        ipBtn.addActionListener(e -> generateData(6));

        JButton emailBtn = new JButton("Mock 测试邮箱");
        emailBtn.addActionListener(e -> generateData(7));

        JButton timestampBtn = new JButton("当前时间戳 (毫秒)");
        timestampBtn.addActionListener(e -> generateData(8));

        gridPanel.add(uuidStandardBtn);
        gridPanel.add(uuidCleanBtn);
        gridPanel.add(pwdBtn);
        gridPanel.add(code6Btn);
        gridPanel.add(phoneBtn);
        gridPanel.add(ipBtn);
        gridPanel.add(emailBtn);
        gridPanel.add(timestampBtn);

        southPanel.add(gridPanel, BorderLayout.CENTER);

        // Bottom Action Bar: Batch selector & Copy/Clear
        JPanel bottomBar = new JPanel(new BorderLayout());

        JPanel batchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel batchLabel = new JLabel(UiDefaults.text("Generator.count"));
        batchPanel.add(batchLabel);
        batchCountCombo = new JComboBox<>(new String[]{"1 条", "5 条", "10 条", "20 条"});
        batchLabel.setLabelFor(batchCountCombo);
        batchCountCombo.getAccessibleContext().setAccessibleName(UiDefaults.text("Generator.count"));
        batchPanel.add(batchCountCombo);
        bottomBar.add(batchPanel, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton copyBtn = new JButton(UiDefaults.text("Action.copyResult"));
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(genResultArea.getText()));

        JButton clearBtn = new JButton(UiDefaults.text("Action.clear"));
        clearBtn.addActionListener(e -> {
            genResultArea.setText("");
            genResultArea.getAccessibleContext().setAccessibleDescription("");
            generationHint.setText("");
        });

        rightActions.add(copyBtn);
        rightActions.add(clearBtn);
        bottomBar.add(rightActions, BorderLayout.EAST);

        southPanel.add(bottomBar, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);
    }

    private void generateData(int type) {
        int count = getSelectedCount();
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append("\n");
            switch (type) {
                case 1 -> sb.append(UUID.randomUUID());
                case 2 -> sb.append(generateCleanUuid());
                case 3 -> sb.append(CommonUtils.generateRandomString(16));
                case 4 -> sb.append(String.format("%06d", rnd.nextInt(1000000)));
                case 5 -> sb.append(generateMockPhone(rnd));
                case 6 -> sb.append(generateMockIp(rnd));
                case 7 -> sb.append(generateMockEmail(rnd));
                case 8 -> sb.append(System.currentTimeMillis());
            }
        }
        String hint = type == 3 ? UiDefaults.text("Generator.passwordHint")
                : type == 4 ? UiDefaults.text("Generator.mockCodeHint") : "";
        generationHint.setText(hint.isBlank() ? " " : hint);
        genResultArea.getAccessibleContext().setAccessibleDescription(hint);
        genResultArea.setText(sb.toString());
    }

    private static String generateCleanUuid() {
        UUID u = UUID.randomUUID();
        long most = u.getMostSignificantBits();
        long least = u.getLeastSignificantBits();
        char[] buf = new char[32];
        formatHexLong(most, buf, 0);
        formatHexLong(least, buf, 16);
        return new String(buf);
    }

    private static void formatHexLong(long val, char[] buf, int offset) {
        for (int i = 15; i >= 0; i--) {
            buf[offset + i] = HEX_CHARS[(int) (val & 0xFL)];
            val >>>= 4;
        }
    }

    private int getSelectedCount() {
        int idx = batchCountCombo.getSelectedIndex();
        return switch (idx) {
            case 1 -> 5;
            case 2 -> 10;
            case 3 -> 20;
            default -> 1;
        };
    }

    private String generateMockPhone(ThreadLocalRandom rnd) {
        String prefix = PHONE_PREFIXES[rnd.nextInt(PHONE_PREFIXES.length)];
        return prefix + String.format("%08d", rnd.nextInt(100000000));
    }

    private String generateMockIp(ThreadLocalRandom rnd) {
        return (rnd.nextInt(220) + 1) + "." + rnd.nextInt(256) + "." 
                + rnd.nextInt(256) + "." + (rnd.nextInt(254) + 1);
    }

    private String generateMockEmail(ThreadLocalRandom rnd) {
        String domain = EMAIL_DOMAINS[rnd.nextInt(EMAIL_DOMAINS.length)];
        return "user_" + (rnd.nextInt(90000) + 10000) + "@" + domain;
    }
}
