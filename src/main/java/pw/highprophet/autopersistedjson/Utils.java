package pw.highprophet.autopersistedjson;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.DefaultJSONParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Scanner;

/**
 * Created by HighProphet945 on 2017/9/18.
 */
public class Utils {
    public static DefaultJSONParser getJSONParser(InputStream is) {
        if (is == null) {
            throw new NullPointerException("Null argument: InputStream");
        }
        Scanner scanner = new Scanner(is);
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNext()) {
            sb.append(scanner.next());
        }
        return new DefaultJSONParser(sb.toString());
    }

    public static void writeJSON(JSON json, Writer writer) throws IOException {
        String jsonString = json.toJSONString();
        int written = 0;
        int length = 4096;
        do {
            if (jsonString.length() - written < length) {
                length = jsonString.length() - written;
            }
            writer.write(jsonString, written, length);
            writer.flush();
            written += length;
        } while (jsonString.length() > written);
        writer.close();
    }
}
