package priv.mika.diytomcat.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import priv.mika.diytomcat.catalina.*;

import java.util.ArrayList;
import java.util.List;
/**
 * 获取server.xml中的元素
 * 在前面的创建 tomcat 内置对象： Server, Service, Engine, Host, Context 的过程中，
 * 本质上就是把 server.xml 里的各个元素映射到上述这些类的实例里面去了， 借用的是 ServerXMLUtil 这个工具进行的。
 */
public class ServerXMLUtil {
    public static List<Context> getContexts(Host host) {
        List<Context> result = new ArrayList<>();
        // 获取server.xml内容
        String xml = FileUtil.readUtf8String(Constant.serverXmlFile);
        // 将xml装换为Document
        Document document = Jsoup.parse(xml);

        Elements elements = document.select("Context");
        for (Element e : elements) {
            String path = e.attr("path");
            String docBase = e.attr("docBase");

            boolean reloadable = Convert.toBool(e.attr("reloadable"), true);
            Context context = new Context(path, docBase, host, reloadable);
            result.add(context);
        }
        return result;
    }

    public static List<Host> getHosts(Engine engine) {
        List<Host> result = new ArrayList<>();
        String xml = FileUtil.readUtf8String(Constant.serverXmlFile);
        Document document = Jsoup.parse(xml);

        Elements elements = document.select("Host");
        for (Element element : elements) {
            String name = element.attr("name");
            Host host = new Host(name, engine);
            result.add(host);
        }
        return result;
    }


    public static String getEngineDefaultHost() {
        String xml = FileUtil.readUtf8String(Constant.serverXmlFile);
        Document document = Jsoup.parse(xml);
        Element host = document.select("Engine").first();
        return host.attr("defaultHost");
    }

    public static String getServiceName() {
        String xml = FileUtil.readUtf8String(Constant.serverXmlFile);
        Document document = Jsoup.parse(xml);
        Element host = document.select("Service").first();
        return host.attr("name");
    }
    /**
     *   获取 Connectors 集合
     */
    public static List<Connector> getConnectors(Service service) {
        List<Connector> result = new ArrayList<>();
        String xml = FileUtil.readUtf8String(Constant.serverXmlFile);
        Document document = Jsoup.parse(xml);
        Elements elements = document.select("Connector");

        for (Element element : elements) {
            int port = Convert.toInt(element.attr("port"));
            String compression = element.attr("compression");
            int compressionMinSize = Convert.toInt(element.attr("compressionMinSize"), 0);
            String noCompressionUserAgents = element.attr("noCompressionUserAgents");
            String compressibleMimeType = element.attr("compressibleMimeType");
            Connector connector = new Connector(service);
            connector.setPort(port);
            connector.setCompression(compression);
            connector.setCompressionMinSize(compressionMinSize);
            connector.setNoCompressionUserAgents(noCompressionUserAgents);
            connector.setCompressibleMimeType(compressibleMimeType);
            result.add(connector);
        }
        return result;
    }

}
