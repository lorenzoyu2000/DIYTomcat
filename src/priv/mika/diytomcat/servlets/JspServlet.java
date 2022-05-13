package priv.mika.diytomcat.servlets;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import priv.mika.diytomcat.catalina.Context;
import priv.mika.diytomcat.classloader.JspClassLoader;
import priv.mika.diytomcat.http.Request;
import priv.mika.diytomcat.http.Response;
import priv.mika.diytomcat.util.Constant;
import priv.mika.diytomcat.util.JspUtil;
import priv.mika.diytomcat.util.WebXMLUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * JspServlet 的基本处理逻辑应该是先把 jsp 转换为 java 文件，然后编译成 class 文件，再加载之后运行。
 */
public class JspServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static JspServlet instance = new JspServlet();

    public static synchronized JspServlet getInstance() {
        return instance;
    }

    private JspServlet() {

    }

    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException, ServletException {
        try {
            Request request = (Request) httpServletRequest;
            Response response = (Response) httpServletResponse;
            String uri = request.getRequestURI();
            if ("/".equals(uri)) {
                uri = WebXMLUtil.getWelcomeFile(request.getContext());
            }

            String fileName = StrUtil.removePrefix(uri, "/");
            File file = FileUtil.file(request.getRealPath(fileName));
            File jspFile = file;
            if (jspFile.exists()) {
                Context context = request.getContext();
                String path = context.getPath();
                // subFolder 这个变量是用于处理 ROOT的，对于ROOT 这个 webapp 而言，
                // 它的 path 是 "/", 那么在 work 目录下，对应的应用目录就是 "_"。
                String subFolder;
                if ("/".equals(path)) {
                    subFolder = "_";
                } else {
                    subFolder = StrUtil.subAfter(path, '/', false);
                }

                String servletClassPath = JspUtil.getServletClassPath(uri, subFolder);
                File jspServletClassFile = new File(servletClassPath);
                if (!jspServletClassFile.exists()) {
                    JspUtil.compileJsp(context, jspFile);
                } else if (jspFile.lastModified() > jspServletClassFile.lastModified()) {
                    JspUtil.compileJsp(context, jspFile);
                    JspClassLoader.invalidJspClassLoader(uri, context);
                }

                String extName = FileUtil.extName(file);
                String mimeType = WebXMLUtil.getMimeType(extName);
                response.setContentType(mimeType);

                JspClassLoader jspClassLoader = JspClassLoader.getJspClassLoader(uri, context);
                String jspServletClassName = JspUtil.getJspServletClassName(uri, subFolder);
                Class jspServletClass = jspClassLoader.loadClass(jspServletClassName);

                HttpServlet httpServlet = context.getHttpServlet(jspServletClass);
                httpServlet.service(request, response);
                if (null != response.getRedirectPath()) {
                    response.setStatus(Constant.CODE_302);
                } else {
                    response.setStatus(Constant.CODE_200);
                }
            } else {
                response.setStatus(Constant.CODE_404);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
