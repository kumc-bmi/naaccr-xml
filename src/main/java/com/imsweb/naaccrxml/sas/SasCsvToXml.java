/*
 * Copyright (C) 2018 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml.sas;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Use this class to convert a given CSV file into a NAACCR XML file.
 * <br/><br/>
 * THIS CLASS IS IMPLEMENTED TO BE COMPATIBLE WITH JAVA 7; BE CAREFUL WHEN MODIFYING IT.
 */
@SuppressWarnings("ALL")
public class SasCsvToXml {

    private static final Map<String, String> _TO_ESCAPE = new HashMap<>();

    static {
        _TO_ESCAPE.put("&", "&amp;");
        _TO_ESCAPE.put("<", "&lt;");
        _TO_ESCAPE.put(">", "&gt;");
        _TO_ESCAPE.put("\"", "&quot;");
        _TO_ESCAPE.put("'", "&apos;'");
        // not really a special character, but behaves like one (to handle new lines)
        _TO_ESCAPE.put("::", "\n");
    }

    private File _csvFile, _xmlFile, _dictFile;

    private String _naaccrVersion, _recordType;

    public SasCsvToXml(String xmlPath, String naaccrVersion, String recordType) {
        this(xmlPath.replace(".xml", ".csv"), xmlPath, naaccrVersion, recordType);
    }

    public SasCsvToXml(String csvPath, String xmlPath, String naaccrVersion, String recordType) {
        _xmlFile = new File(xmlPath);
        System.out.println(" > target XML: " + _xmlFile.getAbsolutePath());

        if (csvPath.endsWith(".gz"))
            csvPath = csvPath.replace(".gz", "");
        _csvFile = new File(csvPath);
        if (!_csvFile.exists())
            System.err.println("!!! Invalid CSV file: " + csvPath);
        else
            System.out.println(" > temp CSV: " + _csvFile.getAbsolutePath());

        _naaccrVersion = naaccrVersion;
        _recordType = recordType;
    }

    public void setDictionary(String dictPath) {
        if (dictPath != null && !dictPath.trim().isEmpty()) {
            _dictFile = new File(dictPath);
            if (!_dictFile.exists())
                System.err.println("!!! Invalid CSV dictionary " + dictPath);
            else
                System.out.println(" > dictionary: " + _dictFile.getAbsolutePath());
        }
    }

    public String getCsvPath() {
        return _csvFile.getAbsolutePath();
    }

    public String getXmlPath() {
        return _xmlFile.getAbsolutePath();
    }

    public String getNaaccrVersion() {
        return _naaccrVersion;
    }

    public String getRecordType() {
        return _recordType;
    }

    public List<SasFieldInfo> getFields() {
        return SasUtils.getFields(_naaccrVersion, _recordType, _dictFile);
    }

    public void convert() throws IOException {
        convert(null);
    }

    public void convert(String fields) throws IOException {
        System.out.println("Starting converting CSV to XML...");
        try {
            Set<String> requestedFields = null;
            if (fields != null && !fields.trim().isEmpty()) {
                requestedFields = new HashSet<>();
                for (String s : fields.split(",", -1))
                    requestedFields.add(s.trim());
            }

            Map<String, String> rootFields = new HashMap<>(), patientFields = new HashMap<>(), tumorFields = new HashMap<>();
            for (SasFieldInfo field : getFields()) {
                if (requestedFields == null || requestedFields.contains(field.getNaaccrId())) {
                    if ("NaaccrData".equals(field.getParentTag()))
                        rootFields.put(field.getTruncatedNaaccrId(), field.getNaaccrId());
                    else if ("Patient".equals(field.getParentTag()))
                        patientFields.put(field.getTruncatedNaaccrId(), field.getNaaccrId());
                    else if ("Tumor".equals(field.getParentTag()))
                        tumorFields.put(field.getTruncatedNaaccrId(), field.getNaaccrId());
                }
            }

            LineNumberReader reader = null;
            BufferedWriter writer = null;
            try {
                reader = new LineNumberReader(new InputStreamReader(new FileInputStream(_csvFile), StandardCharsets.UTF_8));
                writer = SasUtils.createWriter(_xmlFile);

                List<String> headers = new ArrayList<>();
                String line = reader.readLine();
                if (line == null)
                    throw new IOException("Was expecting to find column headers, didn't find them!");
                headers.addAll(Arrays.asList(line.split(",", -1)));

                int patNumIdx = headers.indexOf("patientIdNumber");
                if (patNumIdx == -1)
                    throw new IOException("Unable to find 'patientIdNumber' in the headers");

                String currentPatNum = null;
                line = reader.readLine();
                while (line != null) {
                    List<String> valList = SasUtils.parseCsvLine(reader.getLineNumber(), line);
                    if (headers.size() != valList.size())
                        throw new IOException("Line " + reader.getLineNumber() + ": expected " + headers.size() + " values but got " + valList.size());

                    Map<String, String> values = new HashMap<>();
                    for (int i = 0; i < valList.size(); i++)
                        values.put(headers.get(i), valList.get(i));

                    String patNum = values.get("patientIdNumber");
                    if (patNum == null)
                        throw new IOException("Line " + reader.getLineNumber() + ": patient ID Number is required to write XML files");

                    // do we have to write the root?
                    if (currentPatNum == null) {
                        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                        writer.write("\n");
                        writer.write("<NaaccrData");
                        writer.write(" baseDictionaryUri=\"http://naaccr.org/naaccrxml/naaccr-dictionary-" + _naaccrVersion + ".xml\"");
                        writer.write(" recordType=\"" + _recordType + "\"");
                        writer.write(" specificationVersion=\"1.3\"");
                        writer.write(" xmlns=\"http://naaccr.org/naaccrxml\"");
                        writer.write(">\n");
                        for (Entry<String, String> entry : rootFields.entrySet()) {
                            String val = values.get(entry.getKey());
                            if (val != null && !val.trim().isEmpty())
                                writer.write("    <Item naaccrId=\"" + entry.getValue() + "\">" + cleanUpValue(val) + "</Item>\n");
                        }
                    }

                    // do we have to write the patient?
                    if (currentPatNum == null || !currentPatNum.equals(patNum)) {
                        if (currentPatNum != null)
                            writer.write("    </Patient>\n");
                        writer.write("    <Patient>\n");
                        for (Entry<String, String> entry : patientFields.entrySet()) {
                            String val = values.get(entry.getKey());
                            if (val != null && !val.trim().isEmpty())
                                writer.write("        <Item naaccrId=\"" + entry.getValue() + "\">" + cleanUpValue(val) + "</Item>\n");
                        }
                    }

                    // we always have to write the tumor!
                    writer.write("        <Tumor>\n");
                    for (Entry<String, String> entry : tumorFields.entrySet()) {
                        String val = values.get(entry.getKey());
                        if (val != null && !val.trim().isEmpty())
                            writer.write("            <Item naaccrId=\"" + entry.getValue() + "\">" + cleanUpValue(val) + "</Item>\n");
                    }
                    writer.write("        </Tumor>\n");

                    currentPatNum = patNum;
                    line = reader.readLine();
                }

                writer.write("    </Patient>\n");
                writer.write("</NaaccrData>");
            }
            finally {
                if (writer != null)
                    writer.close();
                if (reader != null)
                    reader.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
        catch (RuntimeException e) {
            e.printStackTrace();
            throw new IOException(e);
        }

        System.out.println("Successfully created " + _xmlFile.getAbsolutePath());
    }

    public void cleanup() {
        if (!_csvFile.delete())
            System.err.println("!!! Unable to cleanup tmp CSV file.");
    }

    private String cleanUpValue(String value) {
        StringBuilder buf = new StringBuilder(value);
        for (Map.Entry<String, String> entry : _TO_ESCAPE.entrySet()) {
            int idx = buf.indexOf(entry.getKey());
            while (idx != -1) {
                buf.replace(idx, idx + entry.getKey().length(), entry.getValue());
                idx = buf.indexOf(entry.getKey(), idx + 1);
            }
        }
        return buf.toString();
    }
}
