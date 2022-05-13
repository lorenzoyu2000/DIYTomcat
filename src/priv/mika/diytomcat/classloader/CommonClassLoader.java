package priv.mika.diytomcat.classloader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
/**
 * @Description  没写公共类加载器时，lib下得jar包也被加载了，是因为用IDE启动的，用bat方式启动就不会加载
 *               类加载器的目的是为了对类进行隔离管理
 */
public class CommonClassLoader extends URLClassLoader {
    public CommonClassLoader() {
        super(new URL[]{});
        try {

            File workingFolder = new File(System.getProperty("user.dir"));
            File libFolder = new File(workingFolder, "lib");

            File[] jarFiles = libFolder.listFiles();
            for (File file : jarFiles) {
                if (file.getName().endsWith("jar")) {
                    URL url = new URL("file:" + file.getAbsolutePath());
                    this.addURL(url);
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }
}

