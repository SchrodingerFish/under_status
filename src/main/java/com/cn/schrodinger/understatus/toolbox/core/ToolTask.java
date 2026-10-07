package com.cn.schrodinger.understatus.toolbox.core;

import java.awt.event.HierarchyEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Per-view latest request guard backed by one bounded, idle-expiring toolbox pool. */
public final class ToolTask {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
                Thread thread = new Thread(runnable, "under-status-toolbox");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    static { WORKERS.allowCoreThreadTimeOut(true); }

    private final JComponent owner;
    private final ThreadPoolExecutor workers;
    private Future<?> pending;
    private long revision;
    private Runnable onShow = () -> {};

    public ToolTask(JComponent owner) { this(owner, WORKERS); }

    ToolTask(JComponent owner, ThreadPoolExecutor workers) {
        this.owner = owner;
        this.workers = workers;
        owner.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0) {
                if (owner.isShowing()) onShow.run();
                else cancel();
            }
        });
    }

    public void onShow(Runnable action) { onShow = action; }

    public void watch(JTextComponent input) { watch(input, () -> {}); }

    public void watch(JTextComponent input, Runnable changed) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { cancel(); changed.run(); }
            @Override public void removeUpdate(DocumentEvent event) { cancel(); changed.run(); }
            @Override public void changedUpdate(DocumentEvent event) { cancel(); changed.run(); }
        });
    }

    public void cancel() {
        revision++;
        if (pending != null) {
            pending.cancel(true);
            if (pending instanceof Runnable runnable) workers.remove(runnable);
            pending = null;
        }
    }

    public <T> void submit(Callable<T> computation, Consumer<T> success, Consumer<String> failure) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Snapshot and submit on EDT");
        cancel();
        if (!owner.isShowing()) return;
        long request = revision;
        try {
            pending = workers.submit(() -> {
                try {
                    T result = computation.call();
                    SwingUtilities.invokeLater(() -> {
                        if (request == revision && owner.isShowing()) success.accept(result);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        if (request == revision && owner.isShowing()) failure.accept(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            failure.accept("工具箱忙，请稍后重试");
        }
    }

    public static String snapshot(JTextComponent input) {
        if (input.getDocument().getLength() > ToolLimits.MAX_INPUT) throw new IllegalArgumentException("输入超过 250,000 字符限制");
        return input.getText();
    }

    public void transform(JTextComponent input, JTextComponent output, JLabel status, UnaryOperator<String> operation) {
        try {
            String text = snapshot(input);
            status.setText("处理中…");
            submit(() -> ToolLimits.output(operation.apply(text)), result -> {
                output.setText(result);
                status.setText("已完成");
            }, error -> status.setText("处理失败: " + error));
        } catch (IllegalArgumentException ex) {
            cancel();
            status.setText(ex.getMessage());
        }
    }
}
