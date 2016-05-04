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
public class TimeoutExecutor {
    private static final Logger logger = LoggerFactory.getLogger(TimeoutExecutor.class);
    
    private final Timer timer;
    
    public TimeoutExecutor() {
        timer = new Timer(true);
    }
    
    public TimeoutExecutor(String name) {
        timer = new Timer(name, true);
    }
    
    public <T> TimerTask setTimeout(final Callable<T> task, long delay) {
        final TimerTask timerTask = new TimerWraper<T>(task, true);

        timer.schedule(timerTask, delay);
        return timerTask;
    }
    
    public TimerTask setInterval(final Callable<Boolean> task, long delay, long period) {
        final TimerTask timerTask = new TimerWraper<Boolean>(task, false);

        timer.scheduleAtFixedRate(timerTask, delay, period);
        return timerTask;
    }
    
    private static class TimerWraper<T> extends TimerTask {
        private Callable<T> task;
        private boolean autoCancle = false;
        
        public TimerWraper(Callable<T> task, boolean autoCancle) {
            super();
            this.task = task;
            this.autoCancle = autoCancle;
        }

        @Override
        public void run() {
            try {
                T cancle = task.call();
                
                boolean cancleIt = autoCancle;
                if(!cancleIt && cancle instanceof Boolean) {
                    cancleIt = (Boolean)cancle;
                }
                
                if(cancleIt) {
                    cancel();
                }
            } catch(Exception e) {
                logger.warn("fail execute interval task '{}'", task, e);
                cancel();
            }
        }
    }
}
