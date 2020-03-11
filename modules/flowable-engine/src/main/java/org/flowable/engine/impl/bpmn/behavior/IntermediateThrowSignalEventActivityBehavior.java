/* Licensed under the Apache License, Version 2.0 (the "License");
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
 */

package org.flowable.engine.impl.bpmn.behavior;

import java.util.List;

import org.flowable.bpmn.model.Signal;
import org.flowable.bpmn.model.SignalEventDefinition;
import org.flowable.bpmn.model.ThrowEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.compatibility.Flowable5CompatibilityHandler;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.event.impl.FlowableEventBuilder;
import org.flowable.engine.impl.event.EventDefinitionExpressionUtil;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.EventSubscriptionUtil;
import org.flowable.engine.impl.util.Flowable5Util;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.entitylink.api.EntityLink;
import org.flowable.entitylink.api.EntityLinkType;
import org.flowable.eventsubscription.service.EventSubscriptionService;
import org.flowable.eventsubscription.service.impl.persistence.entity.SignalEventSubscriptionEntity;

/**
 * @author Tijs Rademakers
 */
public class IntermediateThrowSignalEventActivityBehavior extends AbstractBpmnActivityBehavior {

    private static final long serialVersionUID = 1L;

    protected SignalEventDefinition signalEventDefinition;
    protected boolean processInstanceScope;

    public IntermediateThrowSignalEventActivityBehavior(ThrowEvent throwEvent, SignalEventDefinition signalEventDefinition, Signal signal) {
        if (signal != null) {
            if (Signal.SCOPE_PROCESS_INSTANCE.equals(signal.getScope())) {
                this.processInstanceScope = true;
            }
        }

        this.signalEventDefinition = signalEventDefinition;
    }

    @Override
    public void execute(DelegateExecution execution) {

        CommandContext commandContext = Context.getCommandContext();

        String eventSubscriptionName = EventDefinitionExpressionUtil.determineSignalName(commandContext, signalEventDefinition,
            ProcessDefinitionUtil.getBpmnModel(execution.getProcessDefinitionId()), execution);


        EventSubscriptionService eventSubscriptionService = CommandContextUtil.getEventSubscriptionService(commandContext);
        List<SignalEventSubscriptionEntity> subscriptionEntities = null;
        if (processInstanceScope) {
            subscriptionEntities = eventSubscriptionService.findSignalEventSubscriptionsByProcessInstanceAndEventName(
                            execution.getProcessInstanceId(), eventSubscriptionName);
            
            if (CommandContextUtil.getProcessEngineConfiguration(commandContext).isEnableEntityLinks()) {
                List<EntityLink> entityLinks = CommandContextUtil.getEntityLinkService(commandContext).findEntityLinksByReferenceScopeIdAndType(
                                execution.getProcessInstanceId(), ScopeTypes.BPMN, EntityLinkType.CHILD);
                if (entityLinks != null) {
                    for (EntityLink entityLink : entityLinks) {
                        if (ScopeTypes.BPMN.equals(entityLink.getScopeType())) {
                            subscriptionEntities.addAll(eventSubscriptionService.findSignalEventSubscriptionsByProcessInstanceAndEventName(
                                            entityLink.getScopeId(), eventSubscriptionName));
                            
                        } else if (ScopeTypes.CMMN.equals(entityLink.getScopeType())) {
                            subscriptionEntities.addAll(eventSubscriptionService.findSignalEventSubscriptionsByScopeAndEventName(
                                            entityLink.getScopeId(), ScopeTypes.CMMN, eventSubscriptionName));
                        }
                    }
                }
            }
            
        } else {
            subscriptionEntities = eventSubscriptionService
                    .findSignalEventSubscriptionsByEventName(eventSubscriptionName, execution.getTenantId());
        }

        for (SignalEventSubscriptionEntity signalEventSubscriptionEntity : subscriptionEntities) {
            CommandContextUtil.getProcessEngineConfiguration().getEventDispatcher().dispatchEvent(
                    FlowableEventBuilder.createSignalEvent(FlowableEngineEventType.ACTIVITY_SIGNALED, signalEventSubscriptionEntity.getActivityId(), eventSubscriptionName,
                            null, signalEventSubscriptionEntity.getExecutionId(), signalEventSubscriptionEntity.getProcessInstanceId(),
                            signalEventSubscriptionEntity.getProcessDefinitionId()));

            if (Flowable5Util.isFlowable5ProcessDefinitionId(commandContext, signalEventSubscriptionEntity.getProcessDefinitionId())) {
                Flowable5CompatibilityHandler compatibilityHandler = Flowable5Util.getFlowable5CompatibilityHandler();
                compatibilityHandler.signalEventReceived(signalEventSubscriptionEntity, null, signalEventDefinition.isAsync());
                
            } else {
                EventSubscriptionUtil.eventReceived(signalEventSubscriptionEntity, null, signalEventDefinition.isAsync());
            }
        }

        CommandContextUtil.getAgenda(commandContext).planTakeOutgoingSequenceFlowsOperation((ExecutionEntity) execution, true);
    }

}
