/**
 * 
 */
package fr.inria.soctrace.framesoc.bench.reading;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;

import fr.inria.soctrace.framesoc.bench.reading.FramesocReaderConfig.ConfigLine;
import fr.inria.soctrace.framesoc.ui.loaders.LoaderUtils;
import fr.inria.soctrace.lib.model.Event;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.query.EventQuery;
import fr.inria.soctrace.lib.query.conditions.LogicalCondition;
import fr.inria.soctrace.lib.query.conditions.SimpleCondition;
import fr.inria.soctrace.lib.query.conditions.ConditionsConstants.ComparisonOperation;
import fr.inria.soctrace.lib.query.conditions.ConditionsConstants.LogicalOperation;
import fr.inria.soctrace.lib.search.ITraceSearch;
import fr.inria.soctrace.lib.search.TraceSearch;
import fr.inria.soctrace.lib.storage.DBObject;
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

	private static Map<String, Trace> traces;

	private static final class ReaderOutput {
		public long size;
		public boolean index;
		public boolean param;
		public int interval;
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

		loadTraces();

		FramesocReaderConfig config = new FramesocReaderConfig();
		config.setConfigFile(configFile);
		List<ConfigLine> lines = config.getConfigLines();
		Boolean params[] = config.getParams();
		Integer intervals[] = config.getIntervals();

		System.out.println(ReaderOutput.getHeader());
		for (ConfigLine line : lines) {
			for (Boolean param : params) {
				for (Integer interval : intervals) {
					//System.out.println(line + ", " + param + ", " + interval);
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

	private static void doExperiment(ConfigLine line, Boolean param, int interval)
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
		Assert.isTrue(elist.size() == line.events, "Wrong number of events: expected "
				+ line.events + ", obtained " + elist.size());
		traceDB.close();

		dm.end();
		output.totalTime = dm.getDelta();
		output.intervalTime = output.totalTime;

		return output;
	}

	private static ReaderOutput readInterval(ConfigLine line, Boolean param, int interval)
			throws SoCTraceException {

		if (!traces.containsKey(line.dbName)) {
			throw new SoCTraceException("Trace " + line.dbName + "  not found.");
		}
		Trace t = traces.get(line.dbName);

		ReaderOutput output = new ReaderOutput(line);
		output.param = param;
		output.interval = interval;

		DeltaManager dm = new DeltaManager();
		dm.start();
		output.intervalTime = readIntervals(t, param, interval, line);
		dm.end();
		output.totalTime = dm.getDelta();

		return output;
	}

	private static long readIntervals(Trace t, Boolean param, int interval, ConfigLine line)
			throws SoCTraceException {

		int ev = 0;
		long start = t.getMinTimestamp();
		long end = t.getMaxTimestamp();

		// compute interval duration
		long intervalDuration = LoaderUtils.getIntervalDuration(t, interval);

		// read the time window, interval by interval
		DeltaManager dm = new DeltaManager();
		List<Long> intervals = new ArrayList<>();
		TraceDBObject traceDB = null;
		try {
			traceDB = TraceDBObject.openNewIstance(t.getDbName());
			EventQuery eq = new EventQuery(traceDB);
			long t0 = start;
			while (t0 < end) {
				dm.start();
				// end interval
				long t1 = Math.min(end, t0 + intervalDuration);
				// condition
				ComparisonOperation endComp = (t1 >= end) ? ComparisonOperation.LE
						: ComparisonOperation.LT;
				LogicalCondition and = new LogicalCondition(LogicalOperation.AND);
				and.addCondition(new SimpleCondition("TIMESTAMP", ComparisonOperation.GE, String
						.valueOf(t0)));
				and.addCondition(new SimpleCondition("TIMESTAMP", endComp, String.valueOf(t1)));
				// query
				eq.clear();
				eq.setLoadParameters(param);
				eq.setElementWhere(and);
				// get list
				List<Event> events = eq.getList();
				ev += events.size();
				// next interval
				t0 = t1;
				dm.end();
				if (t1 < end) {
					// not last interval
					intervals.add(dm.getDelta());
				}
			}
			Assert.isTrue(ev == line.events);
		} finally {
			DBObject.finalClose(traceDB);
		}
		// compute avg interval (last interval not in the list)
		long sum = 0;
		for (Long i : intervals) {
			sum += i;
		}
		if (intervals.size() > 0)
			return (long) (sum / ((double) intervals.size()));
		return 0;
	}

	private static void loadTraces() {
		ITraceSearch ts = null;
		try {
			ts = new TraceSearch().initialize();
			List<Trace> tList = ts.getTraces();
			traces = new HashMap<>();
			for (Trace t : tList) {
				traces.put(t.getDbName(), t);
			}
		} catch (SoCTraceException e) {
			e.printStackTrace();
		} finally {
			TraceSearch.finalUninitialize(ts);
		}
	}

}
