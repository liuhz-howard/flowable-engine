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
package org.flowable.test.cmmn.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.flowable.cmmn.model.Case;
import org.flowable.cmmn.model.CmmnModel;
import org.flowable.cmmn.model.ExtensionElement;
import org.flowable.cmmn.model.FieldExtension;
import org.flowable.cmmn.model.ImplementationType;
import org.flowable.cmmn.model.PlanItem;
import org.flowable.cmmn.model.PlanItemDefinition;
import org.flowable.cmmn.model.ServiceTask;
import org.flowable.cmmn.model.Stage;
import org.flowable.cmmn.model.Task;
import org.junit.Test;

/**
 * @author Tijs Rademakers
 */
public class JavaTaskCmmnXmlConverterTest extends AbstractConverterTest {

    private static final String CMMN_RESOURCE = "org/flowable/test/cmmn/converter/java-task.cmmn";

    @Test
    public void convertXMLToModel() throws Exception {
        CmmnModel cmmnModel = readXMLFile(CMMN_RESOURCE);
        validateModel(cmmnModel);
    }

    @Test
    public void convertModelToXML() throws Exception {
        CmmnModel cmmnModel = readXMLFile(CMMN_RESOURCE);
        CmmnModel parsedModel = exportAndReadXMLFile(cmmnModel);
        validateModel(parsedModel);
    }

    public void validateModel(CmmnModel cmmnModel) {
        assertThat(cmmnModel).isNotNull();

        // Case
        assertThat(cmmnModel.getCases())
                .extracting(Case::getId, Case::getInitiatorVariableName)
                .containsExactly(tuple("javaCase", "test"));

        // Plan model
        Stage planModel = cmmnModel.getCases().get(0).getPlanModel();
        assertThat(planModel).isNotNull();
        assertThat(cmmnModel.getCases())
                .extracting(caze -> caze.getPlanModel().getId(), caze -> caze.getPlanModel().getName())
                .containsExactly(tuple("myPlanModel", "My CasePlanModel"));

        // Plan items definitions
        List<PlanItemDefinition> planItemDefinitions = planModel.getPlanItemDefinitions();
        assertThat(planItemDefinitions).hasSize(3);
        assertThat(planModel.findPlanItemDefinitionsOfType(Task.class, false)).hasSize(3);

        // Plan items
        List<PlanItem> planItems = planModel.getPlanItems();
        assertThat(planItems).hasSize(3);

        PlanItem planItemTaskA = cmmnModel.findPlanItem("planItemTaskA");
        PlanItemDefinition planItemDefinition = planItemTaskA.getPlanItemDefinition();
        assertThat(planItemTaskA.getEntryCriteria()).isEmpty();
        assertThat(planItemDefinition).isInstanceOf(ServiceTask.class);
        ServiceTask taskA = (ServiceTask) planItemDefinition;
        assertThat(taskA.getType()).isEqualTo(ServiceTask.JAVA_TASK);
        assertThat(taskA.getImplementationType()).isEqualTo(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
        assertThat(taskA.getImplementation()).isEqualTo("org.flowable.TestJavaDelegate");
        assertThat(taskA.getResultVariableName()).isEqualTo("result");
        assertThat(taskA.isAsync()).isFalse();
        assertThat(taskA.isExclusive()).isFalse();
        assertThat(taskA.isStoreResultVariableAsTransient()).isFalse();

        PlanItem planItemTaskB = cmmnModel.findPlanItem("planItemTaskB");
        planItemDefinition = planItemTaskB.getPlanItemDefinition();
        assertThat(planItemTaskB.getEntryCriteria()).hasSize(1);
        assertThat(planItemDefinition).isInstanceOf(ServiceTask.class);
        ServiceTask taskB = (ServiceTask) planItemDefinition;
        assertThat(taskB.getType()).isEqualTo(ServiceTask.JAVA_TASK);
        assertThat(taskB.getImplementationType()).isEqualTo(ImplementationType.IMPLEMENTATION_TYPE_DELEGATEEXPRESSION);
        assertThat(taskB.getImplementation()).isEqualTo("${testJavaDelegate}");
        assertThat(taskB.getResultVariableName()).isNull();
        assertThat(taskB.isAsync()).isTrue();
        assertThat(taskB.isExclusive()).isTrue();
        assertThat(taskB.isStoreResultVariableAsTransient()).isFalse();

        assertThat(taskB.getFieldExtensions())
                .extracting(FieldExtension::getFieldName, FieldExtension::getStringValue, FieldExtension::getExpression)
                .containsExactly(tuple("fieldA", "test", null), tuple("fieldB", null, "test"), tuple("fieldC", "test", null), tuple("fieldD", null, "test"));

        assertThat(taskB.getExtensionElements()).hasSize(1);
        List<ExtensionElement> extensionElements = taskB.getExtensionElements().get("taskTest");
        assertThat(extensionElements)
                .extracting(ExtensionElement::getName, ExtensionElement::getElementText)
                .containsExactly(tuple("taskTest", "hello"));

        PlanItem planItemTaskC = cmmnModel.findPlanItem("planItemTaskC");
        planItemDefinition = planItemTaskC.getPlanItemDefinition();
        assertThat(planItemTaskC.getEntryCriteria()).isEmpty();
        assertThat(planItemDefinition).isInstanceOf(ServiceTask.class);
        ServiceTask taskC = (ServiceTask) planItemDefinition;
        assertThat(taskC.getType()).isEqualTo(ServiceTask.JAVA_TASK);
        assertThat(taskC.getImplementationType()).isEqualTo(ImplementationType.IMPLEMENTATION_TYPE_EXPRESSION);
        assertThat(taskC.getImplementation()).isEqualTo("${'test'}");
        assertThat(taskC.getResultVariableName()).isEqualTo("transientResult");
        assertThat(taskC.isStoreResultVariableAsTransient()).isTrue();
    }

}
