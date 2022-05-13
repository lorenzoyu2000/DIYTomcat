package priv.mika.diytomcat.watcher;

import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.core.io.watch.WatchUtil;
import cn.hutool.core.io.watch.Watcher;
import cn.hutool.core.lang.Console;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.log.LogFactory;
import priv.mika.diytomcat.catalina.Context;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Context 文件改变监听器
 */
public class ContextFileChangeWatcher {
    private WatchMonitor monitor;
    private boolean stop = false;

    public ContextFileChangeWatcher(Context context) {
        /**
         * 通过WatchUtil.createAll 创建 监听器。
         * context.getDocBase() 代表监听的文件夹
         * Integer.MAX_VALUE 代表监听的深入，如果是0或者1，就表示只监听当前目录，而不监听子目录
         * new Watcher 当有文件发生变化，那么就会访问 Watcher 对应的方法
         */
        this.monitor = WatchUtil.createAll(context.getDocBase(), Integer.MAX_VALUE, new Watcher() {

            private void dealWith(WatchEvent<?> event) {
                /**
                 * 加上 synchronized 同步。 因为这是一个异步处理的，当文件发生变化，会发过来很多次事件。
                 * 所以我们得一个一个事件的处理，否则搞不好就会让 Context 重载多次。
                 */
                synchronized (ContextFileChangeWatcher.class) {
                    String fileName = event.context().toString();
                    if (stop) {
                        return;
                    }

                    if (fileName.endsWith(".jar") || fileName.endsWith(".class") || fileName.endsWith(".xml")) {
                        stop = true;
                        LogFactory.get().info(ContextFileChangeWatcher.this + " 检测到了Web应用下的重要文件变化 {} " , fileName);
                        ThreadUtil.sleep(500);
                        context.reload();
                    }
                }
            }

            @Override
            public void onCreate(WatchEvent<?> event, Path currentPath) {
                Console.log("ContextFileChangeWatcher 创建：{} -> {}", currentPath, event.context());
                dealWith(event);
            }

            @Override
            public void onModify(WatchEvent<?> event, Path currentPath) {
                Console.log("ContextFileChangeWatcher 修改：{} -> {}", currentPath, event.context());
                dealWith(event);
            }

            @Override
            public void onDelete(WatchEvent<?> event, Path currentPath) {
                Console.log("ContextFileChangeWatcher 删除：{} -> {}", currentPath, event.context());
                dealWith(event);
            }

            @Override
            public void onOverflow(WatchEvent<?> event, Path currentPath) {
                Console.log("ContextFileChangeWatcher onOverflow：{} -> {}", currentPath, event.context());
                dealWith(event);
            }
        });
        this.monitor.setDaemon(true);
    }

    public void start() {
        monitor.start();
    }

    public void stop() {
        monitor.close();
    }


}
