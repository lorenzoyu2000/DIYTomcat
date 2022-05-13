package priv.mika.diytomcat.catalina;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import cn.hutool.system.SystemUtil;
import priv.mika.diytomcat.http.Request;
import priv.mika.diytomcat.http.Response;
import priv.mika.diytomcat.util.Constant;
import priv.mika.diytomcat.util.ThreadPoolUtil;
import priv.mika.diytomcat.util.WebXMLUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Connector implements Runnable {
    int port;
    private Service service;

    private String compression;
    private int compressionMinSize;
    private String noCompressionUserAgents;
    private String compressibleMimeType;

    public Connector(Service service) {
        this.service = service;
    }

    public Service getService() {
        return service;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void init() {
        LogFactory.get().info("Initializing ProtocolHandler [http-bio-{}]", port);
    }

    public void start() {
        LogFactory.get().info("Starting ProtocolHandler [http-bio-{}]", port);
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                // 开启一个socket连接,由服务端那个serverSocket来接受
                Socket socket = serverSocket.accept();
                // 将单线程应用改造为多线程
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Request request = new Request(socket, Connector.this);
                            Response response = new Response();
                            HttpProcessor httpProcessor = new HttpProcessor();
                            httpProcessor.execute(socket, request, response);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (!socket.isClosed())
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                        }
                    }
                };
                ThreadPoolUtil.run(runnable);
            }
        } catch (IOException e) {
            LogFactory.get().error(e);
            e.printStackTrace();
        }
    }


    /**
     * @Description log4j打印日志
     */
    private static void logJVM() {
        Map<String, String> infos = new LinkedHashMap<>();
        infos.put("Server version", "StarMika DiyTomcat/1.0");
        infos.put("Server built", "2022-01-14 10:20:22");
        infos.put("Server number", "1.0");
        infos.put("OS Name\t", SystemUtil.get("os.name"));
        infos.put("OS Version", SystemUtil.get("os.version"));
        infos.put("Architecture", SystemUtil.get("os.arch"));
        infos.put("Java Home", SystemUtil.get("java.home"));
        infos.put("JVM Version", SystemUtil.get("java.runtime.version"));
        infos.put("JVM Vendor", SystemUtil.get("java.vm.specification.vendor"));
        Set<String> keys = infos.keySet();
        for (String key : keys) {
            LogFactory.get().info(key + ":\t\t" + infos.get(key));
        }
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }

    public String getNoCompressionUserAgents() {
        return noCompressionUserAgents;
    }

    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        this.noCompressionUserAgents = noCompressionUserAgents;
    }

    public String getCompressibleMimeType() {
        return compressibleMimeType;
    }

    public void setCompressibleMimeType(String compressibleMimeType) {
        this.compressibleMimeType = compressibleMimeType;
    }
}
