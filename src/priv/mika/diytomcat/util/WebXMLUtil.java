package priv.mika.diytomcat.util;

import cn.hutool.core.io.FileUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import priv.mika.diytomcat.catalina.Context;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class WebXMLUtil {
    // 将mimeType 和 后缀名对应
    private static Map<String, String> mimeTypeMapping = new HashMap<>();
    /**
     *  根据后缀名获取mimeType.第一次调用会初始化，如果找不到对应的，就默认返回 "text/html"
     *  这里做了 synchronized 线程安全的处理，
     *  因为会调用 initMimeType 进行初始化，如果两个线程同时来，那么可能导致被初始化两次。
     */
    public static synchronized String getMimeType(String extensionName) {
        if (mimeTypeMapping.isEmpty()) {
            initMimeType();
        }

        String mimeType = mimeTypeMapping.get(extensionName);
        if (null == mimeType) {
            return "text/html";
        }
        return mimeType;
    }

    /**
     *  根据 Context的 docBase 去匹配 web.xml 中的3个文件，
     *  找到哪个，就是哪个，如果都没有找到，默认就返回 index.html 文件
     */
    public static String getWelcomeFile(Context context) {
        String xml = FileUtil.readUtf8String(Constant.webXmlFile);
        Document document = Jsoup.parse(xml);

        Elements elements = document.select("welcome-file");
        for (Element element : elements) {
            String welcomeFileName = element.text();
            File file = new File(context.getDocBase(), welcomeFileName);
            if (file.exists()) {
                return file.getName();
            }
        }
        return "index.html";
    }

    /**
     *  读取web.xml，将文件扩展名和mimeType存入HashMap
     */
    private static void initMimeType() {
        String xml = FileUtil.readUtf8String(Constant.webXmlFile);
        Document document = Jsoup.parse(xml);
        Elements elements = document.select("mime-mapping");
        for (Element element : elements) {
            String extensionName = element.select("extension").text();
            String mimeType = element.select("mime-type").text();
            mimeTypeMapping.put(extensionName, mimeType);
        }
    }
}
