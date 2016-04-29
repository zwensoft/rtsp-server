package com.sengled.cloud.async;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单线程顺序执行的任务。
 * 
 * 类似与浏览器招工的 setTimeout, setInterval
 * 
 * @author 陈修恒
 * @date 2016年4月28日
 */
public class Task {
    private static final Logger logger = LoggerFactory.getLogger(Task.class);
    
    private static final Timer timer = new Timer(true);
    
    public static TimerTask setTimeout(final Callable<Boolean> task, long delay) {
        final TimerTask timerTask = new TimerWraper(task, true);

        timer.schedule(timerTask, delay);
        return timerTask;
    }
    
    public static TimerTask setInterval(final Callable<Boolean> task, long delay, long period) {
        final TimerTask timerTask = new TimerWraper(task, false);

        timer.scheduleAtFixedRate(timerTask, delay, period);
        return timerTask;
    }
    
    private static class TimerWraper extends TimerTask {
        private Callable<Boolean> task;
        private boolean autoCancle = false;
        
        public TimerWraper(Callable<Boolean> task, boolean autoCancle) {
            super();
            this.task = task;
            this.autoCancle = autoCancle;
        }

        @Override
        public void run() {
            try {
                Boolean cancle = task.call();
                if(autoCancle || (null != cancle && cancle)) {
                    cancel();
                }
            } catch(Exception e) {
                logger.warn("fail execute interval task '{}'", task, e);
                cancel();
            }
        }
    }
}
