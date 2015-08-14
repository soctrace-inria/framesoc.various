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

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.wb.swt.SWTResourceManager;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.event.AxisChangeEvent;
import org.jfree.chart.event.AxisChangeListener;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.experimental.chart.swt.ChartComposite;
import org.jfree.ui.RectangleInsets;

import fr.inria.soctrace.framesoc.core.FramesocManager;
import fr.inria.soctrace.framesoc.core.bus.FramesocBusTopic;
import fr.inria.soctrace.framesoc.core.bus.FramesocBusTopicList;
import fr.inria.soctrace.framesoc.core.bus.IFramesocBusListener;
import fr.inria.soctrace.framesoc.ui.model.TimeInterval;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.Trace;
import fr.inria.soctrace.lib.model.Variable;
import fr.inria.soctrace.lib.model.utils.ModelConstants.TimeUnit;
import fr.inria.soctrace.lib.model.utils.SoCTraceException;
import fr.inria.soctrace.lib.model.utils.TimestampFormat;
import fr.inria.soctrace.lib.model.utils.TimestampFormat.TickDescriptor;
import fr.inria.soctrace.lib.query.TraceQuery;
import fr.inria.soctrace.lib.storage.SystemDBObject;

/**
 * View class of the variable visualization tool
 * 
 * @author "Youenn Corre <youenn.corre@inria.fr>"
 */
public class VarVisuView extends ViewPart implements IFramesocBusListener {

	public static final String ID = "fr.inria.soctrace.tools.varvisu.VarVisuView"; //$NON-NLS-1$
	public static final String PLUGIN_ID = Activator.PLUGIN_ID;
	
	private VariableLoader variableLoader;
	
	private Composite compositeChart;
	private ChartComposite chartFrame;
	private XYDataset dataset;
	private XYPlot plot;
	private Trace trace;
	private List<Trace> traces;
	final Map<Integer, Trace> traceMap = new HashMap<Integer, Trace>();
	protected FramesocBusTopicList topics = null;
	
	
	private int numberOfTicks;
	private Combo comboTraces;
	private Button btnLaunchDisplay;
	private static final String CHART_TITLE = "";

	private static final String TOOLTIP_FORMAT = "bin central timestamp: {1}, events: {2}";
	private static final String HISTOGRAM_TITLE = "";
	private static final String X_LABEL = "";
	private static final String Y_LABEL = "";
	private static final boolean HAS_LEGEND = false;
	private static final boolean HAS_TOOLTIPS = true;
	private static final boolean HAS_URLS = true;
	private static final boolean USE_BUFFER = true;
	private static final Color BACKGROUND_PAINT = new Color(255, 255, 255);
	private static final Color DOMAIN_GRIDLINE_PAINT = new Color(230, 230, 230);
	private static final Color RANGE_GRIDLINE_PAINT = new Color(200, 200, 200);
	private static final Color MARKER_OUTLINE_PAINT = new Color(0, 0, 255);
	private static final int TIMESTAMP_MAX_SIZE = 130;

	private final Font TICK_LABEL_FONT = new Font("Tahoma", 0, 11);
	private final Font LABEL_FONT = new Font("Tahoma", 0, 12);
	private final TimestampFormat X_FORMAT = new TimestampFormat();
	private final DecimalFormat Y_FORMAT = new DecimalFormat("0");
	private final XYToolTipGenerator TOOLTIP_GENERATOR = new StandardXYToolTipGenerator(
			TOOLTIP_FORMAT, X_FORMAT, Y_FORMAT);

	public VarVisuView() {
		try {
			variableLoader = new VariableLoader();

			// Register update to synchronize traces
			topics = new FramesocBusTopicList(this);
			topics.addTopic(FramesocBusTopic.TOPIC_UI_TRACES_SYNCHRONIZED);
			topics.addTopic(FramesocBusTopic.TOPIC_UI_SYNCH_TRACES_NEEDED);
			topics.addTopic(FramesocBusTopic.TOPIC_UI_REFRESH_TRACES_NEEDED);
			topics.registerAll();

			loadTraces();
		} catch (SoCTraceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void createPartControl(Composite parent) {
		// parent layout
		GridLayout gl_parent = new GridLayout(1, false);
		gl_parent.verticalSpacing = 2;
		gl_parent.marginWidth = 0;
		gl_parent.horizontalSpacing = 0;
		gl_parent.marginHeight = 0;
		parent.setLayout(gl_parent);
		
		// Top bar
		final ScrolledComposite topBarComposite = new ScrolledComposite(parent, SWT.BORDER | SWT.H_SCROLL);
		topBarComposite.setExpandHorizontal(true);
		topBarComposite.setExpandVertical(true);
		topBarComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1));
		
		final Group groupTraces = new Group(topBarComposite, SWT.NONE);
		groupTraces.setSize(422, 40);
		groupTraces.setForeground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		groupTraces.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		groupTraces.setLayout(new GridLayout(2, false));
		groupTraces.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1));
		
		comboTraces = new Combo(groupTraces, SWT.READ_ONLY);
		GridData gd_comboTraces = new GridData(SWT.FILL, SWT.CENTER, true,
				false, 1, 1);
		gd_comboTraces.widthHint = 170;
		comboTraces.setLayoutData(gd_comboTraces);
		comboTraces.addSelectionListener(new TraceAdapter());
		comboTraces.setToolTipText("Trace Selection");
		
		btnLaunchDisplay = new Button(groupTraces, SWT.NONE);
		btnLaunchDisplay.setText("Launch Display");
		btnLaunchDisplay.addSelectionListener(new LaunchDisplayAdapter());
		
		// Chart Composite
		compositeChart = new Composite(parent, SWT.BORDER);
		compositeChart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true,
				true, 1, 1));
		FillLayout fl_compositeChart = new FillLayout(SWT.HORIZONTAL);
		compositeChart.setLayout(fl_compositeChart);
		compositeChart.addControlListener(new ControlAdapter() {

			@Override
			public void controlResized(ControlEvent e) {
				int width = Math.max(compositeChart.getSize().x - 40, 1);
				numberOfTicks = Math.max(width / TIMESTAMP_MAX_SIZE, 1);
			}
		});
		
		refresh();
	}

	private void createDataset(boolean smoothTransition) {
		dataset = new XYSeriesCollection();

		for (EventProducer ep : variableLoader.getVariables().keySet()) {
			for (EventType type : variableLoader.getVariables().get(ep)
					.keySet()) {
				XYSeries series = new XYSeries(ep.getName() + "_" + type.getName());
				for (Variable variable : variableLoader.getVariables().get(ep)
						.get(type)) {
					series.add(variable.getTimestamp(), variable.getValue());
					if(!smoothTransition)
						series.add(variable.getEndTimestamp(), variable.getValue());
				}
				((XYSeriesCollection) dataset).addSeries(series);
			}
		}
	}

	private JFreeChart createChart() {
		JFreeChart lineChart = ChartFactory.createXYLineChart(CHART_TITLE,
				X_LABEL, Y_LABEL, dataset);
		return lineChart;
	}
	 
	private void displayChart(final JFreeChart chart,
			final TimeInterval displayed) {
		// prepare the new histogram UI
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				// Clean parent
				clearGraph();

				// histogram chart
				chartFrame = new ChartComposite(compositeChart, SWT.NONE,
						chart, true);

				// size
				chartFrame.setSize(compositeChart.getSize());
				// prevent y zooming
				chartFrame.setRangeZoomable(false);
				// prevent x zooming (we do it manually with wheel)
				chartFrame.setDomainZoomable(false);
				plot = chart.getXYPlot();
				// workaround for last xaxis tick not shown (jfreechart bug)
				RectangleInsets insets = plot.getInsets();
				plot.setInsets(new RectangleInsets(insets.getTop(), insets
						.getLeft(), insets.getBottom(), 25));
				// time bounds
				plot.getDomainAxis().setLowerBound(displayed.startTimestamp);
				plot.getDomainAxis().setUpperBound(displayed.endTimestamp);
			}
		});
	}
	 
	private void clearGraph() {
		for (Control c : compositeChart.getChildren()) {
			c.dispose();
		}
	}
	
	@Override
	public void dispose() {
		if (topics != null)
			topics.unregisterAll();

		super.dispose();
	}
	 
	/**
	 * Prepare the plot
	 * 
	 * @param chart
	 *            jfreechart chart
	 * @param displayed
	 *            displayed time interval
	 */
	private void preparePlot(boolean first, JFreeChart chart,
			TimeInterval displayed) {
		// Plot customization
		plot = chart.getXYPlot();
		
		// Grid and background colors
		plot.setBackgroundPaint(BACKGROUND_PAINT);
		plot.setDomainGridlinePaint(DOMAIN_GRIDLINE_PAINT);
		plot.setRangeGridlinePaint(RANGE_GRIDLINE_PAINT);
		
		// Tooltip
		XYItemRenderer renderer = plot.getRenderer();
		renderer.setBaseToolTipGenerator(TOOLTIP_GENERATOR);
		// Disable bar white stripes
		// XYLineRenderer barRenderer = (XYBarRenderer) plot.getRenderer();
		// barRenderer.setBarPainter(new StandardXYBarPainter());
		
		// X axis
		X_FORMAT.setTimeUnit(TimeUnit.getTimeUnit(trace.getTimeUnit()));
		X_FORMAT.setContext(displayed.startTimestamp, displayed.endTimestamp);
		NumberAxis xaxis = (NumberAxis) plot.getDomainAxis();
		xaxis.setTickLabelFont(TICK_LABEL_FONT);
		xaxis.setLabelFont(LABEL_FONT);
		xaxis.setNumberFormatOverride(X_FORMAT);
		TickDescriptor des = X_FORMAT
				.getTickDescriptor(displayed.startTimestamp,
						displayed.endTimestamp, numberOfTicks);
		xaxis.setTickUnit(new NumberTickUnit(des.delta));
		xaxis.addChangeListener(new AxisChangeListener() {
			@Override
			public void axisChanged(AxisChangeEvent arg) {
				long max = ((Double) plot.getDomainAxis().getRange()
						.getUpperBound()).longValue();
				long min = ((Double) plot.getDomainAxis().getRange()
						.getLowerBound()).longValue();
				TickDescriptor des = X_FORMAT.getTickDescriptor(min, max,
						numberOfTicks);
				NumberTickUnit newUnit = new NumberTickUnit(des.delta);
				NumberTickUnit currentUnit = ((NumberAxis) arg.getAxis())
						.getTickUnit();
				// ensure we don't loop
				if (!currentUnit.equals(newUnit)) {
					((NumberAxis) arg.getAxis()).setTickUnit(newUnit);
				}
			}
		});
		
		// Y axis
		NumberAxis yaxis = (NumberAxis) plot.getRangeAxis();
		yaxis.setTickLabelFont(TICK_LABEL_FONT);
		yaxis.setLabelFont(LABEL_FONT);
	}

	@Override
	public void setFocus() {
	}

	/**
	 * Refresh the list of traces
	 */
	void refresh() {
		try {
			loadTraces();
		} catch (SoCTraceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		comboTraces.removeAll();
		int index = 0;
		for (final Trace t : traces) {
			comboTraces.add(t.getAlias(), index);
			traceMap.put(index, t);
			index++;
		}
	}
		
	private class TraceAdapter extends SelectionAdapter {

		@Override
		public void widgetSelected(final SelectionEvent e) {
			trace = traceMap.get(comboTraces.getSelectionIndex());
			variableLoader.clean();
			clearGraph();
		}
	}
	
	/**
	 * Load the traces present in the database 
	 * 
	 * @throws SoCTraceException
	 */
	public void loadTraces() throws SoCTraceException {
		final SystemDBObject sysDB = FramesocManager.getInstance()
				.getSystemDB();
		final TraceQuery tQuery = new TraceQuery(sysDB);
		traces = tQuery.getList();
		sysDB.close();

		// Sort alphabetically
		Collections.sort(traces, new Comparator<Trace>() {
			@Override
			public int compare(final Trace arg0, final Trace arg1) {
				return arg0.getAlias().compareTo(arg1.getAlias());
			}
		});
	}
	
	private class LaunchDisplayAdapter extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {

			final String title = "Displaying curves";
			final Job job = new Job(title) {

				@Override
				protected IStatus run(final IProgressMonitor monitor) {
					monitor.beginTask(title, 3);

					try {
						variableLoader.getVariable(trace, monitor);
					} catch (final SoCTraceException e) {
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								MessageDialog.openInformation(
										getSite().getShell(),
										"Error",
										"Error while querying variables: "
												+ e.getMessage());
							}
						});
						return Status.CANCEL_STATUS;
					}
					
					// if return empty list
					if (variableLoader.getVariables().isEmpty()) {
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								MessageDialog.openInformation(getSite()
										.getShell(), "No variables",
										"No variable detected in this trace.");
							}
						});
						return Status.CANCEL_STATUS;
					}
				
					monitor.subTask("Building chart.");
					
					// Build dataset
					createDataset(true);
					
					// Display chart
					JFreeChart chart = createChart();
					//chart.getXYPlot().setRenderer(new XYSplineRenderer());
					
					TimeInterval displayed = new TimeInterval(trace.getMinTimestamp(), trace.getMaxTimestamp());
					
					preparePlot(false, chart, displayed) ;
					displayChart(chart, displayed);
					monitor.worked(1);
					monitor.done();
					
					return Status.OK_STATUS;
				}
			};
			job.setUser(true);
			job.schedule();
		}
	}

	@Override
	public void handle(FramesocBusTopic topic, Object data) {
		if (topic.equals(FramesocBusTopic.TOPIC_UI_TRACES_SYNCHRONIZED)
				|| topic.equals(FramesocBusTopic.TOPIC_UI_SYNCH_TRACES_NEEDED)
				|| topic.equals(FramesocBusTopic.TOPIC_UI_REFRESH_TRACES_NEEDED)) {
			refresh();
		}
	}
}
