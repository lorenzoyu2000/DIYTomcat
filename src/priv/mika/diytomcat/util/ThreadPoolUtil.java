package priv.mika.diytomcat.util;

import cn.hutool.core.thread.NamedThreadFactory;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
    /**
     * 创建线程池
     */
public class ThreadPoolUtil {
    private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(20, 100, 60, TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(10));

    public static void run(Runnable runnable) {
        threadPool.execute(runnable);
    }
}
