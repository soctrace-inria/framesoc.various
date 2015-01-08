package fr.inria.soctrace.tools.tracegenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.soctrace.framesoc.core.tools.management.PluginImporterJob;
import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;
import fr.inria.soctrace.framesoc.core.tools.model.IPluginToolJobBody;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.utils.DeltaManager;

/**
 * Temictli is a trace generator for the Framesoc framework
 */

public class Temictli extends FramesocTool {

	private static final Logger logger = LoggerFactory.getLogger(Temictli.class);

	private String configFile;
	private final String CatSeparator = "#";
	private final String CSVDelimiter = ";";
	public static final int NumberOfEventInCommit = 20000;

	private static final boolean PRINT_TIME = true;

	/**
	 * Plugin Tool Job body: we use a Job since we have to perform a long operation and we don't
	 * want to freeze the UI.
	 */
	private class TemictliPluginJobBody implements IPluginToolJobBody {

		private String args[];

		public TemictliPluginJobBody(String[] args) {
			this.args = args;
		}

		@Override
		public void run(IProgressMonitor monitor) {

			logger.debug("Arguments: ");
			for (String s : args) {
				logger.debug(s);
			}

			if (args.length < 1) {
				logger.error("Too few arguments");
				return;
			}

			/*
			 * String sysDbName = Configuration.getInstance().get(
			 * SoCTraceProperty.soctrace_db_name); String traceDbName =
			 * FramesocManager.getInstance().getTraceDBName( "CTFTRACE");
			 */

			List<String> configFiles = new ArrayList<String>();

			for (int i = 0; i < args.length; ++i) {
				configFiles.add(args[i]);
				File t = new File(args[i]);
				if (!t.exists()) {
					logger.error("File " + args[i] + " not found");
					return;
				}
			}

			int numTraceFiles = configFiles.size();
			String[] traceFiles = new String[numTraceFiles];
			Iterator<String> it = configFiles.iterator();
			for (int i = 0; i < numTraceFiles; ++i) {
				traceFiles[i] = it.next();
			}

			Temictli generator = new Temictli();
			generator.setConfigFile(configFiles.get(0));
			generator.generateTraces(monitor);
			monitor.done();
		}
	}

	public String getConfigFile() {
		return configFile;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}

	public void generateTraces(IProgressMonitor monitor) {
		File aFile = new File(configFile);

		try {
			if (aFile.canRead() && aFile.isFile()) {
				BufferedReader bufFileReader;
				bufFileReader = new BufferedReader(new FileReader(aFile));
				String line;

				// Read configuration
				while ((line = bufFileReader.readLine()) != null) {

					line = line.trim();
					if (line.isEmpty())
						continue;
					if (line.startsWith("#"))
						continue;

					String[] header = line.split(CSVDelimiter);
					TraceGenConfig aConfig = new TraceGenConfig();
					String[] cats = header[0].split(CatSeparator);
					for (String aCat : cats) {
						aConfig.getCategories().add(stringToCategory(aCat));
					}

					aConfig.setNumberOfEventType(Integer.valueOf(header[1]));
					aConfig.setNumberOfProducers(Integer.valueOf(header[2]));
					aConfig.setNumberOfLeaves(Integer.valueOf(header[3]));
					aConfig.setOnlyLeavesAsProducer(Boolean.valueOf(header[4]));
					aConfig.setNumberOfEvents(Long.valueOf(header[5]));
					aConfig.setForceIndex(Boolean.valueOf(header[6]));
					aConfig.setNumberOfRuns(Integer.valueOf(header[7]));

					if (monitor.isCanceled()) {
						bufFileReader.close();
						return;
					}

					TraceGenerator aGenerator = new TraceGenerator();
					int numberOfWork = (int) (aConfig.getNumberOfEvents() / NumberOfEventInCommit) + 1;
					monitor.beginTask("Generating trace", numberOfWork);

					for (int i = 0; i < aConfig.getNumberOfRuns(); i++) {
						aGenerator.setTraceConfig(aConfig,
								"virtualTrace_" + System.currentTimeMillis());
						DeltaManager dm = new DeltaManager();
						dm.start();
						aGenerator.generateTrace(monitor);
						dm.end();
						if (PRINT_TIME) {
							System.out.println(aConfig.getNumberOfEvents() + ", "
									+ aConfig.isForceIndex() + ", " + dm.getDelta());
						}
					}
				}

				bufFileReader.close();
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SoCTraceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static int stringToCategory(String aCategory) {
		if (aCategory.equals("Event"))
			return EventCategory.PUNCTUAL_EVENT;
		if (aCategory.equals("State"))
			return EventCategory.STATE;
		if (aCategory.equals("Link"))
			return EventCategory.LINK;
		if (aCategory.equals("Variable"))
			return EventCategory.VARIABLE;

		return -1;
	}

	@Override
	public void launch(String[] args) {
		PluginImporterJob job = new PluginImporterJob("Temictli Trace Generator",
				new TemictliPluginJobBody(args));
		job.setUser(true);
		job.schedule();
	}

}
