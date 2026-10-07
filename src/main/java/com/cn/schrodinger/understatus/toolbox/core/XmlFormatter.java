package com.cn.schrodinger.understatus.toolbox.core;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Secure XML serialization. Text, CDATA and xml:space content are never stripped. */
public final class XmlFormatter {
    private XmlFormatter() {}

    public static String format(String xml) { return transform(xml, true); }
    public static String minify(String xml) { return transform(xml, false); }

    private static String transform(String xml, boolean pretty) {
        if (xml == null || xml.isBlank()) return "";
        ToolLimits.input(xml);
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setCoalescing(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", 128);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new org.xml.sax.SAXException("External entities are disabled");
            });
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException ex) throws SAXParseException { throw ex; }
                @Override public void fatalError(SAXParseException ex) throws SAXParseException { throw ex; }
            });
            var document = builder.parse(new InputSource(new StringReader(xml)));
            var transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformers.newTransformer();
            // Without a schema, even whitespace-only text may be significant. Add indentation
            // only to documents with no text nodes and no xml:space="preserve" scope.
            transformer.setOutputProperty(OutputKeys.INDENT, pretty && canIndent(document) ? "yes" : "no");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, xml.stripLeading().startsWith("<?xml") ? "no" : "yes");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return ToolLimits.output(writer.toString());
        } catch (Exception ex) {
            throw new IllegalArgumentException("XML 格式无效或不安全: " + ex.getMessage(), ex);
        }
    }

    private static boolean canIndent(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) return false;
        if (node instanceof org.w3c.dom.Element element
                && "preserve".equals(element.getAttributeNS(XMLConstants.XML_NS_URI, "space"))) return false;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!canIndent(child)) return false;
        }
        return true;
    }
}
