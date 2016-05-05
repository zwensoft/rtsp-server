package com.sengled.cloud.mediaserver.xml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private Map<String, Integer> ports = Collections.emptyMap();
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
		Map<String, Integer> ports = new HashMap<String, Integer>();
		Element portsEl = doc.getRootElement().element("ports");
		if (null != portsEl) {
			@SuppressWarnings("unchecked")
			List<Element> portELs = (List<Element>)portsEl.elements();
			for (Element element : portELs) {
			    String name = element.getName();
                String portString = element.getTextTrim();
                
                int port = Integer.parseInt(portString);
                if (port < 0 || port > 65535) {
                    throw new IOException("illegal port [" + port + "]");
                }
                ports.put(name, port);
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
	
	public Map<String, Integer> getPorts() {
		return ports;
	}
	
	public List<StreamSourceDef> getStreamSources() {
		return streamSources;
	}
	
	public String getMode() {
        return mode;
    }
}
