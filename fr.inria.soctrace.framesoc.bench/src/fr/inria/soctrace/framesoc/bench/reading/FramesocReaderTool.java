/**
 * 
 */
package fr.inria.soctrace.framesoc.bench.reading;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import fr.inria.soctrace.framesoc.core.tools.model.FileInput;
import fr.inria.soctrace.framesoc.core.tools.model.FramesocTool;
import fr.inria.soctrace.framesoc.core.tools.model.IFramesocToolInput;

public class FramesocReaderTool extends FramesocTool {

	@Override
	public void launch(final IFramesocToolInput input) {
		Job job = new Job("Framesoc Reader") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				FileInput finput = (FileInput) input;
				FramesocReader.run(finput.getFiles());
				return Status.OK_STATUS;
			}
		};
		job.schedule();		
	}

}
