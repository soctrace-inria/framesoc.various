/**
 * 
 */
package fr.inria.soctrace.framesoc.bench.reading;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;

public class FramesocReaderTool extends FramesocTool {

	@Override
	public void launch(final String[] args) {
		Job job = new Job("Framesoc Reader") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				FramesocReader.main(args);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

}
