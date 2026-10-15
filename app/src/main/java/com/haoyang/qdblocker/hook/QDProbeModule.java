package com.haoyang.qdblocker.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

/** Phase one: observe known comment bind signatures without changing args, return values or views. */
public final class QDProbeModule extends XposedModule {
    private static final String TARGET = "com.qidian.QDReader";
    private static final String LAB = "com.haoyang.qdblocker.lab";
    private static final String DATA = TARGET + ".repository.entity.chaptercomment.NewParagraphCommentListBean$DataListBean";
    private static final String BOOK = TARGET + ".repository.entity.chaptercomment.NewParagraphCommentListBean$BookInfoBean";
    private static final String HOLDER = TARGET + ".ui.viewholder.chaptercomment.list.";
    private static final Uri SINK = Uri.parse("content://com.haoyang.qdblocker.probe");
    private final Set<String> installed = ConcurrentHashMap.newKeySet();
    private final Set<String> schemas = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor output = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> { Thread t = new Thread(runnable, "QDBlocker-probe"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());
    private volatile Context context;
    private volatile boolean diagnostics = true;
    private volatile String rules = "";
    private long windowStart, lastConfig;
    private int samples;
    private String process = "";

    @Override public void onModuleLoaded(ModuleLoadedParam param) { process = param.getProcessName(); }
    @Override public void onPackageReady(PackageReadyParam param) {
        if ((!TARGET.equals(param.getPackageName()) && !LAB.equals(param.getPackageName())) ||
                !param.getPackageName().equals(process) || !param.isFirstPackage()) return;
        emit("PACKAGE_READY api=" + getApiVersion() + " package=" + param.getPackageName());
        try {
            Method launch = Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class);
            hook(launch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Application app = (Application) chain.getArg(0);
                    if (TARGET.equals(app.getPackageName()) || LAB.equals(app.getPackageName())) {
                        context = app;
                        emit("APPLICATION_READY probe=0.1.0 mode=observe-only process=" + process);
                        install(app.getClassLoader());
                    }
                } catch (Throwable error) { emit("LIFECYCLE_ERROR " + error.getClass().getSimpleName()); }
                return result;
            });
        } catch (Throwable error) { emit("LIFECYCLE_HOOK_ERROR " + error.getClass().getSimpleName()); }
        // Protected hosts must finish Application initialization before business classes are touched.
        // Even a non-initializing lookup may force their loader/native registration prematurely.
    }
    private synchronized void install(ClassLoader loader) {
        for (String suffix : new String[]{"NewParagraphCommentListMainViewHolder", "NewParagraphCommentListReplyViewHolder", "NewParagraphCommentListHotImageViewHolder"}) {
            try {
                Class<?> holder = Class.forName(HOLDER + suffix, false, loader);
                int matches = 0;
                for (Class<?> type = holder; type != null && type.getName().startsWith(TARGET); type = type.getSuperclass()) {
                    for (Method method : type.getDeclaredMethods()) {
                        Class<?>[] args = method.getParameterTypes();
                        if (Modifier.isAbstract(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) ||
                                method.getReturnType() != void.class || args.length != 2 ||
                                !DATA.equals(args[0].getName()) || !BOOK.equals(args[1].getName())) continue;
                        matches++;
                        String signature = method.toGenericString();
                        if (installed.contains(signature)) continue;
                        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            try { observe(method, chain.getArg(0)); }
                            catch (Throwable error) { emit("INSPECT_ERROR " + error.getClass().getSimpleName()); }
                            return result;
                        });
                        installed.add(signature);
                        emit("HOOK_INSTALLED " + signature);
                    }
                }
                if (matches == 0) emit("SIGNATURE_MISSING " + suffix);
            } catch (ClassNotFoundException error) { emit("CLASS_MISSING " + suffix + " (may be protected or renamed)"); }
            catch (Throwable error) { emit("INSTALL_ERROR " + suffix + ' ' + error.getClass().getSimpleName()); }
        }
    }
    private synchronized void observe(Method method, Object data) {
        // Config requests run on the bounded output worker, never in a host UI callback.
        if (SystemClock.elapsedRealtime() - lastConfig > 5000) {
            lastConfig = SystemClock.elapsedRealtime();
            output.execute(this::readConfig);
        }
        if (!diagnostics || data == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now - windowStart > 60_000) { windowStart = now; samples = 0; }
        if (++samples > 40) return;
        if (schemas.add(data.getClass().getName())) emit("SCHEMA " + ProbeInspector.schema(data));
        boolean match = false;
        for (String rule : rules.split("\n")) {
            if (rule.startsWith("name:") && ProbeInspector.matchesExactName(data, rule.substring(5))) { match = true; break; }
        }
        emit("BIND " + method.getDeclaringClass().getSimpleName() + '.' + method.getName() + " candidateIds=[" + ProbeInspector.candidateIds(data) + "] exactNameMatch=" + match + " mapping=UNVERIFIED");
    }
    private void readConfig() {
        Context app = context;
        if (app == null) return;
        try {
            Bundle bundle = app.getContentResolver().call(SINK, "config", null, null);
            if (bundle != null) {
                diagnostics = bundle.getBoolean("diagnostics", true);
                rules = bundle.getString("rules", "");
            }
        } catch (RuntimeException ignored) { /* Logcat remains available when companion isn't installed. */ }
    }
    private void emit(String text) {
        output.execute(() -> {
            readConfig();
            if (!diagnostics) return;
            String annotated = "process=" + process + " " + text;
            String line = annotated.length() > 2400 ? annotated.substring(0, 2400) : annotated;
            try { log(Log.INFO, "QDBlocker", line); }
            catch (Throwable ignored) { Log.i("QDBlocker", line); }
            Context app = context;
            if (app != null) {
                try { app.getContentResolver().call(SINK, "event", line, null); }
                catch (RuntimeException ignored) { /* Missing companion must not affect QDReader. */ }
            }
        });
    }
}
