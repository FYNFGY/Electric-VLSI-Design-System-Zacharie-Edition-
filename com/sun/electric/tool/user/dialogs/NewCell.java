/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NewCell.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.WindowFrame;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JList;

/**
 * Class to handle the "New Cell" dialog.
 */
public class NewCell extends EDialog
{
	private JList viewList;
	private DefaultListModel viewModel;
	private Set<Integer> checkedViews;
	private static boolean makeWindow = false;

	/**
	 * Inner class to control options based on views and technologies.
	 * This is done to access View or Tech without calling toString()
	 */
	private static class ViewTechOption
	{
		View view;
		Technology tech;

		ViewTechOption(View v, Technology t)
		{
			this.view = v;
			this.tech = t;
		}
		public String toString()
		{
			if (view != null)
				return view.getFullName();
			return tech.getTechName();
		}
	}

	/** Creates new form New Cell */
	public NewCell(Frame parent, Library curLib, String defaultName)
	{
		super(parent, true);
		initComponents();
		getRootPane().setDefaultButton(ok);

		// make a list of views
		viewModel = new DefaultListModel();
		viewList = new JList(viewModel);
		// selection is driven by checkboxes only: suppress the JList's own
		// click-selection so it doesn't fight the checkbox toggle
		viewList.setSelectionModel(new DefaultListSelectionModel()
		{
			public void setSelectionInterval(int index0, int index1) {}
			public void addSelectionInterval(int index0, int index1) {}
			public void removeSelectionInterval(int index0, int index1) {}
		});
		view.setViewportView(viewList);
		Technology curTech = Technology.getCurrent();
		Technology defTech = Technology.findTechnology(User.getDefaultTechnology());
		View defaultView = View.LAYOUT;
		if (curTech == Schematics.tech()) defaultView = View.SCHEMATIC;
		else if (curTech == Artwork.tech()) defaultView = View.ICON;
		checkedViews = new LinkedHashSet<Integer>();
		int defaultIndex = -1;
		for (View v : View.getOrderedViews())
		{
			ViewTechOption option = new ViewTechOption(v, null);
			viewModel.addElement(option);
			if (v == defaultView) defaultIndex = viewModel.size()-1;
		}
		if (defaultIndex >= 0) checkedViews.add(defaultIndex);
		// render each view as a checkbox so multiple selection is visible
		viewList.setCellRenderer(new DefaultListCellRenderer() {
			private JCheckBox checkBox = new JCheckBox();
			public Component getListCellRendererComponent(JList list, Object value, int index,
					boolean isSelected, boolean cellHasFocus)
			{
				boolean checked = checkedViews.contains(index);
				checkBox.setText(value.toString());
				checkBox.setSelected(checked);
				checkBox.setEnabled(list.isEnabled());
				checkBox.setOpaque(true);
				if (checked)
				{
					checkBox.setBackground(list.getSelectionBackground());
					checkBox.setForeground(list.getSelectionForeground());
				} else
				{
					checkBox.setBackground(list.getBackground());
					checkBox.setForeground(list.getForeground());
				}
				return checkBox;
			}
		});
        viewList.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				int index = viewList.locationToIndex(e.getPoint());
				if (index < 0) return;
				if (checkedViews.contains(index))
					checkedViews.remove(index);
				else
					checkedViews.add(index);
				viewList.repaint();
				techComboBoxItemStateChanged(null);
			}
		});

		// Choose appropriate technology
		for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();)
		{
			Technology tech = it.next();
			if (tech != Schematics.tech() && tech != Artwork.tech())
			{
				ViewTechOption option = new ViewTechOption(null, tech);
				techComboBox.addItem(option);
				if ((defaultView == View.LAYOUT && tech == curTech) ||
					(tech == defTech)) techComboBox.setSelectedItem(option);
			}
		}

		// Only capable to switch technology if they are layout-based
		techComboBoxItemStateChanged(null);

		// make a popup of libraries
		List<Library> libList = Library.getVisibleLibraries();
		for (Library lib : libList)
		{
			library.addItem(lib.getName());
		}
		if (curLib == null) curLib = Library.getCurrent();
		int curIndex = libList.indexOf(curLib);
		if (curIndex >= 0) library.setSelectedIndex(curIndex);

		newWindow.setSelected(makeWindow);
		if (defaultName != null) cellName.setText(defaultName);
		cellName.grabFocus();
		finishInitialization();
	}

	protected void escapePressed() { cancel(null); }

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    private void initComponents() {//GEN-BEGIN:initComponents
        java.awt.GridBagConstraints gridBagConstraints;

        cancel = new javax.swing.JButton();
        ok = new javax.swing.JButton();
        cellName = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        library = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        newWindow = new javax.swing.JCheckBox();
        view = new javax.swing.JScrollPane();
        techLabel = new javax.swing.JLabel();
        techComboBox = new javax.swing.JComboBox();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("New Cell");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        cancel.setText("Cancel");
        cancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(cancel, gridBagConstraints);

        ok.setText("OK");
        ok.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ok(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(ok, gridBagConstraints);

        cellName.setColumns(20);
        cellName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cellNameActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(cellName, gridBagConstraints);

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel4.setText("Library:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(library, gridBagConstraints);

        jLabel1.setText("Name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        jLabel2.setText("View:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel2, gridBagConstraints);

        newWindow.setText("Make new window");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        getContentPane().add(newWindow, gridBagConstraints);

        view.setMinimumSize(new java.awt.Dimension(200, 150));
        view.setPreferredSize(new java.awt.Dimension(200, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(view, gridBagConstraints);

        techLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        techLabel.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(techLabel, gridBagConstraints);

        techComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                techComboBoxItemStateChanged(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(techComboBox, gridBagConstraints);

        pack();
    }//GEN-END:initComponents

    private void techComboBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_techComboBoxItemStateChanged
		boolean anyLayout = false;
		for (int i = 0; i < viewModel.size(); i++)
		{
			if (!checkedViews.contains(i)) continue;
			ViewTechOption option = (ViewTechOption)viewModel.get(i);
			if (option.view != null && isLayoutView(option.view))
				anyLayout = true;
		}
		techComboBox.setEnabled(anyLayout);
    }//GEN-LAST:event_techComboBoxItemStateChanged

	private static boolean isLayoutView(View v)
	{
		return v == View.LAYOUT || v == View.LAYOUTCOMP || v == View.LAYOUTSKEL;
	}

	private void cellNameActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cellNameActionPerformed
	{//GEN-HEADEREND:event_cellNameActionPerformed
		ok(evt);
	}//GEN-LAST:event_cellNameActionPerformed

	private void cancel(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancel
	{//GEN-HEADEREND:event_cancel
		closeDialog(null);
	}//GEN-LAST:event_cancel

	private void ok(java.awt.event.ActionEvent evt)//GEN-FIRST:event_ok
	{//GEN-HEADEREND:event_ok
		String name = cellName.getText().trim();
		if (name.length() == 0)
		{
			Job.getUserInterface().showErrorMessage("Must type a cell name", "Invalid cell name");
			return;
		}
		List<String> cellNames = new ArrayList<String>();
		for (int i = 0; i < viewModel.size(); i++)
		{
			if (!checkedViews.contains(i)) continue;
			ViewTechOption option = (ViewTechOption)viewModel.get(i);
			String cellName = name;
			if (option.view != View.UNKNOWN) cellName += option.view.getAbbreviationExtension();
			cellNames.add(cellName);
		}
		if (cellNames.isEmpty())
		{
			Job.getUserInterface().showErrorMessage("Select at least one view", "No View Selected");
			return;
		}
		String libName = (String)library.getSelectedItem();
		Library lib = Library.findLibrary(libName);
		if (lib == null)
		{
			Job.getUserInterface().showErrorMessage("No library exists.  Create one first.", "No Library");
			return;
		}
		makeWindow = newWindow.isSelected();

		// create the cells
		Technology tech = null;
		if (techComboBox.isEnabled()) tech = ((ViewTechOption)techComboBox.getSelectedItem()).tech;
		new CreateCell(lib, cellNames, tech, makeWindow);

		closeDialog(null);
	}//GEN-LAST:event_ok

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		makeWindow = newWindow.isSelected();
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

	/**
	 * Class to create cells in a new thread.
	 */
	private static class CreateCell extends Job
	{
		private Library lib;
		private List<String> cellNames;
		private boolean newWindow;
		private Technology tech;
		private List<Cell> newCells = new ArrayList<Cell>();

		protected CreateCell(Library l, List<String> names, Technology t, boolean w)
		{
			super("Create Cells", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.lib = l;
			this.cellNames = names;
			this.newWindow = w;
			this.tech = t;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			// should ensure that the name is valid
			for (String cellName : cellNames)
			{
				Cell newCell = Cell.makeInstance(getEditingPreferences(), lib, cellName);
				if (newCell == null)
					throw new JobException("Unable to create cell " + cellName);
				if (tech != null && newCell.isLayout())
					newCell.setTechnology(tech);
				newCells.add(newCell);
			}
			fieldVariableChanged("newCells");
			return true;
		}

		public void terminateOK()
		{
			if (newCells.isEmpty()) return;
			if (newWindow)
			{
				for (Cell newCell : newCells)
					WindowFrame.createEditWindow(newCell);
			} else
			{
				WindowFrame wf = WindowFrame.getCurrentWindowFrame();
				wf.setCellWindow(newCells.get(0), null);
			}
		}
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancel;
    private javax.swing.JTextField cellName;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JComboBox library;
    private javax.swing.JCheckBox newWindow;
    private javax.swing.JButton ok;
    private javax.swing.JComboBox techComboBox;
    private javax.swing.JLabel techLabel;
    private javax.swing.JScrollPane view;
    // End of variables declaration//GEN-END:variables
}
