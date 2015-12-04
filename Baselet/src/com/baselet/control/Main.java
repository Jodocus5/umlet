package com.baselet.control;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.filechooser.FileSystemView;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.baselet.control.config.Config;
import com.baselet.control.config.handler.ConfigHandler;
import com.baselet.control.constants.Constants;
import com.baselet.control.enums.Program;
import com.baselet.control.util.CanOpenDiagram;
import com.baselet.control.util.Path;
import com.baselet.control.util.RecentlyUsedFilesList;
import com.baselet.diagram.CurrentDiagram;
import com.baselet.diagram.DiagramHandler;
import com.baselet.diagram.Notifier;
import com.baselet.diagram.PaletteHandler;
import com.baselet.diagram.io.OpenFileChooser;
import com.baselet.element.interfaces.GridElement;
import com.baselet.gui.BaseGUI;
import com.baselet.gui.CurrentGui;
import com.baselet.gui.pane.OwnSyntaxPane;

public class Main implements CanCloseProgram, CanOpenDiagram {

	private static final Logger log = Logger.getLogger(Main.class);

	private static Main main = new Main();

	private GridElement editedGridElement;
	private TreeMap<String, PaletteHandler> palettes;
	private final ArrayList<DiagramHandler> diagrams = new ArrayList<DiagramHandler>();

	public static Main getInstance() {
		return main;
	}

	public void init(BaseGUI gui) {
		CurrentGui.getInstance().setGui(gui);
		ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE); // Tooltips should not hide after some time
		gui.initGUI(); // show gui
	}

	public void initLogger() {
		String log4jFilePath = Path.homeProgram() + Constants.LOG4J_PROPERTIES;
		try {
			// If no log4j.properties file exists, we create a simple one
			if (!new File(log4jFilePath).exists()) {
				File tempLog4jFile = File.createTempFile(Constants.LOG4J_PROPERTIES, null);
				tempLog4jFile.deleteOnExit();
				log4jFilePath = tempLog4jFile.getAbsolutePath();
				Writer writer = new BufferedWriter(new FileWriter(tempLog4jFile));
				writer.write(
						"log4j.rootLogger=ERROR, SYSTEM_OUT\n" +
								"log4j.appender.SYSTEM_OUT=org.apache.log4j.ConsoleAppender\n" +
								"log4j.appender.SYSTEM_OUT.layout=org.apache.log4j.PatternLayout\n" +
								"log4j.appender.SYSTEM_OUT.layout.ConversionPattern=%6r | %-5p | %-30c - \"%m\"%n\n");
				writer.close();
			}
			Properties props = new Properties();
			props.put("PROJECT_PATH", Path.homeProgram()); // Put homepath as relative variable in properties file
			FileInputStream inStream = new FileInputStream(log4jFilePath);
			props.load(inStream);
			inStream.close();
			PropertyConfigurator.configure(props);
			log.info("Logger configuration initialized");
		} catch (Exception e) {
			System.err.println("Initialization of " + Constants.LOG4J_PROPERTIES + " failed:");
			e.printStackTrace();
		}
	}

	public void setPropertyPanelToGridElement(final GridElement e) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setPropertyPanelToGridElementHelper(e);
			}
		});
	}

	private void setPropertyPanelToGridElementHelper(GridElement e) {
		editedGridElement = e;
		OwnSyntaxPane propertyPane = CurrentGui.getInstance().getGui().getPropertyPane();
		if (e != null) {
			propertyPane.switchToElement(e);
		}
		else {
			DiagramHandler handler = CurrentDiagram.getInstance().getDiagramHandler();
			if (handler == null) {
				propertyPane.switchToNonElement("");
			}
			else {
				propertyPane.switchToNonElement(handler.getHelpText());
			}
		}
	}

	public void doNew() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				doNewHelper();
			}
		});
	}

	private void doNewHelper() {
		if (lastTabIsEmpty()) {
			return; // If the last tab is empty do nothing (it's already new)
		}
		DiagramHandler diagram = new DiagramHandler(null);
		diagrams.add(diagram);
		CurrentGui.getInstance().getGui().open(diagram);
		if (diagrams.size() == 1) {
			setPropertyPanelToGridElement(null);
		}
	}

	public void doOpenFromFileChooser() {
		List<String> files = new OpenFileChooser().getFilesToOpen(CurrentGui.getInstance().getGui().getMainFrame());
		for (String file : files) {
			doOpen(file);
		}
	}

	@Override
	public void doOpen(final String filename) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				doOpenHelper(filename);
			}
		});
	}

	private void doOpenHelper(String filename) {
		File file = new File(filename);
		if (!file.exists()) {
			Notifier.getInstance().showError(filename + " does not exist");
			return;
		}
		Config.getInstance().setOpenFileHome(file.getParent());
		DiagramHandler handler = getDiagramHandlerForFile(filename);
		if (handler != null) { // File is already opened -> jump to the tab
			CurrentGui.getInstance().getGui().jumpTo(handler);
			Notifier.getInstance().showInfo("switched to " + filename);
		}
		else {
			if (lastTabIsEmpty()) {
				diagrams.get(diagrams.size() - 1).doClose(); // If the last tab is empty close it (the opened diagram replaces the new one)
			}
			editedGridElement = null; // must be set to null here, otherwise the change listener of the property panel will change element text to help_text of diagram (see google code Issue 174)
			DiagramHandler diagram = new DiagramHandler(file);
			diagrams.add(diagram);
			CurrentGui.getInstance().getGui().open(diagram);
			if (diagrams.size() == 1) {
				setPropertyPanelToGridElement(null);
			}
			RecentlyUsedFilesList.getInstance().add(filename);
			Notifier.getInstance().showInfo(filename + " opened");
		}
	}

	private DiagramHandler getDiagramHandlerForFile(String file) {
		for (DiagramHandler d : diagrams) {
			if (d.getFullPathName().equalsIgnoreCase(file)) {
				return d;
			}
		}
		return null;
	}

	/**
	 * If the last diagram tab and it's undo history (=controller) is empty return true, else return false
	 */
	private boolean lastTabIsEmpty() {
		if (!diagrams.isEmpty()) {
			DiagramHandler lastDiagram = diagrams.get(diagrams.size() - 1);
			if (lastDiagram.getController().isEmpty() && lastDiagram.getDrawPanel().getGridElements().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * called by UI when main is closed
	 */
	@Override
	public void closeProgram() {
		ConfigHandler.saveConfig(CurrentGui.getInstance().getGui());
	}

	public TreeMap<String, PaletteHandler> getPalettes() {
		if (palettes == null) {
			palettes = new TreeMap<String, PaletteHandler>(Constants.DEFAULT_FIRST_COMPARATOR);
			// scan palettes
			List<File> palettes = scanForPalettes();
			for (File palette : palettes) {
				this.palettes.put(getFilenameWithoutExtension(palette), new PaletteHandler(palette));
			}
		}
		return palettes;
	}

	private String getFilenameWithoutExtension(File file) {
		return file.getName().substring(0, file.getName().indexOf("."));
	}

	private List<File> scanForPalettes() {
		// scan palettes directory...
		FileSystemView fileSystemView = FileSystemView.getFileSystemView();
		File[] paletteFiles = fileSystemView.getFiles(new File(Path.homeProgram() + "palettes/"), false);
		List<File> palettes = new ArrayList<File>();
		for (File palette : paletteFiles) {
			if (palette.getName().endsWith("." + Program.getInstance().getExtension())) {
				palettes.add(palette);
			}
		}
		return palettes;
	}

	public List<String> getTemplateNames() {
		ArrayList<String> templates = new ArrayList<String>();
		// scan palettes directory...
		FileSystemView fileSystemView = FileSystemView.getFileSystemView();
		File[] templateFiles = fileSystemView.getFiles(new File(Path.customElements()), false);
		for (File template : templateFiles) {
			if (template.getName().endsWith(".java")) {
				templates.add(template.getName().substring(0, template.getName().length() - 5));
			}
		}
		Collections.sort(templates, Constants.DEFAULT_FIRST_COMPARATOR);
		return templates;
	}

	public List<DiagramHandler> getDiagrams() {
		return diagrams;
	}

	public Collection<DiagramHandler> getDiagramsAndPalettes() {
		List<DiagramHandler> returnList = new ArrayList<DiagramHandler>(getDiagrams());
		returnList.addAll(getPalettes().values());
		return returnList;
	}

	public GridElement getEditedGridElement() {
		return editedGridElement;
	}

	public PaletteHandler getPalette() {
		String name = CurrentGui.getInstance().getGui().getSelectedPalette();
		if (name != null) {
			return getPalettes().get(name);
		}
		return null;
	}

}
