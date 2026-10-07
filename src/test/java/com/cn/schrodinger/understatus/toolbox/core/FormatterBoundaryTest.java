package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FormatterBoundaryTest {
    @Test
    void invalidJsonIsRejected() {
        for (String value : new String[]{"{bad}", "{\"x\":}", "[1,]", "{} false", "01"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonFormatter.format(value), value);
        }
    }

    @Test
    void sqlPreservesLiteralsAndLineComments() {
        String sql = "select 'from  x', \"select\" -- keep\nfrom t";
        assertTrue(SqlFormatter.format(sql).contains("'from  x'"));
        assertTrue(SqlFormatter.format(sql).contains("\"select\""));
        assertTrue(SqlFormatter.minify(sql).contains("-- keep\n"));
        assertThrows(IllegalArgumentException.class, () -> SqlFormatter.format("select 'unterminated"));
    }

    @Test
    void xmlPreservesSignificantWhitespaceAndCdata() {
        String xml = "<p xml:space=\"preserve\"><b/>   <i/><![CDATA[>  <]]></p>";
        assertTrue(XmlFormatter.minify(xml).contains("<b/>   <i/>"));
        assertTrue(XmlFormatter.minify(xml).contains("<![CDATA[>  <]]>"));
        assertThrows(IllegalArgumentException.class, () -> XmlFormatter.format("<broken>"));
    }

    @Test
    void jsonUnescapeSupportsUnicodeAndRejectsMalformedEscapes() {
        assertEquals("你", JsonFormatter.unescape("\\u4f60"));
        assertThrows(IllegalArgumentException.class, () -> JsonFormatter.unescape("\\q"));
    }

    @Test
    void xmlRejectsDoctypesAndDeepNestingAndPreservesMixedText() {
        assertThrows(IllegalArgumentException.class, () -> XmlFormatter.format(
                "<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///not-readable'>]><root>&x;</root>"));
        assertThrows(IllegalArgumentException.class, () -> XmlFormatter.minify("<a>".repeat(129) + "</a>".repeat(129)));
        String mixed = "<p>Hello <b>world</b> ! <![CDATA[a < b]]></p>";
        assertEquals(mixed, XmlFormatter.format(mixed));
    }

    @Test
    void sqlKeepsEscapesQuotedIdentifiersAndDollarStrings() {
        String source = "select 'it''s  where', `from`, [order], $$a  from\nb$$, $tag$select  x$tag$ /* from  x */ from t";
        String formatted = SqlFormatter.format(source);
        for (String literal : new String[]{"'it''s  where'", "`from`", "[order]", "$$a  from\nb$$",
                "$tag$select  x$tag$", "/* from  x */"}) assertTrue(formatted.contains(literal));
        assertThrows(IllegalArgumentException.class, () -> SqlFormatter.minify("select /* unfinished"));
        assertThrows(IllegalArgumentException.class, () -> SqlFormatter.minify("select $$unfinished"));
        assertTrue(SqlFormatter.minify("select 'a'\n'b'").contains("'a'\n'b'"));
    }

    @Test
    void jsonRejectsDuplicateClaimsAndPreservesDecimalPrecision() {
        assertThrows(IllegalArgumentException.class, () -> JsonFormatter.minify("{\"exp\":1,\"exp\":2}"));
        assertEquals("1516239022.123456789", JsonFormatter.minify("1516239022.123456789"));
        assertThrows(IllegalArgumentException.class, () -> JsonFormatter.format("[".repeat(129) + "0" + "]".repeat(129)));
    }

    @Test
    void sqlPreservesParameterNamesAndVariableTokens() {
        String sql = "select :limit, :By, @order, @@session.select, $where, $1, ?123 from t";
        String expected = "SELECT :limit, :By, @order, @@session.select, $where, $1, ?123\nFROM t";
        assertEquals(expected, SqlFormatter.format(sql));
        assertEquals(sql, SqlFormatter.minify(sql));
        assertEquals("SELECT :limit", SqlFormatter.format("select :limit"));
        assertEquals("SELECT @order\nFROM t", SqlFormatter.format("select @order from t"));
    }

    @Test
    void sqlPreservesDialectParameterFormsAndQualifiedNames() {
        String sql = "select :limit::integer, $scope::order(suffix), %(limit)s, ${order}, t.order, schema.select from t";
        String expected = "SELECT :limit::integer, $scope::order(suffix), %(limit)s, ${order}, t.order, schema.select\nFROM t";
        assertEquals(expected, SqlFormatter.format(sql));
        assertEquals(sql, SqlFormatter.minify(sql));
        String protectedContent = "select ':limit @order $where', \"@order\", $$:limit @order$$ -- :limit @order\nfrom t";
        assertTrue(SqlFormatter.format(protectedContent).contains("':limit @order $where', \"@order\", $$:limit @order$$ -- :limit @order\n"));
        assertEquals(protectedContent, SqlFormatter.minify(protectedContent));
    }
}
