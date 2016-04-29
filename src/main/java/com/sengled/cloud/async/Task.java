package com.sengled.cloud.async;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

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
    
    public static TimerTask setTimeout(final Runnable task, long delay) {
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch(Exception e) {
                    logger.warn("fail execute timeout task '{}'", task, e);
                    cancel();
                }
            }
        };

        timer.schedule(timerTask, delay);
        return timerTask;
    }
    
    public static TimerTask setInterval(final Runnable task, long delay, long period) {
        final TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch(Exception e) {
                    logger.warn("fail execute interval task '{}'", task, e);
                    cancel();
                }
            }
        };

        timer.scheduleAtFixedRate(timerTask, delay, period);
        return timerTask;
    }
    
    
    public static void main(String[] args) throws IOException {
        Task.setTimeout(new Runnable() {
            
            @Override
            public void run() {
                System.out.println("<<heloo");
            }
        }, 25);
        
        Task.setInterval(new Runnable() {
            
            @Override
            public void run() {
                System.out.println(2);
            }
        }, 250, 1200);

        System.in.read();
    }
}
