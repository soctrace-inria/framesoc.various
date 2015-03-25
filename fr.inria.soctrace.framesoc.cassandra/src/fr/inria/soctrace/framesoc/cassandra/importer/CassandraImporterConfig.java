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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration manager corresponding to a file with the following format:
 * 
 * VARNAME=VALUE
 * 
 * The possible VARNAMEs correspond to the {@code Property} names.
 * 
 * See the file cassandra_importer.conf as an example.
 * 
 * @author "Generoso Pagano <generoso.pagano@inria.fr>"
 */
public class CassandraImporterConfig {
	
	public static enum Property {
		EVENTS,
		PRODUCERS,
		TYPES;		
	}
	
	private Properties config = new Properties();
	
	public boolean load(String path) {
		try {
			File file = new File(path);
			if (!file.exists()) {
				return false;
			} else {
				config.load(new FileInputStream(file));
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public String get(Property var) {
		return config.getProperty(var.name());
	}
	
}
