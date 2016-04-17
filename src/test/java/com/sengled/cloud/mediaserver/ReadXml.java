package com.sengled.cloud.mediaserver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

public class ReadXml {
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException, DocumentException {
		SAXReader reader = new SAXReader(false);
		
		InputStream in = ReadXml.class.getResourceAsStream("/config/streams.xml");
		
		Document doc = reader.read(in);
		Element root = doc.getRootElement();
		List<Element> streamEls = root.elements("stream");
		
		for (Element element : streamEls) {
			System.out.println(element.elementText("name"));
		}
		
		
	}
	
}
