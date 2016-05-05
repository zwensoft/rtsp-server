package com.sengled.cloud.mediaserver.xml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * 用于加载  视频源 配置文件 
 * @author 陈修恒
 */
public class MediaServerConfigs {
	private String mode;
	private int[] ports = new int[]{5454};
	private List<StreamSourceDef> streamSources = Collections.emptyList();
	
	
	
	
	/**
	 * read stream source definition from xml file
	 * 
	 * @param xmlFileUrl  
	 * @return
	 * @throws IOException
	 */
	public static MediaServerConfigs load(String xmlFileUrl) throws IOException {
		FileInputStream in = new FileInputStream(xmlFileUrl);
		
		try {
			return load(in);
		} catch (DocumentException e) {
			throw new IOException( "'" + xmlFileUrl + "' NOT xml file");
		} finally {
			IOUtils.closeQuietly(in);
		}
	}
	
	/**
	 * @param in  xml  input stream
	 * @return
	 * @throws DocumentException
	 * @throws IOException
	 */
	public static MediaServerConfigs load(InputStream in) throws DocumentException, IOException {
		Document doc;
		SAXReader reader = new SAXReader();
		doc = reader.read(in);
		
		
		// <streams><stream>
		List<StreamSourceDef> streams = new ArrayList<StreamSourceDef>();
		Element streamsEl = doc.getRootElement().element("streams");
		if (null != streamsEl) {
			@SuppressWarnings("unchecked")
			List<Element> streamEls = streamsEl.elements("stream");
			
			String name;
			String url;
			String descript;
			for (Element streamEl : streamEls) {
				name = streamEl.elementTextTrim("name");
				url = streamEl.elementTextTrim("url");
				descript = streamEl.elementTextTrim("description");
				
				streams.add(new StreamSourceDef(name, url, descript));
			}
		}

		// <ports><port>
		int[] ports = new int[] { 5454 };
		Element portsEl = doc.getRootElement().element("ports");
		if (null != portsEl) {
			@SuppressWarnings("unchecked")
			List<Element> portELs = (List<Element>)portsEl.elements("port");
			ports = new int[portELs.size()]; 
			for (int i = 0; i < ports.length; i++) {
				ports[i] = Integer.parseInt(portELs.get(i).getTextTrim());
				if (ports[i] < 0 || ports[i] > 65535) {
					throw new IOException("illegal port [" + ports[i] + "]");
				}
			}
		}
		
        //<mode>
        String mode = doc.getRootElement().elementTextTrim("mode");

		MediaServerConfigs configs = new MediaServerConfigs();
		configs.streamSources = streams;
		configs.ports = ports;
		configs.mode = mode;
		return configs;
	}
	
	public int[] getPorts() {
		return ports;
	}
	
	public List<StreamSourceDef> getStreamSources() {
		return streamSources;
	}
	
	public String getMode() {
        return mode;
    }
}
