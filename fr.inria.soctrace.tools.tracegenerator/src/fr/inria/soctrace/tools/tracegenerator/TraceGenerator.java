package fr.inria.soctrace.tools.tracegenerator;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.soctrace.lib.model.Event;
import fr.inria.soctrace.lib.model.EventParamType;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.File;
import fr.inria.soctrace.lib.model.State;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.TraceParam;
import fr.inria.soctrace.lib.model.TraceParamType;
import fr.inria.soctrace.lib.model.TraceType;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.model.utils.ModelConstants.TimeUnit;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.storage.DBObject.DBMode;
import fr.inria.soctrace.lib.storage.utils.SQLConstants.FramesocTable;
import fr.inria.soctrace.lib.storage.SystemDBObject;
import fr.inria.soctrace.lib.storage.TraceDBObject;
import fr.inria.soctrace.lib.utils.DeltaManager;
import fr.inria.soctrace.lib.utils.IdManager;

/**
 * Virtual importer writing into the DB a virtual trace whose parameters may be
 * easily configured.
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

	private static final Logger logger = LoggerFactory
			.getLogger(TraceGenerator.class);

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
	 * Number of parameters (for Trace and Event)
	 */
	public int NUMBER_OF_PARAMETERS = 0;

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
	public final static int MAX_ADVANCE_DURATION = 15;

	public HashMap<EventProducer, Double> coeffMod;

	
	List<EventProducer> producers = new LinkedList<EventProducer>();
	List<EventProducer> leaves = new LinkedList<EventProducer>();
	
	/*
	 * Short-cuts
	 */
	public int numberOfCategories;
	public long maxStateTime = 1l;
	public int eventPerLeaf = 0;
	public int generatedEvent = 0;

	// public String TRACE_TYPE_NAME = TraceGenerator.TYPE_NAME_PREFIX +
	// TraceGenerator.TRACE_TYPE_ID;
	// public int NUMBER_OF_PARAMETER_TYPES = NUMBER_OF_CATEGORIES *
	// numberOfEventType * NUMBER_OF_PARAMETERS;
	// public int TOTAL_NUMBER_OF_EVENTS = numberOfProducers * numberOfEventType
	// * numberOfTypes * NUMBER_OF_CATEGORIES;

	public int getEventsPerCategory() {
		// if (NUMBER_OF_CATEGORIES == 0)
		return 0;
		// return TOTAL_NUMBER_OF_EVENTS / categories.size();*/
	}

	public long getNumberOfEvents(int category) {
		return numberOfEvents;
	}

	public long getMaxTimestamp() {
		long punctuals = getNumberOfEvents(EventCategory.PUNCTUAL_EVENT)
				+ getNumberOfEvents(EventCategory.VARIABLE);
		long nonpunctuals = getNumberOfEvents(EventCategory.STATE)
				+ getNumberOfEvents(EventCategory.LINK);
		return MIN_TIMESTAMP + punctuals + nonpunctuals * (MAX_DURATION + 1)
				- 1;
	}

	/**
	 * Current timestamp used during import
	 */
	private long currentTimestamp = MIN_TIMESTAMP;

	/**
	 * Import a virtual trace into a trace DB according to the constants set.
	 * 
	 * @throws SoCTraceException
	 */
	public void generateTrace(IProgressMonitor monitor)
			throws SoCTraceException {
		/*
		 * Trace events
		 */
		TraceDBObject traceDB = new TraceDBObject(dbName, DBMode.DB_CREATE);

		// Init ID managers
		IdManager eIdManager = new IdManager();
		IdManager etIdManager = new IdManager();
		IdManager epIdManager = new IdManager();
		IdManager eptIdManager = new IdManager();
		IdManager tpIdManager = new IdManager();
		IdManager tptIdManager = new IdManager();
		IdManager producerIdManager = new IdManager();

		// event category, types
		List<EventType> typesList = new ArrayList<EventType>();
		producers = new ArrayList<EventProducer>();
		leaves = new ArrayList<EventProducer>();

		coeffMod = new HashMap<EventProducer, Double>();
		
		monitor.subTask("Generating event types");
		int i = 0, j = 0, k = 0, l = 0;
		
		// Create event types
		for (i = 0; i < numberOfEventType; i++) {
			// Spread them uniformly between the possible categories
			int category = categories.get(i % categories.size());
			EventType aType = createTypes(traceDB, category, etIdManager,
					eptIdManager);
			typesList.add(aType);
		}
		maxStateTime = Long.MAX_VALUE / numberOfEvents;
		eventPerLeaf = (int) (numberOfEvents / (10 * 10 * 10 * 1000000)); 
		
		System.err.println("Gen eventPerLeaf " + eventPerLeaf + ", " + (numberOfEvents));

		monitor.subTask("Generating event producer");
		DeltaManager aDM = new DeltaManager();
		EventProducer root = createEventProd(-1, producerIdManager, traceDB);
		// Create non-leave producer
		for (i = 0; i < 10; i++) {
			EventProducer ep1 = createEventProd(root.getId(),
					producerIdManager, traceDB);
			for (j = 0; j < 10; j++) {
				EventProducer ep2 = createEventProd(ep1.getId(),
						producerIdManager, traceDB);
				for (k = 0; k < 10; k++) {
					EventProducer ep3 = createEventProd(ep2.getId(),
							producerIdManager, traceDB);
					aDM.start();
					DeltaManager toot = new DeltaManager();
					toot.start();
					System.err.println("Gen EP " + i + ", " + j + ", " + k);
					for (l = 0; l < 1000000; l++) {
						EventProducer aLeaf = createLeaveEventProd(ep3.getId(), producerIdManager,
								traceDB);
						
						createEvent(traceDB, typesList, producers, leaves, eIdManager,
								epIdManager, monitor, aLeaf);
						
						if (l % 20000 == 0) {
							if (monitor.isCanceled()) {
								traceDB.dropDatabase();
								return;
							}
							traceDB.commit();
						}
					}
					traceDB.commit();
				}
			}
		}

		traceDB.commit();

		monitor.subTask("Generating events");

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

		traceDB.close();

		monitor.subTask("Filling trace metadata");
		/*
		 * Trace metadata
		 */
		SystemDBObject sysDB = SystemDBObject.openNewIstance();

		TraceType tt = buildTraceType(sysDB);
		tptIdManager.setNextId(sysDB.getMaxId(
				FramesocTable.TRACE_PARAM_TYPE.toString(), "ID") + 1);
		for (i = 0; i < NUMBER_OF_PARAMETERS; i++) {
			TraceParamType tpt = new TraceParamType(tptIdManager.getNextId());
			tpt.setName(PARAMETER_NAME_PREFIX + tpt.getId());
			tpt.setType(PARAMETER_TYPE);
			tpt.setTraceType(tt);
			sysDB.save(tpt);
		}

		Trace t = new Trace(
				sysDB.getNewId(FramesocTable.TRACE.toString(), "ID"));
		t.setAlias(TRACE_NAME + "_" + t.getId());
		t.setBoard(METADATA);
		t.setDbName(dbName);
		t.setDescription(METADATA);
		t.setNumberOfCpus(1);
		t.setNumberOfEvents((int) numberOfEvents);
		t.setOperatingSystem(METADATA);
		t.setOutputDevice(METADATA);
		t.setProcessed(false);
		t.setMinTimestamp(MIN_TIMESTAMP);
		t.setMaxTimestamp(currentTimestamp - 1);
		t.setTimeUnit(TimeUnit.NANOSECONDS.getInt());
		t.setTracedApplication(METADATA);
		t.setTracingDate(new Timestamp(new Date().getTime()));
		t.setType(tt);
		tpIdManager.setNextId(sysDB.getMaxId(
				FramesocTable.TRACE_PARAM.toString(), "ID") + 1);
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

	private TraceType buildTraceType(SystemDBObject sysDB)
			throws SoCTraceException {

		// If the trace type exist already
		if (sysDB.isTraceTypePresent(TRACE_TYPE)) {
			logger.debug("Tracetype exists");
			return sysDB.getTraceType(TRACE_TYPE);
		} else {
			logger.debug("Tracetype does not exist");
			TraceType traceType = new TraceType(sysDB.getNewId(
					FramesocTable.TRACE_TYPE.toString(), "ID"));
			traceType.setName(TRACE_TYPE);
			sysDB.save(traceType);
			return traceType;
		}
	}

	private void createEvent(TraceDBObject traceDB, List<EventType> typesList,
			List<EventProducer> producers, List<EventProducer> leaves,
			IdManager eIdManager, IdManager epIdManager,
			IProgressMonitor monitor, EventProducer eProd) throws SoCTraceException {
		int i;
		Random rand = new Random();
			currentTimestamp = 0l;
			for (i = 0; i < eventPerLeaf; i++) {

				// Randomize event type
				int type = rand.nextInt(typesList.size());
				EventType et = typesList.get(type);
				Event e = null;

				State s = new State(eIdManager.getNextId());
				s.setTimestamp(currentTimestamp);

				// Randomize state duration
				long duration = (long) (Math.abs(rand.nextInt(100))) + 1l;
				currentTimestamp = currentTimestamp + duration;
				s.setEndTimestamp(currentTimestamp);
				s.setImbricationLevel(0);

				e = s;

				e.setCategory(et.getCategory());
				e.setType(et);
				e.setEventProducer(eProd);
				e.setCpu(CPU);
				e.setPage(PAGE);

				traceDB.save(e);
				generatedEvent++;
				if (generatedEvent % Temictli.NumberOfEventInCommit == 0) {
					if (monitor.isCanceled()) {
						return;
					}
					traceDB.commit();
				}
			}
			if (monitor.isCanceled()) {
				return;
			}
			monitor.worked(1);
	}

	private EventType createTypes(TraceDBObject traceDB, int category,
			IdManager etIdManager, IdManager eptIdManager)
			throws SoCTraceException {
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

	public void setTraceConfig(TraceGenConfig aConfig, String aName) {
		categories = aConfig.getCategories();
		numberOfEventType = aConfig.getNumberOfEventType();
		numberOfProducers = aConfig.getNumberOfProducers();
		numberOfEvents = aConfig.getNumberOfEvents();
		numberOfLeaves = aConfig.getNumberOfLeaves();
		onlyLeaveProducer = aConfig.isOnlyLeavesAsProducer();
		numberOfCategories = categories.size();
		dbName = aName;
	}

	public EventProducer createEventProd(int parentId, IdManager producerIdManager,
			TraceDBObject traceDB) throws SoCTraceException {
		EventProducer ep = new EventProducer(producerIdManager.getNextId());
		ep.setName(PRODUCER_NAME_PREFIX + ep.getId());
		ep.setType(NORMAL_PRODUCER_TYPE);
		ep.setLocalId(PRODUCER_LOCAL_ID_PREFIX + ep.getId());
		ep.setParentId(parentId);
		producers.add(ep);
		traceDB.save(ep);
		
		return ep;
	}

	public EventProducer createLeaveEventProd(int parentId, IdManager producerIdManager,
			TraceDBObject traceDB) throws SoCTraceException {
		EventProducer ep = new EventProducer(producerIdManager.getNextId());
		ep.setName(PRODUCER_NAME_PREFIX + ep.getId());
		ep.setType(NORMAL_PRODUCER_TYPE);
		ep.setLocalId(PRODUCER_LOCAL_ID_PREFIX + ep.getId());
		ep.setParentId(parentId);
		producers.add(ep);
		//leaves.add(ep);
		traceDB.save(ep);
		
		return ep;
	}

}
