/**
 * 
 */
package fr.inria.soctrace.framesoc.bench.reading;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.soctrace.framesoc.bench.reading.FramesocReaderConfig.ConfigLine;

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
		public int maxMemory;

		public ReaderOutput(ConfigLine line) {
			size = line.events;
			index = line.index;
		} 
		
		@Override
		public String toString() {
			return size + ", " + index + ", " + param + ", " + interval + ", " + intervalTime
					+ ", " + totalTime + ", " + maxMemory;
		}

		public static String getHeader() {
			return "size index param interval interval_time total_time max_memory";
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(FramesocReader.class);

	/**
	 * @param args
	 *            vector of 1 element, being the configuration file path
	 */
	public static void main(String[] args) {

		logger.debug("Arguments: ");
		for (String s : args) {
			logger.debug(s);
		}

		if (args.length < 1) {
			logger.error("Too few arguments");
			return;
		}

		String configFile = args[0];
		File t = new File(configFile);
		if (!t.exists()) {
			logger.error("File " + configFile + " not found");
			return;
		}

		FramesocReaderConfig config = new FramesocReaderConfig();
		config.setConfigFile(configFile);
		List<ConfigLine> lines = config.getConfigLines();
		Boolean params[] = config.getParams();
		Long intervals[] = config.getIntervals();

		for (ConfigLine line : lines) {
			for (Boolean param : params) {
				for (Long interval : intervals) {
					doExperiment(line, param, interval);
				}
			}
		}

	}

	private static void doExperiment(ConfigLine line, Boolean param, Long interval) {
		logger.debug(line.toString() + ", param=" + param + ", interval=" + interval);
		System.out.println(ReaderOutput.getHeader());
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

	private static ReaderOutput readAll(ConfigLine line, Boolean param) {
		ReaderOutput output = new ReaderOutput(line);
		output.param = param;
		output.interval = 0;

		// TODO compute totalTime and maxMemory
		
		output.intervalTime = output.totalTime;
		return output;
	}

	private static ReaderOutput readInterval(ConfigLine line, Boolean param, Long interval) {
		ReaderOutput output = new ReaderOutput(line);
		output.param = param;
		output.interval = interval;

		// TODO compute intervalTime, totalTime and maxMemory
		
		return output;
	}

}
