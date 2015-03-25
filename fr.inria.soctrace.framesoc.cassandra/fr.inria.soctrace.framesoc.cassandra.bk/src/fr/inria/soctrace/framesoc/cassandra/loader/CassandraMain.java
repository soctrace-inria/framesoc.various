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

import com.datastax.driver.core.*;

public class CassandraMain {

	public static void main(String[] args) {

		Cluster cluster;
		Session session;

		// Connect to the cluster and keyspace "demo"
		cluster = Cluster.builder().addContactPoint("127.0.0.1").build();
		session = cluster.connect("framesoc");

		// Use select to get the user we just entered
		ResultSet results = session.execute("SELECT * FROM EVENT");
		for (Row row : results) {
			System.out.format("%s %d\n", row.getLong("ID"), row.getLong("TIMESTAMP"));
		}

		// Clean up the connection by closing it
		cluster.close();
	}
}
