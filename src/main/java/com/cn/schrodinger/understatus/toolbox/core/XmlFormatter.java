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

    private static final java.util.regex.Pattern MINIFY_PATTERN = java.util.regex.Pattern.compile(">\\s+<");
    private static final TransformerFactory TRANSFORMER_FACTORY;

    static {
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (Exception ignored) {}
        TRANSFORMER_FACTORY = tf;
    }

    public static String format(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return "";
        }
        try {
            Source xmlInput = new StreamSource(new StringReader(xml));
            StringWriter stringWriter = new StringWriter(xml.length() * 3 / 2);
            StreamResult xmlOutput = new StreamResult(stringWriter);
            
            Transformer transformer;
            synchronized (TRANSFORMER_FACTORY) {
                transformer = TRANSFORMER_FACTORY.newTransformer();
            }
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
        return MINIFY_PATTERN.matcher(xml).replaceAll("><").trim();
    }
}
