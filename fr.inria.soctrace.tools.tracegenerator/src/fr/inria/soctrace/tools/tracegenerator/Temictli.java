package fr.inria.soctrace.tools.tracegenerator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.soctrace.framesoc.core.tools.management.PluginImporterJob;
import fr.inria.soctrace.framesoc.core.tools.model.FileInput;
import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;
import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;
import fr.inria.soctrace.framesoc.core.tools.model.IPluginToolJobBody;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;

/**
 * Temictli is a trace generator for the Framesoc framework
 */

public class Temictli extends FramesocTool {

	private static final Logger logger = LoggerFactory
			.getLogger(Temictli.class);

	private String configFile;
	public static final int NumberOfEventInCommit = 20000;

	/**
	 * Plugin Tool Job body: we use a Job since we have to perform a long
	 * operation and we don't want to freeze the UI.
	 */
	private class TemictliPluginJobBody implements IPluginToolJobBody {

		private FileInput args;

		public TemictliPluginJobBody(IFramesocToolInput input) {
			this.args = (FileInput) input;
		}

		@Override
		public void run(IProgressMonitor monitor) {

			logger.debug("Arguments: ");
			for (String s : args.getFiles()) {
				logger.debug(s);
			}

			/*
			 * String sysDbName = Configuration.getInstance().get(
			 * SoCTraceProperty.soctrace_db_name); String traceDbName =
			 * FramesocManager.getInstance().getTraceDBName( "CTFTRACE");
			 */

			Temictli generator = new Temictli();
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
		//int[] numberOfEvents = new int[] { 500000000, 1500000000 };
		int[] numberOfEvents = new int[] { Integer.MAX_VALUE };
		for (int aNumberOfEvents : numberOfEvents) {
			TraceGenConfig aConfig = new TraceGenConfig();
			aConfig.getCategories().add(EventCategory.STATE);
			aConfig.setNumberOfEventType(100);
			aConfig.setNumberOfProducers(11111);
			aConfig.setNumberOfLeaves(10000);
			aConfig.setOnlyLeavesAsProducer(true);
			aConfig.setNumberOfEvents(aNumberOfEvents);

			TraceGenerator aGenerator = new TraceGenerator();
			int numberOfWork = (int) (aConfig.getNumberOfLeaves()) + 1;
			monitor.beginTask("Generating trace", numberOfWork);
			aGenerator.setTraceConfig(aConfig,
					"virtualTrace_" + aNumberOfEvents + "_" + System.currentTimeMillis());

			try {
				aGenerator.generateTrace(monitor);
			} catch (SoCTraceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
	public void launch(IFramesocToolInput input) {
		PluginImporterJob job = new PluginImporterJob(
				"Temictli Trace Generator", new TemictliPluginJobBody(input));
		job.setUser(true);
		job.schedule();
	}

}
