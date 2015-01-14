/**
 * 
 */
package fr.inria.soctrace.framesoc.bench.reading;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader configuration file manager.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class FramesocReaderConfig {

	// # database_path size index runs
	public final static class ConfigLine {
		public final static String SEPARATOR = "\\s+";
		public String dbName;
		public long events;
		public boolean index;
		public boolean eindex;
		public int runs;

		@Override
		public String toString() {
			return "ConfigLine [dbName=" + dbName + ", events=" + events + ", index=" + index
					+ ", eindex=" + eindex + ", runs=" + runs + "]";
		}
	}

	public final static String PARAM = "param";
	public final static String INTERVAL = "interval";
	public final static String LEVEL_SEPARATOR = ",";
	public final static String FACTOR_ASSIGNMENT = "=";
	public final static String CONF_SEPARATOR = "---";

	private Boolean[] params;
	private Integer[] intervals;
	private File configFile;
	private List<ConfigLine> lines;

	/**
	 * Initialization method. Call this before doing anything.
	 * 
	 * @param configFile
	 *            configuration file path
	 */
	public void setConfigFile(String configFile) {
		this.configFile = new File(configFile);
		if (!this.configFile.isFile() || !this.configFile.exists()) {
			throw new IllegalArgumentException();
		}
		parseFile();
	}

	/*
	 * Getters
	 */

	public Boolean[] getParams() {
		if (configFile == null)
			throw new IllegalStateException();
		return params;
	}

	public Integer[] getIntervals() {
		if (configFile == null)
			throw new IllegalStateException();
		return intervals;
	}

	public List<ConfigLine> getConfigLines() {
		if (configFile == null)
			throw new IllegalStateException();
		return lines;
	}

	/*
	 * Utils
	 */

	private void parseFile() {

		try {
			BufferedReader bufFileReader = new BufferedReader(new FileReader(configFile));
			String line;
			// Read configuration

			// header
			while ((line = bufFileReader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("#")) {
					continue;
				}
				// var=val,val,val,..
				if (line.startsWith(PARAM)) {
					String levels[] = getLevels(line);
					params = new Boolean[levels.length];
					int i = 0;
					for (String level : levels) {
						params[i++] = Boolean.valueOf(level);
					}
				} else if (line.startsWith(INTERVAL)) {
					String levels[] = getLevels(line);
					intervals = new Integer[levels.length];
					int i = 0;
					for (String level : levels) {
						intervals[i++] = Integer.valueOf(level);
					}
				} else if (line.equals(CONF_SEPARATOR)) {
					break;
				}
			}

			lines = new ArrayList<>();

			while ((line = bufFileReader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("#")) {
					continue;
				}

				String tokens[] = line.split(ConfigLine.SEPARATOR);
				ConfigLine l = new ConfigLine();
				l.dbName = tokens[0];
				l.events = Long.valueOf(tokens[1]);
				l.index = Boolean.valueOf(tokens[2]);
				l.eindex = Boolean.valueOf(tokens[3]);
				l.runs = Integer.valueOf(tokens[4]);
				lines.add(l);
			}

			bufFileReader.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String[] getLevels(String line) {
		String tokens[] = line.split(FACTOR_ASSIGNMENT);
		if (tokens.length < 2)
			throw new IllegalArgumentException();
		return tokens[1].split(LEVEL_SEPARATOR);
	}

}
