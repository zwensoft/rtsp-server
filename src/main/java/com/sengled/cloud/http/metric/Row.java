package com.sengled.cloud.http.metric;

import java.io.Serializable;

class Row implements Serializable {
    /** */
    private static final long serialVersionUID = 1L;
    private long created;
    private Object[] cols;

    public Row(Object[] cols) {
        this.created = System.currentTimeMillis();
        this.cols = cols;
    }
    
    public Object[] getColValues() {
        return cols;
    }
    
    public long getCreated() {
        return created;
    }

}