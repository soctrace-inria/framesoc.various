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
package fr.inria.soctrace.framesoc.cassandra.importer;

import fr.inria.soctrace.framesoc.core.tools.importers.AbstractTraceMetadataManager;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.storage.SystemDBObject;

public class CassandraTraceMetadata extends AbstractTraceMetadataManager {

	private long min;
	private long max;
	private int events;
	private String dbName;
	
	public CassandraTraceMetadata(SystemDBObject sysDB, String dbName, long min, long max, int events)
			throws SoCTraceException {
		super(sysDB);
		this.dbName = dbName;
		this.min = min;
		this.max = max;
		this.events = events;
	}

	@Override
	public String getTraceTypeName() {
		return "fr.inria.soctrace.framesoc.cassandra";
	}

	@Override
	public void setTraceFields(Trace trace) {
		trace.setDbName(dbName);
		trace.setMinTimestamp(min);
		trace.setMaxTimestamp(max);
		trace.setNumberOfEvents(events);
	}
	
}
