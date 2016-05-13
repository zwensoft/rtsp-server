package com.sengled.cloud.monitor;

import java.io.InputStreamReader;
import java.io.LineNumberReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 计算 CPU 使用率
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class CpuUseRate {
    private static final Logger logger = LoggerFactory.getLogger(CpuUseRate.class);

    private final static String cpuUseRateCmd =
            "vmstat 1 3 | awk '{print $15}' | sed -n '3,5p' | awk '{sum+=$1} END {print sum/NR}'";
    private static final int CPUTIME = 30;

    private static final int PERCENT = 100;

    private static final int FAULTLENGTH = 10;


    private static CpuUseRate instance = new CpuUseRate();

    public static double calculate() {
        try {
            String[] cmdA = {"/bin/sh", "-c", cpuUseRateCmd};
            Process process = Runtime.getRuntime().exec(cmdA);
            LineNumberReader br =
                    new LineNumberReader(new InputStreamReader(process.getInputStream()));
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return 100 - Double.parseDouble(sb.toString());
        } catch (Exception e) {
            return instance.getCpuRatioForWindows();
        }
    }


    /**
     * 获得CPU使用率.
     * 
     * @return 返回cpu使用率
     * @author GuoHuang
     */
    private double getCpuRatioForWindows() {
        try {
            String procCmd =
                    System.getenv("windir")
                            + "\\system32\\wbem\\wmic.exe process get Caption,CommandLine,"
                            + "KernelModeTime,ReadOperationCount,ThreadCount,UserModeTime,WriteOperationCount";
            // 取进程信息
            long[] c0 = readCpu(Runtime.getRuntime().exec(procCmd));
            Thread.sleep(CPUTIME);
            long[] c1 = readCpu(Runtime.getRuntime().exec(procCmd));
            if (c0 != null && c1 != null) {
                long idletime = c1[0] - c0[0];
                long busytime = c1[1] - c0[1];
                return Double.valueOf(
                        PERCENT * (busytime) / (busytime + idletime))
                        .doubleValue();
            } else {
                return 0.0;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0.0;
        }
    }


    /**
     * 
     * 读取CPU信息.
     * 
     * @param proc
     * @return
     * @author GuoHuang
     */
    private long[] readCpu(final Process proc) {
        long[] retn = new long[2];
        try {
            proc.getOutputStream().close();
            InputStreamReader ir = new InputStreamReader(proc.getInputStream());
            LineNumberReader input = new LineNumberReader(ir);
            String line = input.readLine();
            if (line == null || line.length() < FAULTLENGTH) {
                return null;
            }
            int capidx = line.indexOf("Caption");
            int cmdidx = line.indexOf("CommandLine");
            int rocidx = line.indexOf("ReadOperationCount");
            int umtidx = line.indexOf("UserModeTime");
            int kmtidx = line.indexOf("KernelModeTime");
            int wocidx = line.indexOf("WriteOperationCount");
            long idletime = 0;
            long kneltime = 0;
            long usertime = 0;
            while ((line = input.readLine()) != null) {
                if (line.length() < wocidx) {
                    continue;
                }
                // 字段出现顺序：Caption,CommandLine,KernelModeTime,ReadOperationCount,
                // ThreadCount,UserModeTime,WriteOperation
                String caption = Bytes.substring(line, capidx, cmdidx - 1)
                        .trim();
                String cmd = Bytes.substring(line, cmdidx, kmtidx - 1).trim();
                if (cmd.indexOf("wmic.exe") >= 0) {
                    continue;
                }
                logger.debug("{}", line);
                if (caption.equals("System Idle Process")
                        || caption.equals("System")) {
                    idletime += Long.valueOf(
                            Bytes.substring(line, kmtidx, rocidx - 1).trim())
                            .longValue();
                    idletime += Long.valueOf(
                            Bytes.substring(line, umtidx, wocidx - 1).trim())
                            .longValue();
                    continue;
                }

                kneltime += Long.valueOf(
                        Bytes.substring(line, kmtidx, rocidx - 1).trim())
                        .longValue();
                usertime += Long.valueOf(
                        Bytes.substring(line, umtidx, wocidx - 1).trim())
                        .longValue();
            }
            retn[0] = idletime;
            retn[1] = kneltime + usertime;
            return retn;
        } catch (Exception ex) {
            logger.debug("{}", ex.getMessage());
        } finally {
            try {
                proc.getInputStream().close();
            } catch (Exception e) {
                logger.debug("fail close proc.inputStream for '{}'", e.getMessage());
            }
        }
        return null;
    }


    public static class Bytes {
        public static String substring(String src,
                                       int start_idx,
                                       int end_idx) {
            byte[] b = src.getBytes();
            String tgt = "";
            for (int i = start_idx; i <= end_idx; i++) {
                tgt += (char) b[i];
            }
            return tgt;
        }
    }
}
