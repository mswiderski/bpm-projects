package org.jbpm.process.migration.handler;

import java.util.HashMap;
import java.util.Map;

import org.jbpm.process.migration.MigrationManager;
import org.jbpm.process.migration.ProcessData;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

public class ValidationHandler implements WorkItemHandler {

	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		
		
		ProcessData data = (ProcessData) workItem.getParameter("in_processdata");
		
		boolean result = MigrationManager.validate(data);
		
		Map<String, Object> results = new HashMap<String, Object>();
		results.put("out_valid", result);
		
		manager.completeWorkItem(workItem.getId(), results);
	}

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		// no-op
	}

}
