package priv.mika.diytomcat.servlets;

import cn.hutool.core.util.ReflectUtil;
import priv.mika.diytomcat.catalina.Context;
import priv.mika.diytomcat.http.Request;
import priv.mika.diytomcat.http.Response;
import priv.mika.diytomcat.util.Constant;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.rmi.RemoteException;

public class InvokerServlet extends HttpServlet {
    /**
     *   静态内部类懒汉式
     *   什么设计成单例？因为创建对象会有开销，这里如果不设计成单例，每次都会创建新的对象。
     */
    private InvokerServlet(){
    }
    // 使用内部类的方式来实现懒加载
    private static class LazyHolder {
        // 创建单例对象
        private static InvokerServlet instance = new InvokerServlet();
    }
    public static synchronized InvokerServlet getInstance() {
        return LazyHolder.instance;
    }

    public void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException {

        Request request = (Request) httpServletRequest;
        Response response = (Response) httpServletResponse;
        String uri = request.getUri();
        Context context = request.getContext();
        String servletClassName = context.getServletClassName(uri);

        try {
            /**
             * 在实例化 servlet 对象的时候，根据类名称，
             * 通过 context.getWebappClassLoader().loadClass() 方法去获取类对象，
             * 后面再根据这个类对象，实例化出 servlet 对象出来。
             */
            Class servletClass = context.getWebappClassLoader().loadClass(servletClassName);
            Object servletObject = context.getHttpServlet(servletClass);
            ReflectUtil.invoke(servletObject, "service", request, response);

            if (null != response.getRedirectPath()) {
                response.setStatus(Constant.CODE_302);
            } else {
                response.setStatus(Constant.CODE_200);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
