package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.alarm.Alarm;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import com.cn.schrodinger.understatus.settings.SettingsRepository;

/**
 * Simplified Alarm Settings Configuration Dialog (strictly dedicated to alarms management).
 *
 * @author peter/antigravity
 */
public class AlarmSettingDialog extends JDialog {

    private final Runnable onSaveCallback;
    private final List<Alarm> alarms = new ArrayList<>();
    private AlarmTableModel tableModel;
    private JTable alarmTable;

    public AlarmSettingDialog(java.awt.Frame parent, boolean modal, Runnable onSaveCallback) {
        super(parent, modal);
        this.onSaveCallback = onSaveCallback;
        setTitle("定时闹钟管理 (Alarms Configuration)");
        initComponents();
        loadSettings();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Table
        tableModel = new AlarmTableModel(alarms);
        alarmTable = new JTable(tableModel);
        alarmTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alarmTable.setPreferredScrollableViewportSize(new Dimension(520, 180));
        JScrollPane scrollPane = new JScrollPane(alarmTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton addBtn = new JButton("添加 (Add)");
        JButton editBtn = new JButton("修改 (Edit)");
        JButton deleteBtn = new JButton("删除 (Delete)");

        addBtn.addActionListener(e -> showAddAlarmDialog());
        editBtn.addActionListener(e -> showEditAlarmDialog());
        deleteBtn.addActionListener(e -> triggerDeleteAlarm());

        actionPanel.add(addBtn);
        actionPanel.add(editBtn);
        actionPanel.add(deleteBtn);

        panel.add(actionPanel, BorderLayout.SOUTH);

        // Bottom Actions
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton saveBtn = new JButton("保存闹钟 (Save)");
        JButton cancelBtn = new JButton("取消 (Cancel)");

        saveBtn.addActionListener(e -> {
            saveSettings();
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        bottomPanel.add(saveBtn);
        bottomPanel.add(cancelBtn);

        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadSettings() {
        String serializedAlarms = SettingsRepository.getDefault().loadAlarms();
        alarms.clear();
        alarms.addAll(Alarm.deserializeList(serializedAlarms));
        tableModel.fireTableDataChanged();
    }

    private void saveSettings() {
        SettingsRepository.getDefault().saveAlarms(Alarm.serializeList(alarms));
    }

    private void showAddAlarmDialog() {
        Alarm newAlarm = new Alarm();
        AlarmEditDialog dlg = new AlarmEditDialog(this, newAlarm, true);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            alarms.add(newAlarm);
            tableModel.fireTableDataChanged();
        }
    }

    private void showEditAlarmDialog() {
        int selectedRow = alarmTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请选择需要修改的闹钟！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Alarm alarmToEdit = alarms.get(selectedRow);
        AlarmEditDialog dlg = new AlarmEditDialog(this, alarmToEdit, false);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            tableModel.fireTableDataChanged();
        }
    }

    private void triggerDeleteAlarm() {
        int selectedRow = alarmTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "请选择需要删除的闹钟！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "确认删除此闹钟？", "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            alarms.remove(selectedRow);
            tableModel.fireTableDataChanged();
        }
    }

    private static class AlarmTableModel extends AbstractTableModel {
        private final List<Alarm> alarms;
        private final String[] columnNames = {"时间", "提醒内容", "周期规律", "是否启用"};

        public AlarmTableModel(List<Alarm> alarms) {
            this.alarms = alarms;
        }

        @Override
        public int getRowCount() { return alarms.size(); }
        @Override
        public int getColumnCount() { return columnNames.length; }
        @Override
        public String getColumnName(int column) { return columnNames[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Alarm alarm = alarms.get(rowIndex);
            switch (columnIndex) {
                case 0: return alarm.time;
                case 1: return alarm.message;
                case 2: return alarm.getRepeatDescription();
                case 3: return alarm.enabled;
            }
            return null;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 3;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 3 && aValue instanceof Boolean) {
                alarms.get(rowIndex).enabled = (Boolean) aValue;
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
