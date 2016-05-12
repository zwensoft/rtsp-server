package com.sengled.cloud.http.metric;

public interface MetricsGraphics {

    Table getOrCreateTable(String name,
                   String type,
                   String colTemplates);

}
