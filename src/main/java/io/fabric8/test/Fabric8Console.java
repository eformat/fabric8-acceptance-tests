/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.test;

import com.google.common.base.Function;
import com.offbytwo.jenkins.JenkinsServer;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.assertions.KubernetesNamespaceAssert;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.selenium.SeleniumTests;
import io.fabric8.selenium.WebDriverFacade;
import io.fabric8.selenium.forge.NewProjectFormData;
import io.fabric8.selenium.forge.ProjectsPage;
import io.fabric8.selenium.support.NameGenerator;
import io.fabric8.utils.Asserts;
import io.fabric8.utils.Block;
import io.fabric8.utils.Millis;
import net.serenitybdd.core.annotations.findby.By;
import net.thucydides.core.annotations.Step;
import net.thucydides.core.pages.PageObject;
import org.assertj.core.util.Strings;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import static io.fabric8.kubernetes.assertions.Assertions.assertThat;
import static io.fabric8.selenium.SeleniumTests.logInfo;
import static io.fabric8.test.JenkinsAsserts.assertJobLastBuildIsSuccessful;
import static io.fabric8.test.JenkinsAsserts.createJenkinsServer;
import static org.junit.Assert.assertFalse;
import static org.openqa.selenium.support.ui.ExpectedConditions.titleContains;

public class Fabric8Console extends PageObject {

    final String jenkinsName = ServiceNames.JENKINS;
    final String nexusName = ServiceNames.NEXUS;
    final String gogsName = ServiceNames.GOGS;
    final String fabric8Console = ServiceNames.FABRIC8_CONSOLE;
    final String fabric8Forge = ServiceNames.FABRIC8_FORGE;

    final String namespace = "fabric8-test";
    private KubernetesClient client;

    public Fabric8Console(WebDriver driver)
    {
        super(driver);
        this.client = new DefaultKubernetesClient();
    }

    public void open_login_page() {
        String url = System.getProperty("url", "http://fabric8.vagrant.f8");
        this.getDriver().get(url);
    }

    @Step("Given a user is logged in")
    public void log_in(String user, String password) {

        find(By.id("inputUsername")).sendKeys(user);
        find(By.id("inputPassword")).sendKeys(password,Keys.ENTER);

        new WebDriverWait(this.getDriver(), 5).until(titleContains("OpenShift Web Console"));
    }

    @Step("Given the CD pipeline app is running")
    public void CD_pipeline_is_running() throws Exception {
        final KubernetesNamespaceAssert asserts = assertThat(client, namespace);
        asserts.replicationController(jenkinsName).isNotNull();
        asserts.replicationController(nexusName).isNotNull();
        asserts.replicationController(gogsName).isNotNull();
        asserts.replicationController(fabric8Forge).isNotNull();
        asserts.replicationController(fabric8Console).isNotNull();

        asserts.podsForService(fabric8Console).runningStatus().assertSize().isGreaterThan(0);

        logInfo("CD Pipeline is running");

        Asserts.assertWaitFor(Millis.minutes(10), new Block() {
            @Override
            public void invoke() throws Exception {
                asserts.podsForReplicationController(fabric8Forge).logs().containsText("oejs.Server:main: Started");
            }
        });
        logInfo("Forge is started");
    }

    @Step
    public void create_camel_CDI_project() throws Exception {

        KubernetesNamespaceAssert asserts = assertThat(client, namespace);

        SeleniumTests.assertWebDriverForService(client, namespace, fabric8Console, this.getDriver(), new Function<WebDriverFacade, String>() {
            @Override
            public String apply(WebDriverFacade facade) {
                ProjectsPage projects = new ProjectsPage(facade);
                String projectName = "p" + NameGenerator.generateName();

                // lets find the archetype version to use from the forge pod
                Pod forgePod = asserts.podForReplicationController(fabric8Forge);
                String archetypesVersionEnvVar = "FABRIC8_ARCHETYPES_VERSION";
                String archetypesVersion = KubernetesHelper.getPodEnvVar(forgePod, archetypesVersionEnvVar);
                logInfo("the " + fabric8Forge + " pod is using the fabric8 archetypes version: " + archetypesVersion);
                assertFalse("No value for $FABRIC8_ARCHETYPES_VERSION found in pod " + forgePod.getMetadata().getName(), Strings.isNullOrEmpty(archetypesVersion));

                String archetypeFilter = "io.fabric8.archetypes:cdi-camel-archetype:" + archetypesVersion;
                NewProjectFormData projectData = new NewProjectFormData(projectName, archetypeFilter, "maven/CanaryReleaseAndStage.groovy");
                projects.createProject(projectData);

                // now lets assert that the jenkins build has been created etc
                try {
                    JenkinsServer jenkins = createJenkinsServer(facade.getServiceUrl(ServiceNames.JENKINS));
                    String jobName = projects.getGogsUserName() + "-" + projectName;
                    assertJobLastBuildIsSuccessful(Millis.minutes(20), jenkins, jobName);

                } catch (Exception e) {
                    System.out.println("Failed: " + e);
                    throw new AssertionError(e);
                }
                return null;
            }
        });
    }
}
