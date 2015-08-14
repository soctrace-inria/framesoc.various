/*******************************************************************************
 * Copyright (c) 2012-2015 INRIA.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * This tool is used to provide a view of the values of variables of a trace 
 * over time as a curve.
 * 
 * Contributors:
 *     Youenn Corre - initial API and implementation
 ******************************************************************************/
package fr.inria.soctrace.tools.varvisu;

import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;
import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;

/**
 * Main tool class
 * 
 * @author "Youenn Corre <youenn.corre@inria.fr>"
 */
public class VariableVisualization extends FramesocTool {

	public VariableVisualization() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void launch(IFramesocToolInput input) {

		final IWorkbenchWindow window = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow();
		try {
			window.getActivePage().showView(VarVisuView.ID);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
