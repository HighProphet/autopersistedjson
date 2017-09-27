package pw.highprophet.autopersistedjson;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import pw.highprophet.sharedutils.Utils;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

public class AutoPersistedJSONFactory {

    private static final Logger l = LogManager.getLogger(AutoPersistedJSONFactory.class);


    /**
     * 获取一个持久化JSON实例.
     * 以<tt>json</tt>参数内容为准,当json的内容改动时,将其内容写入<tt>persistenceTarget</tt>中
     *
     * @param json              未持久化的JSON实例
     * @param persistenceTarget 持久化文件
     * @param <T>               JSON类型,JSONObject或JSONArray
     * @return 持久化的JSON实例
     * @throws IOException 写入或创建文件时失败
     */
    @SuppressWarnings("unchecked")
    public static <T extends JSON> T getPersistedJSON(T json, File persistenceTarget) throws IOException {
        if (!persistenceTarget.exists()) {
            File parent = persistenceTarget.getParentFile();
            if (!parent.mkdirs()) {
                throw new IOException("Can't create parent folder[" + parent.getAbsolutePath() + "]");
            }
            if (!persistenceTarget.createNewFile()) {
                throw new IOException("Can't create target File[" + persistenceTarget.getAbsolutePath() + "]");
            }
        }
        Utils.writeJSON(json, new OutputStreamWriter(new FileOutputStream(persistenceTarget), "UTF-8"));
        return manufacture(new JSONPersistHandler(json, persistenceTarget), json.getClass());
    }

    /**
     * 获取一个持久化JSON实例.
     * 解析<tt>from</tt>中的内容为JSON,若格式不正确或为空则视为空JSON实例
     *
     * @param from  持久化目标文件
     * @param clazz 实例类型对象,只能是JSONObject.class或JSONArray.class其中之一
     * @param <T>   实例类型,只能是JSONObject或JSONArray其中之一
     * @return 持久化JSON实例
     * @throws IOException 读取文件失败
     */
    @SuppressWarnings("unchecked")
    public static <T extends JSON> T getPersistedJSON(File from, Class<? extends JSON> clazz) throws IOException {
        JSON json = null;
        try {
            json = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            l.error("Exception!", e);
        }
        if (from.length() > 0) {
            try {
                json = clazz.cast(Utils.getJSONParser(new FileInputStream(from)).parse());
            } catch (JSONException e) {
                e.printStackTrace();
                l.info("文件[" + from + "]格式不正确,使用空对象覆盖");
            }
        }
        return manufacture(new JSONPersistHandler(json, from), clazz);
    }

    @SuppressWarnings("unchecked")
    private static <T extends JSON> T manufacture(JSONPersistHandler handler, Class<? extends JSON> clazz) {
        Class iface;
        if (clazz == JSONObject.class) {
            iface = Map.class;
        } else {
            iface = List.class;
        }
        Constructor<? extends JSON> constructor = null;
        try {
            constructor = clazz.getDeclaredConstructor(iface);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            l.error("Exception!", e);
            System.exit(0);
        }
        try {
            return (T) constructor.newInstance(Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{iface}, handler));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            l.error("Exception!", e);
            return null;
        }
    }


}
