package priv.mika.diytomcat;

import priv.mika.diytomcat.classloader.CommonClassLoader;

import java.lang.reflect.Method;

/**
 *  启动程序
 */
public class Bootstrap {

    public static void main(String[] args) throws Exception {
        /**
         * CommonClassLoader加载Server的目的不是为了隔离，
         * 而是为了让Server使用 commonClassLoader里的类
         */
        CommonClassLoader commonClassLoader = new CommonClassLoader();

        /**
         * 设置此线程的上下文 ClassLoader
         *
         *  ClassLoader中只加载了lib下的jar，没有加载其他类，
         *  这里设置了上下文加载器，其他类都通过Bootstrap启动的，
         *  所以除了Bootstrap和CommonClassLoader是用应用类加载器加载的，
         *  其余类都是通过CommonClassLoader来加载。
         */
        Thread.currentThread().setContextClassLoader(commonClassLoader);

        String servletClassName = "priv.mika.diytomcat.catalina.Server";
        Class<?> serverClazz = commonClassLoader.loadClass(servletClassName);
        Object serverObject = serverClazz.newInstance();
        Method method = serverClazz.getMethod("start");
        method.invoke(serverObject);
    }
}

