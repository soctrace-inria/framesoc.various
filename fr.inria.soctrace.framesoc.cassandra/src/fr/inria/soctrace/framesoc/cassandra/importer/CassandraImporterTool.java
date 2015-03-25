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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;

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
import fr.inria.soctrace.lib.utils.DeltaManager;
import fr.inria.soctrace.lib.utils.IdManager;

/**
 * Import a dummy trace inside a keyspace.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class CassandraImporterTool extends FramesocTool {

	private final static String KEYSPACE_BASE = "cassandra";
	protected static final int WORK_STEP = 50000;
	private int events = 10;
	private int types = 10;
	private int producers = 10;

	public CassandraImporterTool() {

	}

	@Override
	public void launch(final IFramesocToolInput input) {

		Job job = new Job("Cassandra Importer") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
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

					System.out.println("Events: " + events);
					System.out.println("Types: " + types);
					System.out.println("Producers: " + producers);

					monitor.beginTask("Importing fake trace", events + producers + types);

					// import the fake trace
					String dbName = FramesocManager.getInstance().getTraceDBName(KEYSPACE_BASE);

					// Trace metadata
					DeltaManager dm = new DeltaManager();
					sysDB = SystemDBObject.openNewIstance();
					CassandraTraceMetadata meta = new CassandraTraceMetadata(sysDB, dbName, 0,
							events * 10, events);
					Trace t = new Trace(sysDB.getNewId(FramesocTable.TRACE.toString(), "ID"));
					meta.setTraceFields(t);
					meta.createMetadata();
					meta.saveMetadata();

					// Trace content
					dm.start();
					session = new CassandraSession(dbName, DBMode.DB_CREATE);
					dm.end("schema");

					// producers
					dm.start();
					for (int i = 0; i < producers; i++) {
						StringBuilder sb = new StringBuilder(
								"INSERT INTO EVENT_PRODUCER (ID, NAME) VALUES ");
						sb.append("(");
						sb.append(i + ", "); // id
						sb.append("'producer_" + i + "'"); // id
						sb.append(");");
						session.execute(sb.toString());
					}
					monitor.worked(producers);
					dm.end("producers");

					// types
					dm.start();
					for (int i = 0; i < types; i++) {
						StringBuilder sb = new StringBuilder(
								"INSERT INTO EVENT_TYPE (ID, NAME) VALUES ");
						sb.append("(");
						sb.append(i + ", "); // id
						sb.append("'type" + i + "'"); // id
						sb.append(");");
						session.execute(sb.toString());
					}
					monitor.worked(types);
					dm.end("types");

					// events
					dm.start();
					PreparedStatement statement = session
							.prepare("INSERT INTO EVENT (ID, CPU, EVENT_TYPE_ID, EVENT_PRODUCER_ID,"
									+ " CATEGORY, TIMESTAMP, LPAR, DPAR) VALUES (?, ? , ? , ? , ? ,"
									+ " ? , ? , ?)");
					List<ResultSetFuture> futures = new ArrayList<>();
					IdManager idm = new IdManager();
					for (int i = 0; i < events; i++) {
						BoundStatement bind = statement.bind(idm.getNextId(), 0,
								((Double) (Math.random() * types)).intValue(),
								((Double) (Math.random() * producers)).intValue(),
								EventCategory.STATE, 10L * i, 10L * i + 9L, 0.0);
						ResultSetFuture resultSetFuture = session.executeAsync(bind);
						futures.add(resultSetFuture);
						if (i % WORK_STEP == 0) {
							System.out.println(i);
							monitor.worked(WORK_STEP);
						}
					}
					dm.end("End import");

				} catch (SoCTraceException e) {
					e.printStackTrace();
					return Status.CANCEL_STATUS;
				} finally {
					DBObject.finalClose(sysDB);
					CassandraSession.finalClose(session);
				}

				return Status.OK_STATUS;
			}
		};

		job.setUser(true);
		job.schedule();
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
