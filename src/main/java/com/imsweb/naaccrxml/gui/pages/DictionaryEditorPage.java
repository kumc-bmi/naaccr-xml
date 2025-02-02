/*
 * Copyright (C) 2017 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml.gui.pages;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

import com.imsweb.naaccrxml.NaaccrFormat;
import com.imsweb.naaccrxml.NaaccrXmlDictionaryUtils;
import com.imsweb.naaccrxml.NaaccrXmlUtils;
import com.imsweb.naaccrxml.entity.dictionary.NaaccrDictionary;
import com.imsweb.naaccrxml.entity.dictionary.NaaccrDictionaryItem;
import com.imsweb.naaccrxml.gui.Standalone;
import com.imsweb.naaccrxml.gui.components.SeerClickableLabel;

/**
 * FUTURE IMPROVEMENTS:
 * - add a "Create New Dictionary" toolbar button to start a new dictionary
 * - keep track whether the current dictionary has been modified or not, display that status and use confirmation upon closing if modified
 */
@SuppressWarnings({"FieldCanBeLocal", "JdkObsolete"})
public class DictionaryEditorPage extends AbstractPage implements ActionListener {

    private static final String _BLANK_VERSION = "<Any>";
    private static final String _NO_FILE_TEXT = "< no current file, use the load button to load an existing dictionary, or the save-as button to save the current dictionary >";

    // global GUI components
    private JLabel _currentFileLbl, _currentFilePreLbl, _currentFileMiddleLbl, _currentFilePostLbl;
    private SeerClickableLabel _openCurrentFileLbl, _openParentFolderLbl;
    private JTextField _dictionaryUriFld, _descFld;
    private JComboBox<String> _versionBox;
    private JTable _itemsTbl;
    private DefaultTableModel _itemsModel;
    private JFileChooser _dictionaryFileChooser, _outputFileChooser;

    private File _currentFile;

    public DictionaryEditorPage() {
        super();

        this.setBorder(new MatteBorder(0, 0, 1, 1, Color.GRAY));

        // NORTH
        JPanel controlsPnl = new JPanel(new BorderLayout());
        this.add(controlsPnl, BorderLayout.NORTH);
        controlsPnl.add(createToolBar(), BorderLayout.NORTH);
        controlsPnl.add(createFilePanel(), BorderLayout.SOUTH);

        // CENTER
        JPanel centerPnl = new JPanel(new BorderLayout());
        this.add(centerPnl, BorderLayout.CENTER);

        // CENTER/NORTH
        JPanel dictAttributesPnl = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        centerPnl.add(dictAttributesPnl, BorderLayout.NORTH);
        dictAttributesPnl.setBorder(new EmptyBorder(10, 10, 0, 10));
        dictAttributesPnl.add(Standalone.createBoldLabel("URI:"));
        dictAttributesPnl.add(Box.createHorizontalStrut(5));
        _dictionaryUriFld = new JTextField(45);
        dictAttributesPnl.add(_dictionaryUriFld);
        dictAttributesPnl.add(Box.createHorizontalStrut(20));
        dictAttributesPnl.add(Standalone.createBoldLabel("Version:"));
        dictAttributesPnl.add(Box.createHorizontalStrut(5));
        Vector<String> versions = new Vector<>();
        versions.add(_BLANK_VERSION);
        NaaccrFormat.getSupportedVersions().stream().sorted(Collections.reverseOrder()).forEach(versions::add);
        _versionBox = new JComboBox<>(versions);
        dictAttributesPnl.add(_versionBox);
        dictAttributesPnl.add(Box.createHorizontalStrut(20));
        dictAttributesPnl.add(Standalone.createBoldLabel("Description:"));
        dictAttributesPnl.add(Box.createHorizontalStrut(5));
        _descFld = new JTextField(40);
        dictAttributesPnl.add(_descFld);

        // CENTER/CENTER
        JPanel tablePnl = new JPanel(new BorderLayout());
        centerPnl.add(tablePnl, BorderLayout.CENTER);
        tablePnl.setBorder(new EmptyBorder(10, 10, 5, 10));
        JPanel tableContentPnl = new JPanel(new BorderLayout());
        tablePnl.add(tableContentPnl, BorderLayout.CENTER);
        _itemsModel = new DefaultTableModel() {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1 || columnIndex == 3 || columnIndex == 4)
                    return Integer.class;
                return String.class;
            }
        };
        _itemsTbl = new JTable(_itemsModel);
        _itemsTbl.setDragEnabled(false);
        _itemsTbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // default behavior of Enter is to go to next cell, I think editing the current cell makes more sense
        _itemsTbl.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "edit");
        _itemsTbl.getActionMap().put("edit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = _itemsTbl.getSelectedRow(), col = _itemsTbl.getSelectedColumn();
                _itemsTbl.editCellAt(row, col);
                Component comp = _itemsTbl.getEditorComponent();
                comp.requestFocusInWindow();
                if (comp instanceof JTextComponent)
                    ((JTextComponent)comp).selectAll();
            }
        });
        DefaultTableCellRenderer itemsRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JComponent comp = (JComponent)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                comp.setBorder(new CompoundBorder(new EmptyBorder(new Insets(1, 1, 1, 1)), getBorder()));
                return comp;
            }
        };
        itemsRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        _itemsTbl.setDefaultRenderer(String.class, itemsRenderer);
        _itemsTbl.setDefaultRenderer(Integer.class, itemsRenderer);
        _itemsTbl.setSelectionBackground(new Color(210, 227, 236));
        _itemsTbl.setSelectionForeground(Color.BLACK);
        _itemsTbl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JPopupMenu popup = new JPopupMenu("Table Popup");
                    popup.setBorder(new BevelBorder(BevelBorder.RAISED));

                    boolean rowSelected = _itemsTbl.getSelectedRow() != -1;

                    JMenuItem addRowFirstItem = new JMenuItem("Insert row first");
                    addRowFirstItem.setActionCommand("table-add-row-first");
                    addRowFirstItem.addActionListener(DictionaryEditorPage.this);
                    popup.add(addRowFirstItem);

                    JMenuItem addRowLastItem = new JMenuItem("Insert row last");
                    addRowLastItem.setActionCommand("table-add-row-last");
                    addRowLastItem.addActionListener(DictionaryEditorPage.this);
                    popup.add(addRowLastItem);

                    popup.addSeparator();

                    JMenuItem addRowBeforeItem = new JMenuItem("Insert row before");
                    addRowBeforeItem.setActionCommand("table-add-row-before");
                    addRowBeforeItem.addActionListener(DictionaryEditorPage.this);
                    if (!rowSelected)
                        addRowBeforeItem.setEnabled(false);
                    popup.add(addRowBeforeItem);

                    JMenuItem addRowAfterItem = new JMenuItem("Insert row after");
                    addRowAfterItem.setActionCommand("table-add-row-after");
                    addRowAfterItem.addActionListener(DictionaryEditorPage.this);
                    if (!rowSelected)
                        addRowAfterItem.setEnabled(false);
                    popup.add(addRowAfterItem);

                    popup.addSeparator();

                    JMenuItem removeSelectedRowItem = new JMenuItem("Remove row");
                    removeSelectedRowItem.setActionCommand("table-remove-row");
                    removeSelectedRowItem.addActionListener(DictionaryEditorPage.this);
                    if (!rowSelected)
                        removeSelectedRowItem.setEnabled(false);
                    popup.add(removeSelectedRowItem);

                    JMenuItem removeAllRowsExceptSelectedItem = new JMenuItem("Remove all other rows");
                    removeAllRowsExceptSelectedItem.setActionCommand("table-remove-all-other-rows");
                    removeAllRowsExceptSelectedItem.addActionListener(DictionaryEditorPage.this);
                    if (!rowSelected)
                        removeAllRowsExceptSelectedItem.setEnabled(false);
                    popup.add(removeAllRowsExceptSelectedItem);

                    JMenuItem removeAllRowsItem = new JMenuItem("Remove all rows");
                    removeAllRowsItem.setActionCommand("table-remove-all-rows");
                    removeAllRowsItem.addActionListener(DictionaryEditorPage.this);
                    popup.add(removeAllRowsItem);

                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        populateGuiFromDictionary(createEmptyDictionary());

        _itemsTbl.getColumnModel().getColumn(1).setPreferredWidth(45); // item number
        _itemsTbl.getColumnModel().getColumn(3).setPreferredWidth(45); // start column
        _itemsTbl.getColumnModel().getColumn(4).setPreferredWidth(30); // length
        _itemsTbl.getColumnModel().getColumn(7).setPreferredWidth(40); // data type
        _itemsTbl.getColumnModel().getColumn(9).setPreferredWidth(40); // trim

        JComboBox<String> recordTypeBox = new JComboBox<>();
        recordTypeBox.addItem("A,M,C,I");
        recordTypeBox.addItem("A,M,C");
        recordTypeBox.addItem("A,M");
        _itemsTbl.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(recordTypeBox));

        JComboBox<String> parentElementBox = new JComboBox<>();
        parentElementBox.addItem(NaaccrXmlUtils.NAACCR_XML_TAG_ROOT);
        parentElementBox.addItem(NaaccrXmlUtils.NAACCR_XML_TAG_PATIENT);
        parentElementBox.addItem(NaaccrXmlUtils.NAACCR_XML_TAG_TUMOR);
        _itemsTbl.getColumnModel().getColumn(6).setCellEditor(new DefaultCellEditor(parentElementBox));

        JComboBox<String> dataTypeBox = new JComboBox<>();
        dataTypeBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_TEXT);
        dataTypeBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_DIGITS);
        dataTypeBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_ALPHA);
        dataTypeBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_MIXED);
        dataTypeBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_NUMERIC);
        dataTypeBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_DATE);
        _itemsTbl.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(dataTypeBox));

        JComboBox<String> paddingBox = new JComboBox<>();
        paddingBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_PADDING_RIGHT_BLANK);
        paddingBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_PADDING_RIGHT_ZERO);
        paddingBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_PADDING_LEFT_BLANK);
        paddingBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_PADDING_LEFT_ZERO);
        _itemsTbl.getColumnModel().getColumn(8).setCellEditor(new DefaultCellEditor(paddingBox));

        JComboBox<String> trimmingBox = new JComboBox<>();
        trimmingBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_TRIM_ALL);
        trimmingBox.addItem(NaaccrXmlDictionaryUtils.NAACCR_TRIM_NONE);
        _itemsTbl.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(trimmingBox));

        _itemsTbl.getSelectionModel().setSelectionInterval(0, 0);
        SwingUtilities.invokeLater(() -> _itemsTbl.requestFocusInWindow());

        JScrollPane tableScrollPane = new JScrollPane(_itemsTbl);
        tableScrollPane.setBorder(null);
        tableContentPnl.add(tableScrollPane, BorderLayout.CENTER);

        // CENTER/SOUTH
        JPanel disclaimerPnl = new JPanel();
        disclaimerPnl.setBorder(new EmptyBorder(0, 10, 5, 0));
        disclaimerPnl.setLayout(new BoxLayout(disclaimerPnl, BoxLayout.Y_AXIS));
        JPanel line1Pnl = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 2));
        line1Pnl.add(
                new JLabel(
                        "Double click a cell or select it and hit Enter to modify its content; hit Enter once you are done editing it (or Escape to cancel). Right click on the table to add or remove rows."));
        disclaimerPnl.add(line1Pnl);
        centerPnl.add(disclaimerPnl, BorderLayout.SOUTH);

        _dictionaryFileChooser = new JFileChooser();
        _dictionaryFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        _dictionaryFileChooser.setDialogTitle("Select File");
        _dictionaryFileChooser.setApproveButtonToolTipText("Select file");
        _dictionaryFileChooser.setMultiSelectionEnabled(false);
        _dictionaryFileChooser.setFileFilter(new FileFilter() {

            @Override
            public String getDescription() {
                return "XML files (*.xml)";
            }

            @Override
            public boolean accept(File f) {
                return f != null && (f.isDirectory() || f.getName().toLowerCase().endsWith(".xml"));
            }
        });

        _outputFileChooser = new JFileChooser();
        _outputFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        _outputFileChooser.setDialogTitle("Select File");
        _outputFileChooser.setApproveButtonToolTipText("Select file");
        _outputFileChooser.setMultiSelectionEnabled(false);
        _outputFileChooser.setFileFilter(new FileFilter() {

            @Override
            public String getDescription() {
                return "XML files (*.xml)";
            }

            @Override
            public boolean accept(File f) {
                return f != null && (f.isDirectory() || f.getName().toLowerCase().endsWith(".xml"));
            }
        });
    }

    private JToolBar createToolBar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setBackground(new Color(206, 220, 227));
        toolbar.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
        toolbar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), new EmptyBorder(5, 10, 5, 0)));
        toolbar.setFloatable(false);
        toolbar.add(createToolbarButton("load", "toolbar-load", "Load dictionary"));
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(createToolbarSeparation());
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(createToolbarButton("save", "toolbar-save", "Save dictionary"));
        toolbar.add(Box.createHorizontalStrut(2));
        toolbar.add(createToolbarButton("save-as", "toolbar-save-as", "Save dictionary into new file"));
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(createToolbarSeparation());
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(createToolbarButton("validate", "toolbar-validate", "Validate dictionary"));
        toolbar.add(createToolbarSeparation());
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(createToolbarButton("csv", "toolbar-csv", "Extract to CSV"));

        return toolbar;
    }

    @SuppressWarnings("ConstantConditions")
    private JButton createToolbarButton(String icon, String action, String tooltip) {
        JButton btn = new JButton();
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setActionCommand(action);
        btn.setToolTipText(tooltip);
        btn.addActionListener(this);
        btn.setIcon(new ImageIcon(Thread.currentThread().getContextClassLoader().getResource("gui/icons/toolbar/editor-" + icon + ".png")));
        btn.setDisabledIcon(new ImageIcon(Thread.currentThread().getContextClassLoader().getResource("gui/icons/toolbar/editor-" + icon + "-disable.png")));
        btn.setBorder(new EmptyBorder(3, 3, 3, 3));
        return btn;
    }

    private JPanel createToolbarSeparation() {
        return new JPanel() {
            @Override
            public void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D)graphics;

                Rectangle bounds = getBounds();
                g.setColor(Color.GRAY);
                g.drawLine(bounds.width / 2, 0, bounds.width / 2, bounds.height);
                g.setColor(Color.LIGHT_GRAY);
                g.drawLine(bounds.width / 2 + 1, 0, bounds.width / 2 + 1, bounds.height);
            }
        };
    }

    private void updateFileInfo() {
        if (_currentFile == null) {
            _currentFileLbl.setText(_NO_FILE_TEXT);
            _openCurrentFileLbl.setAction(null);
            _openParentFolderLbl.setAction(null);
            _currentFilePreLbl.setVisible(false);
            _openCurrentFileLbl.setVisible(false);
            _currentFileMiddleLbl.setVisible(false);
            _openParentFolderLbl.setVisible(false);
            _currentFilePostLbl.setVisible(false);
        }
        else {
            _currentFileLbl.setText(_currentFile.getPath());
            _currentFilePreLbl.setVisible(true);
            _openCurrentFileLbl.setVisible(true);
            _openCurrentFileLbl.setAction(SeerClickableLabel.createOpenFileAction(_currentFile.getPath()));
            _currentFileMiddleLbl.setVisible(true);
            _openParentFolderLbl.setVisible(true);
            _openParentFolderLbl.setAction(SeerClickableLabel.createOpenParentFolderAction(_currentFile.getPath()));
            _currentFilePostLbl.setVisible(true);
        }
    }

    private JPanel createFilePanel() {
        JPanel pnl = new JPanel();
        pnl.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), new EmptyBorder(5, 10, 5, 0)));
        pnl.setLayout(new BorderLayout());
        pnl.setBackground(new Color(222, 232, 237));

        JPanel filePnl = new JPanel();
        filePnl.setOpaque(false);
        pnl.add(filePnl, BorderLayout.WEST);
        filePnl.setBorder(new EmptyBorder(0, 0, 0, 0));
        filePnl.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
        filePnl.add(Standalone.createBoldLabel("Current File:  "));
        _currentFileLbl = new JLabel(_NO_FILE_TEXT);
        filePnl.add(_currentFileLbl);
        filePnl.add(Box.createHorizontalStrut(15));
        _currentFilePreLbl = new JLabel("[ ");
        _currentFilePreLbl.setVisible(false);
        filePnl.add(_currentFilePreLbl);
        _openCurrentFileLbl = new SeerClickableLabel("open file");
        _openCurrentFileLbl.setVisible(false);
        filePnl.add(_openCurrentFileLbl);
        _currentFileMiddleLbl = new JLabel(" | ");
        _currentFileMiddleLbl.setVisible(false);
        filePnl.add(_currentFileMiddleLbl);
        _openParentFolderLbl = new SeerClickableLabel("open folder");
        _openParentFolderLbl.setVisible(false);
        filePnl.add(_openParentFolderLbl);
        _currentFilePostLbl = new JLabel(" ]");
        _currentFilePostLbl.setVisible(false);
        filePnl.add(_currentFilePostLbl);

        return pnl;
    }

    private NaaccrDictionary createEmptyDictionary() {
        NaaccrDictionary dictionary = new NaaccrDictionary();
        dictionary.setDictionaryUri("http://mycompany.com/naaccrxml/my-naaccr-dictionary.xml");
        dictionary.setNaaccrVersion(null);
        dictionary.setDescription("My NAACCR dictionary");
        return dictionary;
    }

    private void populateGuiFromDictionary(NaaccrDictionary dictionary) {
        _dictionaryUriFld.setText(dictionary.getDictionaryUri());
        if (dictionary.getNaaccrVersion() != null)
            _versionBox.setSelectedItem(dictionary.getNaaccrVersion());
        else
            _versionBox.setSelectedItem(_BLANK_VERSION);
        if (dictionary.getDescription() != null)
            _descFld.setText(dictionary.getDescription());

        Vector<String> columns = new Vector<>();
        columns.add("ID");
        columns.add("Num");
        columns.add("Name");
        columns.add("Start Col");
        columns.add("Length");
        columns.add("Record Types");
        columns.add("Parent XML Element");
        columns.add("Data Type");
        columns.add("Padding");
        columns.add("Trimming");

        Vector<Vector<Object>> rows = new Vector<>();
        if (dictionary.getItems().isEmpty()) {
            Vector<Object> row = new Vector<>();
            row.add("myVariable");
            row.add(10000);
            row.add("My Variable");
            row.add(null);
            row.add(1);
            row.add("A,M,C,I");
            row.add(NaaccrXmlUtils.NAACCR_XML_TAG_TUMOR);
            row.add(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_TEXT);
            row.add(NaaccrXmlDictionaryUtils.NAACCR_PADDING_RIGHT_BLANK);
            row.add(NaaccrXmlDictionaryUtils.NAACCR_TRIM_ALL);
            rows.add(row);
        }
        else {
            for (NaaccrDictionaryItem item : dictionary.getItems()) {
                Vector<Object> row = new Vector<>();
                row.add(item.getNaaccrId());
                row.add(item.getNaaccrNum());
                row.add(item.getNaaccrName());
                row.add(item.getStartColumn());
                row.add(item.getLength());
                row.add(item.getRecordTypes());
                row.add(item.getParentXmlElement());
                row.add(item.getDataType() == null ? NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_TEXT : item.getDataType());
                row.add(item.getPadding() == null ? NaaccrXmlDictionaryUtils.NAACCR_PADDING_RIGHT_BLANK : item.getPadding());
                row.add(item.getTrim() == null ? NaaccrXmlDictionaryUtils.NAACCR_TRIM_ALL : item.getTrim());
                rows.add(row);
            }
        }
        _itemsModel.setDataVector(rows, columns);
    }

    private NaaccrDictionary createDictionaryFromGui() {
        NaaccrDictionary dictionary = new NaaccrDictionary();
        dictionary.setDictionaryUri(_dictionaryUriFld.getText().trim());
        dictionary.setSpecificationVersion(NaaccrXmlUtils.CURRENT_SPECIFICATION_VERSION);
        if (!_BLANK_VERSION.equals(_versionBox.getSelectedItem()))
            dictionary.setNaaccrVersion((String)_versionBox.getSelectedItem());
        dictionary.setDescription(_descFld.getText().trim().isEmpty() ? null : _descFld.getText().trim());

        for (int i = 0; i < _itemsModel.getRowCount(); i++) {
            NaaccrDictionaryItem item = new NaaccrDictionaryItem();
            item.setNaaccrId((String)_itemsModel.getValueAt(i, 0));
            item.setNaaccrNum((Integer)_itemsModel.getValueAt(i, 1));
            item.setNaaccrName((String)_itemsModel.getValueAt(i, 2));
            item.setStartColumn((Integer)_itemsModel.getValueAt(i, 3));
            item.setLength((Integer)_itemsModel.getValueAt(i, 4));
            item.setRecordTypes((String)_itemsModel.getValueAt(i, 5));
            item.setParentXmlElement((String)_itemsModel.getValueAt(i, 6));
            item.setDataType((String)_itemsModel.getValueAt(i, 7));
            item.setPadding((String)_itemsModel.getValueAt(i, 8));
            item.setTrim((String)_itemsModel.getValueAt(i, 9));
            dictionary.addItem(item);
        }

        return dictionary;
    }

    private void performLoad() {
        if (_dictionaryFileChooser.showDialog(DictionaryEditorPage.this, "Select") == JFileChooser.APPROVE_OPTION) {
            try {
                populateGuiFromDictionary(NaaccrXmlDictionaryUtils.readDictionary(_dictionaryFileChooser.getSelectedFile()));
                _currentFile = _dictionaryFileChooser.getSelectedFile();
                updateFileInfo();
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog(DictionaryEditorPage.this, "Unable to load dictionary.\r\n\r\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }

            SwingUtilities.invokeLater(() -> _itemsTbl.requestFocusInWindow());
        }
    }

    private void performSave() {
        if (_currentFile == null) {
            performSaveAs();
            return;
        }

        NaaccrDictionary dictionary = performValidate(false);
        if (dictionary == null)
            return;

        try {
            NaaccrXmlDictionaryUtils.writeDictionary(dictionary, _currentFile);
            updateFileInfo();
            JOptionPane.showMessageDialog(DictionaryEditorPage.this, "Dictionary saved.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException e) {
            JOptionPane.showMessageDialog(DictionaryEditorPage.this, "Unable to save dictionary.\r\n\r\nError:\r\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        SwingUtilities.invokeLater(() -> _itemsTbl.requestFocusInWindow());
    }

    private void performSaveAs() {
        NaaccrDictionary dictionary = performValidate(false);
        if (dictionary == null)
            return;

        if (_outputFileChooser.showDialog(DictionaryEditorPage.this, "Select") == JFileChooser.APPROVE_OPTION) {
            try {
                File file = _outputFileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".xml"))
                    file = new File(file.getParentFile(), file.getName() + ".xml");

                if (file.exists()) {
                    int i = JOptionPane.showConfirmDialog(DictionaryEditorPage.this, "The target file already exist and will be overridden. Are you sure?", "Confirmation", JOptionPane.YES_NO_OPTION);
                    if (i != JOptionPane.YES_OPTION)
                        return;
                }

                NaaccrXmlDictionaryUtils.writeDictionary(dictionary, file);
                _currentFile = file;
                updateFileInfo();
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog(DictionaryEditorPage.this, "Unable to save dictionary.\r\n\r\nError:\r\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }

            SwingUtilities.invokeLater(() -> _itemsTbl.requestFocusInWindow());
        }
    }

    private NaaccrDictionary performValidate(boolean showSuccessDlg) {
        NaaccrDictionary dictionary = createDictionaryFromGui();

        String naaccrVersion = (String)_versionBox.getSelectedItem();
        if (_BLANK_VERSION.equals(naaccrVersion))
            naaccrVersion = NaaccrFormat.getSupportedVersions().stream().max(String.CASE_INSENSITIVE_ORDER).orElse(null);

        List<String> errors = NaaccrXmlDictionaryUtils.validateUserDictionary(dictionary, naaccrVersion);
        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder("Dictionary is not valid:");
            for (String error : errors)
                msg.append("\r\n   - ").append(error);
            JOptionPane.showMessageDialog(DictionaryEditorPage.this, msg.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        if (showSuccessDlg)
            JOptionPane.showMessageDialog(DictionaryEditorPage.this, "Dictionary is valid.", "Valid", JOptionPane.INFORMATION_MESSAGE);

        return dictionary;
    }

    private void performExtractToCsv() {
        NaaccrDictionary dictionary = performValidate(false);
        if (dictionary != null)
            performExtractToCsv(dictionary, dictionary.getNaaccrVersion() == null ? "my-naaccr-dictionary.csv" : ("my-naaccr-" + dictionary.getNaaccrVersion() + "-dictionary.csv"));
        else
            JOptionPane.showMessageDialog(DictionaryEditorPage.this, "Only a valid dictionary can be extracted.", "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void performAddRow(boolean relativeToSelected, boolean insertBefore) {

        Vector<Object> row = new Vector<>();
        row.add(null);
        row.add(null);
        row.add(null);
        row.add(null);
        row.add(null);
        row.add("A,M,C,I");
        row.add(NaaccrXmlUtils.NAACCR_XML_TAG_TUMOR);
        row.add(NaaccrXmlDictionaryUtils.NAACCR_DATA_TYPE_TEXT);
        row.add(NaaccrXmlDictionaryUtils.NAACCR_PADDING_RIGHT_BLANK);
        row.add(NaaccrXmlDictionaryUtils.NAACCR_TRIM_ALL);

        int rowToEdit;
        if (relativeToSelected) {
            int selected = _itemsTbl.getSelectedRow();
            if (selected == -1)
                return;
            if (insertBefore) {
                _itemsModel.insertRow(selected, row);
                rowToEdit = selected;
            }
            else {
                _itemsModel.insertRow(selected + 1, row);
                rowToEdit = selected + 1;
            }
        }
        else {
            if (insertBefore) {
                _itemsModel.insertRow(0, row);
                rowToEdit = 0;
            }
            else {
                _itemsModel.addRow(row);
                rowToEdit = _itemsModel.getRowCount() - 1;
            }
        }

        _itemsTbl.getSelectionModel().setSelectionInterval(rowToEdit, rowToEdit);
        _itemsTbl.editCellAt(rowToEdit, 0);
        Component comp = _itemsTbl.getEditorComponent();
        comp.requestFocusInWindow();
    }

    private void performRemoveRow(boolean removeAllRows, boolean keepSelected) {
        int selected = _itemsTbl.getSelectedRow();

        if (removeAllRows) {
            for (int i = _itemsModel.getRowCount() - 1; i >= 0; i--) {
                if (!keepSelected || i != selected)
                    _itemsModel.removeRow(i);
            }
        }
        else if (selected != -1)
            _itemsModel.removeRow(selected);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "toolbar-load":
                performLoad();
                break;
            case "toolbar-save":
                performSave();
                break;
            case "toolbar-save-as":
                performSaveAs();
                break;
            case "toolbar-validate":
                performValidate(true);
                break;
            case "toolbar-csv":
                performExtractToCsv();
                break;
            case "table-add-row-before":
                performAddRow(true, true);
                break;
            case "table-add-row-after":
                performAddRow(true, false);
                break;
            case "table-add-row-first":
                performAddRow(false, true);
                break;
            case "table-add-row-last":
                performAddRow(false, false);
                break;
            case "table-remove-row":
                performRemoveRow(false, false);
                break;
            case "table-remove-all-rows":
                performRemoveRow(true, false);
                break;
            case "table-remove-all-other-rows":
                performRemoveRow(true, true);
                break;
            default:
                throw new RuntimeException("Unknown action: " + e.getActionCommand());
        }
    }
}
