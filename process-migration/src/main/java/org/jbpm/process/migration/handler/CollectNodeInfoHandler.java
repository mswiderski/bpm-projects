package org.jbpm.process.migration.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.process.migration.MigrationManager;
import org.jbpm.process.migration.NodeInformation;
import org.jbpm.process.migration.ProcessData;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

public class CollectNodeInfoHandler implements WorkItemHandler {

	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		// no-op
	}

	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		ProcessData data = (ProcessData) workItem.getParameter("in_processdata");
		
		Map<String, List<NodeInformation>> info = MigrationManager.collectNodeInfo(data);
		
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("out_active", info.get("active"));
		result.put("out_available", info.get("available"));
		
		manager.completeWorkItem(workItem.getId(), result);
	}

}
