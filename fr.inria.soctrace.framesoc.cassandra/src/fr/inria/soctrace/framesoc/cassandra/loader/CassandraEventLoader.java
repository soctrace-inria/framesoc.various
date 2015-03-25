/*******************************************************************************
 * Copyright (c) 2012-2015 INRIA.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Generoso Pagano - initial API and implementation
 ******************************************************************************/
package fr.inria.soctrace.framesoc.cassandra.loader;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

import fr.inria.soctrace.framesoc.ui.gantt.model.IEventLoader;
import fr.inria.soctrace.framesoc.ui.gantt.model.ReducedEvent;
import fr.inria.soctrace.framesoc.ui.loaders.LoaderUtils;
import fr.inria.soctrace.framesoc.ui.model.LoaderQueue;
import fr.inria.soctrace.framesoc.ui.model.TimeInterval;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.query.conditions.ConditionsConstants.ComparisonOperation;
import fr.inria.soctrace.lib.storage.DBObject.DBMode;
import fr.inria.soctrace.lib.storage.utils.SQLConstants.FramesocTable;
import fr.inria.soctrace.lib.utils.DeltaManager;

/**
 * Default event loader for the Gantt Chart.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class CassandraEventLoader implements IEventLoader {

	// logger
	private static final Logger logger = LoggerFactory.getLogger(CassandraEventLoader.class);

	// constants
	private final int EVENTS_PER_QUERY = 100000;

	// set by the user
	private Trace fTrace = null;
	private LoaderQueue<ReducedEvent> fQueue = null;

	// current visualized trace data
	private CassandraSession fSession = null;
	private Map<Integer, EventProducer> fProducers = null;
	private boolean fProducersLoaded = false;
	private Map<Integer, EventType> fTypes = null;
	private boolean fTypesLoaded = false;
	private TimeInterval fTimeInterval;
	private long fLatestStart;

	@Override
	public Map<Integer, EventProducer> getProducers() {
		if (fProducersLoaded)
			return fProducers;
		fProducers = new HashMap<Integer, EventProducer>();
		String query = "SELECT * FROM EVENT_PRODUCER;";
		ResultSet rs = getCassandraSession().execute(query);
		for (Row r : rs) {
			EventProducer ep = new EventProducer(r.getInt(0));
			ep.setName(r.getString(1));
			ep.setType("test");
			ep.setLocalId("T" + ep.getId());
			fProducers.put(ep.getId(), ep);
		}
		fProducersLoaded = true;
		return fProducers;
	}

	@Override
	public Map<Integer, EventType> getTypes() {
		if (fTypesLoaded)
			return fTypes;
		fTypes = new HashMap<Integer, EventType>();
		String query = "SELECT * FROM EVENT_TYPE;";
		ResultSet rs = fSession.execute(query);
		for (Row r : rs) {
			EventType et = new EventType(r.getInt(0), EventCategory.STATE);
			et.setName(r.getString(1));
			fTypes.put(et.getId(), et);
		}
		fTypesLoaded = true;
		return fTypes;
	}

	@Override
	public void setTrace(Trace trace) {
		if (fTrace != trace) {
			clean();
			fTrace = trace;
		}
	}

	@Override
	public void setQueue(LoaderQueue<ReducedEvent> queue) {
		fQueue = queue;
	}

	@Override
	public void release() {
		fTrace = null;
		fQueue = null;
		clean();
	}

	public boolean checkCancel(IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			fQueue.setStop();
			return true;
		}
		return false;
	}

	@Override
	public void loadWindow(long start, long end, IProgressMonitor monitor) {

		try {
			Assert.isNotNull(fTrace, "Null trace in event loader");
			Assert.isNotNull(fQueue, "Null queue in event loader");
			start = Math.max(fTrace.getMinTimestamp(), start);
			end = Math.min(fTrace.getMaxTimestamp(), end);

			fTimeInterval = new TimeInterval(Long.MAX_VALUE, Long.MIN_VALUE);

			// compute interval duration
			long traceDuration = fTrace.getMaxTimestamp() - fTrace.getMinTimestamp();
			long intervalDuration = LoaderUtils.getIntervalDuration(fTrace, EVENTS_PER_QUERY);
			int totalWork = (int) ((double) traceDuration / intervalDuration);

			// read the time window, interval by interval
			monitor.beginTask("Loading Gantt Chart", totalWork);
			int oldWorked = 0;

			int totalEvents = 0;
			TimeInterval firstInterval = null;
			boolean first = true;
			long t0 = start;
			while (t0 < end) {
				// check if cancelled
				if (checkCancel(monitor)) {
					return;
				}

				// load interval
				long t1 = Math.min(end, t0 + intervalDuration);
				if (first) {
					// store the first time interval for later loading
					firstInterval = new TimeInterval(t0, t1);
					first = false;
				}
				boolean last = (t1 >= end);
				List<ReducedEvent> events = loadInterval(false, last, t0, t1, monitor);
				totalEvents = debug(events, totalEvents);
				if (checkCancel(monitor)) {
					return;
				}

				// update progress monitor
				int worked = (int) ((double) (fLatestStart - start) / intervalDuration);
				monitor.worked(Math.max(0, worked - oldWorked));
				oldWorked = worked;
				t0 = t1;

				fQueue.push(events, new TimeInterval(fTimeInterval));
			}

			// load states and links intersecting the start of the first interval
			if (firstInterval != null && firstInterval.startTimestamp != fTrace.getMinTimestamp()) {
				List<ReducedEvent> events = loadInterval(true, (firstInterval.endTimestamp >= end),
						firstInterval.startTimestamp, firstInterval.endTimestamp, monitor);
				totalEvents = debug(events, totalEvents);
				if (checkCancel(monitor)) {
					return;
				}
				fQueue.push(events, new TimeInterval(fTimeInterval));
			}

			fQueue.setComplete();

		} finally {
			if (!fQueue.isStop() && !fQueue.isComplete()) {
				// something went wrong, respect the queue contract anyway
				fQueue.setStop();
			}
			monitor.done();
		}
	}

	private List<ReducedEvent> loadInterval(boolean first, boolean last, long t0, long t1,
			IProgressMonitor monitor) {
		List<ReducedEvent> events = new LinkedList<>();
		try {
			DeltaManager dm = new DeltaManager();
			dm.start();
			ResultSet rs = getCassandraSession().execute(getQuery(t0, t1, first, last));
			logger.debug(dm.endMessage("exec query"));
			dm.start();
			for (Row row : rs) {
				ReducedEvent ev = new CassandraReducedEvent(row);
				if (first
						&& (ev.category != EventCategory.STATE && ev.category != EventCategory.LINK)) {
					continue;
				}
				events.add(ev);
				if (ev.timestamp > fLatestStart)
					fLatestStart = ev.timestamp;
				if (fTimeInterval.startTimestamp > ev.timestamp)
					fTimeInterval.startTimestamp = ev.timestamp;
				long end = ((ev.category == 0) ? ev.timestamp : ev.endTimestamp);
				if (fTimeInterval.endTimestamp < end)
					fTimeInterval.endTimestamp = end;
				if (monitor.isCanceled()) {
					fQueue.setStop();
					break;
				}
			}
			logger.debug(dm.endMessage("reduced event creation"));

		} catch (Exception e) {
			e.printStackTrace();
			fQueue.setStop();
		}
		return events;
	}

	private String getQuery(long t0, long t1, boolean first, boolean last) {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT " + ReducedEvent.SELECT_COLUMNS + " FROM " + FramesocTable.EVENT
				+ " WHERE ");
		ComparisonOperation endComp = (last) ? ComparisonOperation.LE : ComparisonOperation.LT;
		if (first) {
			// states and links: start < t0 and end >= t0
			// condition on category done manually in loadInterval for first interval
			sb.append("(TIMESTAMP) < (" + t0 + ") AND (TIMESTAMP, LPAR) >= ("
					+ fTrace.getMinTimestamp() + ", " + t0 + ") ALLOW FILTERING;");
		} else {
			// all events: start >= t0 and start < t1 (last interval start >= t0 and start <= t1)
			sb.append(" TIMESTAMP >= " + t0 + " AND TIMESTAMP " + endComp + t1
					+ " ALLOW FILTERING; ");
		}
		logger.debug("Query: " + sb.toString());
		return sb.toString();
	}

	private void clean() {
		fProducersLoaded = false;
		fTypesLoaded = false;
		fProducers = new HashMap<Integer, EventProducer>();
		fTypes = new HashMap<Integer, EventType>();
		fLatestStart = Long.MIN_VALUE;
		CassandraSession.finalClose(fSession);
	}

	private CassandraSession getCassandraSession() {
		if (fSession == null) {
			Assert.isNotNull(fTrace, "Null trace in event loader");
			fSession = new CassandraSession(fTrace.getDbName(), DBMode.DB_OPEN);
		}
		return fSession;
	}

	private int debug(List<ReducedEvent> events, int totalEvents) {
		totalEvents += events.size();
		logger.debug("events read : {}", events.size());
		logger.debug("total events: {}", totalEvents);
		for (ReducedEvent event : events) {
			logger.trace(event.toString());
		}
		return totalEvents;
	}

}
