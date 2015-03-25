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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;

import fr.inria.soctrace.lib.storage.DBObject.DBMode;

/**
 * Cassandra session manager.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class CassandraSession {

	private static final String IP = "127.0.0.1";

	private Cluster cluster;
	private Session session;

	public CassandraSession(String keyspace, DBMode dbMode) {
		cluster = Cluster.builder().addContactPoint(IP).build();
		if (dbMode.equals(DBMode.DB_CREATE)) {
			session = cluster.connect();
			execute(keyspace(keyspace));
			execute("USE " + keyspace + ";");
			execute(initEvent());
			execute(initProducer());
			execute(initType());
			execute(initTimestampIndex());
			execute(initEndTimestampIndex());
		} else {
			session = cluster.connect(keyspace);
		}
	}

	public ResultSet execute(String query) {
		System.out.println(query);
		return session.execute(query);
	}

	public void close() {
		if (cluster != null) {
			cluster.close();
		}
	}

	public static void finalClose(CassandraSession session) {
		if (session != null) {
			session.close();
		}
	}

	private String keyspace(String keyspace) {
		return "CREATE KEYSPACE " + keyspace
				+ " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };";
	}

	private String initEvent() {
		return "CREATE TABLE EVENT (ID int, CPU int, EVENT_TYPE_ID int, EVENT_PRODUCER_ID int, CATEGORY int, TIMESTAMP bigint, LPAR bigint, DPAR double, PRIMARY KEY ((ID), TIMESTAMP, LPAR));";
	}

	private String initProducer() {
		return "CREATE TABLE EVENT_PRODUCER (ID int PRIMARY KEY, NAME ascii);";

	}

	private String initType() {
		return "CREATE TABLE EVENT_TYPE (ID int PRIMARY KEY, NAME ascii);";

	}

	private String initTimestampIndex() {
		return "CREATE INDEX IF NOT EXISTS tidx ON EVENT (TIMESTAMP);";

	}

	private String initEndTimestampIndex() {
		return "CREATE INDEX IF NOT EXISTS etidx ON EVENT (LPAR);";
	}
}
