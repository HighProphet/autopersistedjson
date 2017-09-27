package pw.highprophet.autopersistedjson;

import com.alibaba.fastjson.JSON;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import pw.highprophet.sharedutils.Utils;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class JSONPersistHandler implements InvocationHandler {

    private static final Logger l = LogManager.getLogger(JSONPersistHandler.class);

    private static List<JSONPersistHandler> handlerPool;

    private static String[] ARRAY_PERSIST_AFTER = {"clear", "remove", "add", "addAll", "set"};

    private static String[] OBJECT_PERSIST_AFTER = {"remove", "put", "putAll", "putIfAbsent", "merge", "replace"};

    private static final long PERSIST_WINDOW = 500;

    private static long vacantLogInterval;

    //初始化持久化对象池,并在系统退出时将持久化线程结束
    static {
        handlerPool = new ArrayList<>();
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(new Thread(() -> {
            for (JSONPersistHandler handler : handlerPool) {
                handler.persistStop();
            }
            for (JSONPersistHandler handler : handlerPool) {
                try {
                    handler.persistRunner.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    l.debug(e);
                }
            }
        }));
        Properties prop = new Properties();
        InputStream is = JSONPersistHandler.class.getClassLoader().getResourceAsStream("log4j.properties");
        try {
            prop.load(is);
        } catch (IOException e) {
            e.printStackTrace();
            l.error(e);
        }
        vacantLogInterval = Long.parseLong(prop.getProperty("json.persist.VacantLogInterval", "1200000"));
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
        //若此文件已和其他JSON对象绑定,则先将其找出并停止持久化操作
        JSONPersistHandler handler = handlerPool.stream().filter(h -> h.file.equals(file))
                .findFirst().orElse(null);
        if (handler != null) {
            l.info("File[" + file + "] has been bound to another JSON,unbinding.");
            handler.persistStop();
            handlerPool.remove(handler);
        }
        handlerPool.add(this);
        this.persistStart();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        boolean persist = Arrays.stream(persistAfter).anyMatch(s -> s.equals(methodName));
        Object result;
        wLock.lock();
        try {
            result = method.invoke(json, args);
        } finally {
            wLock.unlock();
        }
        if (persist) {
            this.persist();
        }
        return result;
    }

    private ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
    private Lock wLock = reentrantReadWriteLock.writeLock();
    private Lock rLock = reentrantReadWriteLock.readLock();
    private LinkedList<Event> eventQueue = new LinkedList<>();
    private Thread persistRunner;

    private void persistStart() {
        persistRunner = new Thread(() -> {
            boolean readyToPersist = false;
            long waitingCount = 0;
            rLock.lock();
            while (true) {
                Event event;
                synchronized (this) {
                    event = eventQueue.poll();
                }
                if (event == null) {
                    if (readyToPersist) {
                        doPersist();
                        readyToPersist = false;
                        waitingCount = 0;
                    } else {
                        if (waitingCount == 0) {
                            l.debug("nothing to persist, waiting");
                            waitingCount = -vacantLogInterval;
                        } else {
                            waitingCount += PERSIST_WINDOW;
                        }
                        sleep();
                    }
                } else if (event == Event.PERSIST) {
                    readyToPersist = true;
                    l.debug("will persist after modifications");
                    sleep();
                } else if (event == Event.STOP) {
                    if (readyToPersist) {
                        doPersist();
                    }
                    l.debug("JSON[" + file.getName() + "] persist stopped");
                    break;
                }
            }
            rLock.unlock();
        }, toString());
        persistRunner.start();
        l.debug("JSON[" + file.getName() + "] persist started");
    }

    private void doPersist() {
        try {
            l.debug(this + " persisting json data\n" + json);
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            Utils.writeJSON(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
            l.error(e);
        }
    }

    private void sleep() {
        rLock.unlock();
        try {
            Thread.sleep(PERSIST_WINDOW);
        } catch (InterruptedException e) {
            e.printStackTrace();
            l.error(e);
        } finally {
            rLock.lock();
        }
    }

    private synchronized void persistStop() {
        eventQueue.add(Event.STOP);
    }

    private synchronized void persist() {
        eventQueue.add(Event.PERSIST);
    }

    @Override
    public String toString() {
        return "JSONPersistHandler[" + file.getName() + "]";
    }

    enum Event {
        PERSIST, STOP
    }
}
