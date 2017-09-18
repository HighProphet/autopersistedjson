package pw.highprophet.autopersistedjson;

import com.alibaba.fastjson.JSON;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class JSONPersistHandler implements InvocationHandler {

    private static final Logger l = LogManager.getLogger(JSONPersistHandler.class);

    private static List<JSONPersistHandler> handlerPool;

    private static String[] ARRAY_PERSIST_AFTER = {"clear", "remove", "add", "addAll", "set"};

    private static String[] OBJECT_PERSIST_AFTER = {"remove", "put", "putAll", "putIfAbsent", "merge", "replace"};

    private static final long PERSIST_WINDOW = 500;

    //初始化持久化对象池,并在系统退出时将持久化线程结束
    static {
        handlerPool = new ArrayList<>();
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new Thread(() -> {
            for (JSONPersistHandler handler : handlerPool) {
                handler.persistStop();
            }
        }));
    }

    private final JSON json;

    private final File file;

    private String[] persistAfter;

    JSONPersistHandler(JSON json, File file) {
        this.json = json;
        this.file = file;
        if (json instanceof Map) {
            this.persistAfter = OBJECT_PERSIST_AFTER;
        } else {
            this.persistAfter = ARRAY_PERSIST_AFTER;
        }
        handlerPool.add(this);
        this.persistStart();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        boolean persist = false;
        for (String m : persistAfter) {
            if (m.equals(methodName)) {
                persist = true;
                break;
            }
        }
        Object result = null;
        synchronized (json) {
            result = method.invoke(json, args);
            if (persist) {
                this.persist();
            }
        }
        return result;
    }

    private volatile boolean persistStarted = false;

    private volatile PersistenceStatus status = PersistenceStatus.VACANT;

    private synchronized void persistStart() {
        if (!persistStarted) {
            persistStarted = true;
            new Thread(() -> {
                while (persistStarted) {
                    switch (status) {
                        case VACANT:
                            try {
                                Thread.sleep(PERSIST_WINDOW);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            break;
                        case WAITING:
                            synchronized (json) {
                                status = PersistenceStatus.WRITING;
                                try {
                                    json.wait(PERSIST_WINDOW);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case WRITING:
                            synchronized (json) {
                                try {
                                    l.debug(this + " persisting json data\n" + json);
                                    Writer writer = new FileWriter(file);
                                    Utils.writeJSON(json, writer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            status = PersistenceStatus.VACANT;
                            break;
                    }
                }
            }, "jsonPersistHandler").start();
            l.debug(this + " persist started");
        }
    }

    private synchronized void persistStop() {
        persistStarted = false;
        l.debug(this + " persist stopped");
    }

    private synchronized void persist() {
        status = PersistenceStatus.WAITING;
    }

    enum PersistenceStatus {
        VACANT, WAITING, WRITING
    }
}
