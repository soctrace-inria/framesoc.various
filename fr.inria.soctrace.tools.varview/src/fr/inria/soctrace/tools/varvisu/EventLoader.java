/*******************************************************************************
 * Copyright (c) 2012-2015 INRIA.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Youenn Corre - initial API and implementation
 ******************************************************************************/
package fr.inria.soctrace.tools.varvisu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import fr.inria.soctrace.lib.model.Event;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.State;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.Variable;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.query.EventQuery;
import fr.inria.soctrace.lib.query.conditions.ConditionsConstants.ComparisonOperation;
import fr.inria.soctrace.lib.query.conditions.SimpleCondition;
import fr.inria.soctrace.lib.storage.TraceDBObject;

/**
 * Load all the variables from a Trace and store them in a hashmap sorted by
 * event producers and by type
 * 
 * @author "Youenn Corre <youenn.corre@inria.fr>"
 */
public class EventLoader {

	private static final String CATEGORY = "CATEGORY";
	private Map<EventProducer, Map<EventType, List<Variable>>> variables;
	private Map<EventProducer, Map<EventType, List<State>>> states;
	
	public EventLoader() {
		clean();
	}

	/**
	 * Query all the variables in a trace and store them in a hashmap
	 * 
	 * @param aTrace
	 *            the processed trace
	 * @param monitor
	 *            progress monitor
	 * @throws SoCTraceException
	 */
	public void queryVariable(Trace aTrace, IProgressMonitor monitor)
			throws SoCTraceException {
		TraceDBObject traceDB;
		monitor.subTask("Querying variables.");

		traceDB = TraceDBObject.openNewInstance(aTrace.getDbName());

		// Build query to get only variables
		EventQuery eventQuery = new EventQuery(traceDB);
		SimpleCondition selectVariable = new SimpleCondition(CATEGORY,
				ComparisonOperation.EQ, String.valueOf(EventCategory.VARIABLE));
		eventQuery.setElementWhere(selectVariable);

		List<Event> var = eventQuery.getList();
		monitor.worked(1);

		EventQuery eventQueryState = new EventQuery(traceDB);
		SimpleCondition selectState = new SimpleCondition(CATEGORY,
				ComparisonOperation.EQ, String.valueOf(EventCategory.STATE));
		eventQueryState.setElementWhere(selectState);

		List<Event> state = eventQueryState.getList();
		
		// Process the variable
		monitor.subTask("Processing variables.");
		clean();
		processVariables(var);
		processStates(state);
		monitor.worked(1);
	}

	/**
	 * Store the variables in the hashmap
	 * 
	 * @param processedStates
	 *            List of states to process
	 */
	private void processStates(List<Event> processedStates) {
		for (Event event : processedStates) {
			EventProducer ep = event.getEventProducer();
			if (!states.containsKey(ep)) {
				states.put(ep, new HashMap<EventType, List<State>>());
			}

			EventType type = event.getType();
			if (!states.get(ep).containsKey(type)) {
				states.get(ep).put(type, new ArrayList<State>());
			}

			states.get(ep).get(type).add((State) event);
		}
	}
	
	private void processVariables(List<Event> vars) {
		for (Event event : vars) {
			EventProducer ep = event.getEventProducer();
			if (!variables.containsKey(ep)) {
				variables.put(ep, new HashMap<EventType, List<Variable>>());
			}

			EventType type = event.getType();
			if (!variables.get(ep).containsKey(type)) {
				variables.get(ep).put(type, new ArrayList<Variable>());
			}

			variables.get(ep).get(type).add((Variable) event);
		}
	}
	
	/**
	 * Clear the stored variables
	 */
	public void clean() {
		variables = new HashMap<EventProducer, Map<EventType, List<Variable>>>();
		states = new HashMap<EventProducer, Map<EventType, List<State>>>();
	}

	/**
	 * Getter for the hashmap of variables
	 * 
	 * @return the hashmap of variables
	 */
	public Map<EventProducer, Map<EventType, List<Variable>>> getVariables() {
		return variables;
	}
	
	/**
	 * Getter for the hashmap of states
	 * 
	 * @return the hashmap of states
	 */
	public Map<EventProducer, Map<EventType, List<State>>> getStates() {
		return states;
	}

}
