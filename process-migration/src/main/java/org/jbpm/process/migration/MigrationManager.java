package org.jbpm.process.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;

import org.drools.core.command.impl.CommandBasedStatefulKnowledgeSession;
import org.drools.core.command.impl.KnowledgeCommandContext;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.jbpm.process.audit.JPAAuditLogService;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.runtime.manager.impl.jpa.EntityManagerFactoryManager;
import org.jbpm.workflow.core.NodeContainer;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.jbpm.workflow.core.node.CompositeNode;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.impl.NodeInstanceImpl;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.WorkflowProcess;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.kie.internal.runtime.manager.RuntimeManagerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationManager {
	
	private static final Logger logger = LoggerFactory.getLogger(MigrationManager.class);

	public static boolean validate(ProcessData processData) {
		if (processData == null) {
			return false;
		}
		if (isEmpty(processData.getDeploymentId())) {
			logger.error("No deployment id set");
			return false;
		}
		if (isEmpty(processData.getToDeploymentId())) {
			logger.error("No target deployment id set");
			return false;
		}
		if (isEmpty(processData.getToProcessId())) {
			logger.error("No target process id set");
			return false;
		}
		if (processData.getProcessInstanceId() == null) {
			logger.error("No process instance id set"); 
			return false;
		}
		
		if (!RuntimeManagerRegistry.get().isRegistered(processData.getDeploymentId())) {
			logger.error("No deployment found for {}", processData.getDeploymentId());
			
			return false;
		}
		
		if (!RuntimeManagerRegistry.get().isRegistered(processData.getToDeploymentId())) {
			logger.error("No deployment found for {}", processData.getToDeploymentId());
			
			return false;
		}
		
		InternalRuntimeManager manager = (InternalRuntimeManager) RuntimeManagerRegistry.get().getManager(processData.getToDeploymentId());
		if (manager.getEnvironment().getKieBase().getProcess(processData.getToProcessId()) == null) {
			logger.error("No process found for {} in deployment {}", processData.getToProcessId(), processData.getToDeploymentId());
			
			return false;
		}
		
		String auditPu = manager.getDeploymentDescriptor().getAuditPersistenceUnit();
		
		EntityManagerFactory emf = EntityManagerFactoryManager.get().getOrCreate(auditPu);

		JPAAuditLogService auditService = new JPAAuditLogService(emf);
		ProcessInstanceLog log = auditService.findProcessInstance(processData.getProcessInstanceId());
		if (log == null || log.getStatus() != ProcessInstance.STATE_ACTIVE) {
			logger.error("No process instance found or it is not active (id {} in status {}",
					processData.getProcessInstanceId(), (log == null?"-1":log.getStatus()));
			
			return false;
		}
		auditService.dispose();

		return true;
	}
	
	public static Map<String, List<NodeInformation>> collectNodeInfo(ProcessData processData) {
		Map<String, List<NodeInformation>> result = new HashMap<String, List<NodeInformation>>();
		
		List<NodeInformation> activeInstances = new ArrayList<NodeInformation>();
		List<NodeInformation> newProcessNodes = new ArrayList<NodeInformation>();
		
		result.put("active", activeInstances);
		result.put("available", newProcessNodes);		
		
		InternalRuntimeManager currentManager = (InternalRuntimeManager) RuntimeManagerRegistry.get().getManager(processData.getDeploymentId());		
		InternalRuntimeManager toBeManager = (InternalRuntimeManager) RuntimeManagerRegistry.get().getManager(processData.getToDeploymentId());
		
		org.kie.api.definition.process.Process toBeProcess = toBeManager.getEnvironment().getKieBase().getProcess(processData.getToProcessId());
		
		collectNodeInformation((NodeContainer) toBeProcess, newProcessNodes);
		
		String auditPu = currentManager.getDeploymentDescriptor().getAuditPersistenceUnit();
		
		EntityManagerFactory emf = EntityManagerFactoryManager.get().getOrCreate(auditPu);
		
		JPAAuditLogService auditService = new JPAAuditLogService(emf);
		
		List<NodeInstanceLog> logs = auditService.findNodeInstances(processData.getProcessInstanceId());
		collectActiveNodeInformation(logs, activeInstances);
		
		auditService.dispose();
		return result;
	}
	
	public static String migrate(ProcessData processData, List<NodeMapping> nodeMapping) {
		StringBuffer outcomeBuffer = new StringBuffer();
		try {
			InternalRuntimeManager currentManager = (InternalRuntimeManager) RuntimeManagerRegistry.get().getManager(processData.getDeploymentId());		
			InternalRuntimeManager toBeManager = (InternalRuntimeManager) RuntimeManagerRegistry.get().getManager(processData.getToDeploymentId());
			
			org.kie.api.definition.process.Process toBeProcess = toBeManager.getEnvironment().getKieBase().getProcess(processData.getToProcessId());
			
			String auditPu = currentManager.getDeploymentDescriptor().getAuditPersistenceUnit();
			
			EntityManagerFactory emf = EntityManagerFactoryManager.get().getOrCreate(auditPu);
			EntityManager em = emf.createEntityManager();
	         // update variable instance log information with new deployment id and process id
            Query varLogQuery = em.createQuery("update VariableInstanceLog set externalId = :depId, processId = :procId where processInstanceId = :procInstanceId");
            varLogQuery
                .setParameter("depId", processData.getToDeploymentId())
                .setParameter("procId", processData.getToProcessId())
                .setParameter("procInstanceId", processData.getProcessInstanceId());
            
            int varsUpdated = varLogQuery.executeUpdate();
            logger.info("Variable instances updated = {} for process instance id {}", varsUpdated, processData.getProcessInstanceId());            
			
			// update node instance log information with new deployment id and process id
			Query nodeLogQuery = em.createQuery("update NodeInstanceLog set externalId = :depId, processId = :procId where processInstanceId = :procInstanceId");
			nodeLogQuery
				.setParameter("depId", processData.getToDeploymentId())
				.setParameter("procId", processData.getToProcessId())
				.setParameter("procInstanceId", processData.getProcessInstanceId());
			
			int nodesUpdated = nodeLogQuery.executeUpdate();
			logger.info("Node instances updated = {} for process instance id {}", nodesUpdated, processData.getProcessInstanceId());
			
			// update process instance log with new deployment and process id
			Query pInstanceLogQuery = em.createQuery("update ProcessInstanceLog set externalId = :depId, processId = :procId, processVersion= :procVersion where processInstanceId = :procInstanceId");
			pInstanceLogQuery
				.setParameter("depId", processData.getToDeploymentId())
				.setParameter("procId", processData.getToProcessId())
				.setParameter("procVersion", toBeProcess.getVersion())
				.setParameter("procInstanceId", processData.getProcessInstanceId());
			
			int pInstancesUpdated = pInstanceLogQuery.executeUpdate();
			logger.info("Process instances updated = {} for process instance id {}", pInstancesUpdated, processData.getProcessInstanceId());

			try {
    	         // update task audit instance log with new deployment and process id
                Query taskVarLogQuery = em.createQuery("update TaskVariableImpl set processId = :procId where processInstanceId = :procInstanceId");
                taskVarLogQuery
                    .setParameter("procId", processData.getToProcessId())
                    .setParameter("procInstanceId", processData.getProcessInstanceId());
                
                int taskVarUpdated = taskVarLogQuery.executeUpdate();
                logger.info("Task variables updated = {} for process instance id {}", taskVarUpdated, processData.getProcessInstanceId());
			} catch (Throwable e) {
                logger.warn("Cannot update task variables (added in version 6.3) due to {}", e.getMessage());
            }

			
			// update task audit instance log with new deployment and process id
			Query auditTaskLogQuery = em.createQuery("update AuditTaskImpl set deploymentId = :depId, processId = :procId where processInstanceId = :procInstanceId");
			auditTaskLogQuery
				.setParameter("depId", processData.getToDeploymentId())
				.setParameter("procId", processData.getToProcessId())
				.setParameter("procInstanceId", processData.getProcessInstanceId());
			
			int auditTaskUpdated = auditTaskLogQuery.executeUpdate();
			logger.info("Task audit updated = {} for process instance id {}", auditTaskUpdated, processData.getProcessInstanceId());
			
			// update task  instance log with new deployment and process id
			Query taskLogQuery = em.createQuery("update TaskImpl set deploymentId = :depId, processId = :procId where processInstanceId = :procInstanceId");
			taskLogQuery
				.setParameter("depId", processData.getToDeploymentId())
				.setParameter("procId", processData.getToProcessId())
				.setParameter("procInstanceId", processData.getProcessInstanceId());
			
			int taskUpdated = taskLogQuery.executeUpdate();
			logger.info("Tasks updated = {} for process instance id {}", taskUpdated, processData.getProcessInstanceId());
			
			
			try {
				// update context mapping info with new deployment
				Query contextInfoQuery = em.createQuery("update ContextMappingInfo set ownerId = :depId where contextId = :procInstanceId");
				contextInfoQuery
					.setParameter("depId", processData.getToDeploymentId())
					.setParameter("procInstanceId", processData.getProcessInstanceId().toString());
				
				int contextInfoUpdated = contextInfoQuery.executeUpdate();
				logger.info("Context info updated = {} for process instance id {}", contextInfoUpdated, processData.getProcessInstanceId());
			} catch (Throwable e) {
				logger.warn("Cannot update context mapping owner (added in version 6.2) due to {}", e.getMessage());
			}
		
			KieSession current = JPAKnowledgeService.newStatefulKnowledgeSession(currentManager.getEnvironment().getKieBase(), null, currentManager.getEnvironment().getEnvironment());
			KieSession tobe = JPAKnowledgeService.newStatefulKnowledgeSession(toBeManager.getEnvironment().getKieBase(), null, toBeManager.getEnvironment().getEnvironment());
			
			Map<String, String> convertedNodeMapping = new HashMap<String, String>();
			if (nodeMapping != null) {
				for (NodeMapping map : nodeMapping) {
					convertedNodeMapping.put(map.getSourceNodeId(), map.getTargetNodeId());
				}
			}
			upgradeProcessInstance(extractIfNeeded(current), extractIfNeeded(tobe), 
					processData.getProcessInstanceId(), processData.getToProcessId(), convertedNodeMapping);
			
			em.flush();
			em.clear();
			em.close();
			
			current.destroy();
			tobe.destroy();
			outcomeBuffer.append("Migration of process instance (" + processData.getProcessInstanceId() + ") completed successfully to process " + processData.getToProcessId());
		} catch (Exception e) {
			outcomeBuffer.append("Migration of process instance (" + processData.getProcessInstanceId() + ") failed due to " + e.getMessage());
			logger.error("Migration of process instance ({}) failed", processData.getProcessInstanceId(), e);
		}
		
		return outcomeBuffer.toString();
	}
	
    private static void upgradeProcessInstance(KieRuntime oldkruntime,
    		KieRuntime kruntime,
    		long processInstanceId,
    		String processId,
    		Map<String, String> nodeMapping) {
    	if (nodeMapping == null) {
    		nodeMapping = new HashMap<String, String>();
    	}
        WorkflowProcessInstanceImpl processInstance = (WorkflowProcessInstanceImpl) oldkruntime.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            throw new IllegalArgumentException("Could not find process instance " + processInstanceId);
        }
        if (processId == null) {
            throw new IllegalArgumentException("Null process id");
        }
        WorkflowProcess process = (WorkflowProcess) kruntime.getKieBase().getProcess(processId);
        if (process == null) {
            throw new IllegalArgumentException("Could not find process " + processId);
        }
        if (processInstance.getProcessId().equals(processId)) {
            return;
        }
        synchronized (processInstance) {
        	org.kie.api.definition.process.Process oldProcess = processInstance.getProcess();
	        processInstance.disconnect();
	        processInstance.setProcess(oldProcess);
	        updateNodeInstances(processInstance, nodeMapping, (NodeContainer) process);
	        processInstance.setKnowledgeRuntime((InternalKnowledgeRuntime) kruntime);
	        processInstance.setProcess(process);
	        processInstance.reconnect();
		}
    }
    
    private static void updateNodeInstances(NodeInstanceContainer nodeInstanceContainer, Map<String, String> nodeMapping, NodeContainer nodeContainer) {
    	if (nodeMapping == null || nodeMapping.isEmpty()) {
    		return;
    	}
        for (NodeInstance nodeInstance: nodeInstanceContainer.getNodeInstances()) {
            String oldNodeId = (String) ((NodeImpl) ((org.jbpm.workflow.instance.NodeInstance) nodeInstance).getNode()).getMetaData().get("UniqueId");
            String newNodeId = nodeMapping.get(oldNodeId);
            if (newNodeId == null) {
                continue;
            }
            Long upgradedNodeId = findNodeIfByUniqueId(newNodeId, nodeContainer);
            if (upgradedNodeId == null) {
            	try {
            		upgradedNodeId = Long.parseLong(newNodeId);
            	} catch (NumberFormatException e) {
            		continue;
            	}
            }
            
            ((NodeInstanceImpl) nodeInstance).setNodeId(upgradedNodeId);
            if (nodeInstance instanceof NodeInstanceContainer) {
            	updateNodeInstances((NodeInstanceContainer) nodeInstance, nodeMapping, nodeContainer);
            }
        }

    }
    
    private static Long findNodeIfByUniqueId(String uniqueId, NodeContainer nodeContainer) {
    	Long result = null;
    	
    	for (Node node : nodeContainer.getNodes()) {
    		if (uniqueId.equals(node.getMetaData().get("UniqueId"))) {
    			return node.getId();
    		}
    		if (node instanceof NodeContainer) {
    			result = findNodeIfByUniqueId(uniqueId, (NodeContainer) node);
    			if (result != null) {
    				return result;
    			}
    		}
    	}
    	
    	return result;
    }
    
    private static KieRuntime extractIfNeeded(KieSession ksession) {
    	if (ksession instanceof CommandBasedStatefulKnowledgeSession) {
    		return ((KnowledgeCommandContext)((CommandBasedStatefulKnowledgeSession) ksession).getCommandService().getContext()).getKieSession();
    	}
    	
    	return ksession;
    }
    
    private static void collectNodeInformation(NodeContainer container, List<NodeInformation> nodes) {
    	for (Node node : container.getNodes()) {
			if (node instanceof CompositeNode) {
				collectNodeInformation(((CompositeNode) node), nodes);
			} else {
				if (node.getName() == null || node.getName().trim().isEmpty()) {
					continue;
				}
				nodes.add(new NodeInformation(node.getName(), (String) node.getMetaData().get("UniqueId"), String.valueOf(node.getId())));
			}
		}
    }
    
    private static void collectActiveNodeInformation(List<NodeInstanceLog> logs, List<NodeInformation> activeNodes) {
    	Map<String, NodeInstanceLog> nodeInstances = new HashMap<String, NodeInstanceLog>();
    	for (NodeInstanceLog nodeInstance: logs) {
			if (nodeInstance.getType() == NodeInstanceLog.TYPE_ENTER) {
				nodeInstances.put(nodeInstance.getNodeInstanceId(), nodeInstance);
			} else {
				nodeInstances.remove(nodeInstance.getNodeInstanceId());
			}
		}
		if (!nodeInstances.isEmpty()) {
			for (NodeInstanceLog node : nodeInstances.values()) {
				activeNodes.add(new NodeInformation(node.getNodeName(), node.getNodeId(), ""));
			}
		}
    }
	
	private static boolean isEmpty(String value) {
		if (value == null || value.isEmpty()) {
			return true;
		}
		
		return false;
	}
}
