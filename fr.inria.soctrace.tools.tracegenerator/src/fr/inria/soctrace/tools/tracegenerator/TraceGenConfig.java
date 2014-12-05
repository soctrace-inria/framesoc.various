package fr.inria.soctrace.tools.tracegenerator;

import java.util.ArrayList;

public class TraceGenConfig {

	private ArrayList<Integer> categories;
	private int numberOfEventType;
	private int numberOfProducers;
	private int numberOfLeaves;
	private boolean onlyLeavesAsProducer;
	private long numberOfEvents;

	public TraceGenConfig() {
		categories = new ArrayList<Integer>();
		numberOfEventType = 0;
		numberOfProducers = 0;
		numberOfLeaves = 0;
		onlyLeavesAsProducer = false;
		numberOfEvents = 0;
	}

	public ArrayList<Integer> getCategories() {
		return categories;
	}

	public void setCategories(ArrayList<Integer> categories) {
		this.categories = categories;
	}

	public int getNumberOfEventType() {
		return numberOfEventType;
	}

	public void setNumberOfEventType(int numberOfEventType) {
		this.numberOfEventType = numberOfEventType;
	}

	public int getNumberOfProducers() {
		return numberOfProducers;
	}

	public void setNumberOfProducers(int numberOfProducers) {
		this.numberOfProducers = numberOfProducers;
	}

	public int getNumberOfLeaves() {
		return numberOfLeaves;
	}

	public void setNumberOfLeaves(int numberOfLeaves) {
		this.numberOfLeaves = numberOfLeaves;
	}

	public boolean isOnlyLeavesAsProducer() {
		return onlyLeavesAsProducer;
	}

	public void setOnlyLeavesAsProducer(boolean onlyLeavesAsProducer) {
		this.onlyLeavesAsProducer = onlyLeavesAsProducer;
	}

	public long getNumberOfEvents() {
		return numberOfEvents;
	}

	public void setNumberOfEvents(long numberOfEvents) {
		this.numberOfEvents = numberOfEvents;
	}

}
