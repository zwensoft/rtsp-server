package com.sengled.cloud.spring.reports.redis;

import com.sengled.cloud.mediaserver.rtsp.RtspSession;

/**
 * 把 {@link RtspSession} 信息保存到 redis 中
 * 
 * @author 陈修恒
 * @date 2016年5月3日
 */
public class MediaResource extends AbstractRedisResource {
    protected MediaResource() {
        super(MEDIA);
    }
}
