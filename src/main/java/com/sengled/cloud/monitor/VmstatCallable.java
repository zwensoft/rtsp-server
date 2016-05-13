package com.sengled.cloud.monitor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sengled.cloud.monitor.VmstatCallable.VmstatInfo;

/**
 * 执行 vmstat 命令
 * 
 * @author 陈修恒
 * @date 2016年5月13日
 */
public class VmstatCallable implements Callable<VmstatInfo>{
    private static final Logger logger = LoggerFactory.getLogger(VmstatCallable.class);

    public VmstatCallable() {
    }

    @Override
    public VmstatInfo call() throws Exception {
        String[] cmdA = {"/bin/sh", "-c", "vmstat 1 3 | awk '{print $13,$14,$15,$17}'"};
        Process p = Runtime.getRuntime().exec(cmdA);
        try {
            LineNumberReader br =
                    new LineNumberReader(new InputStreamReader(p.getInputStream()));
            
            String[] cols = StringUtils.split(readLine(br));
            VmstatInfo vmstat = new VmstatInfo(cols);
            
            
            String line;
            while ((line = br.readLine()) != null) {
                String[] stringValues = StringUtils.split(line.trim());
                int[] intValues = new int[stringValues.length];
                for (int i = 0; i < stringValues.length; i++) {
                    intValues[i] += Integer.parseInt(stringValues[i]);
                }
                
                vmstat.rows.add(intValues);
            }

            return vmstat;
        } finally {
            p.destroy();
        }
    }
    
    private String readLine(BufferedReader reader) throws IOException {
        String line;
        line = reader.readLine();
        while (null != line && line.trim().length() == 0) {
            line = reader.readLine();
        }

        logger.info("[{}]", line);
        return null != line ? line.trim() : null;
    }
    
    public static final class VmstatInfo {
        private String[] colNames;
        private List<int[]> rows = new ArrayList<int[]>();
        
       
        public VmstatInfo(String[] colNames) {
            super();
            this.colNames = colNames;
        }
        
        public String[] getColNames() {
            return colNames;
        }
        
        /**
         * @param colName
         * @return -1 for unknown
         */
        public double getAverageValue(String colName) {
            if (rows.isEmpty()) {
                return -1;
            }

            int colIndex = ArrayUtils.indexOf(colNames, colName);
            if (ArrayUtils.INDEX_NOT_FOUND == colIndex) {
                return -1;
            }

            int sum = 0;
            for (int[] row : rows) {
                sum += row[colIndex];
            }
            
            return sum / rows.size();
        }
        
    }
}
