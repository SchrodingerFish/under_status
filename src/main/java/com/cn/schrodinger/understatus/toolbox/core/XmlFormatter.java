package com.cn.schrodinger.understatus.toolbox.core;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Secure XML formatting which preserves mixed content and xml:space. */
public final class XmlFormatter {
    private XmlFormatter() {}
    public static String format(String xml) { return transform(xml, true); }
    public static String minify(String xml) { return transform(xml, false); }

    private static String transform(String xml, boolean indent) {
        if (xml == null || xml.isBlank()) return "";
        try {
            DocumentBuilderFactory parser = DocumentBuilderFactory.newInstance();
            parser.setNamespaceAware(true);
            parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            parser.setXIncludeAware(false);
            parser.setExpandEntityReferences(false);
            var builder = parser.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
                @Override public void fatalError(org.xml.sax.SAXParseException ex) throws org.xml.sax.SAXException { throw ex; }
            });
            var document = builder.parse(new InputSource(new StringReader(xml)));
            // In the absence of a schema, even whitespace-only text may be significant.
            // Compact mode does not discard text nodes.
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, indent && !sensitive(document) ? "yes" : "no");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, xml.stripLeading().startsWith("<?xml") ? "no" : "yes");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString().trim();
        } catch (Exception ex) {
            return "XML Format Failed: " + ex.getMessage();
        }
    }

    private static boolean sensitive(Node node) {
        if (node instanceof Element element && element.hasAttributeNS(XMLConstants.XML_NS_URI, "space")) return true;
        boolean text = false, element = false;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            element |= child.getNodeType() == Node.ELEMENT_NODE;
            text |= child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE;
            if (sensitive(child)) return true;
        }
        return text && element;
    }
}
