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
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.draw2d.RectangleFigure;
import org.eclipse.draw2d.XYLayout;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
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
import fr.inria.soctrace.framesoc.ui.colors.FramesocColorManager;
import fr.inria.soctrace.framesoc.ui.model.TimeInterval;
import fr.inria.soctrace.lib.model.EventProducer;
import fr.inria.soctrace.lib.model.EventType;
import fr.inria.soctrace.lib.model.State;
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
	
	private EventLoader eventLoader;
	
	private Composite compositeChart;
	private ChartComposite chartFrame;
	private XYDataset dataset;
	private XYPlot plot;
	private Trace trace = null;
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
	private static final double CANVAS_PROPORTION = 0.1;
	protected static final int STATE_BAR_WIDTH = 15;

	private final Font TICK_LABEL_FONT = new Font("Tahoma", 0, 11);
	private final Font LABEL_FONT = new Font("Tahoma", 0, 12);
	private final TimestampFormat X_FORMAT = new TimestampFormat();
	private final DecimalFormat Y_FORMAT = new DecimalFormat("0");
	private final XYToolTipGenerator TOOLTIP_GENERATOR = new StandardXYToolTipGenerator(
			TOOLTIP_FORMAT, X_FORMAT, Y_FORMAT);
	private Canvas canvas;
	private Figure root;
	private Composite compositeGraph;

	public VarVisuView() {
		try {
			eventLoader = new EventLoader();

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
		
		compositeGraph = new Composite(parent, SWT.NONE);
		compositeGraph.setLayout(new GridLayout(1, false));
		compositeGraph.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true,
				true, 1, 1));
		
		canvas = new Canvas(compositeGraph, SWT.DOUBLE_BUFFERED);
		canvas.setSize(compositeGraph.getSize().x, (int) (CANVAS_PROPORTION * compositeGraph.getSize().y));
		canvas.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		GridData gridData = new GridData();
		gridData.horizontalAlignment = SWT.FILL;
		gridData.grabExcessHorizontalSpace = true;
		canvas.setLayoutData(gridData);

		root = new Figure();
		root.setFont(parent.getFont());
		final XYLayout layout = new XYLayout();
		root.setLayoutManager(layout);
		final LightweightSystem lws = new LightweightSystem(canvas);
		lws.setContents(root);
		lws.setControl(canvas);
		root.setSize(compositeGraph.getSize().x, (int) (CANVAS_PROPORTION * compositeGraph.getSize().y));
		
		// Chart Composite
		compositeChart = new Composite(compositeGraph, SWT.BORDER);
		compositeChart.setLayoutData(new GridData(SWT.FILL));
		gridData = new GridData();
		gridData.horizontalAlignment = SWT.FILL;
		gridData.grabExcessHorizontalSpace = true;
		gridData.verticalAlignment = SWT.FILL;
		gridData.grabExcessVerticalSpace = true;
		compositeChart.setLayoutData(gridData);
		FillLayout fl_compositeChart = new FillLayout(SWT.HORIZONTAL);
		compositeChart.setLayout(fl_compositeChart);
		compositeChart.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				int width = Math.max(compositeChart.getSize().x - (groupTraces.getSize().y + canvas.getSize().y), 1);
				numberOfTicks = Math.max(width / TIMESTAMP_MAX_SIZE, 1);
			}
		});
		
		compositeGraph.addListener (SWT.Resize,  new Listener () {
			    public void handleEvent (Event e) {
			    	if (chartFrame == null)
						return;
					buildTimeLine();
			    }
			  });

		refresh();
	}

	private void createDataset(boolean smoothTransition) {
		dataset = new XYSeriesCollection();

		for (EventProducer ep : eventLoader.getVariables().keySet()) {
			for (EventType type : eventLoader.getVariables().get(ep)
					.keySet()) {
				// Create a new curve per event producer
				XYSeries series = new XYSeries(ep.getName() + "_" + type.getName());
				for (Variable variable : eventLoader.getVariables().get(ep)
						.get(type)) {
					series.add(variable.getTimestamp(), variable.getValue());
					
					if (!smoothTransition)
						// Add the same value at the end of time stamp
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

				// curve chart
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
						.getLeft(), insets.getBottom(), 25)); // time bounds
				plot.getDomainAxis().setLowerBound(displayed.startTimestamp);
				plot.getDomainAxis().setUpperBound(displayed.endTimestamp);
			}
		});
	}
	 
	private void clearGraph() {
		if (root != null && canvas != null) {
			root.removeAll();
			canvas.redraw();
		}
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
	
	private void buildTimeLine() {
		if(root == null || trace == null || chartFrame == null)
			return;
		root.removeAll();
		
		double totalWidth = chartFrame.getScreenDataArea().width;
		int startOffset = chartFrame.getScreenDataArea().x; 
		long totalTime = trace.getMaxTimestamp() - trace.getMinTimestamp();
		double timePerPixel = totalTime / totalWidth;
		
		for(EventProducer ep: eventLoader.getStates().keySet()) {
			for(EventType type: eventLoader.getStates().get(ep).keySet()){
				for(State state: eventLoader.getStates().get(ep).get(type)){
					RectangleFigure rectangle = new RectangleFigure();
					rectangle.setBackgroundColor(FramesocColorManager.getInstance()
							.getEventTypeColor(type.getName()).getSwtColor());
					rectangle.setForegroundColor(FramesocColorManager.getInstance()
							.getEventTypeColor(type.getName()).getSwtColor());
					rectangle.setToolTip(new Label(" " + state.getType().getName() + " "));
					int x0 = (int) ((state.getTimestamp() - trace.getMinTimestamp()) / timePerPixel);
					int x1 = (int) ((state.getEndTimestamp() - trace.getMinTimestamp()) / timePerPixel);
					x0 += startOffset;
					x1 += startOffset;
					
					root.add(rectangle, 
							new Rectangle(new Point(x0, 0), new Point(x1,
									root.getClientArea().height())));
				}
			}
		}
		root.validate();
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
			eventLoader.clean();
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

					if (trace == null) {
						displayError("No trace selected.", "Error");
						return Status.CANCEL_STATUS;
					}

					try {
						eventLoader.queryVariable(trace, monitor);
					} catch (final SoCTraceException e) {
						displayError(
								"Error while querying variables: "
										+ e.getMessage(), "Error");
						return Status.CANCEL_STATUS;
					}

					// If returned empty list
					if (eventLoader.getVariables().isEmpty()) {
						displayError("No variable detected in this trace.",
								"No variable");
						return Status.CANCEL_STATUS;
					}
				
					monitor.subTask("Building chart.");

					// Build dataset
					createDataset(true);

					// Display chart
					JFreeChart chart = createChart();

					TimeInterval displayed = new TimeInterval(
							trace.getMinTimestamp(), trace.getMaxTimestamp());

					preparePlot(false, chart, displayed);
					displayChart(chart, displayed);
					
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							buildTimeLine();
						}
					});

					monitor.worked(1);
					monitor.done();
					
					return Status.OK_STATUS;
				}
			};
			job.setUser(true);
			job.schedule();

		}
		
		private void displayError(final String errorMessage, final String title) {
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					MessageDialog.openInformation(getSite().getShell(), title,
							errorMessage);
				}
			});
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
