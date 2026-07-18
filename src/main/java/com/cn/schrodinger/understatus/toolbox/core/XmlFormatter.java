package com.cn.schrodinger.understatus.toolbox.core;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Standard XML prettifier and minifier utility class.
 * Built-in JDK compliance with zero third-party dependencies.
 *
 * @author peter/antigravity
 */
public class XmlFormatter {

    public static String format(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return "";
        }
        try {
            Source xmlInput = new StreamSource(new StringReader(xml));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            // Restrict external access to prevent XXE vulnerabilities
            transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString().trim();
        } catch (Exception ex) {
            return "XML Format Failed: " + ex.getMessage();
        }
    }

    public static String minify(String xml) {
        if (xml == null) {
            return "";
        }
        // Trim whitespaces between nested XML nodes
        return xml.replaceAll(">\\s+<", "><").trim();
    }
}
