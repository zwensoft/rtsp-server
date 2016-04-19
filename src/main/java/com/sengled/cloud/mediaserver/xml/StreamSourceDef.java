package com.sengled.cloud.mediaserver.xml;

import java.io.Serializable;
import java.net.MalformedURLException;

import com.sengled.cloud.mediaserver.url.URLObject;

/**
 * 一个视频源配置
 * 
 * @author chenxh
 */
public class StreamSourceDef implements Serializable {
	/** */
	private static final long serialVersionUID = 5499044237873780273L;
	private String name;
	private URLObject url;
	private String description;

	protected StreamSourceDef() {
	}

	public StreamSourceDef(String name, String url, String description)
			throws MalformedURLException {
		super();
		this.name = name;
		this.url = new URLObject(url);
		this.description = description;
	}

	public String getName() {
		return name;
	}


	public URLObject getUrl() {
		return url;
	}

	public String getDescription() {
		return description;
	}
}
