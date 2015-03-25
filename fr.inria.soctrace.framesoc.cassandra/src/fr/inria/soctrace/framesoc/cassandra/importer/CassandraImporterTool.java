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

import java.io.File;

import fr.inria.soctrace.framesoc.cassandra.importer.CassandraImporterConfig.Property;
import fr.inria.soctrace.framesoc.cassandra.loader.CassandraSession;
import fr.inria.soctrace.framesoc.core.FramesocManager;
import fr.inria.soctrace.framesoc.core.tools.model.FileInput;
import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;
import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.utils.ModelConstants.EventCategory;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.storage.DBObject;
import fr.inria.soctrace.lib.storage.DBObject.DBMode;
import fr.inria.soctrace.lib.storage.SystemDBObject;
import fr.inria.soctrace.lib.storage.utils.SQLConstants.FramesocTable;
import fr.inria.soctrace.lib.utils.IdManager;

/**
 * Import a dummy trace inside a keyspace.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class CassandraImporterTool extends FramesocTool {

	private final static String KEYSPACE_BASE = "cassandra";
	private int events = 100;
	private int types = 10;
	private int producers = 10;

	public CassandraImporterTool() {

	}

	@Override
	public void launch(IFramesocToolInput input) {

		SystemDBObject sysDB = null;
		CassandraSession session = null;
		try {

			// load configuration
			FileInput fileInput = (FileInput) input;
			String path = fileInput.getFiles().get(0);
			CassandraImporterConfig conf = new CassandraImporterConfig();
			if (!conf.load(path)) {
				throw new SoCTraceException("Error loading configuration file: " + path);
			}
			events = Integer.valueOf(conf.get(Property.EVENTS));
			types = Integer.valueOf(conf.get(Property.TYPES));
			producers = Integer.valueOf(conf.get(Property.PRODUCERS));

			// import the fake trace
			String dbName = FramesocManager.getInstance().getTraceDBName(KEYSPACE_BASE);

			// Trace metadata
			sysDB = SystemDBObject.openNewIstance();
			CassandraTraceMetadata meta = new CassandraTraceMetadata(sysDB, dbName, 0, events * 10,
					events);
			Trace t = new Trace(sysDB.getNewId(FramesocTable.TRACE.toString(), "ID"));
			meta.setTraceFields(t);
			meta.createMetadata();
			meta.saveMetadata();

			// Trace content
			session = new CassandraSession(dbName, DBMode.DB_CREATE);
			IdManager idm = new IdManager();
			for (int i = 0; i < events; i++) {
				StringBuilder sb = new StringBuilder(
						"INSERT INTO EVENT (ID, CPU, EVENT_TYPE_ID, EVENT_PRODUCER_ID, CATEGORY, TIMESTAMP, LPAR, DPAR) VALUES ");
				sb.append("(");
				sb.append(idm.getNextId() + ", "); // id
				sb.append(0 + ", "); // cpu
				sb.append(((Double) (Math.random() * types)).intValue() + ", "); // type id
				sb.append(((Double) (Math.random() * producers)).intValue() + ", "); // prod id
				sb.append(EventCategory.STATE + ", "); // cat
				sb.append(10 * i + ","); // t1
				sb.append(10 * i + 9 + ","); // t2
				sb.append(0); // dpar
				sb.append(");");
				System.out.println(sb.toString());
				session.execute(sb.toString());
			}

			for (int i = 0; i < producers; i++) {
				StringBuilder sb = new StringBuilder(
						"INSERT INTO EVENT_PRODUCER (ID, NAME) VALUES ");
				sb.append("(");
				sb.append(i + ", "); // id
				sb.append("'producer_" + i + "'"); // id
				sb.append(");");
				System.out.println(sb.toString());
				session.execute(sb.toString());
			}

			for (int i = 0; i < types; i++) {
				StringBuilder sb = new StringBuilder("INSERT INTO EVENT_TYPE (ID, NAME) VALUES ");
				sb.append("(");
				sb.append(i + ", "); // id
				sb.append("'type" + i + "'"); // id
				sb.append(");");
				System.out.println(sb.toString());
				session.execute(sb.toString());
			}

		} catch (SoCTraceException e) {
			e.printStackTrace();
		} finally {
			DBObject.finalClose(sysDB);
			CassandraSession.finalClose(session);
		}
	}

	@Override
	public ParameterCheckStatus canLaunch(IFramesocToolInput input) {
		ParameterCheckStatus status = new ParameterCheckStatus(true, "");
		if (!(input instanceof FileInput)) {
			status.valid = false;
			status.message = "Wrong input type";
			return status;
		}

		FileInput fileInput = (FileInput) input;
		if (fileInput.getFiles().size() < 1) {
			status.valid = false;
			status.message = "Specify the configuration file";
			return status;			
		}
		
		String path = fileInput.getFiles().get(0);
		File f = new File(path);
		if (!f.isFile()) {
			status.valid = false;
			status.message = "Configuration file does not exist: " + path;
			return status;						
		}

		return status;

	}

}
