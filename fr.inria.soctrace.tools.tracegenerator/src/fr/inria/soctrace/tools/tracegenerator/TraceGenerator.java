package fr.inria.soctrace.tools.tracegenerator;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.soctrace.lib.model.Event;
import fr.inria.soctrace.lib.model.EventParam;
import fr.inria.soctrace.lib.model.EventParamType;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.File;
import fr.inria.soctrace.lib.model.Link;
import fr.inria.soctrace.lib.model.PunctualEvent;
import fr.inria.soctrace.lib.model.State;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.TraceParam;
import fr.inria.soctrace.lib.model.TraceParamType;
import fr.inria.soctrace.lib.model.TraceType;
import fr.inria.soctrace.lib.model.Variable;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.model.utils.ModelConstants.TimeUnit;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.storage.DBObject.DBMode;
import fr.inria.soctrace.lib.storage.utils.SQLConstants.FramesocTable;
import fr.inria.soctrace.lib.storage.SystemDBObject;
import fr.inria.soctrace.lib.storage.TraceDBObject;
import fr.inria.soctrace.lib.utils.IdManager;

/**
 * Virtual importer writing into the DB a virtual trace whose parameters may be easily configured.
 * 
 * <pre>
 * Conventions: 
 * - if there are N entities, the IDs range from 0 to N-1 
 * - the name of a Producer ${PRODUCER_NAME_PREFIX}_${ID} 
 * - the name of a *Type is ${TYPE_NAME_PREFIX}_${ID} 
 * - the name of a *Parameter is ${PARAMETER_NAME_PREFIX}_${ID}
 * - the local id of a Producer is ${PRODUCER_LOCAL_ID_PREFIX}_${ID}
 * </pre>
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class TraceGenerator {

	private static final Logger logger = LoggerFactory.getLogger(TraceGenerator.class);

	/**
	 * Virtual importer DB name
	 */
	public String dbName;

	/**
	 * String constant to be used for all Trace metadata not known.
	 */
	public final static String TRACE_NAME = "VIRTUAL_TRACE";

	/**
	 * Set of used event categories
	 */
	public ArrayList<Integer> categories;

	/**
	 * Number of producers in the virtual trace
	 */
	public int numberOfProducers = 11;

	/**
	 * Number of types for each category for each producer
	 */
	public int numberOfEventType = 12;

	/**
	 * Number of events for each type for each category for each producer
	 */
	public int numberOfTypes = 13;

	/**
	 * Number of events for each type for each category for each producer
	 */
	public long numberOfEvents = 13;

	public int numberOfLeaves = 0;
	
	public boolean onlyLeaveProducer = true;

	/**
	 * Number of parameters (Event)
	 */
	public int NUMBER_OF_PARAMETERS = 2;

	public int NUMBER_OF_TRACE_PARAMETERS = 0;

	/**
	 * Number of files
	 */
	public int NUMBER_OF_FILES = 3;

	/**
	 * Min timestamp
	 */
	public long MIN_TIMESTAMP = 0;

	/**
	 * Trace ID
	 */
	public static int TRACE_ID = 0;

	/**
	 * Trace Type ID
	 */
	public static int TRACE_TYPE_ID = 0;
	
	public static String TRACE_TYPE = "GENERATED_TRACES";

	/**
	 * Page
	 */
	public static int PAGE = 0;

	/**
	 * CPU
	 */
	public static int CPU = 0;

	/**
	 * String constant to be used for all Trace metadata not known.
	 */
	public final static String METADATA = "VIRTUAL";

	/**
	 * Prefix for type entity names
	 */
	public final static String TYPE_NAME_PREFIX = "TYPE_";

	/**
	 * Prefix for producer names
	 */
	public final static String PRODUCER_NAME_PREFIX = "PRODUCER_";

	/**
	 * Producer types
	 */
	public final static String PRODUCER_TYPE = "VIRTUAL_PRODUCER";
	public final static String NORMAL_PRODUCER_TYPE = "VIRTUAL_PRODUCER_NORMAL";
	public final static String LEAVE_PRODUCER_TYPE = "VIRTUAL_PRODUCER_LEAVE";

	/**
	 * Producer local id prefix
	 */
	public final static String PRODUCER_LOCAL_ID_PREFIX = "PRODUCER_LOCAL_ID_";

	/**
	 * Prefix for parameter type names
	 */
	public final static String PARAMETER_NAME_PREFIX = "PARAMETER_";

	/**
	 * Prefix for file paths and descriptions
	 */
	public final static String FILE_INFO_PREFIX = "FILE_";

	/**
	 * Type of all parameters
	 */
	public final static String PARAMETER_TYPE = "INTEGER";

	/**
	 * Value of all parameters
	 */
	public final static String PARAMETER_VALUE = "10";

	/**
	 * Duration for entities having an end timestamp.
	 */
	public final static int MAX_DURATION = 20;
	public final static int MAX_VARIABLE_DURATION = 15;
	public final static int MAX_STATE_DURATION = 10000;

	List<EventProducer> producers = new ArrayList<EventProducer>();
	List<EventProducer> leaves = new ArrayList<EventProducer>();
	// event category, types
	List<EventType> typesList = new ArrayList<EventType>();
	long numberOfGeneratedEvents;
	long maxTimeStamp;
	TraceDBObject traceDB;

	/*
	 * Short-cuts
	 */
	public int numberOfCategories;

	// public String TRACE_TYPE_NAME = TraceGenerator.TYPE_NAME_PREFIX +
	// TraceGenerator.TRACE_TYPE_ID;
	// public int NUMBER_OF_PARAMETER_TYPES = NUMBER_OF_CATEGORIES *
	// numberOfEventType * NUMBER_OF_PARAMETERS;

	public int getEventsPerCategory() {
		// if (NUMBER_OF_CATEGORIES == 0)
		return 0;
		// return TOTAL_NUMBER_OF_EVENTS / categories.size();*/
	}

	public long getNumberOfEvents(int category) {
		return numberOfEvents;
	}

	public long getMaxTimestamp() {
		return maxTimeStamp;
	}

	/**
	 * Current timestamp used during import
	 */
	private long currentTimestamp = MIN_TIMESTAMP;

	/**
	 * Force indexing even if disabled in config file
	 */
	private boolean forceIndex = false;

	/**
	 * Import a virtual trace into a trace DB according to the constants set.
	 * 
	 * @throws SoCTraceException
	 */
	public void generateTrace(IProgressMonitor monitor) throws SoCTraceException {
		/*
		 * Trace events
		 */
		traceDB = new TraceDBObject(dbName, DBMode.DB_CREATE);

		// Init ID managers
		IdManager eIdManager = new IdManager();
		IdManager etIdManager = new IdManager();
		IdManager epIdManager = new IdManager();
		IdManager eptIdManager = new IdManager();
		IdManager tpIdManager = new IdManager();
		IdManager tptIdManager = new IdManager();
		IdManager producerIdManager = new IdManager();

		Random rand = new Random();

		monitor.subTask("Generating event types");
		int i = 0;

		// Create event types
		for (i = 0; i < numberOfEventType; i++) {
			// Spread them uniformly between the possible categories
			int category = categories.get(i % categories.size());
			EventType aType = createTypes(traceDB, category, etIdManager, eptIdManager);
			typesList.add(aType);
		}

		monitor.subTask("Generating event producer");
		// Set root producer with the ID: -1
		EventProducer root = createEventProd(-1, producerIdManager, traceDB);
		
		// Create non-leave producers
		for (i = 0; i < numberOfProducers - numberOfLeaves; i++) {
			createEventProd(root.getId(), producerIdManager, traceDB);
		}

		int potentialParentsSize = producers.size();
		
		// Create leave producers
		for (i = 0; i < numberOfLeaves; i++) {
			// Randomize parent ID among producers (avoid to set root as
			// parent)
			int parentId = producers.get(
					rand.nextInt(potentialParentsSize - 1) + 1).getId();

			leaves.add(createEventProd(parentId, producerIdManager, traceDB));
		}
		
		traceDB.commit();

		monitor.subTask("Generating events");
		// Create events
		createEvent(eIdManager, epIdManager, monitor);

		if (monitor.isCanceled()) {
			traceDB.dropDatabase();
			return;
		}

		IdManager fileIdManager = new IdManager();
		for (i = 0; i < NUMBER_OF_FILES; i++) {
			File file = new File(fileIdManager.getNextId());
			file.setPath(FILE_INFO_PREFIX + file.getId());
			file.setDescription(FILE_INFO_PREFIX + file.getId());
			traceDB.save(file);
		}

		if (forceIndex) {
			monitor.subTask("Indexing timestamp");
			traceDB.createTimestampIndex();
			monitor.subTask("Indexing event id in EVENT_PARAM");
			traceDB.createEventParamIndex();
		}
		traceDB.close();

		monitor.subTask("Filling trace metadata");
		/*
		 * Trace metadata
		 */
		SystemDBObject sysDB = SystemDBObject.openNewIstance();

		TraceType tt = buildTraceType(sysDB);
		// tt.setName(TYPE_NAME_PREFIX + tt.getId());
		tptIdManager.setNextId(sysDB.getMaxId(FramesocTable.TRACE_PARAM_TYPE.toString(), "ID") + 1);
		for (i = 0; i < NUMBER_OF_TRACE_PARAMETERS; i++) {
			TraceParamType tpt = new TraceParamType(tptIdManager.getNextId());
			tpt.setName(PARAMETER_NAME_PREFIX + tpt.getId());
			tpt.setType(PARAMETER_TYPE);
			tpt.setTraceType(tt);
			sysDB.save(tpt);
		}

		Trace t = new Trace(sysDB.getNewId(FramesocTable.TRACE.toString(), "ID"));
		t.setAlias(TRACE_NAME + "_" + t.getId());
		t.setBoard(METADATA);
		t.setDbName(dbName);
		t.setDescription(METADATA);
		t.setNumberOfCpus(1);
		t.setNumberOfEvents((int) numberOfGeneratedEvents);
		t.setOperatingSystem(METADATA);
		t.setOutputDevice(METADATA);
		t.setProcessed(false);
		t.setMinTimestamp(MIN_TIMESTAMP);
		t.setMaxTimestamp(maxTimeStamp);
		t.setTimeUnit(TimeUnit.NANOSECONDS.getInt());
		t.setTracedApplication(METADATA);
		t.setTracingDate(new Timestamp(new Date().getTime()));
		t.setType(tt);
		tpIdManager.setNextId(sysDB.getMaxId(FramesocTable.TRACE_PARAM.toString(), "ID") + 1);
		for (TraceParamType tpt : tt.getTraceParamTypes()) {
			TraceParam tp = new TraceParam(tpIdManager.getNextId());
			tp.setTraceParamType(tpt);
			tp.setTrace(t);
			tp.setValue(PARAMETER_VALUE);
			sysDB.save(tp);
		}
		sysDB.save(t);

		sysDB.close();
	}

	private TraceType buildTraceType(SystemDBObject sysDB) throws SoCTraceException {

		// If the trace type exist already
		if (sysDB.isTraceTypePresent(TRACE_TYPE)) {
			logger.debug("Tracetype exists.");
			return sysDB.getTraceType(TRACE_TYPE);
		} else {
			logger.debug("Tracetype does not exist.");
			TraceType traceType = new TraceType(sysDB.getNewId(FramesocTable.TRACE_TYPE.toString(),
					"ID"));
			traceType.setName(TRACE_TYPE);
			sysDB.save(traceType);
			return traceType;
		}
	}

	private void createEvent(IdManager eIdManager, IdManager epIdManager,
			IProgressMonitor monitor) throws SoCTraceException {
		int i;
		Random rand = new Random();
		List<EventProducer> eventProducers;
		numberOfGeneratedEvents = 0l;
		maxTimeStamp = MIN_TIMESTAMP;

		// Get the active events producers
		if (onlyLeaveProducer) {
			eventProducers = leaves;
		} else {
			eventProducers = producers;
		}

		// For each producer
		for (EventProducer eProd : eventProducers) {
			// Reset time at MIN_TIMESTAMP
			currentTimestamp = MIN_TIMESTAMP;

			// Create "number of events / number of active producers" events
			for (i = 0; i < numberOfEvents / eventProducers.size(); i++) {
				createAnEvent(i, eProd, eIdManager, epIdManager, rand);
				
				if (numberOfGeneratedEvents % Temictli.NumberOfEventInCommit == 0) {
					if (monitor.isCanceled()) {
						return;
					}

					traceDB.commit();
					monitor.worked(1);
				}
			}
		}

		// Since we performed a division, the exact number of events might not
		// have been generated so generate additional events
		long additionalEvents = numberOfEvents
				- (numberOfEvents / eventProducers.size() * eventProducers
						.size());
		for (i = 0; i < additionalEvents; i++) {
			createAnEvent(i, eventProducers.get(i), eIdManager, epIdManager, rand);

			if (numberOfGeneratedEvents % Temictli.NumberOfEventInCommit == 0) {
				if (monitor.isCanceled()) {
					return;
				}

				traceDB.commit();
				monitor.worked(1);
			}
		}
	}
	
	/**
	 * Generate a new event and save it
	 * 
	 * @param cpt
	 *            the event counter for the current event producer
	 * @param eProd
	 *            the event producer of the event
	 * @param eIdManager
	 *            the ID manager for the event
	 * @param rand
	 *            random number generator
	 * @return the generated event
	 * @throws SoCTraceException
	 */
	public Event createAnEvent(int cpt, EventProducer eProd,
			IdManager eIdManager, IdManager epIdManager, Random rand)
			throws SoCTraceException {
		// Randomize event type
		int type = rand.nextInt(typesList.size());
		EventType et = typesList.get(type);
		Event e = null;

		switch (et.getCategory()) {
		case EventCategory.PUNCTUAL_EVENT:
			e = new PunctualEvent(eIdManager.getNextId());
			e.setTimestamp(currentTimestamp);
			checkMaxTimestamp(currentTimestamp);
			currentTimestamp++;
			break;
		case EventCategory.STATE:
			State s = new State(eIdManager.getNextId());
			s.setTimestamp(currentTimestamp);

			// Randomize state duration and make sure we don't have a
			// timestamp over MAX.Long
			long duration = (Math.abs(rand.nextLong()) / (numberOfEvents + 1l)) + 1l;

			s.setEndTimestamp(currentTimestamp + duration);
			s.setImbricationLevel(0);
			currentTimestamp = currentTimestamp + duration;
			checkMaxTimestamp(currentTimestamp);
			e = s;
			break;
		case EventCategory.LINK:
			Link l = new Link(eIdManager.getNextId());
			l.setTimestamp(currentTimestamp);
			l.setEndTimestamp(currentTimestamp + MAX_DURATION);
			if (onlyLeaveProducer) {// XXX
				l.setEndProducer(leaves.get(cpt % leaves.size()));
			} else {
				l.setEndProducer(producers.get(cpt % producers.size()));
			}
			currentTimestamp = currentTimestamp + MAX_DURATION + 1;
			checkMaxTimestamp(currentTimestamp);
			e = l;
			break;
		case EventCategory.VARIABLE:
			Variable v = new Variable(eIdManager.getNextId());
			v.setTimestamp(currentTimestamp);
			v.setEndTimestamp(0); // XXX
			checkMaxTimestamp(currentTimestamp);
			currentTimestamp++; // XXX
			e = v;
			break;
		}

		Assert.isNotNull(e, "Null event: wrong category");

		e.setCategory(et.getCategory());
		e.setType(et);
		e.setEventProducer(eProd);
		e.setCpu(CPU);
		e.setPage(PAGE);
		
		for (EventParamType ept : e.getType().getEventParamTypes()) {
			EventParam ep = new EventParam(epIdManager.getNextId());
			ep.setEvent(e);
			ep.setEventParamType(ept);
			ep.setValue(PARAMETER_VALUE);
			traceDB.save(ep);
		}

		traceDB.save(e);
		numberOfGeneratedEvents++;
		return e;
	}
	
	private EventType createTypes(TraceDBObject traceDB, int category, IdManager etIdManager,
			IdManager eptIdManager) throws SoCTraceException {
		EventType et = new EventType(etIdManager.getNextId(), category);
		et.setName(TYPE_NAME_PREFIX + et.getId());
		for (int j = 0; j < NUMBER_OF_PARAMETERS; j++) {
			EventParamType ept = new EventParamType(eptIdManager.getNextId());
			ept.setName(PARAMETER_NAME_PREFIX + ept.getId());
			ept.setType(PARAMETER_TYPE);
			ept.setEventType(et);
			traceDB.save(ept);
		}
		traceDB.save(et);
		return et;
	}
	
	/**
	 * Create an event producer with the given parameter
	 * 
	 * @param parentId
	 *            the ID of the parent event producer
	 * @param producerIdManager
	 *            the producer id manager to create the ID of the EP
	 * @param traceDB
	 *            the traceDBObject to save the EP
	 * @return the event producer
	 * @throws SoCTraceException
	 */
	public EventProducer createEventProd(int parentId,
			IdManager producerIdManager, TraceDBObject traceDB)
			throws SoCTraceException {
		EventProducer ep = new EventProducer(producerIdManager.getNextId());
		ep.setName(PRODUCER_NAME_PREFIX + ep.getId());
		ep.setType(NORMAL_PRODUCER_TYPE);
		ep.setLocalId(PRODUCER_LOCAL_ID_PREFIX + ep.getId());
		ep.setParentId(parentId);
		producers.add(ep);
		traceDB.save(ep);

		return ep;
	}
	
	/**
	 * Check if a timestamp is the actual max time stamp
	 * 
	 * @param aTimestamp
	 *            the tested timestamp
	 */
	void checkMaxTimestamp(long aTimestamp) {
		if(aTimestamp > maxTimeStamp)
			maxTimeStamp = aTimestamp;
	}
	

	public void setTraceConfig(TraceGenConfig aConfig, String aName) {
		categories = aConfig.getCategories();
		numberOfEventType = aConfig.getNumberOfEventType();
		numberOfProducers = aConfig.getNumberOfProducers();
		numberOfEvents = aConfig.getNumberOfEvents();
		numberOfLeaves = aConfig.getNumberOfLeaves();
		onlyLeaveProducer = aConfig.isOnlyLeavesAsProducer();
		forceIndex = aConfig.isForceIndex();
		numberOfCategories = categories.size();
		dbName = aName;
	}

}
