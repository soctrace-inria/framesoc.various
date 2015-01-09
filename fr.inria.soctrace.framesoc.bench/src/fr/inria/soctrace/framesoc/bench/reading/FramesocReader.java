/**
 * 
 */
package fr.inria.soctrace.framesoc.bench.reading;

import java.io.File;
import java.util.List;

import org.eclipse.core.runtime.Assert;

import fr.inria.soctrace.framesoc.bench.reading.FramesocReaderConfig.ConfigLine;
import fr.inria.soctrace.lib.model.Event;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.query.EventQuery;
import fr.inria.soctrace.lib.storage.TraceDBObject;
import fr.inria.soctrace.lib.utils.DeltaManager;

/**
 * Framesoc Reader.
 * 
 * Input
 * 
 * The reader takes a configuration file as input.
 * 
 * <pre>
 * The header contains the factors param and intervals with the levels.
 * - param is a boolean (true/false) saying if we have to read params
 * - interval is the interval size (0 means all trace)
 * Example:
 * param=true,false
 * interval=0, 100000
 * 
 * Then there is a separator: ---
 * 
 * Then there is a list of lines. Each line of this file correspond to an experiment. 
 * Each line has the following format:
 * database_path size index param interval runs
 * where:
 * - database_path is the DB path
 * - size is the number of events
 * - index is a boolean (true/false) saying if the trace is indexed
 * - runs is the number of runs per experiment
 * </pre>
 * 
 * Output
 * 
 * <pre>
 * The output is a list of lines having the following format:
 * size, index, param, interval, interval_time, total_time, max_memory
 * where:
 * - size, index, param and interval are the same as above
 * - interval_time is the avg time to read an interval
 * - total_time is the total reading time
 * - max_memory is the max amount of memory used
 * </pre>
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class FramesocReader {

	private static final class ReaderOutput {
		public long size;
		public boolean index;
		public boolean param;
		public long interval;
		public long intervalTime;
		public long totalTime;

		public ReaderOutput(ConfigLine line) {
			size = line.events;
			index = line.index;
		}

		@Override
		public String toString() {
			return size + ", " + index + ", " + param + ", " + interval + ", " + intervalTime
					+ ", " + totalTime;
		}

		public static String getHeader() {
			return "size, index, param, interval, interval_time, total_time";
		}
	}

	/**
	 * @param args
	 *            vector of 1 element, being the configuration file path
	 */
	public static void main(String[] args) {

		if (args.length < 1) {
			System.err.println("Too few arguments");
			return;
		}

		String configFile = args[0];
		File t = new File(configFile);
		if (!t.exists()) {
			System.err.println("File " + configFile + " not found");
			return;
		}

		FramesocReaderConfig config = new FramesocReaderConfig();
		config.setConfigFile(configFile);
		List<ConfigLine> lines = config.getConfigLines();
		Boolean params[] = config.getParams();
		Long intervals[] = config.getIntervals();

		System.out.println(ReaderOutput.getHeader());
		for (ConfigLine line : lines) {
			for (Boolean param : params) {
				for (Long interval : intervals) {
					try {
						doExperiment(line, param, interval);
					} catch (SoCTraceException e) {
						e.printStackTrace();
						System.err.println("Experiment failed: " + line);
					}
				}
			}
		}

	}

	private static void doExperiment(ConfigLine line, Boolean param, Long interval)
			throws SoCTraceException {
		for (int i = 0; i < line.runs; i++) {
			ReaderOutput output = null;
			if (interval == 0) {
				output = readAll(line, param);
			} else {
				output = readInterval(line, param, interval);
			}
			System.out.println(output.toString());
		}
	}
	
	private static ReaderOutput readAll(ConfigLine line, Boolean param) throws SoCTraceException {
		ReaderOutput output = new ReaderOutput(line);
		output.param = param;
		output.interval = 0;

		DeltaManager dm = new DeltaManager();
		dm.start();

		TraceDBObject traceDB = TraceDBObject.openNewIstance(line.dbName);
		EventQuery eq = new EventQuery(traceDB);
		eq.setLoadParameters(param);
		List<Event> elist = eq.getList();
		Assert.isTrue(elist.size() == line.events, "Wrong number of events");
		traceDB.close();

		dm.end();
		output.totalTime = dm.getDelta();
		output.intervalTime = output.totalTime;

		return output;
	}

	private static ReaderOutput readInterval(ConfigLine line, Boolean param, Long interval) {
		ReaderOutput output = new ReaderOutput(line);
		output.param = param;
		output.interval = interval;

		DeltaManager dm = new DeltaManager();
		int intervals = 0;
		dm.start();

		// TODO read
		// TODO update intervals

		dm.end();
		output.totalTime = dm.getDelta();
		output.intervalTime = output.totalTime;
		if (intervals > 0) {
			output.intervalTime = output.totalTime / intervals;
		}

		return output;
	}

	// TODO use heap monitor to measure memory on single executions
	
}
