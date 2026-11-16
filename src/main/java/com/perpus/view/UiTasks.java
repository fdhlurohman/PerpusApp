package com.perpus.view;

import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class UiTasks {
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "perpus-api-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, THREAD_FACTORY);

    private UiTasks() {}

    static <T> void submit(Callable<T> work, Consumer<T> onSuccess,
                          Consumer<Throwable> onFailure, Node... busyNodes) {
        for (Node node : busyNodes) node.setDisable(true);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            for (Node node : busyNodes) node.setDisable(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            for (Node node : busyNodes) node.setDisable(false);
            onFailure.accept(task.getException());
        });

        try {
            EXECUTOR.execute(task);
        } catch (RuntimeException ex) {
            for (Node node : busyNodes) node.setDisable(false);
            onFailure.accept(ex);
        }
    }

    static String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "Kesalahan tidak diketahui";
        }
        return error.getMessage();
    }
}
