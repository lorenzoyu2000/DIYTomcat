package priv.mika.diytomcat.servlets;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import priv.mika.diytomcat.catalina.Context;
import priv.mika.diytomcat.http.Request;
import priv.mika.diytomcat.http.Response;
import priv.mika.diytomcat.util.Constant;
import priv.mika.diytomcat.util.WebXMLUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

public class DefaultServlet extends HttpServlet {
    private DefaultServlet() {

    }
    private static DefaultServlet instance = new DefaultServlet();

    public static synchronized DefaultServlet getInstance() {
        return instance;
    }
    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        Request request = (Request) httpServletRequest;
        Response response = (Response) httpServletResponse;
        Context context = request.getContext();

        String uri = request.getUri();
        if ("/500.html".equals(uri)) {
            throw new RuntimeException("this is a deliberately created exception");
        }

        if ("/".equals(uri)) {
            uri = WebXMLUtil.getWelcomeFile(request.getContext());
        }

        if (uri.endsWith(".jsp")) {
            JspServlet.getInstance().service(request,response);
            return;
        }

        // 取出文件名，并在目录的绝对路径下查找文件
        String fileName = StrUtil.removePrefix(uri, "/");
        File file = FileUtil.file(context.getDocBase(), fileName);

//        // 访问二级目录以上的index.html
//        if (!file.isFile()) {
//            uri = uri + "/" + WebXMLUtil.getWelcomeFile(request.getContext());
//            fileName = StrUtil.removePrefix(uri, "/");
//            file = new File(context.getDocBase(), fileName);
//        }

        // 如果文件存在，那么获取内容并通过 response.getWriter 打印。
        if (file.exists()) {
            // 获取文件扩展名并返回对应 mimeType
            String extensionName = FileUtil.extName(file);
            String mimeType = WebXMLUtil.getMimeType(extensionName);
            response.setContentType(mimeType);
            // 文件读取成二进制返回给response的body
            byte[] body = FileUtil.readBytes(file);
            response.setBody(body);

            if (fileName.equals("timeConsume.html")) {
                ThreadUtil.sleep(1000);
            }
            response.setStatus(Constant.CODE_200);
        } else {
            response.setStatus(Constant.CODE_404);
        }
    }
}
