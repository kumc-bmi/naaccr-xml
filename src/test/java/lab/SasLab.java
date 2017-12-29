/*
 * Copyright (C) 2017 Information Management Services, Inc.
 */
package lab;

import com.imsweb.naaccrxml.NaaccrXmlDictionaryUtils;
import com.imsweb.naaccrxml.NaaccrXmlUtils;
import com.imsweb.naaccrxml.entity.dictionary.NaaccrDictionary;
import com.imsweb.naaccrxml.entity.dictionary.NaaccrDictionaryItem;

public class SasLab {

    public static void main(String[] args) {
        NaaccrDictionary dictionary = NaaccrXmlDictionaryUtils.getBaseDictionaryByVersion("160");

        System.out.println(createSasXmlMapper(dictionary));

    }

    private static String createSasXmlMapper(NaaccrDictionary dictionary) {
        StringBuilder buf = new StringBuilder();

        buf.append("<?xml version=\"1.0\" encoding=\"windows-1252\"?>\r\n");
        buf.append("<!-- ############################################################ -->\r\n");
        buf.append("<!-- SAS XML Libname Engine Map -->\r\n");
        buf.append("<!-- Generated by NAACCR XML Java library -->\r\n");
        buf.append("<!-- ############################################################ -->\r\n");
        buf.append("<SXLEMAP description=\"NAACCR XML Tumors mapping\" name=\"naaccr_xml_tumors_map\" version=\"2.1\">\r\n");
        buf.append("\r\n");
        buf.append("    <NAMESPACES count=\"0\"/>\r\n");
        buf.append("\r\n");
        buf.append("    <!-- ############################################################ -->\r\n");
        buf.append("    <TABLE description=\"Tumors data set\" name=\"tumors\">\r\n");
        buf.append("        <TABLE-PATH syntax=\"XPath\">/NaaccrData/Patient/Tumor</TABLE-PATH>\r\n");

        for (NaaccrDictionaryItem item : dictionary.getItems()) {
            buf.append("\r\n");
            buf.append("        <COLUMN name=\"").append(item.getNaaccrId());
            if (!NaaccrXmlUtils.NAACCR_XML_TAG_TUMOR.equals(item.getParentXmlElement()))
                buf.append("\" retain=\"YES\"");
            buf.append(">\r\n");
            buf.append("            <PATH syntax=\"XPath\">").append(createXpath(item)).append("</PATH>\r\n");
            buf.append("            <DESCRIPTION>")
                    .append(item.getNaaccrName()
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("&", "&amp;"))
                    .append(" [Item #").append(item.getNaaccrNum()).append("]</DESCRIPTION>\r\n");
            buf.append("            <TYPE>character</TYPE>\r\n");
            buf.append("            <DATATYPE>string</DATATYPE>\r\n");
            buf.append("            <LENGTH>").append(item.getLength()).append("</LENGTH>\r\n");
            buf.append("        </COLUMN>\r\n");
        }
        buf.append("    </TABLE>\r\n");
        buf.append("</SXLEMAP>\r\n");

        return buf.toString();
    }

    private static String createXpath(NaaccrDictionaryItem item) {
        switch (item.getParentXmlElement()) {
            case NaaccrXmlUtils.NAACCR_XML_TAG_ROOT:
                return "/NaaccrData/Item[@naaccrId=\"" + item.getNaaccrId() + "\"]";
            case NaaccrXmlUtils.NAACCR_XML_TAG_PATIENT:
                return "/NaaccrData/Patient/Item[@naaccrId=\"" + item.getNaaccrId() + "\"]";
            case NaaccrXmlUtils.NAACCR_XML_TAG_TUMOR:
                return "/NaaccrData/Patient/Tumor/Item[@naaccrId=\"" + item.getNaaccrId() + "\"]";
            default:
                throw new RuntimeException("Unsupported parent XML element: " + item.getParentXmlElement());
        }
    }

}