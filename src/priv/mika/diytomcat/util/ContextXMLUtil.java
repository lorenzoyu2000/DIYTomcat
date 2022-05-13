package priv.mika.diytomcat.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ContextXMLUtil {
    public static String getWatchedResource() {
        try {
            String xml = FileUtil.readUtf8String(Constant.contextXmlFile);
            Document document = Jsoup.parse(xml);
            Element element = document.select("WatchedResource").first();
            return element.text();

        } catch (IORuntimeException e) {
            e.printStackTrace();
            return "WEB-INF/web.xml";
        }
    }
}
