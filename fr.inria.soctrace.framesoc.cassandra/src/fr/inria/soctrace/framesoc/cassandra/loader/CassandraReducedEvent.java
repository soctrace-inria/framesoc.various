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

import com.datastax.driver.core.Row;

import fr.inria.soctrace.framesoc.ui.gantt.model.ReducedEvent;

/**
 * Cassandra reduced event.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class CassandraReducedEvent extends ReducedEvent {

	public CassandraReducedEvent(Row row) {
		cpu = row.getInt(ReducedEvent.CPU);
		category = row.getInt(ReducedEvent.CATEGORY - 1);
		timestamp = row.getLong(ReducedEvent.TIMESTAMP - 1);
		endTimestamp = row.getLong(ReducedEvent.END_TIMESTAMP - 1);
		typeId = row.getInt(ReducedEvent.TYPE_ID - 1);
		producerId = row.getInt(ReducedEvent.PRODUCER_ID - 1);
		endProducerId = (int) row.getDouble(ReducedEvent.END_PRODUCER_ID - 1);
	}

}
