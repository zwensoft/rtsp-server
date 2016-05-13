package com.sengled.cloud.http.metric;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;

import com.alibaba.fastjson.JSONWriter;

class Table {
    final private String name; 
    final private String[] colNames;
    final ConcurrentLinkedDeque<Row> rows = new ConcurrentLinkedDeque<Row>();

    public Table(String name, String colTemplate) {
        this.name = name;
        this.colNames = colTemplate.split(",");
        for (int i = 0; i < colNames.length; i++) {
            colNames[i] = StringUtils.trim(colNames[i]);
        }
       
    }

    public void addRow(Object... row) {
        rows.add(new Row(row));
        
        // 过期数据都删掉, 10s 一条记录的话， 一小时就是  360 条记录
        while (rows.size() > 1024) {
            rows.remove(0);
        }
    }
    
    public String getName() {
        return name;
    }
    
    public boolean ouput(OutputStream out, String col) throws IOException {
        int colIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            if (StringUtils.equals(col, colNames[i])) {
                colIndex = i;
                break;
            }
        }
        if (colIndex < 0) {
            return false;
        }

        
        JSONWriter writer = new JSONWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.startArray();
        
        
        // 只显示最近 30 分钟的数据
        long mixCreated = System.currentTimeMillis() - (1000 * 60 * 30);
        ArrayList<Row> arrayList = new ArrayList<Row>(rows);
        for (Row row : arrayList) {
            if (row.getCreated() < mixCreated) {
                continue;
            }

            writer.startObject();
            
            // value
            Object[] colValues = row.getColValues();
            writer.writeKey("value");
            if (colValues[colIndex] instanceof Number) {
                writer.writeValue(((Number)colValues[colIndex]).intValue());
            } else {
                writer.writeValue(String.valueOf(colValues[colIndex]));
            }
            
            // date
            writer.writeKey("date");
            writer.writeValue(DateFormatUtils.format(row.getCreated(), "yyyy-MM-dd HH:mm:ss"));
            writer.endObject();
        }
        
        writer.endArray();
        writer.close();
        
        return true;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{").append(getClass().getSimpleName());
        buf.append(", ").append(name);
        buf.append(", rows=").append(rows.size());
        buf.append("}");
        return buf.toString();
    }
}