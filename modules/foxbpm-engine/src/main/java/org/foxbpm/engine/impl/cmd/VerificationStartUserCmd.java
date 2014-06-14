/**
 * Copyright 1996-2014 FoxBPM ORG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * @author kenshin
 * @author ych
 */
package org.foxbpm.engine.impl.cmd;

import java.util.List;

import org.foxbpm.engine.exception.FoxBPMException;
import org.foxbpm.engine.exception.FoxBPMIllegalArgumentException;
import org.foxbpm.engine.identity.Group;
import org.foxbpm.engine.impl.connector.Connector;
import org.foxbpm.engine.impl.entity.IdentityLinkEntity;
import org.foxbpm.engine.impl.entity.ProcessDefinitionEntity;
import org.foxbpm.engine.impl.entity.TaskEntity;
import org.foxbpm.engine.impl.entity.TokenEntity;
import org.foxbpm.engine.impl.identity.Authentication;
import org.foxbpm.engine.impl.interceptor.Command;
import org.foxbpm.engine.impl.interceptor.CommandContext;
import org.foxbpm.engine.impl.persistence.deploy.DeploymentManager;
import org.foxbpm.engine.impl.task.TaskDefinition;
import org.foxbpm.kernel.runtime.ListenerExecutionContext;

/**
 * 验证用户是否由于权限发起对应流程
 * 根据流程定义开始节点后面第一个人工节点的任务分配判断
 * @author ych
 *
 */
public class VerificationStartUserCmd implements Command<Boolean>{

	private String userId;
	private String processDefinitionKey;
	private String processDefinitionId;
	public VerificationStartUserCmd(String userId,String processDefinitionKey,String processDefinitionId) {
		this.userId = userId;
		this.processDefinitionKey = processDefinitionKey;
		this.processDefinitionId = processDefinitionId;
	}
	
	@Override
	public Boolean execute(CommandContext commandContext) {
		DeploymentManager deployCache = commandContext.getProcessEngineConfigurationImpl().getDeploymentManager();
		ProcessDefinitionEntity processDefinition = null;
		if(processDefinitionId != null && !"".equals(processDefinitionId)){
			processDefinition = deployCache.findDeployedProcessDefinitionById(processDefinitionId);
		}else if(processDefinitionKey != null && !"".equals(processDefinitionKey)){
			processDefinition = deployCache.findDeployedLatestProcessDefinitionByKey(processDefinitionKey);
		}else{
			throw new FoxBPMIllegalArgumentException("验证发起权限时，流程编号和流程唯一键不能同时为空");
		}
		
		TaskDefinition taskDefinition = processDefinition.getSubTaskDefinition();
		TaskEntity taskEntity = new TaskEntity();
		TokenEntity tokenEntity = new TokenEntity();
		tokenEntity.setAssignTask(taskEntity);
		for (Connector connector : taskDefinition.getActorConnectors()) {
			try {
				connector.notify((ListenerExecutionContext) tokenEntity);
			} catch (Exception e) {
				if(e instanceof FoxBPMException)
					throw (FoxBPMException)e;
				else{
					throw new FoxBPMException("开始节点选择人处理器执行失败，处理器："+connector.getConnectorId() , e);
				}
			}
		}
		
		if(userId.equals(taskEntity.getAssignee())){
			return true;
		}
		if("fixflow_allusers".equals(taskEntity.getAssignee())){
			return true;
		}
		List<Group> groups = Authentication.selectGroupByUserId(userId);
		for(Group group : groups){
			for(IdentityLinkEntity identity : taskEntity.getIdentityLinks()){
				if("fixflow_allusers".equals(identity.getUserId())){
					return true;
				}
				if(group.getGroupType().equals(identity.getGroupType()) && group.getGroupId().equals(identity.getGroupId())){
					return true;
				}
			}
		}
		return false;
	}
}
